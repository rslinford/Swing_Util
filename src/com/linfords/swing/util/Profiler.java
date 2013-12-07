package com.linfords.swing.util;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.linfords.swing.util.AsyncPrinter.Expression;


/**
 * 
 * @author slinford
 */
public class Profiler {
   private final static long NULL_TIME = -1;
   private final static AsyncPrinter aout = AsyncPrinter.defaultInstance();
   private final static DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.FULL);
   private final static Map<String, Summary> summaryMap = Collections.synchronizedMap(new HashMap<String, Summary>());

   /** No instance is ever created */
   private Profiler() {
   }

   private static long profiledPeriodStartMillis = System.currentTimeMillis();
   private static long profiledPeriodStartNanos = System.nanoTime();

   private static boolean logStartDisabled = true;
   private static boolean logEndDisabled = false;

   public static void clearSummary() {
      summaryMap.clear();
      profiledPeriodStartMillis = System.currentTimeMillis();
      profiledPeriodStartNanos = System.nanoTime();
      aout.add("Profiler summary cleared.");
   }

   private final static long NANOS_PER_MILLI = 1000000; // Nanos per millisecond
   private final static long MILLIS_PER_SECOND = 1000; // Mills per second
   private final static long MILLIS_PER_MINUTE = MILLIS_PER_SECOND * 60; // Mills per minute
   private final static long MILLIS_PER_HOUR = MILLIS_PER_MINUTE * 60; // Mills per hour

   public static String nanosToString(long elapsedNanos) {
      boolean negative = (elapsedNanos < 0);

      if (negative) {
         elapsedNanos = -elapsedNanos;
      }

      long millis = elapsedNanos / NANOS_PER_MILLI;
      long nanos = elapsedNanos % NANOS_PER_MILLI;

      long hours = millis / MILLIS_PER_HOUR;
      millis = millis % MILLIS_PER_HOUR;

      long minutes = millis / MILLIS_PER_MINUTE;
      millis = minutes % MILLIS_PER_MINUTE;

      long seconds = millis / MILLIS_PER_SECOND;
      millis -= seconds % MILLIS_PER_SECOND;

      StringBuilder elapsedText = new StringBuilder();
      if (hours != 0) {
         elapsedText.append("hr(").append(hours).append(") ");
      }

      if ((minutes != 0) || (elapsedText.length() > 0)) {
         elapsedText.append("min(").append(minutes).append(") ");
      }

      if ((seconds != 0) || (elapsedText.length() > 0)) {
         elapsedText.append("sec(").append(seconds).append(") ");
      }

      elapsedText.append("millis(").append(millis).append('.').append(nanos).append(")");

      if (negative) {
         elapsedText.insert(0, "NEGATIVE ");
      }

      return elapsedText.toString();
   }

   /**
    * @return true if summary is enabled.
    */
   public static boolean isSummaryEnabled() {
      return true;
   }

   public static Stamp createTimeStamp(SortedMap<Integer, StackTraceElement> elements, String tag,
         AsyncPrinter aout) {
      Stamp stamp = new Stamp(elements, tag);
      recordStart(stamp, aout);
      return stamp;
   }

   public static Stamp createTimeStamp(String key, String tag, AsyncPrinter aout) {
      Stamp stamp = new Stamp(key, tag);
      recordStart(stamp, aout);
      return stamp;
   }

   private static void recordStart(Stamp stamp, AsyncPrinter aout) {
      if (logStartDisabled || (aout == null)) {
         return;
      }

      aout.add("BEGIN " + stamp);
   }

