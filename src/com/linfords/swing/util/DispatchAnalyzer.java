package com.linfords.swing.util;

import java.awt.AWTEvent;
import java.lang.Thread.State;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import com.linfords.swing.util.AsyncPrinter.Action;
import com.linfords.swing.util.AsyncPrinter.DividerStyle;
import com.linfords.swing.util.AsyncPrinter.Expression;
import com.linfords.swing.util.ThreadProfileSynopsis.StackTraceNode;

class DispatchAnalyzer implements Observer {
   private static final Logger log = Logger.getLogger(DispatchAnalyzer.class
         .getName());

   private final static ThreadMXBean THREAD_BEAN = ManagementFactory
         .getThreadMXBean();

   // private final static String EDT_EVENT_QUEUE_PREFIX = "AWT-EventQueue";
   private final static String STACK_INDENT = "   ";

   /** Elapsed time interval for forced logging prior to dispatch completion. */
   private final static long UNREASONABLE_DISPATCH_FORCED_LOGGING_INTERVAL_NANOS = Util.NANOS_PER_SEC * 1;

   /** An "unreasonable dispatch" will get logged when it finishes. */
   private final static long UNREASONABLE_DISPATCH_DURATION_NANO = Util.NANO_PER_MILLI * 150;

   /**
    * Checking for clogs also detects risky practices that can lead to clogs. This time
    * interval is intended to allow for risky practices to be detected before the Russian
    * Roulette game is over. Risky practices include locking an AWT component off the EDT.
    * This doesn't always cause a "clog"; however, changes in timing can turn a near miss
    * into a real "clog".
    */
   private final static long THREAD_CONTENTION_ANALYSIS_INTERVAL_NANOS = Util.NANO_PER_MILLI * 25;

   /**
    * StackTraceElement elements that take "too long" (as defined here) are stored as part
    * of the ThreadProfileSynopsis.
    */
   private final static long UNREASONABLE_METHOD_DURATION_NANO = Util.NANO_PER_MILLI / 10;

   /** Tree of significant StackFrameElements detected during this dispatch */
   private ThreadProfileSynopsis<Integer, StackTraceNode> threadSynopsis = new ThreadProfileSynopsis<Integer, StackTraceNode>();

   private boolean ezeniaCodeDetectedDuringDispatch = false;
   private boolean dispatchPutItselfIntoWaitState = false;

   private StackTraceElement[] previousStack = null;
   private long previousStackNanos = 0;

   /**
    * Unique ID for each dispatch that takes UNREASONABLE_DISPATCH_DURATION_NANO or
    * longer. Is assigned by a calling the synchronized {@code getNewHangNumber} method.
    * */
   private int hangID = -1;

   /** Used to assign each hang its unique ID. */
   private static int globalHangCount = 0;

   private synchronized static int getNewHangNumber() {
      return ++globalHangCount;
   }

   /**
    * Number of times this dispatch has been logged. A number greater than 1 means that
    * the dispatch has taken longer than
    * UNREASONABLE_DISPATCH_FORCED_LOGGING_INTERVAL_NANOS
    */
   private int dispatchAnalysisLoggedCount = 0;

   /**
    * Number of times this dispatch has been checked for a "clog". The frequency of
    * checking is controlled by EDT_CLOG_CHECK_INTERVAL_NANOS.
    */
   private int checkedForEdtClogCount = 0;

   /**
    * The EDT for this dispatch (for the purpose of getting stack traces). I don't know of
    * any API for getting the event dispatch thread, but we can assume that it's the
    * current thread if we're in the middle of dispatching an AWT event... We can't cache
    * this because the EDT can die and be replaced by a new EDT if there's an uncaught
    * exception.
    */
   final Thread eventDispatchThread = Thread.currentThread();

   /**
    * The last time in milliseconds at which we saw a dispatch on the above thread.
    */
   private long dispatchNanoTimeStamp = System.nanoTime();

   /** A unique number in the sequence of all EDT dispatches. */
   final long dispatchID;
   private final AsyncPrinter aout;
   private boolean monitorThreadContention;
   private ThreadInfo dispatchStartThreadInfo;
   private final AWTEvent awtEvent;

   /**
    * Time spent in native code. We are seeing thread contention and core dumps in native
    * DLLs. At times the entire JVM (not just the EDT) bogs down.
    */
   private long nativeNanoTime = 0;

