package dev.sculk.module.modules.combat;

import dev.sculk.SculkClient;
import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.render.Toasts;
import dev.sculk.util.ElytraMath;
import dev.sculk.util.ElytraMath.Phase;
import dev.sculk.util.ElytraMath.Situation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * ElytraBooster: gets you past a gliding Aura target and keeps you inside swing range while Aura and Auto Firework run.
 *
 * Rockets are scheduled by the situation instead of a fixed timer (see ElytraMath.phase):
 *   Approach  rockets are re-fired just before the previous one burns out (seamless boost, optional overlap of several)
 *   Strike    only a controlled closing speed (Pass Speed) so Aura does not fly through the target between two swings
 *   Lead      you are in front of the target: rockets only to keep up with its speed
 * A rocket is only fired when the current look direction pushes you the right way; a rocket fired while Aura aims backwards
 * at a target you just passed would brake you instead.
 *
 * Modes:
 *   Physics  rockets only. Every velocity change comes from real rockets, so the server's own simulation sees nothing it has
 *            not seen from a normal player. Top speed stays at the vanilla rocket limit.
 *   Emulate  while a real rocket is active, additionally applies the vanilla rocket formula (1..2 times per tick), as if more
 *            rockets were stacked. Faster acceleration; a server that counts rockets precisely can still see it.
 *   Raw      flat impulse along the look vector up to Speed Cap. Not tied to any rocket. Uncalibrated, most detectable.
 *
 * Emulate and Raw back off on their own: a server position correction while they were used cuts the Speed Cap to just below
 * the speed you had and pauses them for Backoff seconds. The learned cap resets when the module is toggled.
 *
 * Needs Auto Firework enabled (it does the actual rocket use, Silent included) and Aura enabled with a target.
 */
public final class ElytraBooster extends Module {
   /** A rocket is only fired when look . wanted direction is at least this (cos of ~70 degrees). */
   private static final double MIN_ALIGN = 0.35;
   /** Emulate / Raw only push while looking this closely along the wanted direction (cos of ~37 degrees). */
   private static final double MIN_ALIGN_EXTRA = 0.8;
   private static final double HOLD_SPEED_FACTOR = 0.97;
   private static final long LEASE_MS = 150L;
   private static final double LEARNED_CAP_FLOOR = 0.8;

   private final ModeSetting<Mode> mode = new ModeSetting<>("Mode", Mode.Physics)
      .describe("Physics = rockets only; Emulate = extra vanilla rocket steps; Raw = flat impulse (most detectable)");
   private final NumberSetting engageRange = new NumberSetting("Engage Range", 48.0, 10.0, 120.0, 1.0)
      .describe("Distance to the Aura target below which the booster takes over rocket firing");
   private final NumberSetting strikeRange = new NumberSetting("Strike Range", 7.0, 3.0, 20.0, 0.5)
      .describe("Inside this distance the closing speed is controlled instead of maximised");
   private final NumberSetting leadDistance = new NumberSetting("Lead Distance", 2.5, 0.0, 10.0, 0.5)
      .describe("How far in front of the target (along its heading) counts as 'passed'");
   private final BooleanSetting overtake = new BooleanSetting("Overtake", true)
      .describe("Inside Strike Range keep a small positive closing speed until you are in front of the target");
   private final NumberSetting passSpeed = new NumberSetting("Pass Speed", 0.35, 0.05, 1.5, 0.05)
      .describe("Blocks per tick of closing speed wanted while overtaking inside Strike Range (uncalibrated)")
      .visibleWhen(() -> this.overtake.get());
   private final NumberSetting stack = new NumberSetting("Rocket Stack", 1.0, 1.0, 3.0, 1.0)
      .describe("How many real rockets are kept burning at once while approaching");
   private final NumberSetting refireAhead = new NumberSetting("Refire Ahead", 3.0, 0.0, 10.0, 1.0)
      .describe("Ticks before the estimated end of a rocket at which the next one is fired");
   private final NumberSetting minGap = new NumberSetting("Min Gap", 4.0, 2.0, 20.0, 1.0)
      .describe("Smallest number of ticks between two rocket uses (random +-1 tick is added)");
   private final NumberSetting minSpeed = new NumberSetting("Min Speed", 0.7, 0.2, 1.5, 0.05)
      .describe("Below this speed (blocks per tick) a rocket is fired whatever the phase, so you never stall");
   private final NumberSetting speedCap = new NumberSetting("Speed Cap", 1.75, 1.0, 3.0, 0.05)
      .describe("Emulate / Raw never push beyond this speed (blocks per tick). Uncalibrated; the vanilla rocket limit is about 1.7")
      .visibleWhen(() -> !this.mode.is(Mode.Physics));
   private final NumberSetting emulateSteps = new NumberSetting("Emulate Steps", 1.0, 1.0, 2.0, 1.0)
      .describe("Extra rocket formula applications per tick while a real rocket is active")
      .visibleWhen(() -> this.mode.is(Mode.Emulate));
   private final NumberSetting rawImpulse = new NumberSetting("Raw Impulse", 0.04, 0.01, 0.3, 0.01)
      .describe("Blocks per tick added along the look vector each tick (uncalibrated)")
      .visibleWhen(() -> this.mode.is(Mode.Raw));
   private final NumberSetting backoff = new NumberSetting("Backoff", 5.0, 1.0, 30.0, 1.0)
      .describe("Seconds Emulate / Raw stay off after the server corrected your position")
      .visibleWhen(() -> !this.mode.is(Mode.Physics));
   private final BooleanSetting onlyGlidingTarget = new BooleanSetting("Only Gliding Target", false)
      .describe("Only act while the Aura target is gliding too");
   private final BooleanSetting debug = new BooleanSetting("Debug", false)
      .describe("Once per second: phase, speeds, distances, active rockets, cap and server corrections");

