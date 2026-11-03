package dev.sculk.gui.rows;

import dev.sculk.gui.ModuleButton;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.render.Render2D;
import dev.sculk.render.Theme;
import net.minecraft.client.gui.DrawContext;

public final class NumberRow extends SettingRow {
   private final NumberSetting numberSetting;
   private boolean dragging;

   public NumberRow(NumberSetting setting, ModuleButton parent) {
      super(setting, parent);
      this.numberSetting = setting;
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      Render2D.text(context, this.setting.getName(), this.x, this.y + 4.0F, -8750457);
      String value = this.numberSetting.display();
      Render2D.text(context, value, this.x + this.width - (float)Render2D.textWidth(value), this.y + 4.0F, Theme.ACCENT);
      float trackY = this.y + 17.0F;
      Render2D.roundedRect(context, this.x, trackY - 1.0F, this.width, 2.0F, 1.0F, -15000797);
      float fill = (float)this.numberSetting.fraction() * this.width;
      if (fill > 0.5F) {
         Render2D.roundedRect(context, this.x, trackY - 1.0F, fill, 2.0F, 1.0F, Theme.ACCENT_DIM);
      }

      Render2D.circle(context, this.x + fill, trackY, 3.2F, Theme.ACCENT);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0 && !(mouseY < (double)(this.y + 9.0F)) && this.isHovered(mouseX, mouseY)) {
         this.dragging = true;
         this.apply(mouseX);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void mouseDragged(double mouseX, double mouseY, int button) {
      if (this.dragging && button == 0) {
         this.apply(mouseX);
      }
   }

   @Override
   public void mouseReleased() {
      this.dragging = false;
   }

   private void apply(double mouseX) {
      this.numberSetting.setFromFraction((mouseX - (double)this.x) / (double)this.width);
   }
}