   private final List<String> classesLoaderActivity = Collections
         .synchronizedList(new ArrayList<String>());

   DispatchAnalyzer(long currentDispatchID, final AWTEvent event,
         final boolean monitorThreadContention, final AsyncPrinter aout) {
      this.dispatchID = currentDispatchID;

      this.awtEvent = event;
      this.aout = aout;
      this.monitorThreadContention = monitorThreadContention;

      this.resetDispatchTimeStamp(System.nanoTime());
   }

   /**
    * {@code DispatchAnalyzer} objects are queued if necessary prior to dispatch. If
    * queued, the {@code dispatchNanoTimeStamp} will be reset; otherwise, the time spent
    * waiting in the queue would be counted against the event.
    */
   synchronized void resetDispatchTimeStamp(long nowNanoTime) {
      dispatchNanoTimeStamp = nowNanoTime;
      previousStackNanos = nowNanoTime;
      previousStack = eventDispatchThread.getStackTrace();
      if (monitorThreadContention) {
         dispatchStartThreadInfo = THREAD_BEAN
               .getThreadInfo(eventDispatchThread.getId());
      }
      else {
         dispatchStartThreadInfo = null;
      }
   }

   @Override
   public void update(Observable o, Object arg) {
      if ((arg == null)
            || (false == (arg instanceof LoggingClassLoader.Record.ClassLoaderEvent))) {

         log.warning("Unknown event type received by DispatchAnalyzer: "
               + arg);
         return;
      }
      classesLoaderActivity.add(arg.toString());
   }

   // We can't use StackTraceElement.equals because that insists on checking
   // the filename and line number.
   // That would be version-specific.
   private boolean stackTraceElementIs(StackTraceElement e, String className,
         String methodName, boolean isNative) {
      return e.getClassName().equals(className)
            && e.getMethodName().equals(methodName)
            && e.isNativeMethod() == isNative;
   }

   // Checks whether the given stack looks like it's waiting for another event.
   // This relies on JDK
   // implementation details.
   private boolean isWaitingForNextEvent(StackTraceElement[] currentStack) {
      if (currentStack[0].getClassName().equals(
            EDTPerformanceMonitor.class.getName())) {
         // The most recent element is us (this EDT monitor). Nothing has
         // been dispatched.
         return false;
      }

      return stackTraceElementIs(currentStack[0], "java.lang.Object", "wait",
            true)
            && stackTraceElementIs(currentStack[1], "java.lang.Object",
                  "wait", false)
            && stackTraceElementIs(currentStack[2], "java.awt.EventQueue",
                  "getNextEvent", false);
   }

   private String generateNameForThisDispatch() {
      return "EDT Dispatch #" + dispatchID + " event("
            + awtEvent.getClass().getName() + ") eventParam("
            + awtEvent.paramString() + ") sourceClass("
            + awtEvent.getSource().getClass().getName()
            + ") sourceToString(" + awtEvent.getSource().toString() + ")";
   }

   @Override
   public String toString() {
      return DispatchAnalyzer.class.getName() + " "
            + generateNameForThisDispatch();
   }