   private static final class Fired {
      final long minEnd;
      final long maxEnd;

      Fired(long minEnd, long maxEnd) {
         this.minEnd = minEnd;
         this.maxEnd = maxEnd;
      }
   }

   private final List<Fired> fired = new ArrayList<>();
   private long tickCount;
   private long lastFireTick = Long.MIN_VALUE / 2;
   private int gapJitter;
   private Phase phase = Phase.Idle;
   private int trackedId = Integer.MIN_VALUE;
   private Vec3d lastTargetPos;
   private Vec3d measuredVelocity = Vec3d.ZERO;
   private double lastSpeed;
   private double learnedCap = Double.MAX_VALUE;
   private long extraBlockedUntil;
   private long lastExtraTick = Long.MIN_VALUE / 2;
   private double extraScale = 1.0;
   private int setbacks;
   private int debugTicks;
   private boolean warnedNoAutoFirework;

   public ElytraBooster() {
      super("ElytraBooster", "Overtakes a gliding Aura target with situation-based rocket control and stays in swing range", Category.COMBAT);
      this.addSettings(new Setting[]{
         this.mode, this.engageRange, this.strikeRange, this.leadDistance, this.overtake, this.passSpeed, this.stack, this.refireAhead,
         this.minGap, this.minSpeed, this.speedCap, this.emulateSteps, this.rawImpulse, this.backoff, this.onlyGlidingTarget, this.debug
      });
   }

   public Phase phase() {
      return this.phase;
   }

   @Override
   public void onEnable() {
      this.reset();
      this.learnedCap = Double.MAX_VALUE;
      this.setbacks = 0;
      this.warnedNoAutoFirework = false;
   }

   @Override
   public void onDisable() {
      AutoFirework firework = SculkClient.get(AutoFirework.class);
      if (firework != null) {
         firework.leaseControl(0L);
      }

      this.reset();
   }

   private void reset() {
      this.fired.clear();
      this.phase = Phase.Idle;
      this.trackedId = Integer.MIN_VALUE;
      this.lastTargetPos = null;
      this.measuredVelocity = Vec3d.ZERO;
      this.extraBlockedUntil = 0L;
      this.extraScale = 1.0;
      this.lastSpeed = 0.0;
   }

   /** Called from Mixin7 on the client thread when the server sent a position correction. */
   public void onServerCorrection() {
      ClientPlayerEntity player = this.getMC().player;
      if (!this.isEnabled() || player == null || !player.isFallFlying()) {
         return;
      }

      this.setbacks++;
      boolean extrasRecent = this.tickCount - this.lastExtraTick <= 40L;
      if (!this.mode.is(Mode.Physics) && extrasRecent) {
         this.learnedCap = Math.max(LEARNED_CAP_FLOOR, Math.min(this.learnedCap, this.lastSpeed * 0.97));
         this.extraBlockedUntil = this.tickCount + (long) this.backoff.getInt() * 20L;
         this.extraScale = 0.25;
      }

      if (this.debug.get()) {
         Toasts.show(String.format(Locale.ROOT, "ElytraBooster: server correction #%d at %.2f b/t, cap now %.2f%s", this.setbacks, this.lastSpeed,
            this.currentCap(), extrasRecent ? " (extras were active)" : ""));
      }
   }

