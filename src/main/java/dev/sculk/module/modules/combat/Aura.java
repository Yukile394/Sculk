package dev.sculk.module.modules.combat;

import dev.sculk.SculkClient;
import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.render.Toasts;
import dev.sculk.util.CombatUtils;
import dev.sculk.util.Friends;
import dev.sculk.util.MouseQuantizer;
import dev.sculk.util.RotationManager;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Aura, ported from Tera Client (jako.features.modules.combat.Aura) onto Fabric 1.21.1 / Yarn.
 * Settings and behaviour follow the original: HvH / Smooth rotation, Smart / Only critical gate, sprint drop,
 * shield breaker, pause and auto-disable rules, target filters, randomized attack delay, GCD-quantized rotation
 * with tremor noise, human-like yaw/pitch animation, and the elytra hooks (predicted hitbox, sharp snap).
 * The rotation is silent: it goes through RotationManager, so the camera keeps following the mouse.
 *
 * Left out of the port: the ESP setting (use the Target ESP module, it follows this target) and the
 * ElytraHelper pre-hit slot switch (there is no ElytraHelper module in Sculk).
 */
public final class Aura extends Module {
   private static final float ATTACK_COOLDOWN = 0.86F;

   private final NumberSetting attackRange = new NumberSetting("Range", 3.1, 1.0, 6.0, 0.1);
   private final NumberSetting aimRange = new NumberSetting("AimRange", 3.1, 0.0, 6.0, 0.1);
   private final NumberSetting elytraAimRange = new NumberSetting("ElytraAimRange", 25.0, 10.0, 100.0, 1.0);
   private final ModeSetting<Aura.Mode> rotationMode = new ModeSetting<>("Rotation", Aura.Mode.HvH);
   private final ModeSetting<Aura.CritMode> crit = new ModeSetting<>("Crit", Aura.CritMode.Smart);
   private final ModeSetting<Aura.DropSprint> dropSprint = new ModeSetting<>("DropSprint", Aura.DropSprint.On);
   private final BooleanSetting shieldBreaker = new BooleanSetting("ShieldBreaker", true);
   private final BooleanSetting unpressShield = new BooleanSetting("UnpressShield", true);
   private final BooleanSetting pauseWhileEating = new BooleanSetting("Pause: Eating", false);
   private final BooleanSetting pauseInInventory = new BooleanSetting("Pause: Inventory", true);
   private final BooleanSetting deathDisable = new BooleanSetting("Disable: Death", true);
   private final BooleanSetting killDisable = new BooleanSetting("Disable: Kill", false);
   private final BooleanSetting players = new BooleanSetting("Players", true);
   private final BooleanSetting animals = new BooleanSetting("Animals", true);
   private final BooleanSetting monsters = new BooleanSetting("Monsters", true);
   private final BooleanSetting invisibles = new BooleanSetting("Invisibles", true);
   private final BooleanSetting lagComp = new BooleanSetting("LagComp", true)
      .describe("Holds a swing while the target would already be out of reach when the server processes it (uses ping and the target's speed)");
   private final NumberSetting serverReach = new NumberSetting("ServerReach", 3.0, 1.0, 6.0, 0.1)
      .describe("Largest predicted server-side distance to the hitbox at which a swing is made")
      .visibleWhen(() -> this.lagComp.get());
   private final BooleanSetting debug = new BooleanSetting("Debug", false)
      .describe("Prints HIT or MISS for every swing with distance, predicted distance, ping and target speed");

   private Entity target;
   private boolean lookingAtHitbox;
   private long lastAttackMs;
   private long attackDelayMs;
   private float cooldownGate = ATTACK_COOLDOWN;
   private long pausedAtMs = -10_000L;
   private float tickFallDistance;
   private boolean tickOnGround;
   private Entity notifiedKill;
   private float rotationYaw;
   private float rotationPitch;
   private float desiredYaw;
   private float desiredPitch;
   private final YawAnim yawAnim = new YawAnim(1.0F);
   private final YawAnim pitchAnim = new YawAnim(0.65F);
   private final MouseQuantizer quantizer = new MouseQuantizer();
   private final PitchTremor yawTremor = new PitchTremor();
   private final PitchTremor pitchTremor = new PitchTremor();
   private final long aimNoiseSeed = ThreadLocalRandom.current().nextLong();
   private Entity trackedEntity;
   private Vec3d trackedPos = Vec3d.ZERO;
   private Vec3d targetVelocity = Vec3d.ZERO;
   private LivingEntity probeTarget;
   private int probeTicksLeft;
   private int probeHurtBefore;
   private String probeInfo = "";

