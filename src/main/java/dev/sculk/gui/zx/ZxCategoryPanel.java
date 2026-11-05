package dev.sculk.gui.zx;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.render.Render2D;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

public final class ZxCategoryPanel extends ZxPanel {
   private final List<ZxModuleButton> buttons = new ArrayList<>();

   public ZxCategoryPanel(Category category, List<Module> modules, float x, float y) {
      super(category.label, x, y);

      for (Module module : modules) {
         this.buttons.add(new ZxModuleButton(module));
      }
   }

   @Override
   protected float contentHeight() {
      float total = 0.0F;

      for (ZxModuleButton button : this.buttons) {
         if (button.matches()) {
            total += button.height();
         }
      }

      return total;
   }

   @Override
   protected String badge() {
      return Integer.toString(this.buttons.size());
   }

   @Override
   protected void renderContent(DrawContext c, int mx, int my, float top, boolean mouseOk) {
      float cursor = top;

      for (ZxModuleButton button : this.buttons) {
         if (button.matches()) {
            button.render(c, mx, my, this.x, cursor, this.w, mouseOk);
            cursor += button.height();
         }
      }
   }

   @Override
   protected boolean contentClick(int mx, int my, int button, float top) {
      float cursor = top;

      for (ZxModuleButton b : this.buttons) {
         if (b.matches()) {
            if (b.click(mx, my, button, this.x, cursor, this.w, true)) {
               return true;
            }

            cursor += b.height();
         }
      }

      return false;
   }

   @Override
   protected void contentRelease() {
      this.buttons.forEach(ZxModuleButton::release);
   }
}
