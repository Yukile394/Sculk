package dev.sculk.module.modules.combat;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.util.CombatUtils;
import dev.sculk.util.Friends;
import dev.sculk.util.Rotation;
import dev.sculk.util.RotationManager;
import dev.sculk.util.RotationUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.input.Input;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * AttackAura, ported from Nursultan. Rotation modes: FT, Grim, SpookyTime, Noise, HW.
 * Shared layer: target filters, sorting, critical gate, sprint handling, shield break, auto mace.
 * Every mode gets the same "attack allowed this tick" flag (ready) and returns a yaw/pitch step that is added to the current rotation.
 */
public final class AttackAura extends Module {
   // targets
   private final BooleanSetting players = new BooleanSetting("Players", true);
   private final BooleanSetting mobs = new BooleanSetting("Mobs", true);
   private final BooleanSetting villagers = new BooleanSetting("Villagers", true);
   // target-condition (players only)
   private final BooleanSetting invisible = new BooleanSetting("Invisible", true).describe("Also attack invisible players").visibleWhen(() -> this.players.get());
   private final BooleanSetting naked = new BooleanSetting("Naked", true).describe("Also attack players without armor").visibleWhen(() -> this.players.get());
   private final BooleanSetting bot = new BooleanSetting("Bot", false).describe("Also attack bots (players missing from the tab list)").visibleWhen(() -> this.players.get());
   // main
   private final ModeSetting<AttackAura.Mode> mode = new ModeSetting<>("Mode", AttackAura.Mode.FT)
      .describe("FT = tanh saturation + wave, Grim = hold / step, SpookyTime = predicted hitbox regions, Noise = tanh + gaussian, HW = proportional limit");
   private final ModeSetting<AttackAura.MoveFix> moveFix = new ModeSetting<>("Move Correction", AttackAura.MoveFix.Lite)
      .describe("Lite = keys are re-mapped so you walk where the camera points, Strong = walk along the aim rotation, TargetFollow = walk towards the target")
      .visibleWhen(() -> this.mode.getValue() != AttackAura.Mode.FT);
   private final ModeSetting<AttackAura.Sort> sort = new ModeSetting<>("Sort", AttackAura.Sort.Distance);
   private final ModeSetting<AttackAura.Crit> crit = new ModeSetting<>("Critical Hit", AttackAura.Crit.Disabled)
      .describe("Always = hit only while falling, Only Space = only while holding jump");
   private final ModeSetting<AttackAura.SprintMode> sprint = new ModeSetting<>("Sprint Mode", AttackAura.SprintMode.Default)
      .describe("Sprint is stopped for the hit so criticals can land; Fast resumes it right after");
   private final NumberSetting fov = new NumberSetting("FOV", 180.0, 1.0, 180.0, 1.0);
   private final NumberSetting additionalRange = new NumberSetting("Additional Range", 0.0, 0.0, 3.0, 0.1);
   private final NumberSetting aimRange = new NumberSetting("Aim Range", 1.0, 0.0, 10.0, 0.1).describe("Extra range where the target is tracked and aimed at");
   private final BooleanSetting throughWalls = new BooleanSetting("Through Walls", false);
   private final BooleanSetting shieldBreak = new BooleanSetting("Shield Break", true).describe("Hits a raised shield with an axe from your hotbar");
   private final BooleanSetting autoMace = new BooleanSetting("Auto Mace", true).describe("Swaps to a mace from your hotbar when falling");
   private final NumberSetting cooldown = new NumberSetting("Cooldown", 0.9, 0.5, 1.0, 0.01).describe("Weapon charge needed before a hit");
   // do-not-attack
   private final BooleanSetting noEating = new BooleanSetting("Pause Eating", true).describe("Do not attack while eating or drinking");
   private final BooleanSetting noUsing = new BooleanSetting("Pause Using Item", false).describe("Do not attack while using any item");
   private final BooleanSetting noGliding = new BooleanSetting("Pause Gliding", false).describe("Do not attack while flying with an elytra");

   private Entity target;
   /** Nursultan R3: ticks left with the forward key forced off (Default sprint mode). */
   private int r3;
   /** Nursultan R4: ticks left with sprint forced off. */
   private int r4;
   private int sinceHit = 100;
   private int hits;
   private int targetId = Integer.MIN_VALUE;
   // Grim
   private boolean grimStep;
   // HW
   private int hwCounter;
   // SpookyTime
   private final Spooky spooky = new Spooky();
   private int spookyMiss;

   public AttackAura() {
      super("AttackAura", "Automatically targets and attacks entities", Category.COMBAT);
      this.addSettings(new Setting[]{this.players, this.mobs, this.villagers, this.invisible, this.naked, this.bot, this.mode, this.moveFix,
         this.sort, this.crit, this.sprint, this.fov, this.additionalRange, this.aimRange, this.throughWalls, this.shieldBreak,
         this.autoMace, this.cooldown, this.noEating, this.noUsing, this.noGliding});
   }

   public Entity currentTarget() {
      return this.target;
   }

   @Override
   public void onEnable() {
      this.target = null;
      this.resetState();
   }

   @Override
   public void onDisable() {
      this.target = null;
      this.resetState();
   }

   private void resetState() {
      this.r3 = 0;
      this.r4 = 0;
      this.sinceHit = 100;
      this.hits = 0;
      this.targetId = Integer.MIN_VALUE;
      this.grimStep = false;
      this.hwCounter = 0;
      this.spookyMiss = 0;
      this.spooky.reset();
   }

   // ------------------------------------------------------------------ ranges