   public Aura() {
      super("Aura", "Automatically targets and attacks entities (Tera Client port)", Category.COMBAT);
      this.addSettings(new Setting[]{this.attackRange, this.aimRange, this.elytraAimRange, this.rotationMode, this.crit, this.dropSprint,
         this.shieldBreaker, this.unpressShield, this.pauseWhileEating, this.pauseInInventory, this.deathDisable, this.killDisable,
         this.players, this.animals, this.monsters, this.invisibles, this.lagComp, this.serverReach, this.debug});
   }

   public Entity currentTarget() {
      return this.target;
   }

   /** Other modules call this to stop attacks for one second (Tera: AutoBuff). */
   public void pause() {
      this.pausedAtMs = System.currentTimeMillis();
   }

   private boolean pauseOver() {
      return System.currentTimeMillis() - this.pausedAtMs >= 1000L;
   }

   private float getRange() {
      return this.attackRange.getFloat();
   }

   @Override
   public void onEnable() {
      this.target = null;
      this.lookingAtHitbox = false;
      if (this.mc != null && this.mc.player != null) {
         this.rotationYaw = this.mc.player.getYaw();
         this.rotationPitch = this.mc.player.getPitch();
         this.yawAnim.reset(this.mc.player.getYaw());
         this.pitchAnim.reset(this.mc.player.getPitch());
      }

      this.trackedEntity = null;
      this.targetVelocity = Vec3d.ZERO;
      this.probeTarget = null;
      this.lastAttackMs = System.currentTimeMillis() - 2000L;
      this.attackDelayMs = 0L;
      this.cooldownGate = ATTACK_COOLDOWN;
      this.quantizer.reset();
      this.yawTremor.reset();
      this.pitchTremor.reset();
   }

   @Override
   public void onDisable() {
      this.target = null;
      this.trackedEntity = null;
      this.probeTarget = null;
      this.lookingAtHitbox = false;
      RotationManager.release();
   }

   // ------------------------------------------------------------------ tick

   @Override
   public void onTick() {
      if (this.mc == null || this.mc.player == null || this.mc.world == null || this.mc.interactionManager == null) {
         this.target = null;
         return;
      }

      if (!this.mc.player.isAlive()) {
         this.target = null;
         if (this.deathDisable.get()) {
            Toasts.show("Aura: disabled (death)");
            this.setEnabled(false);
         }

         return;
      }

      this.tickProbe();
      this.tickFallDistance = this.mc.player.fallDistance;
      this.tickOnGround = this.mc.player.isOnGround();
      if (this.mc.currentScreen != null && (this.pauseInInventory.get() || !(this.mc.currentScreen instanceof HandledScreen<?>))) {
         this.lookingAtHitbox = false;
         return;
      }

      this.handleKill();
      this.updateTarget();
      this.trackTarget();
      if (this.target == null) {
         this.lookingAtHitbox = false;
         return;
      }

      CombatUtils.noteInteracted(this.target);
      if (!this.pauseOver() || this.mc.player.isUsingItem() && this.pauseWhileEating.get()) {
         return;
      }

      // The server only knows the rotation the player entity carried when this tick's movement packet went out, so the
      // swing is judged against that rotation first. The new aim is committed afterwards and goes out with the next packet.
      this.lookingAtHitbox = this.isLookingAtHitbox(this.mc.player.getYaw(), this.mc.player.getPitch(), this.target, this.getRange());
      this.tryAttack();
      this.commitRotation();
   }

   private void updateTarget() {
      Entity previous = this.target;
      Entity candidate = this.findTarget();
      if (this.target == null) {
         this.target = candidate;
      } else if (this.skipEntity(this.target)) {
         this.target = null;
      }

      if (previous == null && this.target != null) {
         this.yawAnim.reset(this.mc.player.getYaw());
         this.pitchAnim.reset(this.mc.player.getPitch());
         this.quantizer.reset();
      }
   }

   private void handleKill() {
      if (this.target instanceof LivingEntity living && (living.getHealth() <= 0.0F || living.isDead())) {
         if (this.notifiedKill != this.target) {
            this.notifiedKill = this.target;
            Toasts.show("Aura: target neutralized");
            if (this.killDisable.get()) {
               this.setEnabled(false);
            }
         }
      } else if (this.notifiedKill != null && this.notifiedKill != this.target) {
         this.notifiedKill = null;
      }
   }

   // -------------------------------------------------------------- rotation

   private Box getTargetBox(Entity entity) {
      return entity.getBoundingBox();
   }

   private Aura.Mode getEffectiveRotationMode() {
      return this.rotationMode.getValue();
   }

