package dev.sculk.render;

public final class Animation {
   private float value;
   private long last = System.nanoTime();

   public Animation(float initial) {
      this.value = initial;
   }

   public float animate(float target, float speed) {
      long now = System.nanoTime();
      float dt = Math.min((float)(now - this.last) / 1.0E9F, 0.1F);
      this.last = now;
      float factor = 1.0F - (float)Math.exp((double)(-speed * dt));
      this.value = this.value + (target - this.value) * factor;
      if (Math.abs(target - this.value) < 0.01F) {
         this.value = target;
      }

      return this.value;
   }

   public float get() {
      return this.value;
   }

   public void set(float value) {
      this.value = value;
   }
}
