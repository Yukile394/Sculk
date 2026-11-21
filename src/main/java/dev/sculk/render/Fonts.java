package dev.sculk.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Text helper for the whole client UI. Vanilla Minecraft glyphs are never used: every string is drawn with the
 * bundled SF Medium (normal) or SF Bold (titles) TrueType fonts declared in assets/sculk/font/*.json.
 */
public final class Fonts {
   public static final Identifier MEDIUM = Identifier.of("sculk", "sf_medium");
   public static final Identifier BOLD = Identifier.of("sculk", "sf_bold");

   // The same few hundred strings are measured and drawn every frame; building a Text and measuring it each time
   // was a large part of the GUI cost, so both are cached (cleared every 30 s and whenever they grow too large,
   // which also picks up a font reload).
   private static final java.util.HashMap<String, Text> STYLED_CACHE = new java.util.HashMap<>();
   private static final java.util.HashMap<String, Text> STYLED_BOLD_CACHE = new java.util.HashMap<>();
   private static final java.util.HashMap<String, Integer> WIDTH_CACHE = new java.util.HashMap<>();
   private static final java.util.HashMap<String, Integer> WIDTH_BOLD_CACHE = new java.util.HashMap<>();
   private static long lastClear = System.currentTimeMillis();

   private Fonts() {
   }

   private static void housekeeping() {
      long now = System.currentTimeMillis();
      if (now - lastClear > 30000L || STYLED_CACHE.size() > 1500 || STYLED_BOLD_CACHE.size() > 1500 || WIDTH_CACHE.size() > 1500 || WIDTH_BOLD_CACHE.size() > 1500) {
         STYLED_CACHE.clear();
         STYLED_BOLD_CACHE.clear();
         WIDTH_CACHE.clear();
         WIDTH_BOLD_CACHE.clear();
         lastClear = now;
      }
   }

   private static TextRenderer renderer() {
      return MinecraftClient.getInstance().textRenderer;
   }

   public static Text styled(String text, boolean bold) {
      housekeeping();
      java.util.HashMap<String, Text> cache = bold ? STYLED_BOLD_CACHE : STYLED_CACHE;
      Text cached = cache.get(text);
      if (cached == null) {
         MutableText out = Text.literal(text);
         // sf_bold.ttf draws missing-glyph boxes in game, so bold is Medium with the vanilla bold style instead
         out.setStyle(Style.EMPTY.withFont(MEDIUM).withBold(bold));
         cache.put(text, out);
         cached = out;
      }

      return cached;
   }

   public static void draw(DrawContext context, String text, float x, float y, int color, boolean bold, boolean shadow) {
      if ((color >>> 24) < 4) {
         return;
      }

      context.drawText(renderer(), styled(text, bold), Math.round(x), Math.round(y), color, shadow);
   }

   public static void draw(DrawContext context, String text, float x, float y, int color, boolean bold) {
      draw(context, text, x, y, color, bold, false);
   }

   public static void draw(DrawContext context, String text, float x, float y, int color) {
      draw(context, text, x, y, color, false, false);
   }

   public static void drawShadow(DrawContext context, String text, float x, float y, int color) {
      draw(context, text, x, y, color, false, true);
   }

   /** Text drawn at a scale (1.0 = normal size); the position is the top-left corner of the scaled text. */
   public static void drawScaled(DrawContext context, String text, float x, float y, float scale, int color, boolean bold) {
      if ((color >>> 24) < 4) {
         return;
      }

      context.getMatrices().push();
      context.getMatrices().translate(x, y, 0.0F);
      context.getMatrices().scale(scale, scale, 1.0F);
      context.drawText(renderer(), styled(text, bold), 0, 0, color, false);
      context.getMatrices().pop();
   }

   public static void drawCentered(DrawContext context, String text, float centerX, float y, int color, boolean bold) {
      draw(context, text, centerX - (float)width(text, bold) / 2.0F, y, color, bold);
   }

   public static int width(String text, boolean bold) {
      java.util.HashMap<String, Integer> cache = bold ? WIDTH_BOLD_CACHE : WIDTH_CACHE;
      Integer cached = cache.get(text);
      if (cached == null) {
         cached = renderer().getWidth(styled(text, bold));
         cache.put(text, cached);
      }

      return cached;
   }

   public static int width(String text) {
      return width(text, false);
   }
}