   private double attackRange() {
      return this.mc.player.getEntityInteractionRange() + this.additionalRange.getValue();
   }

   private double trackRange() {
      return this.attackRange() + this.aimRange.getValue();
   }

   // -------------------------------------------------------------------- tick

   @Override
   public void onTick() {
      if (this.mc == null || this.mc.player == null || this.mc.world == null || this.mc.interactionManager == null) {
         this.target = null;
         return;
      }

      this.sinceHit++;

      if (this.mc.currentScreen != null || this.mc.player.isSpectator() || !this.mc.player.isAlive()) {
         this.target = null;
         return;
      }

      if (this.target == null || !this.valid(this.target, this.mc.player.getEyePos())) {
         this.target = this.pickTarget();
      }

      if (this.target == null) {
         this.targetId = Integer.MIN_VALUE;
         return;
      }

      if (this.target.getId() != this.targetId) {
         this.targetId = this.target.getId();
         this.spooky.reset();
         this.spookyMiss = 0;
      }

      Vec3d eye = this.mc.player.getEyePos();
      boolean ready = this.ready(this.target, eye);
      this.rotate(this.target, eye, ready);

      CombatUtils.noteInteracted(this.target);
      if (!ready) {
         return;
      }

      boolean aimed = this.facing(this.target, eye);
      if (this.mode.getValue() == AttackAura.Mode.SpookyTime) {
         // SpookyTime needs consecutive clean ray hits (3, or 4 once sinceHit >= 15)
         if (aimed) {
            this.spookyMiss++;
         } else {
            this.spookyMiss = 0;
            return;
         }
         if (this.spookyMiss < (this.sinceHit < 15 ? 3 : 4)) {
            return;
         }
         this.spookyMiss = 0;
      } else if (!aimed) {
         return;
      }

      this.attack(this.target);
   }

   // ------------------------------------------------------------------ attack

   private boolean paused() {
      if (this.noGliding.get() && this.mc.player.isFallFlying()) {
         return true;
      }

      if (this.mc.player.isUsingItem()) {
         if (this.noUsing.get()) {
            return true;
         }

         if (this.noEating.get()) {
            switch (this.mc.player.getActiveItem().getUseAction()) {
               case EAT, DRINK -> {
                  return true;
               }
               default -> {
               }
            }
         }
      }

      return false;
   }

   /** Ticks that have to pass between two hits. SpookyTime adds 3 on every 4th hit. */
   private int interval() {
      int base = 10;
      if (this.mode.getValue() == AttackAura.Mode.SpookyTime && this.hits % 4 == 0) {
         base += 3;
      }

      return base;
   }

   /** Nursultan class11315.y(): the surroundings allow a critical hit at all. */
   private boolean envCritOk() {
      PlayerEntity p = this.mc.player;
      if (p.hasStatusEffect(StatusEffects.BLINDNESS) || p.hasStatusEffect(StatusEffects.LEVITATION) || p.hasVehicle() || p.isClimbing()
         || p.isFallFlying() || p.getAbilities().flying) {
         return false;
      }

      Box above = p.getBoundingBox().expand(0.0, 0.09, 0.0).offset(0.0, 0.1, 0.0);
      if (p.isOnGround() && this.mc.world.getBlockCollisions(p, above).iterator().hasNext()) {
         return false;
      }

      if (p.isOnGround() && p.getPose() == net.minecraft.entity.EntityPose.SWIMMING) {
         return false;
      }

      double d = p.isSwimming() ? 0.0 : 0.6;
      Box body = p.getBoundingBox().shrink(0.0, d, 0.0).offset(0.0, d, 0.0);
      return !this.mc.world.containsFluid(body);
   }

   /** Mace mode (Nursultan class11079.u): holding a mace, or falling with a mace in the hotbar and Auto Mace on. */
   private boolean maceMode() {
      if (this.mc.player.getMainHandStack().isOf(Items.MACE)) {
         return true;
      }

      if (this.mc.player.fallDistance < 1.0F || !this.autoMace.get()) {
         return false;
      }

      for (int i = 0; i < 9; i++) {
         if (this.mc.player.getInventory().getStack(i).isOf(Items.MACE)) {
            return true;
         }
      }

      return false;
   }

   /** Critical path is switched off when Disabled, and for SpookyTime when boxed in above and below on its 13 tick hit. */
   private boolean critActive() {
      switch (this.crit.getValue()) {
         case Disabled -> {
            return false;
         }
         case OnlySpace -> {
            if (!this.mc.options.jumpKey.isPressed() && this.mc.player.isOnGround()) {
               return false;
            }
         }
         default -> {
         }
      }

      if (this.mode.getValue() == AttackAura.Mode.SpookyTime && this.interval() == 13) {
         Box up = this.mc.player.getBoundingBox().offset(0.0, 0.1F, 0.0);
         Box down = this.mc.player.getBoundingBox().offset(0.0, -0.5, 0.0);
         boolean above = this.mc.world.getBlockCollisions(this.mc.player, up).iterator().hasNext();
         boolean below = this.mc.world.getBlockCollisions(this.mc.player, down).iterator().hasNext();
         if (above && below) {
            return false;
         }
      }

      return true;
   }

   /** Result of {@link #simulate(int)}: what the client player will look like a few ticks from now. */
   private record Sim(double fall, boolean onGround, boolean landed) {
   }

