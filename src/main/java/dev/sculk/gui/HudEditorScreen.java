package dev.sculk.gui;

import dev.sculk.SculkClient;
import dev.sculk.hud.HudElement;
import dev.sculk.hud.HudLayout;
import dev.sculk.hud.HudRenderer;
import dev.sculk.module.modules.client.Fps15;
import dev.sculk.render.Render2D;
import dev.sculk.render.Theme;
import dev.sculk.util.ConfigManager;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

public final class HudEditorScreen extends Screen {
   private HudElement dragging;
   private double grabX;
   private double grabY;

   public HudEditorScreen() {
      super(Text.literal("sculk hud"));
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      int screenWidth = context.getScaledWindowWidth();
      int screenHeight = context.getScaledWindowHeight();
      context.fill(0, 0, this.width, this.height, -1274805238);

      for (int x = 0; x < this.width; x += 40) {
         context.fill(x, 0, x + 1, this.height, 318767103);
      }

      for (int y = 0; y < this.height; y += 40) {
         context.fill(0, y, this.width, y + 1, 318767103);
      }

      Render2D.roundedRect(context, 8.0F, 8.0F, (float)(this.width - 16), 22.0F, 6.0F, -1072689130);
      Render2D.roundedOutline(context, 8.0F, 8.0F, (float)(this.width - 16), 22.0F, 6.0F, 1.0F, -14474452);
      Render2D.text(context, "HUD Editor", 16.0F, 15.0F, Theme.ACCENT);
      Render2D.text(
         context,
         "Drag: move   Right click: reset   ESC: save & close",
         (float)(this.width - 16 - Render2D.textWidth("Drag: move   Right click: reset   ESC: save & close")),
         15.0F,
         -8750457
      );

      for (HudElement element : HudElement.values()) {
         HudRenderer.beginFrame(element, true);
         int elementWidth = HudRenderer.width(element);
         int elementHeight = HudRenderer.height(element);
         float x = pixelX(element, elementWidth, screenWidth);
         float y = pixelY(element, elementHeight, screenHeight);
         HudRenderer.draw(context, element, x, y);
         boolean selected = element == this.dragging;
         boolean hovered = (float)mouseX >= x && (float)mouseX <= x + (float)elementWidth && (float)mouseY >= y && (float)mouseY <= y + (float)elementHeight;
         if (selected || hovered) {
            Render2D.roundedOutline(
               context, x - 2.0F, y - 2.0F, (float)(elementWidth + 4), (float)(elementHeight + 4), 6.0F, 1.0F, selected ? Theme.ACCENT : 1442840575
            );
         }
      }

      if (this.dragging != null) {
         Render2D.text(context, this.dragging.label(), (float)(mouseX + 10), (float)(mouseY - 12), Theme.ACCENT);
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      int screenWidth = this.width;
      int screenHeight = this.height;
      HudElement[] elements = HudElement.values();

      for (int i = elements.length - 1; i >= 0; i--) {
         HudElement element = elements[i];
         int elementWidth = HudRenderer.width(element);
         int elementHeight = HudRenderer.height(element);
         float x = pixelX(element, elementWidth, screenWidth);
         float y = pixelY(element, elementHeight, screenHeight);
         if (!(mouseX < (double)x) && !(mouseX > (double)(x + (float)elementWidth)) && !(mouseY < (double)y) && !(mouseY > (double)(y + (float)elementHeight))) {
            if (button == 1) {
               HudLayout.reset(element);
               HudLayout.save();
               return true;
            }

            if (button == 0) {
               this.dragging = element;
               this.grabX = mouseX - (double)x;
               this.grabY = mouseY - (double)y;
               return true;
            }
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (this.dragging != null && button == 0) {
         int elementWidth = HudRenderer.width(this.dragging);
         int elementHeight = HudRenderer.height(this.dragging);
         float x = clamp((float)(mouseX - this.grabX), 0.0F, (float)Math.max(0, this.width - elementWidth));
         float y = clamp((float)(mouseY - this.grabY), 0.0F, (float)Math.max(0, this.height - elementHeight));
         HudLayout.set(
            this.dragging, x / Math.max(1.0F, (float)(this.width - elementWidth)), y / Math.max(1.0F, (float)(this.height - elementHeight))
         );
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.dragging != null) {
         this.dragging = null;
         HudLayout.save();
         ConfigManager.save(SculkClient.modules());
         return true;
      } else {
         return super.mouseReleased(mouseX, mouseY, button);
      }
   }

   public void close() {
      HudLayout.save();
      Fps15 clickGui = SculkClient.get(Fps15.class);
      if (clickGui != null && clickGui.isEnabled()) {
         clickGui.setEnabled(false);
      }

      super.close();
   }

   public boolean shouldPause() {
      return false;
   }

   private static float pixelX(HudElement element, int elementWidth, int screenWidth) {
      return HudLayout.x(element) * (float)Math.max(0, screenWidth - elementWidth);
   }

   private static float pixelY(HudElement element, int elementHeight, int screenHeight) {
      return HudLayout.y(element) * (float)Math.max(0, screenHeight - elementHeight);
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }
}
