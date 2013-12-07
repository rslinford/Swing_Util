package com.linfords.swing.util;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors the AWT event dispatch thread for events that take longer than a certain time
 * to be dispatched.
 * <p/>
 * The principle is to record the time at which we start processing an event, and have
 * another thread check frequently to see if we're still processing. If the other thread
 * notices that we've been processing a single event for too long, it prints a stack trace
 * showing what the event dispatch thread is doing, and continues to time it until it
 * finally finishes.
 * <p/>
 * This is useful in determining what code is causing your Java application's GUI to be
 * unresponsive.
 * 
 * <p>
 * The original blog can be found here<br>
 * <a href= "http://elliotth.blogspot.com/2005/05/automatically-detecting-awt-event.html"
 * > Automatically detecting AWT event dispatch thread hangs</a>
 * </p>
 * 
 * @author Elliott Hughes <enh@jessies.org>
 * 
 *         Advice, bug fixes, and test cases from Alexander Potochkin and Oleg
 *         Sukhodolsky.
 * 
 *         https://swinghelper.dev.java.net/
 */
public final class EDTPerformanceMonitor extends EventQueue {
   private static final Logger log = Logger.getLogger(EDTPerformanceMonitor.class.getName());

   /** Used to assign each dispatch its unique ID. */
   private static long globalDispatchNumber = 0;

   private static LoggingClassLoader loggingClassLoader = null;
   private static final int INVALID_DISPATCH_ID = -1;

   private static synchronized long nextDispatchID() {
      return ++globalDispatchNumber;
   }

   private final static AsyncPrinter aout = AsyncPrinter.defaultInstance();

   final static boolean THREAD_CONTENTION_MONITORING;
   static {
      boolean temp = false;
      ThreadMXBean tb = ManagementFactory.getThreadMXBean();
      if (tb.isThreadContentionMonitoringSupported()) {
         aout.add("Thread contention monitoring is supported:");
         if (tb.isThreadContentionMonitoringEnabled()) {
            aout.add("  and is already enabled.");
            temp = true;
         }
         else {
            try {
               tb.setThreadContentionMonitoringEnabled(true);
               if (tb.isThreadContentionMonitoringEnabled() == false) {
                  throw new Exception("unknown");
               }
               aout.add("  and has been successfully enabled.");
               temp = true;
            }
            catch (Exception e) {
               aout.add("  but has failed to enable, reason: " + e.getMessage());
               temp = false;
            }
         }
      }
      else {
         aout.add("Thread contention not supported by this JVM.");
         temp = false;
      }
      THREAD_CONTENTION_MONITORING = temp;
   }

   private final static EDTPerformanceMonitor INSTANCE = new EDTPerformanceMonitor();

   /**
    * AnalysisTimerTask interval. The wait time in between a time slice analysis of the
    * current dispatch
    */
   private final static long ANALYSIS_INTERVAL_MILLI = 5;

   /**
    * The first several dispatches seem to be system setup and installing this very
    * monitoring tool. They aren't helpful, just confusing.
    */
   private final static long PROFILING_DISABLED_UNTIL_AFTER_DISPATCH_COUNT = 2;

   /**
    * The currently outstanding event dispatches. The implementation of modal dialogs is a
    * common cause for multiple outstanding dispatches.
    */
   private final LinkedList<DispatchAnalyzer> dispatches = new LinkedList<DispatchAnalyzer>();

   private EDTPerformanceMonitor() {
      initTimer();
   }

   /**
    * Sets up a timer to check for hangs frequently.
    */
   private void initTimer() {
      final long initialDelayMs = 100; // Wait before beginning monitor
      final boolean isDaemon = true;
      Timer timer = new Timer("EDT Analyzer", isDaemon);
      timer.schedule(new AnalysisTimerTask(), initialDelayMs, ANALYSIS_INTERVAL_MILLI);
   }