   /**
    * Vertical-only prediction of the player (Nursultan runs a full mock player here).
    * Jump, gravity, drag and block collisions are simulated, water / lava / ladders return the current state.
    */
   private Sim simulate(int ticks) {
      PlayerEntity p = this.mc.player;
      boolean ground = p.isOnGround();
      boolean landed = p.isOnGround();
      double fall = p.fallDistance;
      if (p.isTouchingWater() || p.isInLava() || p.isClimbing() || p.hasVehicle() || p.isFallFlying()) {
         return new Sim(fall, ground, landed);
      }

      double vy = p.getVelocity().y;
      Box box = p.getBoundingBox();
      boolean jump = this.mc.options.jumpKey.isPressed();
      for (int t = 0; t < ticks; t++) {
         if (jump && ground) {
            vy = 0.42;
            if (p.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
               vy += 0.1 * (double)(p.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() + 1);
            }
         }

         double adjusted = vy;
         for (net.minecraft.util.shape.VoxelShape shape : this.mc.world.getBlockCollisions(p, box.stretch(0.0, vy, 0.0))) {
            adjusted = shape.calculateMaxDistance(net.minecraft.util.math.Direction.Axis.Y, box, adjusted);
         }

         landed = vy < 0.0 && adjusted != vy;
         if (landed) {
            fall = 0.0;
         } else if (adjusted < 0.0) {
            fall -= adjusted;
         }

         box = box.offset(0.0, adjusted, 0.0);
         if (adjusted != vy) {
            vy = 0.0;
         }

         vy = (vy - 0.08) * 0.98;
         ground = landed;
      }

      return new Sim(fall, ground, landed);
   }

   /** Nursultan class11079.N(class11915): about to jump into a hit, so the forward key is cut for a moment. */
   private boolean preCrit(Sim sim1) {
      boolean landed = this.mc.player.isOnGround();
      return landed && this.sinceHit >= this.interval() - 1 && sim1.fall() > 0.0 && this.mc.player.getAttackCooldownProgress(1.5F) > 0.9F;
   }

   /** The shared "attack now" flag handed to every rotation mode (Nursultan: b). Facing is checked after the rotation. */
   private boolean ready(Entity entity, Vec3d eye) {
      if (this.paused()) {
         return false;
      }

      if (this.sinceHit < this.interval()) {
         return false;
      }

      if (this.distance(entity, eye) > this.attackRange()) {
         return false;
      }

      boolean critOn = this.critActive();
      boolean envOk = this.envCritOk();
      boolean mace = this.maceMode();
      if (critOn && envOk && !mace) {
         if (this.preCrit(this.simulate(1))) {
            this.r3 = 12;
         }

         if (this.mc.player.isOnGround()) {
            return false;
         }
      }

      boolean maceDrop = mace && this.mc.player.fallDistance > 1.3F;
      if (!maceDrop && this.mc.player.getAttackCooldownProgress(0.5F) < this.cooldown.getFloat()) {
         return false;
      }

      if (!this.throughWalls.get() && this.blocked(entity, eye)) {
         return false;
      }

      if (critOn && envOk && (this.mc.player.fallDistance == 0.0F || this.mc.player.isOnGround())) {
         return false;
      }

      return true;
   }

   private void attack(Entity entity) {
      int previous = this.mc.player.getInventory().selectedSlot;
      int slot = this.weaponSlot(entity);
      boolean swapped = slot >= 0 && slot != previous;
      if (swapped) {
         this.mc.player.getInventory().selectedSlot = slot;
      }

      CombatUtils.hitEntity(entity, true);
      if (swapped) {
         this.mc.player.getInventory().selectedSlot = previous;
      }

      this.sinceHit = 0;
      this.hits++;
      if (this.mode.getValue() == AttackAura.Mode.SpookyTime) {
         // SpookyTime always drops sprint for 12 ticks after a hit
         this.r4 = 12;
      }
   }

   /** Hotbar slot to hit with (axe for shields, mace while falling), or -1 to keep the current item. */
   private int weaponSlot(Entity entity) {
      if (this.shieldBreak.get() && entity instanceof LivingEntity living && living.isBlocking()
         && !(this.mc.player.getMainHandStack().getItem() instanceof AxeItem)) {
         for (int i = 0; i < 9; i++) {
            if (this.mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
               return i;
            }
         }
      }

      if (this.autoMace.get() && this.mc.player.fallDistance >= 1.5F && !this.mc.player.getMainHandStack().isOf(Items.MACE)) {
         for (int i = 0; i < 9; i++) {
            if (this.mc.player.getInventory().getStack(i).isOf(Items.MACE)) {
               return i;
            }
         }
      }

      return -1;
   }

   // ---------------------------------------------------------------- rotation

   private static float sat(float x, float c) {
      return (float)((double)c * Math.tanh((double)(x / c)));
   }

   private static float gauss(float sigma) {
      return (float)ThreadLocalRandom.current().nextGaussian() * sigma;
   }

   /** Mean of two uniforms (Nursultan class09166.y). */
   private static float tri(float min, float max) {
      ThreadLocalRandom r = ThreadLocalRandom.current();
      return min + (max - min) * (float)((r.nextDouble() + r.nextDouble()) * 0.5);
   }

   private static float uniform(float min, float max) {
      return min + (max - min) * ThreadLocalRandom.current().nextFloat();
   }

   /** Smallest rotation the mouse can produce at the current sensitivity. */
   private float mouseStep() {
      double s = this.mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
      return (float)(s * s * s * 8.0 * 0.15);
   }

   private float snap(float delta, float step) {
      if (step <= 0.0F) {
         return delta;
      }

      return Math.round(delta / step) * step;
   }