   private Vec3d closestPointOnBox(Vec3d eye, Box box) {
      double cx = MathHelper.clamp(eye.x, box.minX, box.maxX);
      double cy = MathHelper.clamp(eye.y, box.minY, box.maxY);
      double cz = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
      double dx = cx - eye.x;
      double dy = cy - eye.y;
      double dz = cz - eye.z;
      if (dx * dx + dy * dy + dz * dz < 1.0E-12) {
         return box.getCenter();
      }

      return new Vec3d(cx, cy, cz);
   }

   private Vec3d getAimPoint() {
      // aim where the target will be when the next movement packet is judged
      Box box = this.getTargetBox(this.target).offset(this.targetVelocity);
      if (this.getEffectiveRotationMode() == Aura.Mode.HvH) {
         return this.closestPointOnBox(this.mc.player.getEyePos(), box);
      }

      double t = (double)System.currentTimeMillis() / 900.0;
      double fracX = MathHelper.clamp(0.5 + YawAnim.fbm(this.aimNoiseSeed, t) * 0.2, 0.28, 0.72);
      double fracY = MathHelper.clamp(0.72 + YawAnim.fbm(this.aimNoiseSeed ^ 0xA5A5A5A5L, t * 0.8) * 0.17, 0.55, 0.9);
      double fracZ = MathHelper.clamp(0.5 + YawAnim.fbm(this.aimNoiseSeed ^ 0x5A5A5A5AL, t * 1.1) * 0.2, 0.28, 0.72);
      return new Vec3d(
         box.minX + (box.maxX - box.minX) * fracX,
         box.minY + (box.maxY - box.minY) * fracY,
         box.minZ + (box.maxZ - box.minZ) * fracZ
      );
   }

   private float[] anglesTo(Vec3d point) {
      Vec3d eye = this.mc.player.getEyePos();
      double difX = point.x - eye.x;
      double difY = eye.y - point.y;
      double difZ = point.z - eye.z;
      float currentYaw = this.mc.player.getYaw();
      float yaw = currentYaw + MathHelper.wrapDegrees((float)(Math.toDegrees(Math.atan2(difZ, difX)) - 90.0) - currentYaw);
      float pitch = MathHelper.clamp((float)Math.toDegrees(Math.atan2(difY, Math.sqrt(difX * difX + difZ * difZ))), -90.0F, 90.0F);
      return new float[]{yaw, pitch};
   }

   private void updateDesiredRotation() {
      float[] raw = this.anglesTo(this.getAimPoint());
      if (this.getEffectiveRotationMode() == Aura.Mode.Smooth) {
         long now = System.currentTimeMillis();
         this.desiredYaw = this.yawAnim.update(now, raw[0]);
         this.desiredPitch = MathHelper.clamp(this.pitchAnim.update(now, raw[1]), -90.0F, 90.0F);
      } else {
         this.desiredYaw = raw[0];
         this.desiredPitch = raw[1];
      }
   }

   private void applyRotation(float yaw, float pitch) {
      this.rotationYaw = yaw;
      this.rotationPitch = pitch;
      RotationManager.set(yaw, pitch, true, true);
   }

   private void commitRotation() {
      this.updateDesiredRotation();
      double gcd = this.quantizer.getGcd();
      double noiseScale = Math.max(gcd * 0.6, 0.02);
      float noisyYaw = (float)((double)this.desiredYaw + this.yawTremor.next(noiseScale));
      float noisyPitch = (float)((double)this.desiredPitch + this.pitchTremor.next(noiseScale * 0.6));
      this.applyRotation(
         this.quantizer.yaw(this.mc.player.getYaw(), noisyYaw),
         this.quantizer.pitch(this.mc.player.getPitch(), noisyPitch)
      );
      this.lookingAtHitbox = this.isLookingAtHitbox(this.rotationYaw, this.rotationPitch, this.target, this.getRange());

      // elytra: the target moves too fast for the smoothed aim, snap onto the target's eye position
      if (!this.lookingAtHitbox && this.mc.player.isFallFlying() && this.getEffectiveRotationMode() == Aura.Mode.HvH && this.autoCrit()) {
         Vec3d aim = new Vec3d(this.target.getX(), this.target.getEyeY(), this.target.getZ()).add(this.targetVelocity);
         float[] snap = this.anglesTo(aim);
         this.applyRotation(
            this.quantizer.yaw(this.mc.player.getYaw(), snap[0]),
            this.quantizer.pitch(this.mc.player.getPitch(), snap[1])
         );
         this.lookingAtHitbox = this.isLookingAtHitbox(this.rotationYaw, this.rotationPitch, this.target, this.getRange());
      }
   }