   // ------------------------------------------------------------------ tick

   @Override
   public void onTick() {
      ClientPlayerEntity player = this.mc.player;
      if (player == null || this.mc.world == null) {
         return;
      }

      this.tickCount++;
      this.pruneFired();
      if (this.extraBlockedUntil != 0L && this.tickCount >= this.extraBlockedUntil && this.extraScale < 1.0) {
         this.extraScale = Math.min(1.0, this.extraScale + 0.02);
      }

      AutoFirework firework = SculkClient.get(AutoFirework.class);
      Aura aura = SculkClient.get(Aura.class);
      Entity target = aura != null && aura.isEnabled() ? aura.currentTarget() : null;
      this.trackTarget(target);

      boolean gliding = player.isFallFlying();
      if (!gliding) {
         this.fired.clear();
      }

      Vec3d myVelocity = player.getVelocity();
      this.lastSpeed = gliding && ElytraMath.finite(myVelocity) ? myVelocity.length() : 0.0;

      if (!gliding || target == null || !target.isAlive() || this.mc.currentScreen != null || player.isDead()) {
         this.phase = Phase.Idle;
         return;
      }

      if (firework == null || !firework.isEnabled()) {
         this.phase = Phase.Idle;
         if (!this.warnedNoAutoFirework) {
            this.warnedNoAutoFirework = true;
            Toasts.show("ElytraBooster: enable Auto Firework, it fires the rockets");
         }

         return;
      }

      this.warnedNoAutoFirework = false;
      if (this.onlyGlidingTarget.get() && !(target instanceof LivingEntity living && living.isFallFlying())) {
         this.phase = Phase.Idle;
         return;
      }

      Vec3d targetVelocity = this.targetVelocity(target);
      Situation situation = ElytraMath.analyse(player.getBoundingBox().getCenter(), myVelocity, target.getBoundingBox().getCenter(), targetVelocity);
      this.phase = ElytraMath.phase(situation, this.engageRange.getValue(), this.strikeRange.getValue(), this.leadDistance.getValue());
      if (this.phase == Phase.Idle) {
         return;
      }

      // from here on the booster is the only source of rockets; AutoFirework's own timer stands down
      firework.leaseControl(LEASE_MS);

      Vec3d look = player.getRotationVector();
      Vec3d wanted = this.phase == Phase.Approach || !situation.hasHeading() ? situation.toTarget() : situation.heading();
      double align = ElytraMath.finite(look) && ElytraMath.finite(wanted) ? look.dotProduct(wanted) : -1.0;

      boolean stalling = situation.mySpeed() < this.minSpeed.getValue();
      boolean wanting = stalling || (align >= MIN_ALIGN && this.policyWantsRocket(situation));
      if (wanting && this.rocketDue(stalling)) {
         int flight = firework.peekRocketFlight();
         if (firework.fireNow()) {
            this.recordFire(flight);
         }
      }

      if (!this.mode.is(Mode.Physics) && this.phase == Phase.Approach && align >= MIN_ALIGN_EXTRA) {
         this.applyExtra(player, look);
      }

      this.debugLine(situation);
   }

   // ------------------------------------------------------------------ rocket policy

   private boolean policyWantsRocket(Situation s) {
      if (this.phase == Phase.Approach) {
         return true;
      }

      boolean holdRule = s.mySpeed() < s.targetSpeed() * HOLD_SPEED_FACTOR;
      if (this.phase == Phase.Strike && this.overtake.get() && s.hasHeading() && s.ahead() < this.leadDistance.getValue()) {
         return s.closing() < this.passSpeed.getValue() || holdRule;
      }

      return holdRule;
   }

   /** Pure scheduling: true when fewer than Rocket Stack rockets are certain to still be burning after Refire Ahead ticks. */
   private boolean rocketDue(boolean urgent) {
      int gap = (int) this.minGap.getValue().doubleValue() + this.gapJitter;
      if (this.tickCount - this.lastFireTick < Math.max(1, gap)) {
         return false;
      }

      if (urgent) {
         return true;
      }

      int ahead = this.refireAhead.getInt();
      int certain = 0;
      for (Fired f : this.fired) {
         if (f.minEnd - this.tickCount > ahead) {
            certain++;
         }
      }

      return certain < this.stack.getInt();
   }

