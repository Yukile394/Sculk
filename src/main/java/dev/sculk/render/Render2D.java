package dev.sculk.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

public final class Render2D {
   private Render2D() {
   }

   public static void roundedRect(DrawContext context, float x, float y, float width, float height, float radius, int color) {
      if (!(width <= 0.0F) && !(height <= 0.0F)) {
         float r = Math.max(0.0F, Math.min(radius, Math.min(width, height) / 2.0F));
         if (r < 0.5F) {
            fill(context, x, y, x + width, y + height, color);
         } else {
            int top = (int)Math.ceil((double)y);
            int bottom = (int)Math.ceil((double)(y + height));

            for (int row = top; row < bottom; row++) {
               float inset = cornerInset((float)row + 0.5F, y, height, r);
               int x1 = (int)Math.ceil((double)(x + inset));
               int x2 = (int)Math.floor((double)(x + width - inset));
               if (x2 > x1) {
                  context.fill(x1, row, x2, row + 1, color);
               }
            }
         }
      }
   }

   public static void roundedOutline(DrawContext context, float x, float y, float width, float height, float radius, float thickness, int color) {
      if (!(width <= 0.0F) && !(height <= 0.0F)) {
         float t = Math.max(1.0F, thickness);
         float r = Math.max(0.0F, Math.min(radius, Math.min(width, height) / 2.0F));
         int top = (int)Math.ceil((double)y);
         int bottom = (int)Math.ceil((double)(y + height));
         float innerTop = y + t;
         float innerHeight = height - t * 2.0F;
         float innerRadius = Math.max(0.0F, r - t);

         for (int row = top; row < bottom; row++) {
            float sample = (float)row + 0.5F;
            float outerInset = cornerInset(sample, y, height, r);
            int ox1 = (int)Math.ceil((double)(x + outerInset));
            int ox2 = (int)Math.floor((double)(x + width - outerInset));
            if (ox2 > ox1) {
               if (!(innerHeight <= 0.0F) && !(sample < innerTop) && !(sample > innerTop + innerHeight)) {
                  float innerInset = cornerInset(sample, innerTop, innerHeight, innerRadius);
                  int ix1 = (int)Math.ceil((double)(x + t + innerInset));
                  int ix2 = (int)Math.floor((double)(x + width - t - innerInset));
                  if (ix1 > ox1) {
                     context.fill(ox1, row, ix1, row + 1, color);
                  }

                  if (ox2 > ix2) {
                     context.fill(ix2, row, ox2, row + 1, color);
                  }
               } else {
                  context.fill(ox1, row, ox2, row + 1, color);
               }
            }
         }
      }
   }

   public static void circle(DrawContext context, float centerX, float centerY, float radius, int color) {
      if (!(radius <= 0.0F)) {
         int top = (int)Math.ceil((double)(centerY - radius));
         int bottom = (int)Math.ceil((double)(centerY + radius));

         for (int row = top; row < bottom; row++) {
            float dy = (float)row + 0.5F - centerY;
            float squared = radius * radius - dy * dy;
            if (!(squared <= 0.0F)) {
               float half = (float)Math.sqrt((double)squared);
               int x1 = (int)Math.ceil((double)(centerX - half));
               int x2 = (int)Math.floor((double)(centerX + half));
               if (x2 > x1) {
                  context.fill(x1, row, x2, row + 1, color);
               }
            }
         }
      }
   }

   /** Plain rectangle, float coordinates. */
   public static void rect(DrawContext context, float x, float y, float width, float height, int color) {
      if (width > 0.0F && height > 0.0F) {
         fill(context, x, y, x + width, y + height, color);
      }
   }

   /** Four colour gradient quad (top-left, top-right, bottom-right, bottom-left). */
   public static void gradient(DrawContext context, float x1, float y1, float x2, float y2, int tl, int tr, int br, int bl) {
      if (x2 <= x1 || y2 <= y1) {
         return;
      }

      Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShader(GameRenderer::getPositionColorProgram);
      BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      buffer.vertex(matrix, x1, y1, 0.0F).color(tl);
      buffer.vertex(matrix, x1, y2, 0.0F).color(bl);
      buffer.vertex(matrix, x2, y2, 0.0F).color(br);
      buffer.vertex(matrix, x2, y1, 0.0F).color(tr);
      BufferRenderer.drawWithGlobalProgram(buffer.end());
      RenderSystem.disableBlend();
   }

   public static void gradientH(DrawContext context, float x1, float y1, float x2, float y2, int left, int right) {
      gradient(context, x1, y1, x2, y2, left, right, right, left);
   }

   public static void gradientV(DrawContext context, float x1, float y1, float x2, float y2, int top, int bottom) {
      gradient(context, x1, y1, x2, y2, top, top, bottom, bottom);
   }

   public static boolean hovered(double mouseX, double mouseY, double x, double y, double width, double height) {
      return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
   }

   public static void scissor(DrawContext context, float x1, float y1, float x2, float y2) {
      context.enableScissor((int)Math.floor((double)x1), (int)Math.floor((double)y1), (int)Math.ceil((double)x2), (int)Math.ceil((double)y2));
   }

   public static void endScissor(DrawContext context) {
      context.disableScissor();
   }

   private static void fill(DrawContext context, float x1, float y1, float x2, float y2, int color) {
      context.fill((int)Math.ceil((double)x1), (int)Math.ceil((double)y1), (int)Math.floor((double)x2), (int)Math.floor((double)y2), color);
   }

   private static float cornerInset(float sample, float top, float height, float radius) {
      if (radius <= 0.0F) {
         return 0.0F;
      } else {
         float dy = 0.0F;
         if (sample < top + radius) {
            dy = top + radius - sample;
         } else if (sample > top + height - radius) {
            dy = sample - (top + height - radius);
         }

         if (dy <= 0.0F) {
            return 0.0F;
         } else {
            float squared = radius * radius - dy * dy;
            return radius - (float)Math.sqrt((double)Math.max(0.0F, squared));
         }
      }
   }

   public static void text(DrawContext context, String text, float x, float y, int color) {
      text(context, text, x, y, color, false);
   }

   public static void text(DrawContext context, String text, float x, float y, int color, boolean shadow) {
      context.drawText(MinecraftClient.getInstance().textRenderer, text, Math.round(x), Math.round(y), color, shadow);
   }

   public static void textCentered(DrawContext context, String text, float centerX, float y, int color) {
      text(context, text, centerX - (float)textWidth(text) / 2.0F, y, color);
   }

   public static int textWidth(String text) {
      return MinecraftClient.getInstance().textRenderer.getWidth(text);
   }

   public static String truncate(String text, int maxWidth) {
      if (textWidth(text) <= maxWidth) {
         return text;
      } else {
         String ellipsis = "...";
         int budget = maxWidth - textWidth(ellipsis);
         StringBuilder out = new StringBuilder();

         for (char c : text.toCharArray()) {
            if (textWidth(out.toString() + c) > budget) {
               break;
            }

            out.append(c);
         }

         return out + ellipsis;
      }
   }
}
