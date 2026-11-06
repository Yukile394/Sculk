package dev.sculk.module.modules.combat;

import dev.sculk.SculkClient;
import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.util.CombatUtils;
import dev.sculk.util.Friends;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public abstract class AimAssistModule extends Module {
   private final BooleanSetting players = new BooleanSetting("Player", true);
   private final NumberSetting distance = new NumberSetting("Distance", 3.92, 0.5, 150.0, 0.01);
   private final NumberSetting fov = new NumberSetting("FOV", 92.0, 1.0, 360.0, 1.0);
   private final BooleanSetting slot = new BooleanSetting("Slot", true);
   private final NumberSetting slots = new NumberSetting("Slots", 1.0, 1.0, 9.0, 1.0);
   private final NumberSetting lockSpeed = new NumberSetting("Lock Speed", 0.18, 0.05, 5.0, 0.01)
      .describe("Dusuk = daha az snap, anti-cheat dostu");
   private final BooleanSetting precise = new BooleanSetting("Precise", false);
   private final NumberSetting preciseAngle = new NumberSetting("Angle", 30.06, 1.0, 180.0, 0.01);
   private final BooleanSetting preciseInvisible = new BooleanSetting("Invisible", false);
   private final BooleanSetting preciseNowalls = new BooleanSetting("Nowalls", true);
   private final BooleanSetting monsters = new BooleanSetting("Monsters", false);
   private final BooleanSetting gcdBypass = new BooleanSetting("GCD Bypass", true)
      .describe("Mouse delta'yi GCD'ye uyumlu hale getir (Polar/Grim)");
   private final ModeSetting<AimAssistModule.AimPart> aimPart = new ModeSetting<>("Aim Part", AimAssistModule.AimPart.Body)
      .describe("Head/Body/Legs/Feet sec; Auto = smoothhud varsayilan");
   private Entity target;
   private Vec3d smoothedPoint;
   private long lastTime = -1L;
   private float gcdYaw, gcdPitch;

   protected AimAssistModule(String name, String description) {
      super(name, description, Category.COMBAT);
      this.addSettings(new Setting[]{this.players, this.distance, this.fov, this.slot, this.slots, this.lockSpeed,
         this.precise, this.preciseAngle, this.preciseInvisible, this.preciseNowalls, this.monsters, this.gcdBypass, this.aimPart});
   }

   @Override
   public void onEnable() {
      this.target = null; this.smoothedPoint = null; this.lastTime = -1L;
   }

   @Override
   public void onDisable() {
      this.target = null; this.smoothedPoint = null; this.lastTime = -1L;
   }

   @Override
   public void onRenderTick(float tickDelta) {
      if (this.mc != null && this.mc.player != null && this.mc.world != null && this.mc.currentScreen == null) {
         float dt = this.deltaTime();
         Vec3d eyePos = this.mc.player.getCameraPosVec(tickDelta);
         Vec2f playerRot = new Vec2f(this.mc.player.getPitch(), this.mc.player.getYaw());
         boolean slotEnabled = this.slot.get();
         int requiredSlot = this.slots.getInt();
         float fovVal = this.fov.getFloat();
         boolean allowInvisible = this.allowInvisibleTargets();
         boolean lineOfSight = this.requireLineOfSight();
         double reachSq = this.distance.getValue() * this.distance.getValue();
         float maxAngle = this.activeAngle();
         float lockSpeedVal = this.lockSpeed.getFloat();
         Entity newTarget = null;
         Vec2f bestDelta = null;
         Entity stickyTarget = this.stickyTarget();

         for (Entity e : this.mc.world.getEntities()) {
            Vec3d point;
            if (e != null && e != this.mc.player && e.isLiving() && !e.isRemoved()
               && !this.shouldIgnoreCorpse(e) && this.inFov(e, fovVal)
               && (allowInvisible || !e.isInvisibleTo(this.mc.player))
               && !e.isSpectator() && !e.isInvulnerable()
               && (!slotEnabled || this.activeSlot() + 1 == requiredSlot)
               && this.entityAllowed(e)
               && !(eyePos.squaredDistanceTo(point = this.aimPoint(e)) > reachSq)) {
               Vec2f delta = this.rotationTo(playerRot, eyePos, point);
               if (!(Math.abs(delta.y) > maxAngle) && (!lineOfSight || !this.raycastBlocked(eyePos, point))) {
                  if (e == this.target || e == stickyTarget) { newTarget = e; bestDelta = delta; break; }
                  if (bestDelta == null || Math.abs(delta.y) < Math.abs(bestDelta.y)) { newTarget = e; bestDelta = delta; }
               }
            }
         }

         if (bestDelta == null) {
            this.target = null; this.smoothedPoint = null;
         } else {
            boolean sameTarget = newTarget == this.target;
            this.target = newTarget;
            Vec3d predicted = this.predictPoint(newTarget, eyePos, dt, sameTarget);
            Vec2f smoothed = this.smooth(this.rotationTo(playerRot, eyePos, predicted), maxAngle, lockSpeedVal);
            float newPitch = this.mc.player.getPitch() + smoothed.x;
            float newYaw = this.mc.player.getYaw() + smoothed.y;
            if (this.gcdBypass.get()) {
               newYaw = this.applyGCD(newYaw, true);
               newPitch = this.applyGCD(newPitch, false);
            }
            this.mc.player.setPitch(newPitch);
            this.mc.player.setYaw(newYaw);
         }
      } else {
         this.target = null; this.smoothedPoint = null;
      }
   }

   /** GCD simülasyonu: Polar/Grim rotation delta'yi mouse sensitivity'sine gore kontrol eder */
   private float applyGCD(float value, boolean yaw) {
      float sensitivity = this.mc.options.getMouseSensitivity().getValue().floatValue();
      float f = sensitivity * 0.6F + 0.2F;
      float gcd = f * f * f * 8.0F * 0.15F;
      if (gcd < 0.0001F) gcd = 0.0001F;
      float base = yaw ? this.gcdYaw : this.gcdPitch;
      float delta = MathHelper.wrapDegrees(value - base);
      float quantized = Math.round(delta / gcd) * gcd;
      float result = base + quantized;
      if (yaw) this.gcdYaw = result; else this.gcdPitch = result;
      return result;
   }

   private int activeSlot() {
      AutoFirework firework = SculkClient.get(AutoFirework.class);
      return firework != null ? firework.effectiveSlot() : this.mc.player.getInventory().selectedSlot;
   }

   public Entity currentTarget() { return this.target; }

   private boolean entityAllowed(Entity e) {
      if (Friends.isAlly(e)) return false;
      if (e instanceof PlayerEntity) return this.players.get();
      return !(e instanceof Monster) ? !this.players.get() : this.precise.get() && this.monsters.get();
   }

   private float activeAngle() { return this.precise.get() ? this.preciseAngle.getFloat() : 180.0F; }
   private boolean allowInvisibleTargets() { return !this.precise.get() || this.preciseInvisible.get(); }
   private boolean requireLineOfSight() { return this.preciseNowalls.get(); }

   private Vec2f rotationTo(Vec2f playerRot, Vec3d eyePos, Vec3d point) {
      Vec3d diff = point.subtract(eyePos);
      if (diff.lengthSquared() == 0.0) return new Vec2f(0.0F, 0.0F);
      Vec3d norm = diff.normalize();
      double yaw = Math.toDegrees(Math.atan2(-norm.x, norm.z));
      double pitch = Math.toDegrees(Math.asin(-norm.y));
      float pitchDelta = (float)(pitch - (double)playerRot.x);
      float yawDelta = (float)(yaw - (double)playerRot.y);
      return this.wrap(new Vec2f(pitchDelta, yawDelta));
   }

   private Vec2f wrap(Vec2f v) { return new Vec2f(this.wrapDegrees(v.x), this.wrapDegrees(v.y)); }
   private float wrapDegrees(float v) { while (v <= -180.0F) v += 360.0F; while (v > 180.0F) v -= 360.0F; return v; }

   private boolean raycastBlocked(Vec3d start, Vec3d end) {
      if (this.mc.world == null) return false;
      BlockHitResult hit = this.mc.world.raycast(new RaycastContext(start, end, ShapeType.OUTLINE, FluidHandling.NONE, this.mc.player));
      return hit.getType() != Type.MISS;
   }

   private float deltaTime() {
      long now = System.nanoTime();
      if (this.lastTime <= 0L) { this.lastTime = now; return 0.0027777778F; }
      float dt = (float)((double)(now - this.lastTime) / 1.0E9);
      this.lastTime = now;
      if (dt < 0.001F) dt = 0.001F;
      if (dt > 0.05F) dt = 0.05F;
      return dt;
   }

   private Vec2f smooth(Vec2f delta, float maxAngle, float lockSpeed) {
      float x = delta.x, y = delta.y;
      float len = MathHelper.sqrt(x * x + y * y);
      float maxMove = maxAngle * 0.26F;
      // Daha yumusak factor - snap engeli
      float factor = (len > maxMove ? 0.28F : (len > 0.35F ? 0.24F : 0.2F)) * lockSpeed;
      float mx = x * factor, my = y * factor;
      float moveLen = MathHelper.sqrt(mx * mx + my * my);
      if (moveLen > maxMove && moveLen > 0.0F) {
         float scale = maxMove / moveLen;
         mx *= scale; my *= scale;
      }
      if (len <= 0.02F) { mx = x; my = y; }
      return new Vec2f(mx, my);
   }

   private Vec3d predictPoint(Entity e, Vec3d eyePos, float dt, boolean sameTarget) {
      Vec3d point = this.aimPoint(e);
      double dist = eyePos.distanceTo(point);
      Vec3d predicted = point.subtract(e.getVelocity().multiply(dist * 0.03));
      if (sameTarget && this.smoothedPoint != null) {
         float factor = 1.0F - (float)Math.exp((double)(-14.0F * dt));
         factor = MathHelper.clamp(factor, 0.08F, 0.28F);
         this.smoothedPoint = this.smoothedPoint.lerp(predicted, (double)factor);
         return this.smoothedPoint;
      } else {
         this.smoothedPoint = predicted;
         return this.smoothedPoint;
      }
   }

   protected Vec3d aimPoint(Entity e) { return this.aimPointAt(e, this.aimPart.getValue()); }

   protected Vec3d aimPointAt(Entity e, AimAssistModule.AimPart part) {
      Box box = e.getBoundingBox();
      if (part == AimAssistModule.AimPart.Auto) {
         if (box == null) return e.getPos();
         if (e instanceof PlayerEntity p && this.needsStatic(p)) {
            Vec3d pos = p.getPos();
            return new Vec3d(pos.x, pos.y + 0.9, pos.z);
         }
         return box.getCenter();
      } else if (box == null) {
         return e.getPos();
      } else {
         double height = box.maxY - box.minY;
         double y = switch (part) {
            case Head -> box.maxY - height * 0.1;
            default -> box.minY + height * 0.5;
            case Legs -> box.minY + height * 0.25;
            case Feet -> box.minY + height * 0.05;
         };
         return new Vec3d((box.minX + box.maxX) / 2.0, y, (box.minZ + box.maxZ) / 2.0);
      }
   }

   private boolean needsStatic(PlayerEntity p) {
      if (!p.isFallFlying() && !p.isSwimming() && !p.isSleeping()) {
         EntityPose pose = p.getPose();
         return pose == EntityPose.FALL_FLYING || pose == EntityPose.SWIMMING || pose == EntityPose.SLEEPING || pose == EntityPose.SPIN_ATTACK;
      }
      return true;
   }

   private Entity stickyTarget() {
      Entity last = CombatUtils.getLastInteracted();
      return last != null && last != this.mc.player && last.isAlive() && !last.isRemoved() ? last : null;
   }

   private boolean inFov(Entity e, double fovVal) {
      if (e == null) return false;
      fovVal *= 0.5;
      double diff = (double)this.angleDiff(this.mc.player.getYaw(), this.rotationToEntity(e)[0]);
      return diff < fovVal;
   }

   private float[] rotationToEntity(Entity e) {
      Vec3d pos = e.getPos();
      return this.rotationFromPos(pos.x, pos.y + (double)e.getEyeHeight(e.getPose()), pos.z);
   }

   private float[] rotationFromPos(double tx, double ty, double tz) {
      Vec3d p = this.mc.player.getPos();
      double dx = tx - p.x;
      double dy = ty - (p.y + (double)this.mc.player.getEyeHeight(this.mc.player.getPose()));
      double dz = tz - p.z;
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
      float pitch = (float)(-(Math.atan2(dy, horizontal) * 180.0 / Math.PI));
      return new float[]{yaw, pitch};
   }

   private float angleDiff(float a, float b) {
      float diff = Math.abs(b - a) % 360.0F;
      if (diff > 180.0F) diff = 360.0F - diff;
      return diff;
   }

   private boolean shouldIgnoreCorpse(Entity e) {
      if (e == null || e.isRemoved()) return true;
      if (!(e instanceof LivingEntity le)) return false;
      if (e.isAlive() && !le.isDead()) return le.getHealth() <= 0.0F ? true : le.deathTime > 0 || this.isPlayerDead(e);
      return true;
   }

   private boolean isPlayerDead(Entity e) {
      if (!(e instanceof PlayerEntity)) return false;
      return e instanceof LivingEntity le ? le.getHealth() <= 0.0F && !e.isRemoved() : false;
   }

   public static enum AimPart { Auto, Head, Body, Legs, Feet; }
}
