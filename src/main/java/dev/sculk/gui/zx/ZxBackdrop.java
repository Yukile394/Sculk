package dev.sculk.gui.zx;

import dev.sculk.render.Render2D;
import dev.sculk.render.Snow;
import dev.sculk.render.Theme;
import net.minecraft.client.gui.DrawContext;

/** Full screen background of the click GUI: night gradient, soft accent glow at the bottom, falling snow. */
public final class ZxBackdrop {
   private ZxBackdrop() {
   }

   public static void render(DrawContext context, int width, int height, int mouseX, float fade) {
      render(context, width, height, mouseX, fade, true, false);
   }

   public static void render(DrawContext context, int width, int height, int mouseX, float fade, boolean snow, boolean lite) {
      float w = (float)width;
      float h = (float)height;
      Render2D.gradientV(context, 0.0F, 0.0F, w, h, Theme.scaleAlpha(0xFF060812, 0.80F * fade), Theme.scaleAlpha(0xFF0A0C18, 0.86F * fade));
      Render2D.gradientV(context, 0.0F, h * 0.5F, w, h, Theme.scaleAlpha(Theme.ACCENT, 0.0F), Theme.scaleAlpha(Theme.ACCENT, 0.13F * fade));
      Render2D.gradientV(context, 0.0F, 0.0F, w, 40.0F, Theme.scaleAlpha(0xFF000000, 0.35F * fade), 0x00000000);
      if (snow) {
         Snow.render(context, width, height, mouseX, Zx.dt, fade, lite);
      }
   }
}
