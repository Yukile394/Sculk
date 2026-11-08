// Language: Java 17, file: dev/sculk/util/Timer.java, runtime: Fabric client util
// Monotonic millisecond timer for interval-gated actions (rockets, combat sync, any repeating trigger).
// delay() jitters its effective threshold +-6% (min 15ms) each closed cycle, re-rolling on every
// fire -- defeats fixed-interval periodicity analysis (Grim/Vulcan TimerCheck style): a client
// acting every exactly N ms is a statistical tell, this never repeats the same effective gap twice.
package dev.sculk.util;

public final class Timer {
   private long last = System.currentTimeMillis();
   private long jitterMs = 0L;

   public void reset() {
      this.last = System.currentTimeMillis();
      this.jitterMs = 0L;
   }

   public boolean delay(long millis) {
      if (this.jitterMs == 0L) {
         long span = Math.max(15L, (long) (millis * 0.06));
         this.jitterMs = (long) ((Math.random() * 2.0 - 1.0) * span);
      }
      long effective = Math.max(1L, millis + this.jitterMs);
      boolean ready = System.currentTimeMillis() - this.last >= effective;
      if (ready) {
         this.jitterMs = 0L;
      }
      return ready;
   }

   public long elapsed() {
      return System.currentTimeMillis() - this.last;
   }

   /**
    * Forces delay() to read as "already elapsed" on the next check without a real reset --
    * used to fire on an edge event (elytra just opened) instead of waiting out whatever phase
    * the interval was mid-cycle in.
    */
   public void forceElapsed(long millis) {
      this.last = System.currentTimeMillis() - millis;
      this.jitterMs = 0L;
   }
}
