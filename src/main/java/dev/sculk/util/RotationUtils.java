package dev.sculk.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

public final class RotationUtils {
   private static final MinecraftClient MC = MinecraftClient.getInstance();

   private RotationUtils() {
   }

   public static Rotation direction(Entity entity, Vec3d target) {
      double dx = target.x - entity.getX();
      double dy = target.y - entity.getEyeY();
      double dz = target.z - entity.getZ();
      double flat = (double)MathHelper.sqrt((float)(dx * dx + dz * dz));
      return new Rotation(MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0), -MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dy, flat))));
   }

   public static double angleTo(Rotation rotation) {
      double diffYaw = MathHelper.wrapDegrees((double)MC.player.getYaw() - rotation.yaw());
      double diffPitch = MathHelper.wrapDegrees((double)MC.player.getPitch() - rotation.pitch());
      return Math.sqrt(diffYaw * diffYaw + diffPitch * diffPitch);
   }

   public static Rotation deltaTo(Rotation target) {
      return new Rotation(
         (double)MathHelper.wrapDegrees((float)target.yaw() - MC.player.getYaw()),
         (double)MathHelper.wrapDegrees((float)target.pitch() - MC.player.getPitch())
      );
   }

   public static Vec3d lookVec(float yaw, float pitch) {
      float f = pitch * (float) (Math.PI / 180.0);
      float g = -yaw * (float) (Math.PI / 180.0);
      float h = MathHelper.cos(g);
      float i = MathHelper.sin(g);
      float j = MathHelper.cos(f);
      float k = MathHelper.sin(f);
      return new Vec3d((double)(i * j), (double)(-k), (double)(h * j));
   }
}
