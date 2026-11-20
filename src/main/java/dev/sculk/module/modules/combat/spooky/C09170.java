package dev.sculk.module.modules.combat.spooky;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Nursultan class09170: angle helpers and the mouse sensitivity step. */
public final class C09170 {
   private C09170() {
   }

   /** Wrapped yaw difference from f2 to f3. */
   public static float N(float f2, float f3) {
      return MathHelper.wrapDegrees(f3 - f2);
   }

   /** Rotation from the player's eye towards a point. */
   public static Rot N(Vec3d point) {
      return Rot.toward(point);
   }

   /** Smallest rotation the mouse can produce (degrees). */
   public static double N() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.options == null) {
         return 0.0128;
      }

      double d = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
      return d * d * d * 8.0 * 0.15;
   }
}
