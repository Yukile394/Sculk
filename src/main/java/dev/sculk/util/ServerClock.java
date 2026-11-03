package dev.sculk.util;

public final class ServerClock {
   private static float intervalMs = 50.0F;
   private static long tick = Long.MIN_VALUE;
   private static long lastNanos;

   private ServerClock() {
   }

   public static void record(long worldTick) {
      long now = System.nanoTime();
      if (tick != Long.MIN_VALUE && worldTick != tick) {
         float delta = (float)(now - lastNanos) / 1000000.0F;
         if (delta > 1.0F && delta < 2000.0F) {
            intervalMs = intervalMs + (delta - intervalMs) * 0.15F;
         }
      }

      tick = worldTick;
      lastNanos = now;
   }

   public static long tick() {
      return tick;
   }

   public static boolean jammed() {
      if (tick == Long.MIN_VALUE) {
         return false;
      } else {
         long silentMs = (System.nanoTime() - lastNanos) / 1000000L;
         return (float)silentMs > Math.max(500.0F, intervalMs * 3.0F);
      }
   }
}