   private AttackAura.MoveFix effectiveMoveFix() {
      return this.mode.getValue() == AttackAura.Mode.FT ? AttackAura.MoveFix.Lite : this.moveFix.getValue();
   }

   /** Adds a step to the current aim rotation (snapped to the mouse sensitivity grid) and hands it to the silent rotation manager. */
   private void applyStep(float yawStep, float pitchStep, boolean smoothBack) {
      float gcd = this.mouseStep();
      float yaw = this.snap(yawStep, gcd);
      float pitch = this.snap(pitchStep, gcd);
      RotationManager.set(this.mc.player.getYaw() + yaw, this.mc.player.getPitch() + pitch, smoothBack, this.effectiveMoveFix() == AttackAura.MoveFix.Lite);
   }

   /** Keeps the current aim rotation for this tick. */
   private void hold() {
      RotationManager.set(this.mc.player.getYaw(), this.mc.player.getPitch(), false, this.effectiveMoveFix() == AttackAura.MoveFix.Lite);
   }

   /** Movement input hook (called from Mixin10): target-follow walking and the R3 / R4 sprint counters. */
   public void onInput(Input input, boolean slowDown, float slowDownFactor) {
      if (this.mc == null || this.mc.player == null) {
         return;
      }

      if (this.effectiveMoveFix() == AttackAura.MoveFix.TargetFollow && this.target != null) {
         Vec3d d = this.target.getPos().subtract(this.mc.player.getPos());
         float yaw = (float)Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0F;
         input.pressingForward = true;
         input.pressingBack = false;
         input.pressingLeft = false;
         input.pressingRight = false;
         RotationManager.fixMovement(input, yaw, slowDown, slowDownFactor);
      }

      if (this.sprint.getValue() == AttackAura.SprintMode.Default) {
         if (this.r3 > 0) {
            input.pressingForward = false;
            input.movementForward = 0.0F;
         }

         if (this.r4 > 0) {
            this.mc.player.setSprinting(false);
         }
      } else if (this.r3 > 0 || this.r4 > 0) {
         this.mc.player.setSprinting(false);
      }

      this.r3 = Math.max(0, this.r3 - 1);
      this.r4 = Math.max(0, this.r4 - 1);
   }

   private void rotate(Entity entity, Vec3d eye, boolean ready) {
      AttackAura.Mode m = this.mode.getValue();
      double reach = this.attackRange();
      double range = ready || m == AttackAura.Mode.HW ? reach : this.trackRange();
      Vec3d point = m == AttackAura.Mode.SpookyTime ? this.spookyPoint(entity, eye) : this.hitboxPoint(entity, eye, range);
      Rotation wanted = RotationUtils.direction(this.mc.player, point);
      float dYaw = MathHelper.wrapDegrees((float)wanted.yaw() - this.mc.player.getYaw());
      float dPitch = (float)wanted.pitch() - this.mc.player.getPitch();
      int age = this.mc.player.age;

      switch (m) {
         case FT -> {
            if (this.sinceHit == 1) {
               this.hold();
               return;
            }

            float yawStep;
            if (ready) {
               yawStep = dYaw + gauss(2.0F);
            } else {
               yawStep = sat(dYaw, uniform(40.0F, 60.0F)) + (float)Math.sin(age) * 10.0F + gauss(2.5F);
            }

            float pitchStep = sat(dPitch, 8.0F) + (float)Math.cos(age) * 4.0F + gauss(1.0F);
            this.applyStep(yawStep, pitchStep, true);
         }
         case Grim -> {
            if (this.sinceHit > 1 && !ready) {
               this.grimStep = true;
               this.hold();
               return;
            }

            if (ready && this.facing(entity, eye)) {
               this.grimStep = false;
               this.hold();
               return;
            }

            float step = this.grimStep ? uniform(7.0F, 13.0F) : 0.0F;
            float yawStep = ready ? dYaw : step;
            float pitchStep = ready ? dPitch : (this.mc.player.getPitch() > 0.0F ? -step : step);
            this.applyStep(yawStep, pitchStep, false);
         }
         case SpookyTime -> {
            // the planner always runs with the idle profile, the hit is verified separately
            float yawStep = sat(dYaw, 35.0F) + gauss(0.4F);
            float pitchStep = sat(dPitch, 12.0F) + gauss(0.2F);
            this.applyStep(yawStep, pitchStep, true);
         }
         case Noise -> {
            if (this.sinceHit == 1) {
               this.hold();
               return;
            }

            float yawStep = sat(dYaw, 45.0F) + (ready ? 0.0F : gauss(5.0F));
            float pitchStep = sat(dPitch, 5.0F) + (ready ? 0.0F : gauss(1.0F));
            this.applyStep(yawStep, pitchStep, true);
         }
         case HW -> {
            if (this.hwCounter > 0) {
               this.hwCounter--;
            }

            if (ready) {
               this.hwCounter = 2;
            }

            if (this.hwCounter == 0) {
               this.hold();
               return;
            }

            float r = (float)Math.hypot(Math.abs(dYaw), Math.abs(dPitch));
            if (r < 1.0E-4F) {
               this.hold();
               return;
            }

            float limitY = Math.abs(dYaw / r) * 360.0F;
            float limitP = Math.abs(dPitch / r) * 360.0F;
            float yawStep = Math.min(Math.max(dYaw, -limitY), limitY) + gauss(0.6F);
            float pitchStep = Math.min(Math.max(dPitch, -limitP), limitP) + gauss(0.3F);
            this.applyStep(yawStep, pitchStep, true);
         }
      }
   }

