package dev.sculk.module.modules.combat.spooky;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Nursultan class09139 (original method names kept). */
public final class C09139 {
   private C09139() {
   }

   /** Look vector of (yaw f2, pitch f3). */
   public static Vec3d N(float f2, float f3) {
      float f4 = f2 * (float)(Math.PI / 180.0);
      float f5 = -f3 * (float)(Math.PI / 180.0);
      float f6 = MathHelper.cos(f5);
      float f7 = MathHelper.sin(f5);
      float f8 = MathHelper.cos(f4);
      float f9 = MathHelper.sin(f4);
      return new Vec3d(f7 * f8, -f9, f6 * f8);
   }

   /** Uniform random float in [d2, d3]. */
   public static float N(double d2, double d3) {
      if (d3 <= d2) {
         return (float)d2;
      }

      return (float)(d2 + (d3 - d2) * ThreadLocalRandom.current().nextDouble());
   }
}