   /**
    * Called repeatedly by the timer to analyze the current dispatch, identify problem
    * code, and to report dispatches that take longer than
    * UNREASONABLE_DISPATCH_FORCED_LOGGING_INTERVAL_NANOS.
    */
   synchronized void anaylzeEdtTimeSlice() {
      long currentStackNanos = System.nanoTime();
      StackTraceElement[] currentStack = eventDispatchThread.getStackTrace();

      if (currentStack == null) {
         aout.add(new Expression() {
            @Override
            public Object eval() {
               return "Failed to obtain stack trace from EDT "
                     + eventDispatchThread;
            }
         });

         return;
      }

      if (isWaitingForNextEvent(currentStack)) {
         // Don't be fooled by a modal dialog if it's waiting for its next
         // event. As long as the modal
         // dialog's event pump doesn't get stuck, it's okay for the outer
         // pump to be suspended.
         return;
      }

      // previousStack is currently set in constructor so the following if
      // should never be true. But
      // I'm not sure it should work that way yet... leaving it in for now.
      if (previousStack == null) {
         throw new IllegalStateException("Shouldn't get here.");
      }

      long elapsedTimeSliceNanos = currentStackNanos - previousStackNanos;

      // Thread-wait detection. Object.wait() should never be called by
      // dispatched event.
      if (currentStack[0].getMethodName().equals("wait")
            && currentStack[0].isNativeMethod()) {
         StackTraceNode naughtyThreadWaitCaller = threadSynopsis
               .getNullSafe(currentStack.length - 2, currentStack[1]);
         naughtyThreadWaitCaller.calledThreadWait = true;
         dispatchPutItselfIntoWaitState = true;
      }

      int pi = previousStack.length - 1;
      int ci = currentStack.length - 1;

      int cruxIndex = -1;
      while (pi >= 0) {
         int i = previousStack.length - pi - 1;
         try {
            if (cruxIndex == -1) {
               if (previousStack[pi].equals(currentStack[ci])) {
                  if ((pi == 0) || (ci == 0)) {
                     // We are the top of one or both stacks. No frames
                     // to compare compare after this. This
                     // is the crux of the current stack.
                     cruxIndex = i;
                     threadSynopsis.getNullSafe(cruxIndex,
                           previousStack[pi]).elapsedNanos += elapsedTimeSliceNanos;
                     if (previousStack[pi].isNativeMethod()) {
                        nativeNanoTime += elapsedTimeSliceNanos;
                     }
                  }
                  // Frames were the same. Whether crux was found or not
                  // there is
                  // nothing left to do this round.
                  continue;
               }
               else {
                  // This is the first frame where the traces differ,
                  // i.e., the previous frame is the
                  // "crux"
                  cruxIndex = i - 1;
                  threadSynopsis.getNullSafe(cruxIndex,
                        previousStack[pi + 1]).elapsedNanos += elapsedTimeSliceNanos;
                  if (previousStack[pi + 1].isNativeMethod()) {
                     nativeNanoTime += elapsedTimeSliceNanos;
                  }
               }
            }

            if (cruxIndex > -1) {
               // Having marked the crux, no more analysis of the current
               // frame is needed. Any profiled
               // frames, beyond the crux, are associated with the previous
               // stack only and should be tossed
               // making note of any that took too much time.

               if ((ci >= 0) && (Util.isEzeniaCode(currentStack[ci]))) {
                  ezeniaCodeDetectedDuringDispatch = true;
               }

               StackTraceNode info = threadSynopsis.remove(i);
               if (info == null) {
                  continue;
               }

               // Credit time to new crux. Save FrameInfo if it took
               // "too long" or for other assorted reasons.
               threadSynopsis.recordFrameInfo(cruxIndex, info,
                     UNREASONABLE_METHOD_DURATION_NANO, aout);
            }
         }
         finally {
            pi--;
            ci--;
         }
      }

      while (ci >= 0) {
         if (Util.isEzeniaCode(currentStack[ci--])) {
            ezeniaCodeDetectedDuringDispatch = true;
         }
      }

      // //////////////////////////////////////
      // Decide whether it's to print something

      long elapsed = elapsedNanoTimeSinceDispatch();
      if (elapsed > (THREAD_CONTENTION_ANALYSIS_INTERVAL_NANOS * (checkedForEdtClogCount + 1))) {
         analyzeThreadContention(Risk.HIGH);
         checkedForEdtClogCount++;
      }
      else if (elapsed > (UNREASONABLE_DISPATCH_FORCED_LOGGING_INTERVAL_NANOS * (dispatchAnalysisLoggedCount + 1))) {
         if (hangID == -1) {
            hangID = getNewHangNumber();
         }

         if (dispatchAnalysisLoggedCount < 2) {
            analyzeThreadContention(Risk.HIGH);
         }
         else if (dispatchAnalysisLoggedCount == 2) {
            analyzeThreadContention(Risk.MEDIUM);
         }
         else if (dispatchAnalysisLoggedCount > 2) {
            analyzeThreadContention(Risk.INFO);
         }

         logCurrentAnalysis("", true); // Event dispatch is still in progress
         Util.checkForDeadlock(aout);
         analyzeThreadContention(Risk.INFO);
      }

      previousStack = currentStack;
      previousStackNanos = currentStackNanos;
   }

   /**
    * Returns how long this dispatch has been going on (in milliseconds).
    */
   private long elapsedNanoTimeSinceDispatch() {
      return (System.nanoTime() - dispatchNanoTimeStamp);
   }

   /** Final logging for this dispatch. No additional message. */
   private void logCurrentAnalysis() {
      logCurrentAnalysis("", false);
   }

