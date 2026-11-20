package dev.sculk.module.modules.combat.spooky;

/** Faithful translations of the class04995 overloads used by the planner classes. */
public final class MathX_P {
   private MathX_P() {
   }

   public static float N(float f2, float f3, float f4) {
      if (f2 < f3) {
         return f3;
      }
      return Math.min(f2, f4);
   }

   public static double N(double d2, double d3, double d4) {
      if (d2 < d3) {
         return d3;
      }
      return Math.min(d2, d4);
   }

   public static double u(double d2, double d3, double d4) {
      return d3 + d2 * (d4 - d3);
   }

   public static float B(float f2, float f3, float f4) {
      return f3 + f2 * (f4 - f3);
   }
}
