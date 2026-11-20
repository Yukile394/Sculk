package dev.sculk.module.modules.combat.spooky;

/** Nursultan class11086 (record). */
public final class C11086 {
   public boolean active;
   public float yawOffset;
   public float pitchOffset;
   public boolean releaseAim;

   public C11086(boolean active, float yawOffset, float pitchOffset, boolean releaseAim) {
      this.active = active;
      this.yawOffset = yawOffset;
      this.pitchOffset = pitchOffset;
      this.releaseAim = releaseAim;
   }

   public boolean L() {
      return this.releaseAim;
   }

   public float i() {
      return this.pitchOffset;
   }

   public static C11086 u() {
      return new C11086(false, 0.0F, 0.0F, false);
   }

   public float y() {
      return this.yawOffset;
   }

   public boolean N() {
      return this.active;
   }
}