   /** Shared hitbox point selector: visible surface points, the one closest to where the camera already looks. */
   private Vec3d hitboxPoint(Entity entity, Vec3d eye, double range) {
      Box box = entity.getBoundingBox().contract(0.01);
      float yaw = this.mc.player.getYaw();
      float pitch = this.mc.player.getPitch();
      List<Vec3d> points = new ArrayList<>();
      int n = 5;
      boolean inside = box.contains(eye);
      for (int a = 0; a < n; a++) {
         double fa = (double)a / (double)(n - 1);
         for (int b = 0; b < n; b++) {
            double fb = (double)b / (double)(n - 1);
            double x = MathHelper.lerp(fa, box.minX, box.maxX);
            double z = MathHelper.lerp(fb, box.minZ, box.maxZ);
            double y2 = MathHelper.lerp(fb, box.minY, box.maxY);
            // side faces (x/z fixed) and top/bottom faces
            if (inside || eye.x > box.maxX) {
               points.add(new Vec3d(box.maxX, y2, z));
            }

            if (inside || eye.x < box.minX) {
               points.add(new Vec3d(box.minX, y2, z));
            }

            if (inside || eye.z > box.maxZ) {
               points.add(new Vec3d(x, y2, box.maxZ));
            }

            if (inside || eye.z < box.minZ) {
               points.add(new Vec3d(x, y2, box.minZ));
            }

            if (inside || eye.y > box.maxY) {
               points.add(new Vec3d(x, box.maxY, z));
            }

            if (inside || eye.y < box.minY) {
               points.add(new Vec3d(x, box.minY, z));
            }
         }
      }

      Vec3d best = null;
      double bestScore = Double.MAX_VALUE;
      for (Vec3d p : points) {
         if (eye.distanceTo(p) > range + 1.0) {
            continue;
         }

         if (!this.throughWalls.get() && this.solidBetween(eye, p)) {
            continue;
         }

         Rotation r = RotationUtils.direction(this.mc.player, p);
         double dy = MathHelper.wrapDegrees((float)r.yaw() - yaw);
         double dp = r.pitch() - pitch;
         double score = dy * dy + dp * dp;
         if (score < bestScore) {
            bestScore = score;
            best = p;
         }
      }

      if (best != null) {
         return best;
      }

      return new Vec3d(MathHelper.clamp(eye.x, box.minX, box.maxX), MathHelper.clamp(eye.y, box.minY, box.maxY), MathHelper.clamp(eye.z, box.minZ, box.maxZ));
   }

   private boolean solidBetween(Vec3d eye, Vec3d point) {
      HitResult hit = this.mc.world.raycast(new RaycastContext(eye, point, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this.mc.player));
      return hit.getType() != HitResult.Type.MISS;
   }

   private boolean facing(Entity entity, Vec3d eye) {
      Vec3d look = this.mc.player.getRotationVec(1.0F);
      double reach = this.attackRange() + 0.5;
      Box box = entity.getBoundingBox().expand(0.1);
      if (box.contains(eye)) {
         return true;
      }

      Optional<Vec3d> hit = box.raycast(eye, eye.add(look.x * reach, look.y * reach, look.z * reach));
      return hit.isPresent();
   }

   // ------------------------------------------------------------- spooky-time

   private static final class Spooky {
      static final int MID_CHEST = 0;
      static final int UPPER_CHEST = 1;
      static final int SHOULDER = 2;
      static final int HEAD_LINE = 3;

      int region = UPPER_CHEST;
      long nextSwitch;
      float fx = 0.5F;
      float fy = 0.68F;
      float fz = 0.5F;
      float prevFx = 0.5F;
      float prevFy = 0.68F;
      float prevFz = 0.5F;
      float phase;
      float sign = 1.0F;
      double lastDx;
      double lastDz;
      boolean hasLast;

      void reset() {
         this.region = UPPER_CHEST;
         this.nextSwitch = 0L;
         this.fx = 0.5F;
         this.fy = 0.68F;
         this.fz = 0.5F;
         this.prevFx = 0.5F;
         this.prevFy = 0.68F;
         this.prevFz = 0.5F;
         this.phase = (float)(ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0);
         this.sign = 1.0F;
         this.lastDx = 0.0;
         this.lastDz = 0.0;
         this.hasLast = false;
      }
   }

   /** True when the player changed direction or strafed hard around the target since the last tick. */
   private boolean spookyStrafe(Box box) {
      Vec3d c = box.getCenter();
      double dx = this.mc.player.getX() - c.x;
      double dz = this.mc.player.getZ() - c.z;
      Spooky s = this.spooky;
      if (!s.hasLast) {
         s.lastDx = dx;
         s.lastDz = dz;
         s.hasLast = true;
         return false;
      }

      double dot = s.lastDx * dx + s.lastDz * dz;
      double cross = s.lastDx * dz - s.lastDz * dx;
      double moved = Math.hypot(dx - s.lastDx, dz - s.lastDz);
      boolean reversed = dot <= 0.0 && moved > 0.12;
      boolean strafed = Math.abs(cross) > 0.32 && moved > 0.2;
      s.lastDx = dx;
      s.lastDz = dz;
      return reversed || strafed;
   }

   private int spookyRegion(boolean near, boolean tightA, boolean tightB) {
      float f = ThreadLocalRandom.current().nextFloat();
      if (tightA && tightB) {
         if (f < 0.42F) {
            return Spooky.UPPER_CHEST;
         }

         return f < 0.74F ? Spooky.SHOULDER : Spooky.HEAD_LINE;
      }

      if (near) {
         if (f < 0.38F) {
            return Spooky.SHOULDER;
         }

         if (f < 0.76F) {
            return Spooky.UPPER_CHEST;
         }

         return f < 0.91F ? Spooky.MID_CHEST : Spooky.HEAD_LINE;
      }

      if (f < 0.46F) {
         return Spooky.UPPER_CHEST;
      }

      if (f < 0.76F) {
         return Spooky.SHOULDER;
      }

      return f < 0.92F ? Spooky.MID_CHEST : Spooky.HEAD_LINE;
   }

