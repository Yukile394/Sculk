package dev.sculk.gui.rows;

import dev.sculk.gui.ModuleButton;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.render.Render2D;
import net.minecraft.client.gui.DrawContext;

public final class ModeRow extends SettingRow {
   private final ModeSetting<?> modeSetting;

   public ModeRow(ModeSetting<?> setting, ModuleButton parent) {
      super(setting, parent);
      this.modeSetting = setting;
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      Render2D.text(context, this.setting.getName(), this.x, this.y + 7.0F, -8750457);
      String value = this.modeSetting.display();
      Render2D.text(context, value, this.x + this.width - (float)Render2D.textWidth(value), this.y + 7.0F, -1447440);
      Render2D.text(context, "<", this.x + this.width - (float)Render2D.textWidth(value) - 10.0F, this.y + 7.0F, -8750457);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.isHovered(mouseX, mouseY)) {
         return false;
      } else if (button == 0) {
         this.modeSetting.cycle(1);
         return true;
      } else if (button == 1) {
         this.modeSetting.cycle(-1);
         return true;
      } else {
         return false;
      }
   }
}
