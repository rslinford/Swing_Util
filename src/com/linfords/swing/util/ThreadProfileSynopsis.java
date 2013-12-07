package com.linfords.swing.util;

import java.util.Iterator;
import java.util.TreeMap;

public class ThreadProfileSynopsis<K, V> extends TreeMap<Integer, ThreadProfileSynopsis.StackTraceNode> {
   private static final long serialVersionUID = 1L;

   StackTraceNode getNullSafe(final Integer key, final StackTraceElement frame) {
      return getNullSafe(key, frame, Util.isPerformanceRisk(frame));
   }

   private StackTraceNode getNullSafe(final Integer key, final StackTraceElement frame,
            final boolean suspectCode) {
      StackTraceNode info = super.get(key);

      if (info == null) {
         info = new StackTraceNode(key, frame, suspectCode);
         super.put(key, info);
      }
      return info;
   }

   /**
    * Credits elapsed time to new crux of tree. Saves frameInfo if it appears to be significant.
    */
   void recordFrameInfo(final int newCruxIndex, final StackTraceNode expiredFrameInfo,
            final long unreasonableNanos, final AsyncPrinter aout) {
      StackTraceNode crux = super.get(newCruxIndex);
      if (crux == null) {
         StringBuilder sb = new StringBuilder("Monitor malfunction storing frameinfo: ").
                  append(expiredFrameInfo).append(" Index(").append(newCruxIndex).append(") did not exist");
         aout.add(sb);
         return;
      }

      crux.elapsedNanos += expiredFrameInfo.elapsedNanos; // credit time to crux

      // Tests for significance of frameInfo. Should it stay or should it go?
      if ((expiredFrameInfo.elapsedNanos >= unreasonableNanos) || expiredFrameInfo.calledThreadWait
               || expiredFrameInfo.dontRunThisCodeOnTheEDT || expiredFrameInfo.isOurStuff) {
         crux.getSubProfile().put(expiredFrameInfo.height, expiredFrameInfo);
      }
   }

   private void dump(final StringBuilder sb, StringBuilder indentSum, final String indentIncrement) {
      for (Iterator<StackTraceNode> it = super.values().iterator(); it.hasNext();) {
         it.next().dump(sb, indentSum, indentIncrement);
      }
   }

   void dump(final StringBuilder sb, final String indentIncrement) {
      dump(sb, new StringBuilder(indentIncrement), indentIncrement);
   }

   static class StackTraceNode {

      /** Created lazily. Only to be accessed through getter */
      private ThreadProfileSynopsis<Integer, StackTraceNode> subProfile = null;

      long elapsedNanos = 0;
      boolean calledThreadWait = false;
      public boolean isOurStuff = false;

      final StackTraceElement frame;
      final int height;
      final boolean dontRunThisCodeOnTheEDT;

      private StackTraceNode(final int height, final StackTraceElement frame) {
         this.height = height;
         this.frame = frame;
         this.dontRunThisCodeOnTheEDT = Util.isPerformanceRisk(frame);
      }

      private StackTraceNode(final int height, final StackTraceElement frame, final boolean suspectCode) {
         this.height = height;
         this.frame = frame;
         this.dontRunThisCodeOnTheEDT = suspectCode;
         this.isOurStuff = Util.isEzeniaCode(frame);
      }

      public String toString() {
         return (elapsedNanos == 0 ? "      " : Util.elapsedNanoFormatterSeconds(elapsedNanos)) + " ["
                  + height + "]" + frame.toString() + (calledThreadWait ? " ** thread wait **" : "")
                  + (dontRunThisCodeOnTheEDT ? " -- performance risk --" : "");
      }

      private TreeMap<Integer, StackTraceNode> getSubProfile() {
         if (subProfile != null) {
            return subProfile;
         }

         subProfile = new ThreadProfileSynopsis<Integer, StackTraceNode>();
         return subProfile;
      }

      private void dump(final StringBuilder sb, final StringBuilder indentSum, final String indentIncrement) {
         sb.append('\n').append(indentSum).append(this);
         dumpSubProfile(sb, indentSum.append(indentIncrement), indentIncrement);
         // Using a StringBuilder has the disadvantage of "remembering" (i.e. not shrinking)
         // its length after popping the stack, causing to grow and grow during tree traversal. 
         // Here we manually shrink the length of indentSum. I'm not sure whether this performs 
         // better than using String objects and concatenation. I'd hope that there's less garbage 
         // collection with a StringBuffer.
         indentSum.setLength(indentSum.length() - indentIncrement.length());
      }

      private void dumpSubProfile(final StringBuilder sb, final StringBuilder indentSum,
            final String indentIncrement) {
         if (subProfile != null) {
            subProfile.dump(sb, indentSum, indentIncrement);
         }
      }
   }

}
