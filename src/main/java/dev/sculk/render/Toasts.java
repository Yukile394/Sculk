package dev.sculk.render;

import net.minecraft.client.gui.DrawContext;

public final class Toasts {
   private static final long SHOWING_MS = 2400L;
   private static final long FADE_MS = 600L;
   private static final int PAD_X = 8;
   private static final int HEIGHT = 16;
   private static final float Y = 12.0F;
   private static String text = "";
   private static long until;

   private Toasts() {
   }

   public static void show(String message) {
      text = message;
      until = System.currentTimeMillis() + 2400L;
   }

   public static void render(DrawContext context) {
      long left = until - System.currentTimeMillis();
      if (left > 0L && !text.isEmpty()) {
         float alpha = Math.min(1.0F, (float)left / 600.0F);
         float width = (float)Render2D.textWidth(text) + 16.0F;
         float x = ((float)context.getScaledWindowWidth() - width) / 2.0F;
         float y = 12.0F + (1.0F - alpha) * 4.0F;
         Render2D.roundedRect(context, x, y, width, 16.0F, 6.0F, Theme.scaleAlpha(Theme.PANEL, alpha));
         Render2D.roundedOutline(context, x, y, width, 16.0F, 6.0F, 1.0F, Theme.scaleAlpha(-14474452, alpha));
         Render2D.text(context, text, x + 8.0F, y + 4.0F, Theme.scaleAlpha(Theme.ACCENT, alpha), true);
      }
   }
}