//   public static void recordPerformance(Profiler.Stamp stamp) {
//      recordPerformance(stamp, (String) null);
//   }

   public static void recordPerformance(Stamp stamp, String result, AsyncPrinter aout) {
      // Stop clock now, soon as possible. These are nanoseconds!
      final long endingNanos = System.nanoTime();

      // As a logging helper method we should never blow up with a null
      // pointer, no matter what we are passed.  It doesn't make sense
      // to pass a null to this method, but we will 'make do' because
      // throwing an exception is not a good option.
      if (stamp == null) {
         aout.add("recordPerformance received a null Stamp. result(" + result + ")");
         // show a stack trace so that we know what code was responsible
         aout.add(new Exception("null Profiler.Stamp"));
         return;
      }

      // Sets the result and stops the "clock" by setting elpasedNanos based on 'endingNanaos'.
      stamp.stopTheClock(result, endingNanos);

      if (!logEndDisabled && (aout != null)) {
         final boolean fLogStartDisabled = logStartDisabled;
         final Stamp fStamp = stamp;
         aout.add(new Expression() {
            @Override
            public Object eval() {
               StringBuilder sb = new StringBuilder();
               if (!fLogStartDisabled) {
                  sb.append("END ");
               }
               return sb.append(fStamp);
            }
         });
      }

      final String key = stamp.getKey();
      Summary summary;
      synchronized (summaryMap) {
         summary = (Summary) summaryMap.get(key);
         if (summary == null) {
            summary = new Summary(key);
            summaryMap.put(key, summary);
         }
      }

      synchronized (summary) {
         summary.invoked++;
         summary.totalNanos += stamp.elpasedNanos;
         if (stamp.elpasedNanos > summary.slowest) {
            summary.slowest = stamp.elpasedNanos;
         }

         if (stamp.elpasedNanos < summary.fastest) {
            summary.fastest = stamp.elpasedNanos;
         }
      }
   }

   public final static String SUMMARY_KEY_COMPARATOR_NAME = "SUMMARY_KEY_COMPARATOR";

   public final static Comparator<Summary> SUMMARY_KEY_COMPARATOR = new Comparator<Summary>() {
      public int compare(Summary s1, Summary s2) {
         int result = s1.key.compareTo(s2.key);
         if (result != 0) {
            return result;
         }

         if (s1.totalNanos > s2.totalNanos) {
            return -1;
         }
         else if (s1.totalNanos < s2.totalNanos) {
            return 1;
         }

         if (s1.invoked > s2.invoked) {
            return -1;
         }
         else if (s1.invoked < s2.invoked) {
            return 1;
         }

         if (s1.slowest > s2.slowest) {
            return -1;
         }
         else if (s1.slowest < s2.slowest) {
            return 1;
         }

         if (s1.fastest > s2.fastest) {
            return -1;
         }
         else if (s1.fastest < s2.fastest) {
            return 1;
         }

         return 0;
      }

      public boolean equals(Object obj) {
         // this comparator is only equal to itself
         return this == obj;
      }
   };

   public final static String SUMMARY_TOTAL_COMPARATOR_NAME = "SUMMARY_TOTAL_COMPARATOR";

   public final static Comparator<Summary> SUMMARY_TOTAL_COMPARATOR = new Comparator<Summary>() {
      public int compare(Summary s1, Summary s2) {
         if (s1.totalNanos > s2.totalNanos) {
            return -1;
         }
         else if (s1.totalNanos < s2.totalNanos) {
            return 1;
         }

         int result = s1.key.compareTo(s2.key);
         if (result != 0) {
            return result;
         }

         if (s1.invoked > s2.invoked) {
            return -1;
         }
         else if (s1.invoked < s2.invoked) {
            return 1;
         }

         if (s1.slowest > s2.slowest) {
            return -1;
         }
         else if (s1.slowest < s2.slowest) {
            return 1;
         }

         if (s1.fastest > s2.fastest) {
            return -1;
         }
         else if (s1.fastest < s2.fastest) {
            return 1;
         }

         return 0;
      }

      public boolean equals(Object obj) {
         // this comparator is only equal to itself
         return this == obj;
      }
   };

   public final static String SUMMARY_INVOKED_COMPARATOR_NAME = "SUMMARY_INVOKED_COMPARATOR";

   public final static Comparator<Summary> SUMMARY_INVOKED_COMPARATOR = new Comparator<Summary>() {
      public int compare(Summary s1, Summary s2) {
         if (s1.invoked > s2.invoked) {
            return -1;
         }
         else if (s1.invoked < s2.invoked) {
            return 1;
         }

         int result = s1.key.compareTo(s2.key);
         if (result != 0) {
            return result;
         }

         if (s1.totalNanos > s2.totalNanos) {
            return -1;
         }
         else if (s1.totalNanos < s2.totalNanos) {
            return 1;
         }

         if (s1.slowest > s2.slowest) {
            return -1;
         }
         else if (s1.slowest < s2.slowest) {
            return 1;
         }

         if (s1.fastest > s2.fastest) {
            return -1;
         }
         else if (s1.fastest < s2.fastest) {
            return 1;
         }

         return 0;
      }

      public boolean equals(Object obj) {
         // this comparator is only equal to itself
         return this == obj;
      }
   };

   public final static String SUMMARY_SLOWEST_COMPARATOR_NAME = "SUMMARY_SLOWEST_COMPARATOR";

   public final static Comparator<Summary> SUMMARY_SLOWEST_COMPARATOR = new Comparator<Summary>() {
      public int compare(Summary s1, Summary s2) {
         if (s1.slowest > s2.slowest) {
            return -1;
         }
         else if (s1.slowest < s2.slowest) {
            return 1;
         }

         int result = s1.key.compareTo(s2.key);
         if (result != 0) {
            return result;
         }

         if (s1.totalNanos > s2.totalNanos) {
            return -1;
         }
         else if (s1.totalNanos < s2.totalNanos) {
            return 1;
         }

         if (s1.invoked > s2.invoked) {
            return -1;
         }
         else if (s1.invoked < s2.invoked) {
            return 1;
         }

         if (s1.fastest > s2.fastest) {
            return -1;
         }
         else if (s1.fastest < s2.fastest) {
            return 1;
         }

         return 0;
      }

      public boolean equals(Object obj) {
         // this comparator is only equal to itself
         return this == obj;
      }
   };

   public final static String SUMMARY_FASTEST_COMPARATOR_NAME = "SUMMARY_FASTEST_COMPARATOR";

   public final static Comparator<Summary> SUMMARY_FASTEST_COMPARATOR = new Comparator<Summary>() {
      public int compare(Summary s1, Summary s2) {
         if (s1.fastest > s2.fastest) {
            return -1;
         }
         else if (s1.fastest < s2.fastest) {
            return 1;
         }

         int result = s1.key.compareTo(s2.key);
         if (result != 0) {
            return result;
         }

         if (s1.totalNanos > s2.totalNanos) {
            return -1;
         }
         else if (s1.totalNanos < s2.totalNanos) {
            return 1;
         }

         if (s1.invoked > s2.invoked) {
            return -1;
         }
         else if (s1.invoked < s2.invoked) {
            return 1;
         }

         if (s1.slowest > s2.slowest) {
            return -1;
         }
         else if (s1.slowest < s2.slowest) {
            return 1;
         }

         return 0;
      }

      public boolean equals(Object obj) {
         // this comparator is only equal to itself
         return this == obj;
      }
   };

   public final static String SUMMARY_AVERAGE_COMPARATOR_NAME = "SUMMARY_AVERAGE_COMPARATOR";

   public final static Comparator<Summary> SUMMARY_AVERAGE_COMPARATOR = new Comparator<Summary>() {
      public int compare(Summary s1, Summary s2) {
         if (s1.calculateAverage() > s2.calculateAverage()) {
            return -1;
         }
         else if (s1.calculateAverage() < s2.calculateAverage()) {
            return 1;
         }

         int result = s1.key.compareTo(s2.key);
         if (result != 0) {
            return result;
         }

         if (s1.totalNanos > s2.totalNanos) {
            return -1;
         }
         else if (s1.totalNanos < s2.totalNanos) {
            return 1;
         }

         if (s1.invoked > s2.invoked) {
            return -1;
         }
         else if (s1.invoked < s2.invoked) {
            return 1;
         }

         if (s1.slowest > s2.slowest) {
            return -1;
         }
         else if (s1.slowest < s2.slowest) {
            return 1;
         }

         return 0;
      }

      public boolean equals(Object obj) {
         // this comparator is only equal to itself
         return this == obj;
      }
   };

   public static enum SummarySortOrder {
      byKey("Sorted by key", SUMMARY_KEY_COMPARATOR),
      byTotal("Sorted by total", SUMMARY_TOTAL_COMPARATOR),
      byInvoked("Sorted by invoked", SUMMARY_INVOKED_COMPARATOR),
      bySlowest("Sorted by slowest", SUMMARY_SLOWEST_COMPARATOR),
      byFastest("Sorted by fasted", SUMMARY_FASTEST_COMPARATOR),
      byAverage("Sorted by average", SUMMARY_AVERAGE_COMPARATOR);

      public final String displayName;
      public final Comparator<Summary> comparable;

      private SummarySortOrder(String displayName, Comparator<Summary> comparable) {
         this.displayName = displayName;
         this.comparable = comparable;
      }
   }