   private void spookyReroll(long now, boolean near, boolean tightA, boolean tightB) {
      Spooky s = this.spooky;
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      int old = s.region;
      s.prevFx = s.fx;
      s.prevFy = s.fy;
      s.prevFz = s.fz;
      s.region = this.spookyRegion(near, tightA, tightB);
      if (s.region == old && rnd.nextFloat() < (near ? 0.62F : 0.44F)) {
         int next = rnd.nextInt(4);
         s.region = next == old ? (old + 1) % 4 : next;
      }

      s.sign = rnd.nextFloat() < 0.55F ? -s.sign : (rnd.nextBoolean() ? 1.0F : -1.0F);
      switch (s.region) {
         case Spooky.HEAD_LINE -> {
            s.fx = MathHelper.clamp(0.5F + tri(-0.13F, 0.13F), 0.34F, 0.66F);
            s.fy = tri(0.78F, 0.91F);
            s.fz = MathHelper.clamp(0.5F + tri(-0.11F, 0.11F), 0.34F, 0.66F);
         }
         case Spooky.SHOULDER -> {
            s.fx = MathHelper.clamp(0.5F + s.sign * tri(0.17F, 0.32F), 0.18F, 0.82F);
            s.fy = tri(0.62F, tightA ? 0.82F : 0.78F);
            s.fz = MathHelper.clamp(0.5F + tri(-0.2F, 0.2F), 0.22F, 0.78F);
         }
         case Spooky.MID_CHEST -> {
            s.fx = MathHelper.clamp(0.5F + tri(-0.22F, 0.22F), 0.24F, 0.76F);
            s.fy = tri(0.56F, 0.69F);
            s.fz = MathHelper.clamp(0.5F + tri(-0.22F, 0.22F), 0.24F, 0.76F);
         }
         default -> {
            s.fx = MathHelper.clamp(0.5F + tri(-0.19F, 0.19F), 0.26F, 0.74F);
            s.fy = tri(0.66F, tightA && tightB ? 0.86F : 0.82F);
            s.fz = MathHelper.clamp(0.5F + tri(-0.2F, 0.2F), 0.24F, 0.76F);
         }
      }

      if (near) {
         s.fx = MathHelper.lerp(0.46F, s.prevFx, s.fx);
         s.fy = MathHelper.lerp(0.58F, s.prevFy, s.fy);
         s.fz = MathHelper.lerp(0.46F, s.prevFz, s.fz);
      }

      s.phase = (float)(rnd.nextDouble() * Math.PI * 2.0);
      long min = (long)(near ? (tightA ? 64.0 : 82.0) : 58.0);
      long max = (long)(near ? (tightA ? 145.0 : 205.0) : 165.0);
      s.nextSwitch = now + min + (long)(tri(0.0F, 1.0F) * (double)(max - min));
   }

   /**
    * SpookyTime aim point: moving hitbox region + motion prediction + slow sine drift.
    * The predicted point is used if it is actually visible, otherwise the cheapest visible one of nine fixed candidates.
    */
   private Vec3d spookyPoint(Entity entity, Vec3d eye) {
      Box box = entity.getBoundingBox();
      Spooky s = this.spooky;
      long now = System.currentTimeMillis();
      // bl: current rotation already hits the target, bl2: attack window, bl3: critical fall window
      boolean near = this.spookyVisible(entity, eye, eye.add(this.mc.player.getRotationVec(1.0F).multiply(this.attackRange())));
      boolean tightA = this.sinceHit >= Math.max(1, this.interval() - 1);
      boolean tightB = this.critWindow();
      boolean strafe = this.spookyStrafe(box);
      if (strafe) {
         s.nextSwitch = 0L;
      }

      if (now >= s.nextSwitch || !near || strafe) {
         this.spookyReroll(now, near, tightA, tightB);
      }

      Vec3d center = box.getCenter();
      Vec3d vt = entity.getVelocity();
      if (vt.lengthSquared() < 1.0E-6) {
         vt = new Vec3d(entity.getX() - entity.prevX, entity.getY() - entity.prevY, entity.getZ() - entity.prevZ);
      }

      Vec3d vp = this.mc.player.getVelocity();
      double rx = vt.x - vp.x * 0.42;
      double rz = vt.z - vp.z * 0.42;
      double speed = Math.hypot(rx, rz);
      double dist = eye.distanceTo(center);
      double q = near ? MathHelper.clamp(dist * 0.18 + speed * 2.4, 0.18, 1.25) : MathHelper.clamp(dist * 0.46 + speed * 5.8, 0.85, 5.2);

      Vec3d base = new Vec3d(
         MathHelper.lerp((double)s.fx, box.minX, box.maxX),
         MathHelper.lerp((double)s.fy, box.minY, box.maxY),
         MathHelper.lerp((double)s.fz, box.minZ, box.maxZ));
      Vec3d predicted = new Vec3d(
         MathHelper.clamp(center.x + rx * q, box.minX, box.maxX),
         MathHelper.clamp(center.y + vt.y * MathHelper.clamp(q * 0.42, 0.0, 1.45), box.minY, box.maxY),
         MathHelper.clamp(center.z + rz * q, box.minZ, box.maxZ));

      double amp = near ? 0.014 : 0.046;
      double ox = Math.sin((double)now * 0.0105 + (double)s.phase) * (box.maxX - box.minX) * amp;
      double oy = Math.cos((double)now * 0.0073 + (double)(s.phase * 0.73F)) * (box.maxY - box.minY) * amp * 0.28;
      double oz = Math.sin((double)now * 0.0121 + (double)(s.phase * 1.31F)) * (box.maxZ - box.minZ) * amp;
      double blend = near && !strafe ? (tightA && tightB ? 0.13 : 0.075) : (tightA && tightB ? 0.58 : 0.38);
      Vec3d mixed = new Vec3d(
         MathHelper.lerp(blend, base.x, predicted.x) + ox,
         MathHelper.lerp(blend, base.y, predicted.y) + oy,
         MathHelper.lerp(blend, base.z, predicted.z) + oz);
      double h = box.maxY - box.minY;
      Vec3d point = new Vec3d(
         MathHelper.clamp(mixed.x, box.minX, box.maxX),
         MathHelper.clamp(mixed.y, box.minY + h * 0.46, box.minY + h * 0.92),
         MathHelper.clamp(mixed.z, box.minZ, box.maxZ));

      if (this.spookyVisible(entity, eye, point)) {
         return point;
      }

      return this.spookyCandidate(entity, eye, point);
   }