   private void recordFire(int flightDuration) {
      this.lastFireTick = this.tickCount;
      this.gapJitter = ThreadLocalRandom.current().nextInt(-1, 2);
      this.fired.add(new Fired(this.tickCount + ElytraMath.rocketMinLifeTicks(flightDuration), this.tickCount + ElytraMath.rocketMaxLifeTicks(flightDuration)));
   }

   private void pruneFired() {
      this.fired.removeIf(f -> f.maxEnd <= this.tickCount);
   }

   private boolean rocketMaybeActive() {
      for (Fired f : this.fired) {
         if (f.maxEnd > this.tickCount) {
            return true;
         }
      }

      return false;
   }

   // ------------------------------------------------------------------ extra velocity (Emulate / Raw)

   private double currentCap() {
      return Math.min(this.speedCap.getValue(), this.learnedCap);
   }

   private void applyExtra(ClientPlayerEntity player, Vec3d look) {
      if (this.tickCount < this.extraBlockedUntil || player.isOnGround() || player.hasVehicle() || player.isTouchingWater() || player.isInLava()) {
         return;
      }

      Vec3d before = player.getVelocity();
      if (!ElytraMath.finite(before) || !ElytraMath.finite(look)) {
         return;
      }

      Vec3d after;
      if (this.mode.is(Mode.Emulate)) {
         if (!this.rocketMaybeActive()) {
            return;
         }

         after = before;
         for (int i = 0; i < this.emulateSteps.getInt(); i++) {
            after = ElytraMath.rocketStep(after, look);
         }
      } else {
         double room = Math.max(0.0, this.currentCap() - before.length());
         after = before.add(look.multiply(Math.min(this.rawImpulse.getValue(), room)));
      }

      Vec3d delta = after.subtract(before).multiply(this.extraScale);
      Vec3d result = ElytraMath.capSpeed(before, before.add(delta), this.currentCap());
      if (ElytraMath.finite(result) && result.squaredDistanceTo(before) > 1.0E-8) {
         player.setVelocity(result);
         this.lastExtraTick = this.tickCount;
      }
   }

   // ------------------------------------------------------------------ target tracking

   /** Remote players' getVelocity() stays near zero on the client, so the speed is measured from the position change per tick. */
   private void trackTarget(Entity target) {
      if (target == null) {
         this.trackedId = Integer.MIN_VALUE;
         this.lastTargetPos = null;
         this.measuredVelocity = Vec3d.ZERO;
         return;
      }

      Vec3d pos = target.getPos();
      if (target.getId() != this.trackedId || this.lastTargetPos == null) {
         this.trackedId = target.getId();
         this.lastTargetPos = pos;
         this.measuredVelocity = Vec3d.ZERO;
         return;
      }

      Vec3d step = pos.subtract(this.lastTargetPos);
      this.lastTargetPos = pos;
      if (step.lengthSquared() > 64.0) {
         this.measuredVelocity = Vec3d.ZERO;
      } else {
         this.measuredVelocity = this.measuredVelocity.multiply(0.4).add(step.multiply(0.6));
      }
   }

   private Vec3d targetVelocity(Entity target) {
      Vec3d reported = target.getVelocity();
      return reported.lengthSquared() > this.measuredVelocity.lengthSquared() ? reported : this.measuredVelocity;
   }

   // ------------------------------------------------------------------ debug

   private void debugLine(Situation s) {
      if (!this.debug.get() || ++this.debugTicks < 20) {
         return;
      }

      this.debugTicks = 0;
      int certain = 0;
      for (Fired f : this.fired) {
         if (f.minEnd > this.tickCount) {
            certain++;
         }
      }

      Toasts.show(String.format(Locale.ROOT, "ElytraBooster %s: me %.2f tgt %.2f dist %.1f ahead %.1f lat %.1f closing %.2f rockets %d/%d cap %s x%.2f corr %d",
         this.phase, s.mySpeed(), s.targetSpeed(), s.distance(), s.ahead(), s.lateral(), s.closing(), certain, this.fired.size(),
         this.mode.is(Mode.Physics) ? "-" : String.format(Locale.ROOT, "%.2f", this.currentCap()), this.extraScale, this.setbacks));
   }

   public static enum Mode {
      Physics,
      Emulate,
      Raw;
   }
}
