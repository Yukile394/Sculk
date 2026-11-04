package dev.sculk.gui;

import dev.sculk.SculkClient;
import dev.sculk.module.Category;
import dev.sculk.module.modules.client.Fps15;
import dev.sculk.render.Animation;
import dev.sculk.render.Render2D;
import dev.sculk.render.Toasts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

public final class ClickGuiScreen extends Screen {
   private static final int GAP = 10;
   private static final int MARGIN = 12;
   private final List<CategoryWindow> windows = new ArrayList<>();
   private final Animation fadeIn = new Animation(0.0F);

   public ClickGuiScreen() {
      super(Text.literal("sculk"));
   }

   protected void init() {
      this.windows.clear();

      for (Category category : Category.values()) {
         this.windows.add(new CategoryWindow(category, 0.0F, 0.0F));
      }

      this.layout();
      this.fadeIn.set(0.0F);
   }

   private void layout() {
      int perRow = Math.max(1, (this.width - 24 + 10) / 186);
      perRow = Math.min(perRow, this.windows.size());
      List<Float> rowHeights = new ArrayList<>();

      for (int start = 0; start < this.windows.size(); start += perRow) {
         float rowHeight = 0.0F;

         for (int i = start; i < Math.min(start + perRow, this.windows.size()); i++) {
            rowHeight = Math.max(rowHeight, this.windows.get(i).contentHeight());
         }

         rowHeights.add(rowHeight);
      }

      float total = (float)(10 * (rowHeights.size() - 1));

      for (float rowHeight : rowHeights) {
         total += rowHeight;
      }

      float y = Math.max(12.0F, ((float)this.height - total) / 2.0F);

      for (int row = 0; row < rowHeights.size(); row++) {
         int start = row * perRow;
         int count = Math.min(perRow, this.windows.size() - start);
         float rowWidth = (float)(count * 176 + (count - 1) * 10);
         float x = ((float)this.width - rowWidth) / 2.0F;

         for (int column = 0; column < count; column++) {
            this.windows.get(start + column).moveTo(x + (float)(column * 186), y);
         }

         y += rowHeights.get(row) + 10.0F;
      }
   }

<<<<<<< HEAD
=======
   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }

>>>>>>> a073ff2 (Guncelleme)
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      float alpha = this.fadeIn.animate(1.0F, 10.0F);
      context.fill(0, 0, this.width, this.height, ((int)(alpha * 144.0F) & 0xFF) << 24);
      float glow = alpha * 8.0F;
      Render2D.roundedRect(
         context, (float)this.width / 2.0F - 260.0F, (float)this.height / 2.0F - 220.0F, 520.0F, 440.0F, 16.0F, (int)glow << 24 | 328968
      );

      for (CategoryWindow window : this.windows) {
         window.render(context, mouseX, mouseY, delta);
      }

      super.render(context, mouseX, mouseY, delta);
      Toasts.render(context);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      for (int i = this.windows.size() - 1; i >= 0; i--) {
         CategoryWindow window = this.windows.get(i);
         if (window.mouseClicked(mouseX, mouseY, button)) {
            if (button == 0 && i != this.windows.size() - 1) {
               this.windows.remove(i);
               this.windows.add(window);
            }

            return true;
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      for (CategoryWindow window : this.windows) {
         window.mouseDragged(mouseX, mouseY, button);
      }

      return true;
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      for (CategoryWindow window : this.windows) {
         window.mouseReleased(mouseX, mouseY, button);
      }

      return super.mouseReleased(mouseX, mouseY, button);
   }

<<<<<<< HEAD
   public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
=======
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
>>>>>>> a073ff2 (Guncelleme)
      for (CategoryWindow window : this.windows) {
         window.mouseScrolled(mouseX, mouseY, verticalAmount);
      }

      return true;
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      for (CategoryWindow window : this.windows) {
         if (window.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
         }
      }

      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   public boolean charTyped(char chr, int modifiers) {
      for (CategoryWindow window : this.windows) {
         if (window.charTyped(chr)) {
            return true;
         }
      }

      return super.charTyped(chr, modifiers);
   }

   public void close() {
      Fps15 module = SculkClient.get(Fps15.class);
      if (module != null && module.isEnabled()) {
         module.setEnabled(false);
      } else {
         super.close();
      }
   }

   public void removed() {
      Fps15 module = SculkClient.get(Fps15.class);
      if (module != null) {
         module.onScreenClosed();
      }
   }

   public boolean shouldPause() {
      return false;
   }
}
