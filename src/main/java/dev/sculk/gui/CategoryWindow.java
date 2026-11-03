package dev.sculk.gui;

import dev.sculk.SculkClient;
import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.render.Render2D;
import dev.sculk.render.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

public final class CategoryWindow {
   public static final int WIDTH = 176;
   public static final int HEADER = 22;
   private static final float DRAG_SMOOTHING = 0.35F;
   private final Category category;
   private final List<ModuleButton> buttons = new ArrayList<>();
   private float x;
   private float y;
   private float targetX;
   private float targetY;
   private boolean dragging;
   private float dragOffsetX;
   private float dragOffsetY;

   public CategoryWindow(Category category, float x, float y) {
      this.category = category;
      this.x = x;
      this.y = y;
      this.targetX = x;
      this.targetY = y;

      for (Module module : SculkClient.byCategory(category)) {
         this.buttons.add(new ModuleButton(this, module));
      }
   }

   public Category category() {
      return this.category;
   }

   public float x() {
      return this.x;
   }

   public float y() {
      return this.y;
   }

   public void moveTo(float x, float y) {
      this.x = x;
      this.y = y;
      this.targetX = x;
      this.targetY = y;
   }

   public float contentHeight() {
      float height = 22.0F;
      if (this.buttons.isEmpty()) {
         return height + 24.0F;
      } else {
         for (ModuleButton button : this.buttons) {
            height += button.height();
         }

         return height + 4.0F;
      }
   }

   private void updateOffsets() {
      float offset = 26.0F;

      for (ModuleButton button : this.buttons) {
         button.setOffsetY(offset);
         offset += button.height();
      }
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      if (this.dragging) {
         this.x = this.x + (this.targetX - this.x) * 0.35F;
         this.y = this.y + (this.targetY - this.y) * 0.35F;
      }

      this.updateOffsets();
      float height = this.contentHeight();
      Render2D.roundedRect(context, this.x + 2.0F, this.y + 6.0F, 172.0F, height - 6.0F, 8.0F, 2046820352);
      Render2D.roundedRect(context, this.x, this.y, 176.0F, height, 8.0F, Theme.PANEL);
      Render2D.roundedRect(context, this.x, this.y, 176.0F, 22.0F, 8.0F, Theme.HEADER);
      context.fill((int)this.x, (int)(this.y + 22.0F - 6.0F), (int)(this.x + 176.0F), (int)(this.y + 22.0F + 8.0F), Theme.HEADER);
      context.fill((int)this.x + 10, (int)(this.y + 22.0F), (int)(this.x + 176.0F) - 10, (int)(this.y + 22.0F) + 2, Theme.ACCENT_DIM);
      Render2D.roundedOutline(context, this.x, this.y, 176.0F, height, 8.0F, 1.5F, -14474452);
      Render2D.text(context, this.category.label, this.x + 12.0F, this.y + 7.0F, Theme.ACCENT);
      String count = Integer.toString(this.buttons.size());
      Render2D.text(context, count, this.x + 176.0F - 12.0F - (float)Render2D.textWidth(count), this.y + 7.0F, -8750457);
      context.enableScissor((int)this.x, (int)(this.y + 22.0F + 2.0F), (int)(this.x + 176.0F), (int)(this.y + height - 2.0F));
      if (this.buttons.isEmpty()) {
         Render2D.textCentered(context, "no modules yet", this.x + 88.0F, this.y + 22.0F + 10.0F, -8750457);
      }

      float offset = 26.0F;

      for (ModuleButton button : this.buttons) {
         button.setOffsetY(offset);
         button.render(context, mouseX, mouseY, delta);
         offset += button.height();
      }

      context.disableScissor();
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      this.updateOffsets();
      boolean inside = mouseX >= (double)this.x
         && mouseX <= (double)(this.x + 176.0F)
         && mouseY >= (double)this.y
         && mouseY <= (double)(this.y + this.contentHeight());
      if (!inside) {
         return false;
      } else if (mouseY <= (double)(this.y + 22.0F)) {
         if (button == 0) {
            this.dragging = true;
            this.dragOffsetX = (float)mouseX - this.x;
            this.dragOffsetY = (float)mouseY - this.y;
            return true;
         } else {
            return true;
         }
      } else {
         for (ModuleButton moduleButton : this.buttons) {
            if (moduleButton.mouseClicked(mouseX, mouseY, button)) {
               return true;
            }
         }

         return true;
      }
   }

   public void mouseDragged(double mouseX, double mouseY, int button) {
      if (this.dragging && button == 0) {
         this.targetX = (float)mouseX - this.dragOffsetX;
         this.targetY = (float)mouseY - this.dragOffsetY;
      } else {
         for (ModuleButton moduleButton : this.buttons) {
            moduleButton.mouseDragged(mouseX, mouseY, button);
         }
      }
   }

   public void mouseReleased(double mouseX, double mouseY, int button) {
      if (button == 0) {
         this.dragging = false;

         for (ModuleButton button_ : this.buttons) {
            button_.mouseReleased();
         }
      }
   }

   public void mouseScrolled(double mouseX, double mouseY, double amount) {
      if (!(mouseX < (double)this.x)
         && !(mouseX > (double)(this.x + 176.0F))
         && !(mouseY < (double)this.y)
         && !(mouseY > (double)(this.y + this.contentHeight()))) {
         float scroll = (float)(amount * 10.0);
         this.targetY += scroll;
         this.y += scroll;
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      for (ModuleButton button : this.buttons) {
         if (button.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
         }
      }

      return false;
   }

   public boolean charTyped(char chr) {
      for (ModuleButton button : this.buttons) {
         if (button.charTyped(chr)) {
            return true;
         }
      }

      return false;
   }

   public void collapseOthers(ModuleButton keep) {
      for (ModuleButton button : this.buttons) {
         if (button != keep) {
            button.setExpanded(false);
         }
      }
   }

   public void resetAnimations() {
      for (ModuleButton button : this.buttons) {
         button.resetAnimations();
      }
   }
}
