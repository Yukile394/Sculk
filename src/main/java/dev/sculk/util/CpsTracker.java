package dev.sculk.util;

import java.util.ArrayDeque;
import java.util.Deque;

public final class CpsTracker {
   private static final Deque<Long> CLICKS = new ArrayDeque<>();

   private CpsTracker() {
   }

   public static void click() {
      long now = System.currentTimeMillis();
      CLICKS.addLast(now);
      purge(now);
   }

   public static int cps() {
      purge(System.currentTimeMillis());
      return CLICKS.size();
   }

   private static void purge(long now) {
      while (!CLICKS.isEmpty() && now - CLICKS.peekFirst() > 1000L) {
         CLICKS.pollFirst();
      }
   }
}
