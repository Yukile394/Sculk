package dev.sculk.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.DrawContext;

/**
 * Falling snow for the GUI background. Flakes have a depth: far ones are small, dim and slow, near ones are bigger,
 * brighter and faster, and they sway sideways. The mouse position adds a gentle wind.
 */
public final class Snow {
   private static final Random RNG = new Random();
   private static final List<Flake> FLAKES = new ArrayList<>();
   private static final int SNOW_COLOR = 0xFFEAF3FF;
   private static int sceneWidth;
   private static int sceneHeight;
   private static float time;

   private Snow() {
   }

   private static final class Flake {
      float x;
      float y;
      float depth;
      float phase;
      float freq;
      float amp;
   }

   private static void spawn(Flake flake, int width, int height, boolean anywhere) {
      flake.x = RNG.nextFloat() * (float)width;
      flake.y = anywhere ? RNG.nextFloat() * (float)height : -4.0F - RNG.nextFloat() * 24.0F;
      flake.depth = 0.25F + RNG.nextFloat() * 0.75F;
      flake.phase = RNG.nextFloat() * 6.2831855F;
      flake.freq = 0.4F + RNG.nextFloat() * 0.9F;
      flake.amp = 4.0F + RNG.nextFloat() * 10.0F;
   }

   /**
    * @param dt     seconds since the previous frame
    * @param factor 0..1 overall opacity (used for the fade-in of the GUI)
    */
   public static void render(DrawContext context, int width, int height, int mouseX, float dt, float factor) {
      if (width <= 0 || height <= 0 || factor <= 0.01F) {
         return;
      }

      if (width != sceneWidth || height != sceneHeight) {
         sceneWidth = width;
         sceneHeight = height;
         FLAKES.clear();
         int count = Math.max(90, Math.min(280, width * height / 7000));

         for (int i = 0; i < count; i++) {
            Flake flake = new Flake();
            spawn(flake, width, height, true);
            FLAKES.add(flake);
         }
      }

      time += dt;
      float wind = ((float)mouseX / (float)width - 0.5F) * 16.0F;

      for (Flake flake : FLAKES) {
         flake.y += (14.0F + 46.0F * flake.depth) * dt;
         flake.x += wind * flake.depth * dt;
         if (flake.y > (float)height + 4.0F) {
            spawn(flake, width, height, false);
         }

         if (flake.x < -12.0F) {
            flake.x += (float)width + 24.0F;
         } else if (flake.x > (float)width + 12.0F) {
            flake.x -= (float)width + 24.0F;
         }

         float drawX = flake.x + (float)Math.sin((double)(time * flake.freq + flake.phase)) * flake.amp * flake.depth;
         float size = 0.8F + 2.4F * flake.depth;
         float alpha = (0.25F + 0.65F * flake.depth) * factor;
         int color = Theme.scaleAlpha(SNOW_COLOR, alpha);
         if (size >= 2.0F) {
            Render2D.circle(context, drawX, flake.y, size * 1.7F, Theme.scaleAlpha(SNOW_COLOR, alpha * 0.10F));
            Render2D.circle(context, drawX, flake.y, size / 2.0F + 0.4F, color);
         } else {
            int px = Math.round(drawX);
            int py = Math.round(flake.y);
            context.fill(px, py, px + 1, py + 1, color);
         }
      }
   }
}