//   public final static Map<String, Comparator<Summary>> SUMMARY_COMPARATOR_MAP_BY_NAME;
//   static {
//      Map<String, Comparator<Summary>> temp = new HashMap<String, Comparator<Summary>>();
//      temp.put(SUMMARY_KEY_COMPARATOR_NAME, SUMMARY_KEY_COMPARATOR);
//      temp.put(SUMMARY_TOTAL_COMPARATOR_NAME, SUMMARY_TOTAL_COMPARATOR);
//      temp.put(SUMMARY_INVOKED_COMPARATOR_NAME, SUMMARY_INVOKED_COMPARATOR);
//      temp.put(SUMMARY_SLOWEST_COMPARATOR_NAME, SUMMARY_SLOWEST_COMPARATOR);
//      temp.put(SUMMARY_FASTEST_COMPARATOR_NAME, SUMMARY_FASTEST_COMPARATOR);
//      temp.put(SUMMARY_AVERAGE_COMPARATOR_NAME, SUMMARY_AVERAGE_COMPARATOR);
//      
//      SUMMARY_COMPARATOR_MAP_BY_NAME = Collections.unmodifiableMap(temp);
//   }
//
//   public final static Map<Integer, Comparator<Summary>> SUMMARY_COMPARATOR_MAP_BY_INT;
//   static {
//      Map<Integer, Comparator<Summary>> temp = new HashMap<Integer, Comparator<Summary>>();
//      int i = 0;
//      temp.put(i++, SUMMARY_KEY_COMPARATOR);
//      temp.put(i++, SUMMARY_TOTAL_COMPARATOR);
//      temp.put(i++, SUMMARY_INVOKED_COMPARATOR);
//      temp.put(i++, SUMMARY_SLOWEST_COMPARATOR);
//      temp.put(i++, SUMMARY_FASTEST_COMPARATOR);
//      temp.put(i++, SUMMARY_AVERAGE_COMPARATOR);
//      
//      SUMMARY_COMPARATOR_MAP_BY_INT = Collections.unmodifiableMap(temp);
//   }