   private Vec3d spookyCandidate(Entity entity, Vec3d eye, Vec3d reference) {
      Box box = entity.getBoundingBox();
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      double cx = (box.minX + box.maxX) / 2.0;
      double cz = (box.minZ + box.maxZ) / 2.0;
      double mx = Math.max(0.015, (box.maxX - box.minX) * 0.18);
      double mz = Math.max(0.015, (box.maxZ - box.minZ) * 0.18);
      double x0 = box.minX + mx;
      double x1 = box.maxX - mx;
      double z0 = box.minZ + mz;
      double z1 = box.maxZ - mz;
      double y28 = MathHelper.lerp(0.28, box.minY, box.maxY);
      double y18 = MathHelper.lerp(0.18, box.minY, box.maxY);
      double y38 = MathHelper.lerp(0.38, box.minY, box.maxY);
      double y46 = MathHelper.lerp(0.46, box.minY, box.maxY);
      Vec3d[] candidates = {
         new Vec3d(cx, y28, cz),
         new Vec3d(x0 + rnd.nextDouble() * (x1 - x0), y18 + rnd.nextDouble() * (y38 - y18), z0 + rnd.nextDouble() * (z1 - z0)),
         new Vec3d(x0, y28, cz),
         new Vec3d(x1, y28, cz),
         new Vec3d(cx, y28, z0),
         new Vec3d(cx, y28, z1),
         new Vec3d(cx, y18, cz),
         new Vec3d(cx, y38, cz),
         new Vec3d(cx, y46, cz)
      };
      Rotation ref = RotationUtils.direction(this.mc.player, reference);
      Vec3d best = reference;
      double bestCost = Double.MAX_VALUE;
      for (Vec3d c : candidates) {
         if (!this.spookyVisible(entity, eye, c)) {
            continue;
         }

         Rotation r = RotationUtils.direction(this.mc.player, c);
         double cost = Math.abs(MathHelper.wrapDegrees((float)(r.yaw() - ref.yaw()))) * 0.86
            + Math.abs(ref.pitch() - r.pitch()) * 4.2
            + Math.max(0.0, c.y - y38) * 3.8
            + eye.squaredDistanceTo(c) * 0.01;
         if (cost < bestCost) {
            bestCost = cost;
            best = c;
         }
      }

      return best;
   }

