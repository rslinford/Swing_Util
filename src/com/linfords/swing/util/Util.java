package com.linfords.swing.util;

import java.awt.EventQueue;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;

public final class Util {
   /**
    * No instances are created. Used for static utility methods only.
    */
   private Util() {
   }

   public final static long NANO_PER_MILLI = 1000000;
   public final static long NANOS_PER_SEC = 1000 * NANO_PER_MILLI;
   public final static long NANOS_PER_MIN = 60 * NANOS_PER_SEC;

// public final static long NANOS_PER_HR = 60 * NANOS_PER_MIN;

   public static String elapsedNanoFormatterMinutes(long nanos) {
      return String.format("%02d:%02d:%03d.%06d",
            nanos / NANOS_PER_MIN,
            nanos % NANOS_PER_MIN / NANOS_PER_SEC,
            nanos % NANOS_PER_MIN % NANOS_PER_SEC / NANO_PER_MILLI,
            nanos % NANOS_PER_MIN % NANOS_PER_SEC % NANO_PER_MILLI);
   }

   public static String elapsedNanoFormatterSeconds(long nanos) {
      return String.format("%02d:%03d.%06d",
            nanos / NANOS_PER_SEC,
            nanos % NANOS_PER_SEC / NANO_PER_MILLI,
            nanos % NANOS_PER_SEC % NANO_PER_MILLI);
   }

   public static String elapsedMillisFormatterSeconds(long millis) {
      return elapsedNanoFormatterSeconds(millis * NANO_PER_MILLI);
   }
   
   public static boolean isAwtEventDispatchMethod(StackTraceElement frame) {
      return frame.getClassName().equals(EventQueue.class.getName())
            && frame.getMethodName().equals("dispatchEvent");
   }

   public static boolean isEzeniaCode(StackTraceElement stackTraceElement) {
      final String className = stackTraceElement.getClassName();
      return (className.startsWith("zenClient")
            || className.startsWith("com.ezenia")
            || className.startsWith("clientCommon") || className.startsWith("zenWhiteboard"));
   }

   public static boolean isPerformanceRisk(StackTraceElement stackTraceElement) {
      final String className = stackTraceElement.getClassName();
      final String methodName = stackTraceElement.getMethodName();

      boolean suspect = (className.startsWith("javax.imageio.ImageIO") && methodName.equals("<clinit>"))
            //            || className.startsWith("javax.crypto")
            || (className.startsWith("javax.crypto.Cipher") && methodName.equals("getInstance"))
            || (className.startsWith("com.sun.crypto.provider.AESCipher") && methodName.equals("<init>"))
            || className.startsWith("com.sun.jna")
            || className.startsWith("com.rsa")
            || (className.startsWith("sun.misc.Unsafe") && methodName.equals("park"))
            || (className.startsWith("java.lang.Object") && methodName.equals("wait"))
            || (className.startsWith("java.io.FileInputStream") && methodName.equals("readBytes"))
            || className.startsWith("java.util.zip")
            || className.startsWith("java.util.jar")
            //            || className.startsWith("java.net")
            || (className.startsWith("java.net.Socket") && methodName.equals("connect"))
            || (className.startsWith("java.net.Socket") && methodName.matches("(?i)[^r]*read.*")) // contain 'read' case insensitive
            || (className.startsWith("java.net.Socket") && methodName.matches("(?i)[^w]*write.*")) // contain 'write' case insensitive
            || (className.startsWith("java.net.URLClassLoader") && methodName.equals("access"))
            //            || className.startsWith("java.security.AccessController")
            || (className.startsWith("sun.awt.datatransfer.SunClipboard") && methodName.equals("getContents"))
            || className.startsWith("org.apache.batik.bridge.DocumentJarClassLoader")
            || (className.startsWith("org.apache.batik.bridge.BridgeContext") && methodName.equals("<init>"))
            || (className.startsWith("org.apache.batik.svggen.SVGGraphics2D") && methodName.equals("<init>"))
            || (className.startsWith("org.apache.batik.dom.svg.SVGDOMImplementation") && (methodName.equals("createDocument") || methodName.equals("clinit")))
            || className.startsWith("com.ezenia.clientgateway.EzXMPPClient")
            || (className.startsWith("com.ezenia.smack.XMPPConnection") && methodName.equals("initConnection"))
            || (className.startsWith("com.ezenia.smack.PacketCollector") && methodName.equals("nextResult"));

      return suspect;
   }

   public static StringBuilder fullStackTrace(final ThreadInfo ti) {
      return fullStackTrace(ti, null);
   }