   /** Ray (from the eye along yaw / pitch, length distance) against the target hitbox, slab method. */
   private boolean isLookingAtHitbox(float yaw, float pitch, Entity entity, float distance) {
      float yawRad = yaw * (float)(Math.PI / 180.0);
      float pitchRad = pitch * (float)(Math.PI / 180.0);
      float cosPitch = MathHelper.cos(pitchRad);
      double[] dir = {-MathHelper.sin(yawRad) * cosPitch, -MathHelper.sin(pitchRad), MathHelper.cos(yawRad) * cosPitch};
      Vec3d eye = this.mc.player.getEyePos();
      double[] origin = {eye.x, eye.y, eye.z};
      Box box = this.getTargetBox(entity);
      double[] min = {box.minX, box.minY, box.minZ};
      double[] max = {box.maxX, box.maxY, box.maxZ};
      double tMin = 0.0;
      double tMax = distance;
      for (int axis = 0; axis < 3; axis++) {
         if (dir[axis] > -1.0E-9 && dir[axis] < 1.0E-9) {
            if (origin[axis] < min[axis] || origin[axis] > max[axis]) {
               return false;
            }
         } else {
            double inv = 1.0 / dir[axis];
            double t1 = (min[axis] - origin[axis]) * inv;
            double t2 = (max[axis] - origin[axis]) * inv;
            if (t1 > t2) {
               double tmp = t1;
               t1 = t2;
               t2 = tmp;
            }

            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
               return false;
            }
         }
      }