   private void logCurrentAnalysis(final String additionalMessage,
         final boolean dispatchStillInProcess) {
      AsyncPrinter.Divider header = aout.createDivider(
            " EDT Profiler - Dispatch #" + dispatchID + " "
                  + (dispatchStillInProcess ? "in progress" : "complete")
                  + additionalMessage + " ", DividerStyle.BEGIN);
      header.printFirstTimeOnly();

      dispatchAnalysisLoggedCount++;

      final String elapsedForDispatch = Util
            .elapsedNanoFormatterSeconds(elapsedNanoTimeSinceDispatch());

      final int f_hangID = hangID;
      final int f_loggedCount = dispatchAnalysisLoggedCount;
      aout.add(new Expression() {
         @Override
         public Object eval() {
            StringBuffer sb = new StringBuffer(elapsedForDispatch);
            if (dispatchStillInProcess) {
               sb.append(" UI freeze elapsed and counting...");
            }
            else {
               sb.append(" total for UI to unfreeze.");
            }
            sb.append(" hangID(" + f_hangID + ") logCount(" + f_loggedCount
                  + ")");
            if ((additionalMessage != null)
                  && (additionalMessage.length() > 0)) {
               sb.append(" ").append(additionalMessage);
            }
            sb.append(":");
            return sb;
         }
      });

      boolean someAnalysis = false;
      boolean plainStackDumped = false;

      final Expression headerOutput = new Expression() {
         @Override
         public Object eval() {
            return new StringBuilder(generateNameForThisDispatch())
                  .append((dispatchPutItselfIntoWaitState ? ("\n"
                        + STACK_INDENT + "Object.wait() was called. See details.")
                        : ""));
         }
      };

      aout.add(headerOutput);

      if (monitorThreadContention) {
         ThreadInfo currentInfo = THREAD_BEAN
               .getThreadInfo(eventDispatchThread.getId());
         aout.add(" * blocked count: "
               + (currentInfo.getBlockedCount() - dispatchStartThreadInfo
                     .getBlockedCount()));
         aout.add(" * blocked elapsed time: "
               + Util.elapsedMillisFormatterSeconds(currentInfo
                     .getBlockedTime()
                     - dispatchStartThreadInfo.getBlockedTime()));
         aout.add(" * wait count: "
               + (currentInfo.getWaitedCount() - dispatchStartThreadInfo
                     .getWaitedCount()));
         aout.add(" * wait elapsed time: "
               + Util.elapsedMillisFormatterSeconds(currentInfo
                     .getWaitedTime()
                     - dispatchStartThreadInfo.getWaitedTime()));
         aout.add(" * native elapsed time: "
               + Util.elapsedNanoFormatterSeconds(nativeNanoTime));
      }

      synchronized (classesLoaderActivity) {
         aout.add(" * classes loaded: " + classesLoaderActivity.size());
         for (Iterator<String> it = classesLoaderActivity.iterator(); it
               .hasNext();) {
            aout.add("    " + it.next());
         }
      }

      if (ezeniaCodeDetectedDuringDispatch) {
         aout.add("Ezenia code detected during dispatch.");
      }
      else {
         aout.add("No Ezenia code detected during dispatch.");
      }

      someAnalysis = true;

      if (threadSynopsis.size() > 0) {
         final StringBuilder sb = new StringBuilder();
         sb.append(elapsedForDispatch
               + " <-- Wall clock time (human perception), profiled:");
         threadSynopsis.dump(sb, STACK_INDENT);
         aout.add(sb.toString());
         someAnalysis = true;
      }
      else if (!plainStackDumped && (previousStack != null)) {
         aout.add(" No thread profiling info yet. Last recorded stack:");
         Util.printStackTrace(previousStack, STACK_INDENT, aout);
         someAnalysis = true;
         plainStackDumped = true;
      }

      if (!someAnalysis) {
         aout.add(" No analysis available.");
      }

      aout.createDivider(header).printFirstTimeOnly();
   }

   synchronized void dispose() {
      boolean unreasonable = elapsedNanoTimeSinceDispatch() > UNREASONABLE_DISPATCH_DURATION_NANO;

      if (unreasonable) {
         if (hangID == -1) {
            hangID = getNewHangNumber();
         }
         logCurrentAnalysis();
      }
   }

   private static enum Risk {
      INFO, MEDIUM, HIGH
   }