   private class AnalysisTimerTask extends TimerTask {
      @Override
      public void run() {
         DispatchAnalyzer dispatchAnalyzer;
         synchronized (dispatches) {
            if (dispatches.isEmpty()) {
               // Nothing to do. We don't destroy the timer when there's nothing happening
               // because it would mean a lot more work on every single AWT event that
               // gets dispatched.
               return;
            }
            dispatchAnalyzer = dispatches.getLast();
         }

         // Only the most recent dispatch can be hung; nested dispatches by their nature
         // cause the outer dispatch pump to be suspended.
         try {
            dispatchAnalyzer.anaylzeEdtTimeSlice();
         }
         catch (Exception e) {
            log.log(Level.WARNING, "Unhandled error during time-slice analysis", e);
         }
      }
   }

   /**
    * Sets up hang detection for the event dispatch thread.
    * @param loggingClassLoader
    * 
    * @param loggingClassLoader
    * 
    * @param out
    */
   public static void initMonitoring(final LoggingClassLoader loggingClassLoader) {
      EDTPerformanceMonitor.loggingClassLoader = loggingClassLoader;
      Toolkit.getDefaultToolkit().getSystemEventQueue().push(INSTANCE);
   }

   /**
    * Overrides EventQueue.dispatchEvent to call our pre and post hooks either side of the
    * system's event dispatch code.
    */
   @Override
   protected void dispatchEvent(AWTEvent event) {

      DispatchAnalyzer dispatchAnalyzer = null;

      long currentDispatchID = INVALID_DISPATCH_ID;
      boolean analysisQueued = false;

      try {
         // Do not allow an Exception in monitoring to disrupt the actual program. The
         // following try block is to ensure that the event gets dispatched. It is here
         // for robustness and is truly here for exceptional cases that are not known.
         try {
            currentDispatchID = nextDispatchID();

            // The first several dispatches seem to be system setup and
            // installing this very monitoring tool.
            if (currentDispatchID > PROFILING_DISABLED_UNTIL_AFTER_DISPATCH_COUNT) {
               dispatchAnalyzer = new DispatchAnalyzer(currentDispatchID, event,
                        THREAD_CONTENTION_MONITORING, aout);
               preDispatchEvent(dispatchAnalyzer);
               loggingClassLoader.addObserver(dispatchAnalyzer);
               analysisQueued = true;
            }
         }
         catch (Exception e) {
            log.log(Level.WARNING, "Problem during prep of Dispatch #" + currentDispatchID
                     + " analysisQueued(" + analysisQueued + ")", e);
         }

         super.dispatchEvent(event);
      }
      finally {
         if (analysisQueued) {
            postDispatchEvent();
            loggingClassLoader.deleteObserver(dispatchAnalyzer);
         }
      }
   }

   /**
    * Starts tracking a dispatch.
    * 
    * @param event
    */
   private synchronized void preDispatchEvent(DispatchAnalyzer dispatchAnalyzer) {
      synchronized (dispatches) {
         dispatches.addLast(dispatchAnalyzer);
      }
   }

   /**
    * Stops tracking a dispatch.
    */
   private synchronized void postDispatchEvent() {
      DispatchAnalyzer justFinishedDispatch;
      synchronized (dispatches) {
         // We've finished the most nested dispatch, and don't need it any
         // longer.
         if (dispatches.peek() == null) {
            log.log(Level.WARNING, "Unexpected state", new IllegalStateException(
                     "postDispatchEvent encountered an empty 'dispatches' queue"));
            return;
         }
         justFinishedDispatch = dispatches.removeLast();
      }

      justFinishedDispatch.dispose();

      // The other dispatches, which have been waiting, need to be credited
      // extra time. We do this rather
      // simplistically by pretending they've just been redispatched.
      Thread currentEventDispatchThread = Thread.currentThread();
      synchronized (dispatches) {
         long now = System.nanoTime();
         for (DispatchAnalyzer dispatchInfo : dispatches) {
            if (dispatchInfo.eventDispatchThread == currentEventDispatchThread) {
               dispatchInfo.resetDispatchTimeStamp(now);
            }
         }
      }
   }

}
