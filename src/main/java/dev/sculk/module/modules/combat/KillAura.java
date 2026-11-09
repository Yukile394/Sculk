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
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Kill Aura: picks the best target in range, turns toward it and attacks on cooldown. */
public final class KillAura extends Module {
   public enum Sort { Distance, Smart, FOV, Health }
   public enum Crits { Off, Falling }

   private final NumberSetting range = new NumberSetting("Range", 3.0, 2.0, 6.0, 0.1);
   private final NumberSetting aimRange = new NumberSetting("Aim Range", 1.0, 0.0, 6.0, 0.1)
      .describe("Extra distance beyond Range where the aura already turns toward the target");
   private final NumberSetting fov = new NumberSetting("FOV", 180.0, 1.0, 180.0, 1.0)
      .describe("180 = any direction");
   private final ModeSetting<Sort> sort = new ModeSetting<>("Sort", Sort.Distance);
   private final BooleanSetting players = new BooleanSetting("Players", true);
   private final BooleanSetting mobs = new BooleanSetting("Mobs", true);
   private final BooleanSetting villagers = new BooleanSetting("Villagers", false);
   private final BooleanSetting invisible = new BooleanSetting("Invisible", true);
   private final BooleanSetting naked = new BooleanSetting("Naked", true).describe("Players wearing no armor");
   private final BooleanSetting walls = new BooleanSetting("Through Walls", false);
   private final ModeSetting<Crits> crits = new ModeSetting<>("Criticals", Crits.Off)
      .describe("Falling = hit only while falling for critical damage");
   private final BooleanSetting cooldown = new BooleanSetting("Cooldown Sync", true)
      .describe("Wait for the weapon cooldown before attacking");
   private final BooleanSetting pauseUsing = new BooleanSetting("Pause While Using", true)
      .describe("No attacks while eating, drinking or blocking");
   private final BooleanSetting shieldBreak = new BooleanSetting("Shield Break", true)
      .describe("Switch to an axe for a moment when the target is blocking");
   private final BooleanSetting rotate = new BooleanSetting("Rotate", true);
   private final NumberSetting speed = new NumberSetting("Rotation Speed", 40.0, 1.0, 180.0, 1.0)
      .describe("Max degrees per tick toward the target")
      .visibleWhen(this.rotate::get);
   private final BooleanSetting swing = new BooleanSetting("Swing", true);
   private LivingEntity target;

   public KillAura() {
      super("Kill Aura", "Automatically attacks the best target around you", Category.COMBAT);
      this.addSettings(new Setting[]{this.range, this.aimRange, this.fov, this.sort, this.players, this.mobs,
         this.villagers, this.invisible, this.naked, this.walls, this.crits, this.cooldown, this.pauseUsing,
         this.shieldBreak, this.rotate, this.speed, this.swing});
   }

   /** Current target, used by Target ESP. */
   public LivingEntity getTarget() {
      return this.target;
   }

   @Override
   public void onEnable() {
      this.target = null;
   }

   @Override
   public void onDisable() {
      this.target = null;
   }

   @Override
   public void onTick() {
      var mc = this.getMC();
      if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
         this.target = null;
         return;
      }

      if (this.target == null || !this.valid(this.target, this.range.getValue() + this.aimRange.getValue())) {
         this.target = this.findTarget();
      }
      if (this.target == null) {
         return;
      }

      Vec3d eye = mc.player.getEyePos();
      Vec3d aim = closestPoint(this.target.getBoundingBox(), eye);
      if (this.rotate.get()) {
         this.turnToward(aim);
      }

      if (eye.squaredDistanceTo(aim) > this.range.getValue() * this.range.getValue()) {
         return;
      }
      if (this.pauseUsing.get() && mc.player.isUsingItem()) {
         return;
      }
      if (this.cooldown.get() && mc.player.getAttackCooldownProgress(0.5F) < 0.92F) {
         return;
      }
      if (!this.critReady()) {
         return;
      }

