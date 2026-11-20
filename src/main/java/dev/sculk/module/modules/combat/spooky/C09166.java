package dev.sculk.module.modules.combat.spooky;

import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

/** Nursultan class09166: the random source of the SpookyTime aim system (original method names kept). */
public class C09166 {
   private SplittableRandom rng = this.seeded();
   private float smoothA;
   private float smoothB;

   private SplittableRandom seeded() {
      long l = System.nanoTime() ^ ThreadLocalRandom.current().nextLong() ^ (long)System.identityHashCode(this) << 32;
      l ^= l >>> 33;
      l *= -49064778989728563L;
      l ^= l >>> 33;
      l *= -4265267296055464877L;
      l ^= l >>> 33;
      return new SplittableRandom(l);
   }

   public void y() {
      this.rng = this.seeded();
      this.smoothA = 0.0F;
      this.smoothB = 0.0F;
   }

   /** Triangular distribution (mean of two uniforms). */
   public float y(float f2, float f3) {
      if (f3 <= f2) {
         return f2;
      }

      double d = (this.rng.nextDouble() + this.rng.nextDouble()) * 0.5;
      return f2 + (f3 - f2) * (float)d;
   }

   /** Smoothed noise in [-f2, f2]; bl picks one of two independent channels. */
   public float N(boolean bl, float f2) {
      float f3 = (bl ? this.smoothA : this.smoothB) * this.N(0.52F, 0.76F) + this.y(-f2, f2) * this.N(0.24F, 0.48F);
      f3 = Math.max(-f2, Math.min(f2, f3));
      if (bl) {
         this.smoothA = f3;
      } else {
         this.smoothB = f3;
      }

      return f3;
   }

   /** Bernoulli with probability f2. */
   public boolean N(float f2) {
      return this.rng.nextDouble() < (double)Math.max(0.0F, Math.min(1.0F, f2));
   }

   /** Uniform float. */
   public float N(float f2, float f3) {
      if (f3 <= f2) {
         return f2;
      }

      return f2 + (f3 - f2) * (float)this.rng.nextDouble();
   }

   /** Random sign (+1 / -1). */
   public int N() {
      return this.rng.nextBoolean() ? 1 : -1;
   }

   /** Uniform int in [n2, n3]. */
   public int N(int n2, int n3) {
      if (n3 <= n2) {
         return n2;
      }

      return this.rng.nextInt(n2, n3 + 1);
   }
}
