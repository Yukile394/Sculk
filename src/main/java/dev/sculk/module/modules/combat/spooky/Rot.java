package dev.sculk.module.modules.combat.spooky;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Nursultan class11499 / class11500: a yaw + pitch pair. A "raw" rotation (class11500, used for differences) does not clamp the pitch. */
public class Rot {
   protected final float yaw;
   protected final float pitch;
   private final boolean raw;

   public Rot(float yaw, float pitch) {
      this(yaw, pitch, false);
   }

   public Rot(float yaw, float pitch, boolean raw) {
      this.yaw = yaw;
      this.pitch = pitch;
      this.raw = raw;
   }

   public float y() {
      return this.yaw;
   }

   public float R() {
      return this.raw ? this.pitch : MathHelper.clamp(this.pitch, -90.0F, 90.0F);
   }

   public float yaw() {
      return this.y();
   }

   public float pitch() {
      return this.R();
   }

   /** class11499.N(class11499): component-wise sum. */
   public Rot N(Rot other) {
      return new Rot(this.y() + other.y(), this.R() + other.R());
   }

   public Rot N(float dyaw, float dpitch) {
      return new Rot(this.y() + dyaw, this.R() + dpitch);
   }

   /** class11499.y(class11499): wrapped yaw difference and raw pitch difference. */
   public Rot y(Rot other) {
      return new Rot(MathHelper.wrapDegrees(this.y() - other.y()), this.R() - other.R(), true);
   }

   /** class11499.U(): the look vector. */
   public Vec3d U() {
      return Vec3d.fromPolar(this.R(), this.y());
   }

   /** class11499.N(class06889): angle in degrees between this look direction and the direction towards a point. */
   public float N(Vec3d point) {
      Vec3d eye = MinecraftClient.getInstance().player.getEyePos();
      Vec3d dir = point.subtract(eye).normalize();
      double dot = MathHelper.clamp(Vec3d.fromPolar(this.R(), this.y()).dotProduct(dir), -1.0, 1.0);
      return (float)Math.toDegrees(Math.acos(dot));
   }

   /** class11505.N(): the player's own rotation. */
   public static Rot current() {
      return new Rot(MinecraftClient.getInstance().player.getYaw(), MinecraftClient.getInstance().player.getPitch());
   }

   /** class11505.N(class06889): rotation from the player's eye to a point. */
   public static Rot toward(Vec3d point) {
      Vec3d d = point.subtract(MinecraftClient.getInstance().player.getEyePos());
      float yaw = MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0F);
      float pitch = -(float)Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
      return new Rot(yaw, pitch);
   }

   @Override
   public String toString() {
      return "Yaw: " + this.y() + ", Pitch: " + this.R();
   }
}