      this.attack(this.target);
   }

   private void attack(LivingEntity victim) {
      var inv = this.getMC().player.getInventory();
      int previous = inv.selectedSlot;
      int axe = -1;
      if (this.shieldBreak.get() && victim instanceof PlayerEntity p && p.isBlocking() && !CombatUtils.isShieldFacingAway(p)) {
         for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() instanceof AxeItem) {
               axe = i;
               break;
            }
         }
      }

      if (axe != -1) {
         inv.selectedSlot = axe;
      }
      CombatUtils.hitEntity(victim, this.swing.get());
      CombatUtils.noteInteracted(victim);
      if (axe != -1) {
         inv.selectedSlot = previous;
      }
   }

   private boolean critReady() {
      if (this.crits.getValue() == Crits.Off) {
         return true;
      }
      var p = this.getMC().player;
      if (p.isClimbing() || p.isTouchingWater() || p.hasVehicle() || p.isFallFlying()) {
         return true;
      }
      return !p.isOnGround() && p.fallDistance > 0.0F;
   }

   private LivingEntity findTarget() {
      var mc = this.getMC();
      double reach = this.range.getValue() + this.aimRange.getValue();
      Box area = mc.player.getBoundingBox().expand(reach + 2.0);
      Comparator<LivingEntity> order = Comparator.<LivingEntity>comparingInt(e -> e instanceof PlayerEntity ? 0 : 1)
         .thenComparingDouble(this::score);

      LivingEntity best = null;
      for (Entity e : mc.world.getOtherEntities(mc.player, area)) {
         if (e instanceof LivingEntity living && this.valid(living, reach)) {
            if (best == null || order.compare(living, best) < 0) {
               best = living;
            }
         }
      }
      return best;
   }

   private double score(LivingEntity e) {
      var p = this.getMC().player;
      double dist = p.getEyePos().squaredDistanceTo(closestPoint(e.getBoundingBox(), p.getEyePos()));
      return switch (this.sort.getValue()) {
         case Distance -> dist;
         case Health -> e.getHealth() + e.getAbsorptionAmount();
         case FOV -> RotationUtils.angleTo(RotationUtils.direction(p, e.getBoundingBox().getCenter()));
         case Smart -> (e.getHealth() + e.getAbsorptionAmount()) * 2.0 + Math.sqrt(dist) * 3.0;
      };
   }

   private boolean valid(LivingEntity e, double reach) {
      var mc = this.getMC();
      if (e == mc.player || !e.isAlive() || e.isRemoved() || e.isSpectator() || e instanceof ArmorStandEntity) {
         return false;
      }
      if (Friends.isAlly(e) || !this.typeAllowed(e)) {
         return false;
      }
      if (!this.invisible.get() && e.isInvisible()) {
         return false;
      }
      if (!this.naked.get() && e instanceof PlayerEntity && !this.hasArmor(e)) {
         return false;
      }

      Vec3d eye = mc.player.getEyePos();
      Vec3d point = closestPoint(e.getBoundingBox(), eye);
      if (eye.squaredDistanceTo(point) > reach * reach) {
         return false;
      }
      if (this.fov.getValue() < 180.0 && RotationUtils.angleTo(RotationUtils.direction(mc.player, point)) > this.fov.getValue()) {
         return false;
      }
      return this.walls.get() || this.visible(eye, point);
   }

   private boolean typeAllowed(LivingEntity e) {
      if (e instanceof PlayerEntity) {
         return this.players.get();
      }
      if (e instanceof VillagerEntity) {
         return this.villagers.get();
      }
      return this.mobs.get() && e instanceof Monster;
   }

   private boolean hasArmor(LivingEntity e) {
      return !e.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
         || !e.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
         || !e.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
         || !e.getEquippedStack(EquipmentSlot.FEET).isEmpty();
   }

   private boolean visible(Vec3d from, Vec3d to) {
      var mc = this.getMC();
      return mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player))
         .getType() == HitResult.Type.MISS;
   }

   private static Vec3d closestPoint(Box box, Vec3d from) {
      return new Vec3d(
         MathHelper.clamp(from.x, box.minX, box.maxX),
         MathHelper.clamp(from.y, box.minY, box.maxY),
         MathHelper.clamp(from.z, box.minZ, box.maxZ)
      );
   }

   private void turnToward(Vec3d aim) {
      var p = this.getMC().player;
      Rotation delta = RotationUtils.deltaTo(RotationUtils.direction(p, aim));
      float max = this.speed.getFloat();
      float dYaw = MathHelper.clamp((float)delta.yaw(), -max, max);
      float dPitch = MathHelper.clamp((float)delta.pitch(), -max, max);
      p.setYaw(p.getYaw() + dYaw);
      p.setPitch(MathHelper.clamp(p.getPitch() + dPitch, -90.0F, 90.0F));
   }
}