   private void analyzeThreadContention(Risk reportingLevel) {
      final long[] idArray = THREAD_BEAN.getAllThreadIds();

      if ((idArray == null) || (idArray.length == 0)) {
         if (reportingLevel != Risk.INFO) {
            aout.add(EDTPerformanceMonitor.class.getName()
                  + " detected no threads.");
         }
         return;
      }

      // Create header here. But don't we don't add it to the queue. We will
      // do
      // so later. But creating it here gives it a higher priority for the
      // AsyncPrinter's
      // PriorityQueue. Wouldn't want it to appear out of order.
      AsyncPrinter.Divider header = aout.createDivider(
            " EDT Risk Check #" + checkedForEdtClogCount
                  + " for dispatch #" + dispatchID + " ",
            DividerStyle.BEGIN);

      final ThreadInfo[] infoArray = THREAD_BEAN.getThreadInfo(idArray, true,
            true);

      final Set<Long> threadsToDump = new TreeSet<Long>();
      for (ThreadInfo info : infoArray) {
         if (info == null) {
            continue;
         }
         String threadName = info.getThreadName();
         Risk detectedRisk = Risk.INFO;
         try {
            if (info.getThreadId() == eventDispatchThread.getId()) {

               State threadState = info.getThreadState();

               aout.add("Thread '" + threadName + "' is " + threadState,
                     Action.STASH);

               if (State.RUNNABLE != threadState) {

                  // The EDT should remain RUNNABLE during a dispatch. In a perfect world
                  // it would never be
                  // blocked or in a wait-state when there is work to be done.
                  // potentialProblemExist = true;
                  // threadsToDump.add(info.getThreadId());

                  if (reportingLevel.compareTo(Risk.MEDIUM) > -1) {
                     threadsToDump.add(info.getThreadId());
                  }

                  String lockName = info.getLockName();
                  boolean objectMonitorExists = lockName != null;
                  if (objectMonitorExists) {
                     // A bit of hypocrisy:
                     if (!lockName.startsWith("zenClient.tools.DispatchAnalyzer")) {
                        detectedRisk = Risk.MEDIUM;
                     }
                     aout.add(STACK_INDENT + "on object '" + lockName + "'", Action.STASH);
                  }
                  else {
                     aout.add(STACK_INDENT + "no object", Action.STASH);
                  }

                  long lockOwnerId = info.getLockOwnerId();
                  if (lockOwnerId > -1) {
                     aout.add(STACK_INDENT + STACK_INDENT + "object owned by thread '"
                           + info.getLockOwnerName() + "' thead id '" + lockOwnerId + "'", Action.STASH);
                     if (reportingLevel.compareTo(Risk.MEDIUM) > -1) {
                        threadsToDump.add(lockOwnerId);
                     }
                  }
                  else if (objectMonitorExists) {
                     aout.add(STACK_INDENT + STACK_INDENT + "object not currently owned by any thread",
                           Action.STASH);
                  }
               } // END the EDT is not in a RUNNABLE state

               MonitorInfo[] monitorInfos = info.getLockedMonitors();
               if ((monitorInfos != null) && (monitorInfos.length > 0)) {
                  aout.add("Thread '" + threadName + "' has locked monitors:", Action.STASH);
                  for (MonitorInfo mi : monitorInfos) {
                     aout.add(STACK_INDENT + threadName + " has locked: " + mi, Action.STASH);
                  }
               }

               boolean tooLong = elapsedNanoTimeSinceDispatch() > UNREASONABLE_DISPATCH_DURATION_NANO;
               if (info.isInNative() && tooLong) {
                  if (reportingLevel == Risk.HIGH) {
                     threadsToDump.add(info.getThreadId());
                  }
                  aout.add("Thread '" + threadName + "' is in native code, along with threads:", Action.STASH);
                  boolean otherNative = false;
                  for (ThreadInfo info2 : infoArray) {
                     if (info2 == null) {
                        // happens occasionally, usually during shutdown
                        continue;
                     }
                     if (info2.isInNative()
                           && info2.getThreadId() != eventDispatchThread
                                 .getId()) {
                        if (reportingLevel == Risk.HIGH) {
                           threadsToDump.add(info2.getThreadId());
                        }
                        aout.add(
                              STACK_INDENT + "thread '"
                                    + info2.getThreadName() + "' "
                                    + info2.getThreadState(),
                              Action.STASH);
                        MonitorInfo[] monitorInfos2 = info2
                              .getLockedMonitors();
                        if ((monitorInfos2 != null)
                              && (monitorInfos2.length > 0)) {
                           otherNative = true;
                           for (MonitorInfo mi : monitorInfos2) {
                              aout.add(STACK_INDENT + STACK_INDENT + "has locked: " + mi, Action.STASH);
                           }
                        }
                     }
                  }

                  if (otherNative) {
                     detectedRisk = Risk.MEDIUM;
                  }
                  else {
                     aout.add(STACK_INDENT + "no other threads", Action.STASH);
                  }
               }
            } // END if AWT-EventQueue
            else {
               // Analyze locked monitors of non-EDT threads
               MonitorInfo[] monitorInfos = info.getLockedMonitors();
               if ((monitorInfos != null) && (monitorInfos.length > 0)) {
                  aout.add("Thread '" + threadName + "' has locked items:", Action.STASH);
                  for (MonitorInfo mi : monitorInfos) {
                     String lockedClassName = mi.getClassName();
                     aout.add(STACK_INDENT + mi, Action.STASH);
                     try {
                        Class<?> lockedClass = Class
                              .forName(lockedClassName);
                        if (java.awt.Component.class
                              .isAssignableFrom(lockedClass)) {
                           // Big risk here. Just asking for an EDT clog.
                           detectedRisk = Risk.HIGH;
                           threadsToDump.add(info.getThreadId());
                           aout.add(STACK_INDENT + STACK_INDENT + "DEADLOCK RISK: AWT component '"
                                 + lockedClassName + "' locked by thread '" + threadName + "' --at--> "
                                 + mi.getLockedStackFrame(), Action.STASH);
                        }
                     }
                     catch (ClassNotFoundException e) {
                        aout.add(STACK_INDENT + lockedClassName + " "
                              + e, Action.STASH);
                     }
                  }
               }

               // Check non-EDT thread for method calls to AWT components, i.e., for
               // potential EDT rule violations. (but only do the work if the thread
               // hasn't been flagged already by the above block)
               if (threadsToDump.contains(info.getThreadId()) == false) {
                  StackTraceElement[] stack = info.getStackTrace();
                  boolean awtComponentDetected = false;
                  for (int i = 0; i < stack.length; i++) {
                     try {
                        Class<?> clazz = Class.forName(stack[i]
                              .getClassName());
                        if (java.awt.Component.class
                              .isAssignableFrom(clazz)) {
                           if (awtComponentDetected == false) {
                              awtComponentDetected = true;
                              aout.add("Thread '" + threadName + "' possible EDT rule violation by calling:",
                                    Action.STASH);

                           }
                           aout.add(STACK_INDENT + " awt component '" + clazz + "' method '"
                                 + stack[i].getMethodName() + "'");
                           threadsToDump.add(info.getThreadId());
                        }
                     }
                     catch (ClassNotFoundException e) {
                        aout.add("Thread '" + threadName + "'"
                              + stack[i].getClassName() + " " + e,
                              Action.STASH);
                     }
                  }
               }

            } // END else (thread other than AWT-EventQueue)

            // Dump stack traces of threads mentioned above
            if (threadsToDump.size() > 0) {
               aout.add("", Action.STASH);
               long[] ids = new long[threadsToDump.size()];
               Iterator<Long> it = threadsToDump.iterator();
               for (int i = 0; i < ids.length; i++) {
                  ids[i] = it.next();
               }

               ThreadInfo[] tis = THREAD_BEAN.getThreadInfo(ids, true,
                     true);
               if (tis == null) {
                  // don't know if happens, but now I'm paranoid
                  continue;
               }
               for (int i = 0; i < ids.length; i++) {
                  aout.add(tis[i], Action.STASH); // silently ignores
                  // nulls and just
                  // returns
               }
            }
         }
         finally {
            threadsToDump.clear();
            if (detectedRisk.compareTo(reportingLevel) > -1) {
               if (aout.deferredItemSize() > 0) {
                  header.printFirstTimeOnly();
                  aout.printDeferredItems();
               }
            }
            else {
               aout.dropDeferredItems();
            }
         }
      } // END for (ThreadInfo info : infoArray)
      if (header.hasBeenPrinted()) {
         aout.createDivider(header).printFirstTimeOnly();
      }
   }

} // END inner class DispatchAnalyzer
