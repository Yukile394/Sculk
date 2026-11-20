package dev.sculk.module.modules.combat.spooky;

/** Nursultan class11097 (record). */
public final class C11097 {
   public Rot rotation;
   public boolean currentHardRaycast;
   public boolean serverHardRaycast;
   public boolean urgentCatchup;
   public boolean holdPitch;
   public boolean detachedAim;
   public boolean overshootAim;
   public boolean pullbackFlick;
   public float yawError;
   public float pitchError;
   public double distance;

   public C11097(Rot rotation, boolean currentHardRaycast, boolean serverHardRaycast, boolean urgentCatchup, boolean holdPitch, boolean detachedAim, boolean overshootAim, boolean pullbackFlick, float yawError, float pitchError, double distance) {
      this.rotation = rotation;
      this.currentHardRaycast = currentHardRaycast;
      this.serverHardRaycast = serverHardRaycast;
      this.urgentCatchup = urgentCatchup;
      this.holdPitch = holdPitch;
      this.detachedAim = detachedAim;
      this.overshootAim = overshootAim;
      this.pullbackFlick = pullbackFlick;
      this.yawError = yawError;
      this.pitchError = pitchError;
      this.distance = distance;
   }

   public float L() {
      return this.pitchError;
   }

   public boolean M() {
      return this.detachedAim;
   }

   public boolean B() {
      return this.pullbackFlick;
   }

   public boolean Z() {
      return this.urgentCatchup;
   }

   public boolean i() {
      return this.serverHardRaycast;
   }

   public float U() {
      return this.yawError;
   }

   public boolean z() {
      return this.currentHardRaycast;
   }

   public double u() {
      return this.distance;
   }

   public Rot y() {
      return this.rotation;
   }

   public boolean N() {
      return this.holdPitch;
   }

   public boolean R() {
      return this.overshootAim;
   }
}