   /** True when a ray from the eye to the point reaches the target first (no block and no other entity in front). */
   private boolean spookyVisible(Entity target, Vec3d eye, Vec3d point) {
      Vec3d dir = point.subtract(eye);
      double len = dir.length();
      if (len < 1.0E-4) {
         return true;
      }

      Vec3d end = eye.add(dir.normalize().multiply(Math.max(len, this.attackRange())));
      Box sweep = new Box(eye, end).expand(1.0);
      double best = Double.MAX_VALUE;
      Entity hitEntity = null;
      for (Entity e : this.mc.world.getOtherEntities(this.mc.player, sweep, x -> !x.isSpectator() && x.canHit())) {
         Box hb = e == target ? e.getBoundingBox() : e.getBoundingBox().expand(Math.max(1.0E-4, (double)e.getTargetingMargin()));
         Optional<Vec3d> hit = hb.contains(eye) ? Optional.of(eye) : hb.raycast(eye, end);
         if (hit.isEmpty()) {
            continue;
         }

         double d = eye.squaredDistanceTo(hit.get());
         if (hitEntity == null || d < best) {
            hitEntity = e;
            best = d;
         }
      }

      if (hitEntity != target) {
         return false;
      }

      HitResult block = this.mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this.mc.player));
      return !(block.getType() == HitResult.Type.BLOCK && eye.squaredDistanceTo(block.getPos()) <= best);
   }

   private boolean fallCrit(float fall, double vy) {
      return vy < -0.008 && fall + 0.018F >= 0.08F;
   }

   /** Nursultan class11063: the player is about to land with a critical fall (or critical is disabled). */
   private boolean critWindow() {
      if (this.crit.getValue() == AttackAura.Crit.Disabled) {
         return true;
      }

      PlayerEntity p = this.mc.player;
      if (p.isClimbing() || p.isTouchingWater() || p.isSwimming() || p.isInLava() || p.hasVehicle()
         || p.hasStatusEffect(StatusEffects.BLINDNESS) || p.hasStatusEffect(StatusEffects.LEVITATION)) {
         return false;
      }

      double vy = p.getVelocity().y;
      if (this.fallCrit(p.fallDistance, vy)) {
         return true;
      }

      if (vy >= -0.008) {
         return false;
      }

      Box box = p.getBoundingBox();
      float fall = p.fallDistance;
      for (int i = 0; i < 2; i++) {
         box = box.offset(0.0, vy - 0.001, 0.0);
         if (!this.mc.world.getBlockCollisions(p, box).iterator().hasNext()) {
            return false;
         }

         fall += (float)Math.max(0.0, -vy);
         if (this.fallCrit(fall, vy)) {
            return true;
         }

         vy = (vy - 0.08) * 0.98;
      }

      return false;
   }

   // --------------------------------------------------------------- targeting

   private Entity pickTarget() {
      Vec3d eye = this.mc.player.getEyePos();
      double range = this.trackRange();
      Box area = this.mc.player.getBoundingBox().expand(range + 2.0);
      Comparator<Entity> order = Comparator.<Entity>comparingInt(e -> this.blocked(e, eye) ? 1 : 0)
         .thenComparingInt(e -> e instanceof PlayerEntity ? 0 : 1)
         .thenComparingDouble(e -> this.score(e, eye));
      return this.mc.world.getOtherEntities(this.mc.player, area, e -> this.valid(e, eye)).stream().min(order).orElse(null);
   }

   private double score(Entity entity, Vec3d eye) {
      LivingEntity living = (LivingEntity)entity;
      return switch (this.sort.getValue()) {
         case Distance -> this.distance(entity, eye);
         case Health -> living.getHealth() + living.getAbsorptionAmount();
         case FOV -> Math.abs(this.yawTo(entity, eye));
         case Smart -> this.distance(entity, eye) * 2.0 + (living.getHealth() + living.getAbsorptionAmount()) * 0.4 + Math.abs(this.yawTo(entity, eye)) * 0.02;
      };
   }

   private boolean valid(Entity entity, Vec3d eye) {
      if (entity == this.mc.player || !(entity instanceof LivingEntity living) || entity instanceof ArmorStandEntity) {
         return false;
      }

      if (!living.isAlive() || living.isRemoved() || living.getHealth() <= 0.0F || entity.isSpectator() || Friends.isAlly(entity)) {
         return false;
      }

      if (!this.typeAllowed(entity)) {
         return false;
      }

      if (this.distance(entity, eye) > this.trackRange()) {
         return false;
      }

      if (this.fov.getValue() < 180.0 && Math.abs(this.yawTo(entity, eye)) > this.fov.getValue()) {
         return false;
      }

      return true;
   }

   private boolean typeAllowed(Entity entity) {
      if (entity instanceof PlayerEntity player) {
         if (!this.players.get()) {
            return false;
         }

         if (!this.invisible.get() && player.isInvisible()) {
            return false;
         }

         if (!this.naked.get() && this.isNaked(player)) {
            return false;
         }

         return this.bot.get() || !this.isBot(player);
      }

      if (entity instanceof VillagerEntity || entity instanceof WanderingTraderEntity) {
         return this.villagers.get();
      }

      return this.mobs.get();
   }

   private boolean isNaked(PlayerEntity player) {
      return player.getEquippedStack(EquipmentSlot.HEAD).isEmpty() && player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
         && player.getEquippedStack(EquipmentSlot.LEGS).isEmpty() && player.getEquippedStack(EquipmentSlot.FEET).isEmpty();
   }

   private boolean isBot(PlayerEntity player) {
      return this.mc.getNetworkHandler() != null && this.mc.getNetworkHandler().getPlayerListEntry(player.getUuid()) == null;
   }

   /** True when a block sits between your eyes and the target's aim point. */
   private boolean blocked(Entity entity, Vec3d eye) {
      return this.solidBetween(eye, this.aimPoint(entity, eye));
   }

   private double yawTo(Entity entity, Vec3d eye) {
      Rotation wanted = RotationUtils.direction(this.mc.player, this.aimPoint(entity, eye));
      return MathHelper.wrapDegrees((float)wanted.yaw() - this.mc.player.getYaw());
   }

   private Vec3d aimPoint(Entity entity, Vec3d eye) {
      Box box = entity.getBoundingBox();
      double y = MathHelper.clamp(eye.y, box.minY + 0.25, box.maxY - 0.15);
      return new Vec3d((box.minX + box.maxX) / 2.0, y, (box.minZ + box.maxZ) / 2.0);
   }

   /** Distance from the eye to the closest point of the target's hitbox. */
   private double distance(Entity entity, Vec3d eye) {
      Box box = entity.getBoundingBox();
      return eye.distanceTo(new Vec3d(MathHelper.clamp(eye.x, box.minX, box.maxX), MathHelper.clamp(eye.y, box.minY, box.maxY), MathHelper.clamp(eye.z, box.minZ, box.maxZ)));
   }

   public static enum Mode {
      FT,
      Grim,
      SpookyTime,
      Noise,
      HW;
   }

   public static enum MoveFix {
      Lite,
      Strong,
      TargetFollow;
   }

   public static enum Sort {
      Distance,
      Smart,
      FOV,
      Health;
   }

   public static enum Crit {
      Disabled,
      Always,
      OnlySpace;
   }

   public static enum SprintMode {
      Default,
      Fast;
   }
}
