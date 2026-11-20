package dev.sculk.module.modules.combat.spooky;

/** Nursultan class11085 (enum). */
public enum C11085 {
   MID_CHEST,
   UPPER_CHEST,
   SHOULDER,
   HEAD_LINE;

   public C11085 N(C09166 rng) {
      C11085[] arr = values();
      C11085 pick = arr[rng.N(0, arr.length - 1)];
      return pick == this ? arr[(this.ordinal() + 1) % arr.length] : pick;
   }
}