      return true;
   }

   // ---------------------------------------------------------------- attack

   private void tryAttack() {
      if (this.target == null || !this.autoCrit() || !this.lookingAtHitbox || !this.withinServerReach()) {
         return;
      }

      if (this.shieldBreaker(false)) {
         return;
      }

      this.startProbe();
      boolean[] state = this.preAttack();
      this.attack();
      this.postAttack(state[0], state[1]);
   }

   // ----------------------------------------------------- latency / reach

   /** Per-tick velocity of the target, smoothed; the client moves other entities in small interpolation steps. */
   private void trackTarget() {
      if (this.target == null) {
         this.trackedEntity = null;
         this.targetVelocity = Vec3d.ZERO;
         return;
      }

      Vec3d pos = this.target.getPos();
      if (this.target != this.trackedEntity) {
         this.trackedEntity = this.target;
         this.trackedPos = pos;
         this.targetVelocity = Vec3d.ZERO;
         return;
      }

      Vec3d step = pos.subtract(this.trackedPos);
      this.trackedPos = pos;
      if (step.lengthSquared() > 64.0) {
         this.targetVelocity = Vec3d.ZERO;
      } else {
         this.targetVelocity = this.targetVelocity.multiply(0.6).add(step.multiply(0.4));
      }
   }

   private int pingMs() {
      if (this.mc.getNetworkHandler() == null) {
         return 0;
      }

      PlayerListEntry entry = this.mc.getNetworkHandler().getPlayerListEntry(this.mc.player.getUuid());
      return entry == null ? 0 : MathHelper.clamp(entry.getLatency(), 0, 400);
   }

   /**
    * How many ticks the target has moved on the server by the time our swing is processed: the position we see is old by
    * the one-way latency plus the client interpolation (about 1.5 ticks), and the swing needs another one-way trip.
    */
   private double lagTicks() {
      return (double)this.pingMs() / 50.0 + 1.5;
   }

   private double distanceToBox(Box box) {
      Vec3d eye = this.mc.player.getEyePos();
      return this.closestPointOnBox(eye, box).distanceTo(eye);
   }

   private Box predictedServerBox() {
      return this.getTargetBox(this.target).offset(this.targetVelocity.multiply(this.lagTicks()));
   }

   /** False while the target is predicted to be beyond ServerReach when the server handles the swing. */
   private boolean withinServerReach() {
      if (!this.lagComp.get()) {
         return true;
      }

      return this.distanceToBox(this.predictedServerBox()) <= (double)this.serverReach.getFloat();
   }

   // ------------------------------------------------------------- debug

   private void startProbe() {
      if (!this.debug.get() || !(this.target instanceof LivingEntity living)) {
         this.probeTarget = null;
         return;
      }

      this.probeTarget = living;
      this.probeHurtBefore = living.hurtTime;
      this.probeTicksLeft = MathHelper.clamp(this.pingMs() / 50 + 4, 4, 8);
      this.probeInfo = String.format(
         Locale.ROOT,
         "dist %.2f pred %.2f ping %d spd %.2f b/t",
         this.distanceToBox(this.getTargetBox(living)),
         this.distanceToBox(this.predictedServerBox()),
         this.pingMs(),
         this.targetVelocity.length()
      );
   }

   private void tickProbe() {
      if (this.probeTarget == null) {
         return;
      }

      if (!this.debug.get()) {
         this.probeTarget = null;
         return;
      }

      boolean hit = this.probeTarget.hurtTime >= 8 && this.probeHurtBefore < 8;
      if (hit || --this.probeTicksLeft <= 0) {
         this.mc.player.sendMessage(Text.literal((hit ? "[Aura] HIT  " : "[Aura] MISS ") + this.probeInfo), false);
         this.probeTarget = null;
      }
   }

   private void attack() {
      this.mc.interactionManager.attackEntity(this.mc.player, this.target);
      this.mc.player.swingHand(Hand.MAIN_HAND);
      this.rollNextAttack();
   }

   /** Randomized gap between hits (450-550 ms, 7 % chance of an extra 150-400 ms) and a jittered charge gate. */
   private void rollNextAttack() {
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      this.lastAttackMs = System.currentTimeMillis();
      this.attackDelayMs = (long)rnd.nextDouble(450.0, 550.0);
      if (rnd.nextFloat() < 0.07F) {
         this.attackDelayMs += (long)rnd.nextDouble(150.0, 400.0);
      }

      this.cooldownGate = ATTACK_COOLDOWN + (float)rnd.nextDouble(-0.08, 0.08);
   }

   private float getAttackCooldown() {
      return this.mc.player.getAttackCooldownProgress(0.0F);
   }

   private boolean isInWeb() {
      Box box = this.mc.player.getBoundingBox();
      BlockPos base = this.mc.player.getBlockPos();
      for (int x = base.getX() - 2; x <= base.getX() + 2; x++) {
         for (int y = base.getY() - 1; y <= base.getY() + 4; y++) {
            for (int z = base.getZ() - 2; z <= base.getZ() + 2; z++) {
               BlockPos pos = new BlockPos(x, y, z);
               if (box.intersects(new Box(pos)) && this.mc.world.getBlockState(pos).getBlock() == Blocks.COBWEB) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean isAboveWater() {
      return this.mc.player.isSubmergedInWater()
         || this.mc.world.getBlockState(BlockPos.ofFloored(this.mc.player.getX(), this.mc.player.getY() - 0.4, this.mc.player.getZ())).getBlock() == Blocks.WATER;
   }

   private boolean willCrit() {
      return this.crit.getValue() != Aura.CritMode.Off
         && !this.tickOnGround
         && this.tickFallDistance > 0.0F
         && !this.mc.player.isClimbing()
         && !this.mc.player.isTouchingWater()
         && !this.mc.player.isInLava()
         && !this.mc.player.isSubmergedInWater()
         && !this.isAboveWater()
         && !this.mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
         && !this.mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
         && !this.mc.player.hasVehicle()
         && !this.mc.player.getAbilities().flying
         && !this.mc.player.isFallFlying()
         && !this.isInWeb();
   }

   /** The critical / cooldown gate: true when a hit may be made this tick. */
   private boolean autoCrit() {
      boolean onlyCrit = this.crit.getValue() == Aura.CritMode.Only;
      boolean skipCrit = this.crit.getValue() == Aura.CritMode.Off
         || this.mc.player.getAbilities().flying
         || this.mc.player.isFallFlying()
         || this.mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
         || this.mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
         || this.isInWeb();
      if (System.currentTimeMillis() - this.lastAttackMs < this.attackDelayMs) {
         return false;
      }

      if (this.pauseInInventory.get() && this.mc.currentScreen instanceof HandledScreen<?>) {
         return false;
      }

      if (this.getAttackCooldown() < this.cooldownGate) {
         return false;
      }

      boolean groundedOrFalling = this.tickOnGround || this.tickFallDistance > 0.0F;
      if (!this.mc.options.jumpKey.isPressed() && groundedOrFalling && !onlyCrit) {
         return true;
      }

      if (this.mc.player.isInLava() || this.mc.player.isSubmergedInWater()) {
         return true;
      }

      if (!this.mc.options.jumpKey.isPressed() && this.isAboveWater()) {
         return true;
      }

      if (!skipCrit) {
         return !this.tickOnGround && this.tickFallDistance > 0.0F;
      }

      return true;
   }

   // ---------------------------------------------------------- sprint / shield

   private void sendPacket(Packet<?> packet) {
      if (this.mc.getNetworkHandler() != null) {
         this.mc.getNetworkHandler().sendPacket(packet);
      }
   }

   /** Returns {wasBlocking, droppedSprint}. */
   private boolean[] preAttack() {
      boolean blocking = this.mc.player.isUsingItem() && this.mc.player.getActiveItem().getUseAction() == UseAction.BLOCK;
      if (blocking && this.unpressShield.get()) {
         this.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
      }

      boolean dropSprint = this.mc.player.isSprinting() && this.dropSprint.getValue() == Aura.DropSprint.On && this.willCrit();
      if (dropSprint) {
         this.mc.player.setSprinting(false);
         this.mc.options.sprintKey.setPressed(false);
         this.sendPacket(new ClientCommandC2SPacket(this.mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
      }

      return new boolean[]{blocking, dropSprint};
   }

   private void postAttack(boolean wasBlocking, boolean droppedSprint) {
      if (droppedSprint) {
         this.mc.player.setSprinting(true);
         this.mc.options.sprintKey.setPressed(true);
         this.sendPacket(new ClientCommandC2SPacket(this.mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
      }

      if (wasBlocking && this.unpressShield.get()) {
         this.mc.interactionManager.interactItem(this.mc.player, Hand.OFF_HAND);
      }
   }

   /** Inventory index (0-8 hotbar, 9-35 main inventory) of the axe with the most durability, or -1. */
   private int bestAxeSlot() {
      int best = -1;
      int bestDurability = 0;
      for (int i = 9; i < 45; i++) {
         int index = i >= 36 ? i - 36 : i;
         var stack = this.mc.player.getInventory().getStack(index);
         if (stack != null && stack.getItem() instanceof AxeItem && stack.getMaxDamage() > bestDurability) {
            best = index;
            bestDurability = stack.getMaxDamage();
         }
      }

      return best;
   }

   private boolean shieldBreaker(boolean instant) {
      if (!this.shieldBreaker.get() || !(this.target instanceof PlayerEntity victim)) {
         return false;
      }

      int axeSlot = this.bestAxeSlot();
      if (axeSlot == -1) {
         return false;
      }

      if (!victim.isUsingItem() && !instant) {
         return false;
      }

      if (victim.getOffHandStack().getItem() != Items.SHIELD && victim.getMainHandStack().getItem() != Items.SHIELD) {
         return false;
      }

      int selected = this.mc.player.getInventory().selectedSlot;
      int syncId = this.mc.player.currentScreenHandler.syncId;
      if (axeSlot >= 9) {
         this.mc.interactionManager.clickSlot(syncId, axeSlot, selected, SlotActionType.SWAP, this.mc.player);
         this.sendPacket(new CloseHandledScreenC2SPacket(syncId));
         this.mc.interactionManager.attackEntity(this.mc.player, this.target);
         this.mc.player.swingHand(Hand.MAIN_HAND);
         this.mc.interactionManager.clickSlot(syncId, axeSlot, selected, SlotActionType.SWAP, this.mc.player);
         this.sendPacket(new CloseHandledScreenC2SPacket(syncId));
      } else {
         this.sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));
         this.mc.interactionManager.attackEntity(this.mc.player, this.target);
         this.mc.player.swingHand(Hand.MAIN_HAND);
         this.sendPacket(new UpdateSelectedSlotC2SPacket(selected));
      }

      this.rollNextAttack();
      return true;
   }

   // --------------------------------------------------------------- targeting

   private float getSquaredRotateDistance() {
      float dst = this.mc.player.isFallFlying() ? this.elytraAimRange.getFloat() : this.getRange() + this.aimRange.getFloat();
      return dst * dst;
   }

   private boolean isInRange(Entity entity, float squaredRange) {
      Vec3d center = this.getTargetBox(entity).getCenter();
      Vec3d eye = this.mc.player.getEyePos();
      double dx = center.x - eye.x;
      double dy = center.y - eye.y;
      double dz = center.z - eye.z;
      return dx * dx + dy * dy + dz * dz <= (double)squaredRange;
   }

   private Entity findTarget() {
      float squaredRange = this.getSquaredRotateDistance();
      LivingEntity best = null;
      double bestScore = Double.MAX_VALUE;
      for (Entity entity : this.mc.world.getEntities()) {
         if (!(entity instanceof LivingEntity living) || this.skipEntity(living, squaredRange)) {
            continue;
         }

         double dx = living.getX() - this.mc.player.getX();
         double dy = living.getY() - this.mc.player.getY();
         double dz = living.getZ() - this.mc.player.getZ();
         double score = dx * dx + dy * dy + dz * dz;
         if (best == null || score < bestScore) {
            best = living;
            bestScore = score;
         }
      }

      return best;
   }

   private boolean skipEntity(Entity entity) {
      return this.skipEntity(entity, this.getSquaredRotateDistance());
   }

   private boolean skipEntity(Entity entity, float squaredRange) {
      if (!(entity instanceof LivingEntity living) || entity == this.mc.player) {
         return true;
      }

      if (living.isDead() || !entity.isAlive() || entity instanceof ArmorStandEntity || entity instanceof CatEntity) {
         return true;
      }

      if (this.skipNotSelected(entity)) {
         return true;
      }

      if (entity.isInvisible() && !this.invisibles.get()) {
         return true;
      }

      if (!this.isInRange(entity, squaredRange)) {
         return true;
      }

      return Friends.isAlly(entity);
   }

   private boolean skipNotSelected(Entity entity) {
      if (entity instanceof PlayerEntity) {
         return !this.players.get();
      }

      if (entity instanceof AnimalEntity) {
         return !this.animals.get();
      }

      if (entity instanceof MobEntity) {
         return !this.monsters.get();
      }

      return false;
   }

   public static enum Mode {
      HvH,
      Smooth;
   }

   public static enum CritMode {
      Off,
      Smart,
      Only;
   }

   public static enum DropSprint {
      Off,
      On;
   }

   /** Human-like angle animation (ease-out-back segments, saccades, bow, pauses, micro correction, idle jitter). */
   private static final class YawAnim {
      private float curveEnd;
      private long microDuration;
      private float retargetThreshold;
      private final float speedMul;
      private final long noiseSeed = ThreadLocalRandom.current().nextLong();
      private float from = Float.NaN;
      private float to = Float.NaN;
      private float c1;
      private long reactionUntil;
      private long segmentStart;
      private long duration;
      private boolean microCorrecting;
      private long microStart;
      private float microFrom;
      private float bowAmplitude;
      private float bowPhase;
      private boolean saccade;
      private boolean hasPause;
      private long pauseAtElapsedMs;
      private long pauseDurationMs;
      private boolean freshAcquisition = true;

      YawAnim(float speedMul) {
         this.speedMul = speedMul;
      }

      private static float random(float min, float max) {
         return (float)ThreadLocalRandom.current().nextDouble(min, max);
      }

      void reset(float current) {
         this.from = current;
         this.to = current;
         this.freshAcquisition = true;
         this.microCorrecting = false;
         this.saccade = false;
         this.hasPause = false;
         this.bowAmplitude = 0.0F;
         this.bowPhase = 0.5F;
         this.c1 = 0.5F;
         this.reactionUntil = 0L;
         this.segmentStart = 0L;
         this.duration = 60L;
         this.rollSegmentShape();
      }

      private void rollSegmentShape() {
         this.curveEnd = random(0.87F, 0.96F);
         this.microDuration = (long)random(55.0F, 95.0F);
         this.retargetThreshold = random(1.0F, 2.2F);
      }

      float update(long now, float destination) {
         if (Float.isNaN(this.to)) {
            this.reset(destination);
         }

         if (Math.abs(MathHelper.wrapDegrees(destination - this.to)) > this.retargetThreshold) {
            this.retarget(this.currentRaw(now), destination);
         }

         return this.currentRaw(now);
      }

      private void retarget(float currentAnimated, float destination) {
         this.from = currentAnimated;
         this.to = currentAnimated + MathHelper.wrapDegrees(destination - currentAnimated);
         this.c1 = random(0.0F, 1.0F) < 0.4F ? random(0.1F, 0.35F) : random(0.4F, 1.0F);
         long now = System.currentTimeMillis();
         if (this.freshAcquisition) {
            this.reactionUntil = now + (long)random(50.0F, 150.0F);
            this.freshAcquisition = false;
         } else {
            this.reactionUntil = now;
         }

         this.segmentStart = this.reactionUntil;
         float angularDist = Math.abs(this.to - this.from);
         float base = 45.0F + 26.0F * (float)Math.pow(angularDist, 0.55) / this.speedMul;
         float variance = 1.0F + random(-0.18F, 0.18F);
         long baseDuration = (long)(MathHelper.clamp(base, 60.0F, 560.0F) * variance);
         this.saccade = angularDist > 15.0F && random(0.0F, 1.0F) < 0.3F;
         if (this.saccade) {
            baseDuration = (long)((float)baseDuration * random(0.55F, 0.75F));
         }

         this.duration = Math.max(60L, baseDuration);
         float bowScale = MathHelper.clamp(angularDist / 6.0F, 0.0F, 1.0F);
         this.bowAmplitude = random(-1.4F, 1.4F) * bowScale;
         this.bowPhase = random(0.35F, 0.65F);
         this.hasPause = !this.saccade && random(0.0F, 1.0F) < 0.15F;
         if (this.hasPause) {
            this.pauseAtElapsedMs = (long)((float)this.duration * random(0.25F, 0.6F));
            this.pauseDurationMs = (long)random(20.0F, 40.0F);
         }

         this.microCorrecting = false;
         this.rollSegmentShape();
      }

      private long effectiveElapsed(long elapsed) {
         if (!this.hasPause || elapsed < this.pauseAtElapsedMs) {
            return elapsed;
         }

         if (elapsed < this.pauseAtElapsedMs + this.pauseDurationMs) {
            return this.pauseAtElapsedMs;
         }

         return elapsed - this.pauseDurationMs;
      }

      private float currentRaw(long now) {
         if (now < this.reactionUntil) {
            return this.from;
         }

         long elapsed = this.effectiveElapsed(now - this.segmentStart);
         if (elapsed >= this.duration) {
            if (!this.microCorrecting) {
               this.microCorrecting = true;
               this.microStart = now;
               this.microFrom = this.from + (this.to - this.from) * easeOutBack(this.curveEnd, this.c1);
            }

            long microElapsed = now - this.microStart;
            if (microElapsed >= this.microDuration) {
               return this.to + this.idleJitter(now);
            }

            float mp = (float)microElapsed / (float)this.microDuration;
            return this.microFrom + (this.to - this.microFrom) * easeOutQuad(mp);
         }

         float rawT = (float)elapsed / (float)this.duration;
         float speedNoise = 1.0F + 0.13F * (float)fbm(this.noiseSeed, (double)rawT * 6.0 + (double)now / 500.0);
         float warpedT = MathHelper.clamp(rawT * speedNoise, 0.0F, 1.0F);
         float t = warpedT * this.curveEnd;
         float eased = easeOutBack(t, this.c1);
         float bow = this.bowAmplitude * bowBump(rawT, this.bowPhase);
         return this.from + (this.to - this.from) * MathHelper.clamp(eased, -0.4F, 1.4F) + bow;
      }

      private static float bowBump(float t, float phase) {
         float remapped = t < phase ? 0.5F * (t / phase) : 0.5F + 0.5F * ((t - phase) / (1.0F - phase));
         return 4.0F * remapped * (1.0F - remapped);
      }

      private float idleJitter(long now) {
         double n = fbm(this.noiseSeed, (double)now / 260.0);
         float amplitude = 0.1F + (float)((fbm(this.noiseSeed ^ 0x5DEECE66DL, (double)now / 4000.0) + 1.0) * 0.5) * 0.4F;
         return (float)(n * (double)amplitude);
      }

      private static float easeOutBack(float t, float c1) {
         float c3 = c1 + 1.0F;
         float p = t - 1.0F;
         return 1.0F + c3 * p * p * p + c1 * p * p;
      }

      private static float easeOutQuad(float t) {
         return 1.0F - (1.0F - t) * (1.0F - t);
      }

      static double fbm(long seed, double t) {
         return noise1(seed, t) * 0.6 + noise1(seed ^ 0x9E3779B97F4A7C15L, t * 2.3) * 0.3 + noise1(seed ^ 0x632BE59BD9B4E019L, t * 5.1) * 0.1;
      }

      private static double noise1(long seed, double t) {
         long i = (long)Math.floor(t);
         double f = t - (double)i;
         double u = f * f * (3.0 - 2.0 * f);
         double a = hashUnit(seed, i);
         double b = hashUnit(seed, i + 1L);
         return a + (b - a) * u;
      }

      private static double hashUnit(long seed, long i) {
         long h = seed + i * -4417276706812531889L;
         h ^= h >>> 33;
         h *= -49064778989728563L;
         h ^= h >>> 33;
         h *= -4265267296055464877L;
         h ^= h >>> 33;
         return (double)(h >>> 11) * 2.220446049250313E-16 - 1.0;
      }
   }

   /** Smoothed gaussian hand tremor added on top of the aim. */
   private static final class PitchTremor {
      private double value;
      private double velocity;

      double next(double scale) {
         this.velocity = this.velocity * 0.75 + ThreadLocalRandom.current().nextGaussian() * scale * 0.3;
         this.value = this.value * 0.92 + this.velocity;
         return this.value;
      }

      void reset() {
         this.value = 0.0;
         this.velocity = 0.0;
      }
   }
}
