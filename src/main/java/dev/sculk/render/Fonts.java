package dev.sculk.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Text helper for the GUI. With {@link #custom} on, ZeltrixWare's SF Medium / SF Bold TTFs
 * (assets/sculk/font/*.json) are used, otherwise the vanilla font is used.
 */
public final class Fonts {
   public static final Identifier MEDIUM = Identifier.of("sculk", "sf_medium");
   public static final Identifier BOLD = Identifier.of("sculk", "sf_bold");
   public static boolean custom = false;

   private Fonts() {
   }

   private static TextRenderer renderer() {
      return MinecraftClient.getInstance().textRenderer;
   }

   public static Text styled(String text, boolean bold) {
      MutableText out = Text.literal(text);
      if (custom) {
         out.setStyle(Style.EMPTY.withFont(bold ? BOLD : MEDIUM));
      } else if (bold) {
         out.formatted(Formatting.BOLD);
      }

      return out;
   }

   public static void draw(DrawContext context, String text, float x, float y, int color, boolean bold) {
      if ((color >>> 24) < 4) {
         return;
      }

      context.drawText(renderer(), styled(text, bold), Math.round(x), Math.round(y), color, false);
   }

   public static void draw(DrawContext context, String text, float x, float y, int color) {
      draw(context, text, x, y, color, false);
   }

   public static void drawCentered(DrawContext context, String text, float centerX, float y, int color, boolean bold) {
      draw(context, text, centerX - (float)width(text, bold) / 2.0F, y, color, bold);
   }

   public static int width(String text, boolean bold) {
      return renderer().getWidth(styled(text, bold));
   }

   public static int width(String text) {
      return width(text, false);
   }
}
