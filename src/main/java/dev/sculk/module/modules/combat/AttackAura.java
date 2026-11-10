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
import dev.sculk.util.RotationUtils;
import java.util.Comparator;
import java.util.Optional;
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

/** AttackAura, ported from Nursultan: target filters, sorting, critical logic, sprint handling, shield break and auto mace. */
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
   private final ModeSetting<AttackAura.Rotate> rotate = new ModeSetting<>("Rotation", AttackAura.Rotate.Smooth);
   private final NumberSetting speed = new NumberSetting("Speed", 6.0, 1.0, 10.0, 0.1).describe("Rotation speed")
      .visibleWhen(() -> this.rotate.getValue() == AttackAura.Rotate.Smooth);
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
   private final NumberSetting cooldown = new NumberSetting("Cooldown", 0.92, 0.5, 1.0, 0.01).describe("Weapon charge needed before a hit");
   // do-not-attack
   private final BooleanSetting noEating = new BooleanSetting("Pause Eating", true).describe("Do not attack while eating or drinking");
   private final BooleanSetting noUsing = new BooleanSetting("Pause Using Item", false).describe("Do not attack while using any item");
   private final BooleanSetting noGliding = new BooleanSetting("Pause Gliding", false).describe("Do not attack while flying with an elytra");
   private Entity target;
   private boolean resumeSprint;

   public AttackAura() {
      super("AttackAura", "Automatically targets and attacks entities", Category.COMBAT);
      this.addSettings(new Setting[]{this.players, this.mobs, this.villagers, this.invisible, this.naked, this.bot, this.rotate, this.speed,
         this.sort, this.crit, this.sprint, this.fov, this.additionalRange, this.aimRange, this.throughWalls, this.shieldBreak,
         this.autoMace, this.cooldown, this.noEating, this.noUsing, this.noGliding});
   }

   public Entity currentTarget() {
      return this.target;
   }

   @Override
   public void onEnable() {
      this.target = null;
      this.resumeSprint = false;
   }

   @Override
   public void onDisable() {
      this.target = null;
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

      if (this.resumeSprint) {
         this.resumeSprint = false;
         if (this.mc.options.forwardKey.isPressed() && !this.mc.player.isSneaking()) {
            this.mc.player.setSprinting(true);
         }
      }

      if (this.mc.currentScreen != null || this.mc.player.isSpectator() || !this.mc.player.isAlive()) {
         this.target = null;
         return;
      }

      if (this.target == null || !this.valid(this.target, this.mc.player.getEyePos())) {
         this.target = this.pickTarget();
      }

      if (this.target == null) {
         return;
      }

      Vec3d eye = this.mc.player.getEyePos();
      if (this.rotate.getValue() != AttackAura.Rotate.None) {
         this.turn(this.aimPoint(this.target, eye));
      }

      CombatUtils.noteInteracted(this.target);
      this.attack(eye);
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

   private boolean critReady() {
      PlayerEntity p = this.mc.player;
      boolean airborne = !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater() && !p.hasVehicle() && !p.isFallFlying()
         && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasStatusEffect(StatusEffects.LEVITATION) && !p.getAbilities().flying;
      return airborne && p.fallDistance > 0.0F && p.getVelocity().y < 0.0;
   }

   private boolean critGate() {
      return switch (this.crit.getValue()) {
         case Disabled -> true;
         case Always -> this.critReady();
         case OnlySpace -> !this.mc.options.jumpKey.isPressed() || this.critReady();
      };
   }

   private void attack(Vec3d eye) {
      Entity entity = this.target;
      if (entity == null || this.paused()) {
         return;
      }

      if (this.distance(entity, eye) > this.attackRange()) {
         return;
      }

      if (this.mc.player.getAttackCooldownProgress(0.5F) < this.cooldown.getFloat()) {
         return;
      }

      if (!this.critGate()) {
         return;
      }

      if (this.rotate.getValue() != AttackAura.Rotate.None && !this.facing(entity, eye)) {
         return;
      }

      if (!this.throughWalls.get() && this.blocked(entity, eye)) {
         return;
      }

      int previous = this.mc.player.getInventory().selectedSlot;
      int slot = this.weaponSlot(entity);
      boolean swapped = slot >= 0 && slot != previous;
      if (swapped) {
         this.mc.player.getInventory().selectedSlot = slot;
      }

      if (this.crit.getValue() != AttackAura.Crit.Disabled && this.mc.player.isSprinting()) {
         this.mc.player.setSprinting(false);
         this.resumeSprint = this.sprint.getValue() == AttackAura.SprintMode.Fast;
      }

      CombatUtils.hitEntity(entity, true);
      if (swapped) {
         this.mc.player.getInventory().selectedSlot = previous;
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

   private void turn(Vec3d point) {
      Rotation wanted = RotationUtils.direction(this.mc.player, point);
      float yawDiff = MathHelper.wrapDegrees((float)wanted.yaw() - this.mc.player.getYaw());
      float pitchDiff = (float)wanted.pitch() - this.mc.player.getPitch();
      float yawStep = yawDiff;
      float pitchStep = pitchDiff;
      if (this.rotate.getValue() == AttackAura.Rotate.Smooth) {
         float factor = MathHelper.clamp(this.speed.getFloat() / 10.0F, 0.05F, 1.0F);
         float maxStep = 10.0F + this.speed.getFloat() * 12.0F;
         yawStep = MathHelper.clamp(yawDiff * factor, -maxStep, maxStep);
         pitchStep = MathHelper.clamp(pitchDiff * factor, -maxStep, maxStep);
      }

      this.mc.player.setYaw(this.mc.player.getYaw() + yawStep);
      this.mc.player.setPitch(MathHelper.clamp(this.mc.player.getPitch() + pitchStep, -90.0F, 90.0F));
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
      Vec3d point = this.aimPoint(entity, eye);
      HitResult hit = this.mc.world.raycast(new RaycastContext(eye, point, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this.mc.player));
      return hit.getType() != HitResult.Type.MISS;
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

   public static enum Rotate {
      Smooth,
      Snap,
      None;
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
