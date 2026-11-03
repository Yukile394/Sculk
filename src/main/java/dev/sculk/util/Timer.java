package dev.sculk.util;

public final class Timer {
   private long last = System.currentTimeMillis();

   public void reset() {
      this.last = System.currentTimeMillis();
   }

   public boolean delay(long millis) {
      return System.currentTimeMillis() - this.last >= millis;
   }

   public long elapsed() {
      return System.currentTimeMillis() - this.last;
   }
}