//   public static String summaryToString(String summaryComparatorName) throws Exception {
//      Comparator<Summary> comparator = SUMMARY_COMPARATOR_MAP_BY_NAME.get(summaryComparatorName);
//      if (comparator == null) {
//         throw new Exception("summaryToString received an invaled summaryComparatorName("
//               + summaryComparatorName + ")");
//      }
//      return summaryToString(comparator);
//   }

   public static String summaryToString(Comparator<Summary> summaryComparator) {
      StringBuffer sb = new StringBuffer();

      long currentTimeMillis = System.currentTimeMillis();
      long elapsedNanos = System.nanoTime() - profiledPeriodStartNanos;

      sb.append("  <b>includes activity...</b>\n\n").append("      from: ").append(dateFormat.format(profiledPeriodStartMillis)).
            append("\n").append("        to: ").append(dateFormat.format(currentTimeMillis)).append("\n\n").append("  <b>elapsed time:</b> ").
            append(nanosToString(elapsedNanos)).append("\n\n");

      synchronized (summaryMap) {
         List<Summary> summaryList = new ArrayList<Summary>(summaryMap.values());
         Collections.sort(summaryList, summaryComparator);
         Iterator<Summary> it = summaryList.iterator();
         while (it.hasNext()) {
            Summary summary = it.next();
            sb.append("<b>").append(summary.key).append("</b>\n").append(summary).append("\n");
         }
      }
      return sb.toString();
   }

   public static class Summary {
      final String key;
      long invoked = 0;
      long fastest = Long.MAX_VALUE;
      long slowest = Long.MIN_VALUE;
      long totalNanos = 0;

      public Summary(String key) {
         this.key = key;
      }

      public long calculateAverage() {
         if (invoked == 0) {
            // -1 makes sense in the context of sorting.  We want methods 
            // that haven't been called to come last.  Averages are sorted from 
            // big to small.
            return -1;
         }

         return totalNanos / invoked;
      }

      public String toString() {
         String average;
         try {
            average = nanosToString(totalNanos / invoked);
         }
         catch (Exception excp) {
            average = "N/A";
         }

         String slowestNormalized;
         if (slowest == Long.MIN_VALUE) {
            slowestNormalized = "N/A";
         }
         else {
            slowestNormalized = nanosToString(slowest);
         }

         String fastestNormalized;
         if (fastest == Long.MAX_VALUE) {
            fastestNormalized = "N/A";
         }
         else {
            fastestNormalized = nanosToString(fastest);
         }

         return new StringBuffer("   Invoked: ").append(invoked).append("\n").append("   Fastest: ").
               append(fastestNormalized).append("\n").append("   Slowest: ").append(slowestNormalized).
               append("\n").append("   Average: ").append(average).append("\n").append("     Total: ").
               append(nanosToString(totalNanos)).append("\n").toString();
      }
   }

   public static class Stamp {

      /**
       * Used to avoid null pointer errors. Null is checked once in the factory method. This TreeMap is
       * never modified.
       */
      private final static SortedMap<Integer, StackTraceElement> EMPTY_ELEMENTS = new TreeMap<Integer, StackTraceElement>();

      /**
       * Set at the end of the timed event. If it is null then the "clock"
       * is still ticking.
       */
      private String result = null;

      /**
       * The date when this Profiler.Stamp was created. Not used to calculate
       * the elapsed time. See nanos.
       */
      final public long wallClockStartTime = System.currentTimeMillis();

      /**
       * Nano measurement, according to Java spec, does not relate
       * to wall clock time. It for measuring elapsed periods.
       * 
       * This variable gets on Stamp creation.
       */
      public final long startNanos = System.nanoTime();

      /**
       * Elapsed nanos.
       * 
       * Gets set when results are received.
       */
      private long elpasedNanos = NULL_TIME;

      final private SortedMap<Integer, StackTraceElement> elements;

      /**
       * Composed lazily from 'elements' and 'tag'. Always use getter.
       */
      private String key = null;

      /**
       * Descriptive one word tag of what we are profiling, e.g. "method"
       * for profiling the whole method, "iteration" for profiling a single
       * iteration.
       */
      final public String tag;

      private Stamp(SortedMap<Integer, StackTraceElement> elements, String tag) {
         this.elements = (elements == null) ? EMPTY_ELEMENTS : elements;
         this.tag = (tag == null) ? "TAG" : tag;
      }

      public Stamp(String key, String tag) {
         this.elements = EMPTY_ELEMENTS;
         this.key = key;
         this.tag = tag;
      }

      synchronized public boolean isInProgress() {
         return elpasedNanos == NULL_TIME;
      }

      /**
       * Sets the results, stops the "clock", and calculates elapsed nanos.
       * 
       * @param result
       * @param endingNanos
       */
      synchronized public void stopTheClock(String result, long endingNanos) {
         elpasedNanos = endingNanos - startNanos;
         this.result = result == null ? "DONE" : result;
      }

      synchronized public long getElapsedNanos() {
         return (elpasedNanos == NULL_TIME) ? System.nanoTime() - startNanos : elpasedNanos;
      }

      public String getKey() {
         if (key != null) {
            return key;
         }
         StringBuilder sb = new StringBuilder();
         for (Integer key : elements.keySet()) {
            if (sb.length() > 0) {
               sb.append(" <- ");
            }
            else {
               sb.append(tag).append(' ');
            }
            StackTraceElement value = elements.get(key);
            sb.append('[').append(key).append(']').append(value);
         }
         return sb.toString();
      }

      public String toString() {
         StringBuilder sb = new StringBuilder("ELAPSED").append("[").append(Util.elapsedNanoFormatterMinutes(getElapsedNanos())).
               append("]").append(isInProgress() ? " in progress" : " RESULT " + result).append(" ").append(getKey());

         return sb.toString();
      }
   }
}
