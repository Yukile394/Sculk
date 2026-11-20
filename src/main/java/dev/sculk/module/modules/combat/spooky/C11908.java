package dev.sculk.module.modules.combat.spooky;

import java.util.concurrent.ThreadLocalRandom;

/** Nursultan class11908: random / math helpers (original method names kept). */
public final class C11908 {
   private C11908() {
   }

   /** Gaussian noise scaled by f2. */
   public static float y(float f2) {
      return (float)ThreadLocalRandom.current().nextGaussian() * f2;
   }

   /** Uniform float. */
   public static float y(float f2, float f3) {
      return f2 + (f3 - f2) * ThreadLocalRandom.current().nextFloat();
   }

   /** Log-normal: d2 * exp(d3 * gauss). */
   public static double y(double d2, double d3) {
      return d2 * Math.exp(d3 * ThreadLocalRandom.current().nextGaussian());
   }

   /** Radians to degrees (float). */
   public static float y(double d2) {
      return (float)d2 * 57.295776F;
   }

   /** Uniform int in [n2, n3]. */
   public static int N(int n2, int n3) {
      return ThreadLocalRandom.current().nextInt(n3 - n2 + 1) + n2;
   }

   /** Rounds d2 to a multiple of d3. */
   public static double N(double d2, double d3) {
      return (double)Math.round((double)Math.round(d2 / d3) * d3 * 1000000.0) / 1000000.0;
   }

   public static float N(float f2, float f3) {
      return (float)Math.round((float)Math.round(f2 / f3) * f3 * 100.0F) / 100.0F;
   }

   /** Exponentially modified gaussian: mean d2, sigma d3, exponential tail d4. */
   public static double N(double d2, double d3, double d4) {
      double d5 = d2 + d3 * ThreadLocalRandom.current().nextGaussian();
      double d6 = -d4 * Math.log(1.0 - ThreadLocalRandom.current().nextDouble());
      return d5 + d6;
   }

   /** Degrees to radians. */
   public static float N(float f2) {
      return f2 * (float)(Math.PI / 180.0);
   }
}