   /**
    * Generates a full stack trace from 'ti' and stores it in 'sb'. This code was patterned
    * after the toString method of ThreadInfo, but with the artificial MAX_DEPTH=8
    * removed.
    * 
    * @param sb
    *           The target for the thread dump. If null then a new StringBuilder
    *           is created
    * @param ti
    * @return A reference to StringBuilder used.
    */
   public static StringBuilder fullStackTrace(final ThreadInfo ti, StringBuilder sb) {
      if (sb == null) {
         sb = new StringBuilder();
      }

      sb.append("\"" + ti.getThreadName() + "\"" +
                                            " Id=" + ti.getThreadId() + " " +
                                            ti.getThreadState());
      if (ti.getLockName() != null) {
         sb.append(" on " + ti.getLockName());
      }
      if (ti.getLockOwnerName() != null) {
         sb.append(" owned by \"" + ti.getLockOwnerName() +
                     "\" Id=" + ti.getLockOwnerId());
      }
      if (ti.isSuspended()) {
         sb.append(" (suspended)");
      }
      if (ti.isInNative()) {
         sb.append(" (in native)");
      }
      sb.append('\n');
      int i = 0;
      StackTraceElement[] stackTrace = ti.getStackTrace();
      for (; i < stackTrace.length; i++) {
         StackTraceElement ste = stackTrace[i];
         sb.append("\tat " + ste.toString());
         sb.append('\n');
         if (i == 0 && ti.getLockInfo() != null) {
            Thread.State ts = ti.getThreadState();
            switch (ts) {
            case BLOCKED:
               sb.append("\t-  blocked on " + ti.getLockInfo());
               sb.append('\n');
               break;
            case WAITING:
               sb.append("\t-  waiting on " + ti.getLockInfo());
               sb.append('\n');
               break;
            case TIMED_WAITING:
               sb.append("\t-  waiting on " + ti.getLockInfo());
               sb.append('\n');
               break;
            default:
            }
         }

         for (MonitorInfo mi : ti.getLockedMonitors()) {
            if (mi.getLockedStackDepth() == i) {
               sb.append("\t-  locked " + mi);
               sb.append('\n');
            }
         }
      }
      if (i < stackTrace.length) {
         sb.append("\t...");
         sb.append('\n');
      }

      LockInfo[] locks = ti.getLockedSynchronizers();
      if (locks.length > 0) {
         sb.append("\n\tNumber of locked synchronizers = " + locks.length);
         sb.append('\n');
         for (LockInfo li : locks) {
            sb.append("\t- " + li);
            sb.append('\n');
         }
      }

      return sb;
   }

   public static void checkForDeadlock(AsyncPrinter aout) {
      ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
      //threadBean.getThreadInfo(Thread.currentThread().getId(), Integer.MAX_VALUE).getStackTrace()
      long[] threadIds = threadBean.findMonitorDeadlockedThreads();
      if (threadIds == null) {
         return;
      }

      aout.add("deadlock detected involving the following threads:");
      ThreadInfo[] threadInfos = threadBean.getThreadInfo(threadIds, Integer.MAX_VALUE);
      for (ThreadInfo info : threadInfos) {
         aout.add("Thread #" + info.getThreadId() + " " + info.getThreadName() + " ("
                  + info.getThreadState() + ") waiting on object(" + info.getLockName()
               + ") held by thread ("
                  + info.getLockOwnerName() + ")");
         aout.add(fullStackTrace(info));
      }
   }

   public static String stackTraceToString(final StackTraceElement[] stackTrace, final String indent) {
      StringBuilder result = new StringBuilder();
      // We used to avoid showing any code above where this class gets involved in event dispatch, but that
      // hides potentially useful information when dealing with modal dialogs. Maybe we should reinstate that,
      // but search from the other end of the stack?
      for (StackTraceElement stackTraceElement : stackTrace) {
         result.append(indent + stackTraceElement);
      }
      return result.toString();
   }

   public static void printStackTrace(final StackTraceElement[] stack, final String indent, AsyncPrinter aout) {
      aout.add(new AsyncPrinter.Expression() {
         @Override
         public Object eval() {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < stack.length; i++) {
               sb.append((i > 0 ? indent + indent : indent) + stack[i] + '\n');
            }
            return sb;
         }
      });
   }

   public static void printThreadSummary(ThreadInfo info, AsyncPrinter aout) {
      aout.add("getThreadName: " + info.getThreadName());
      aout.add("getThreadId: " + info.getThreadId());
      aout.add("getThreadState: " + info.getThreadState());
      aout.add("isNative: " + info.isInNative());

      aout.add("getLockInfo: " + info.getLockInfo());
      aout.add("getLockName: " + info.getLockName());
      aout.add("getLockOwnerId: " + info.getLockOwnerId());
      aout.add("getLockOwnerName: " + info.getLockOwnerName());

      aout.add("getBlockedCount: " + info.getBlockedCount());
      aout.add("getBlockedTime: " + info.getBlockedTime());

      aout.add("getLockedMonitors: " + Arrays.asList(info.getLockedMonitors()));
      aout.add("getLockedSynchronizers: " + Arrays.asList(info.getLockedSynchronizers()));
   }

}
