package dev.sculk.module.modules.combat.spooky;

/** Nursultan class09124: a millisecond stopwatch. */
public class C09124 {
   private long start = System.currentTimeMillis();

   public long y() {
      return System.currentTimeMillis() - this.start;
   }

   public boolean N(long l2) {
      return this.y() >= l2;
   }

   public void N() {
      this.start = System.currentTimeMillis();
   }
}
