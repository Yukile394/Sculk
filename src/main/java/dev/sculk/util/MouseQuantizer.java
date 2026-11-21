package dev.sculk.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

/**
 * Snaps rotation deltas onto the grid a real mouse produces (GCD bypass), ported from Tera Client.
 * One mouse step is (sens * 0.6 + 0.2)^3 * 1.2 degrees; every delta is rounded to a whole number of steps and
 * the rounding error is carried over to the next call, so the long-term aim still converges on the wanted angle.
 */
public final class MouseQuantizer {
   private double cachedSensitivity = Double.NaN;
   private double cachedGcd;
   private double yawResidual;
   private double pitchResidual;

   public double getGcd() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.options == null) {
         return this.cachedGcd;
      }

      double sensitivity = mc.options.getMouseSensitivity().getValue();
      if (sensitivity != this.cachedSensitivity) {
         this.cachedSensitivity = sensitivity;
         double v = sensitivity * 0.6 + 0.2;
         this.cachedGcd = v * v * v * 1.2;
      }

      return this.cachedGcd;
   }

   public void reset() {
      this.yawResidual = 0.0;
      this.pitchResidual = 0.0;
   }

   public float yaw(float current, float desired) {
      double gcd = this.getGcd();
      if (!(gcd > 1.0E-9)) {
         return desired;
      }

      double delta = MathHelper.wrapDegrees((double)desired - (double)current) + this.yawResidual;
      long steps = Math.round(delta / gcd);
      double applied = (double)steps * gcd;
      this.yawResidual = MathHelper.clamp(delta - applied, -gcd, gcd);
      return (float)((double)current + applied);
   }

   public float pitch(float current, float desired) {
      double gcd = this.getGcd();
      if (!(gcd > 1.0E-9)) {
         return MathHelper.clamp(desired, -90.0F, 90.0F);
      }

      double delta = MathHelper.clamp((double)desired, -90.0, 90.0) - (double)current + this.pitchResidual;
      long steps = Math.round(delta / gcd);
      double applied = (double)steps * gcd;
      this.pitchResidual = MathHelper.clamp(delta - applied, -gcd, gcd);
      return (float)MathHelper.clamp((double)current + applied, -90.0, 90.0);
   }
}
