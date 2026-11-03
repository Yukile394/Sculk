package dev.sculk.gui.rows;

import dev.sculk.gui.ModuleButton;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.render.Render2D;
import dev.sculk.render.Theme;
import net.minecraft.client.gui.DrawContext;

public final class BooleanRow extends SettingRow {
   private final BooleanSetting booleanSetting;

   public BooleanRow(BooleanSetting setting, ModuleButton parent) {
      super(setting, parent);
      this.booleanSetting = setting;
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      Render2D.text(context, this.setting.getName(), this.x, this.y + 7.0F, -8750457);
      float pillWidth = 22.0F;
      float pillX = this.x + this.width - pillWidth;
      float pillY = this.y + 6.0F;
      int trackColor = this.booleanSetting.get() ? Theme.ACCENT : -15000797;
      Render2D.roundedRect(context, pillX, pillY, pillWidth, 10.0F, 5.0F, trackColor);
      float knobX = this.booleanSetting.get() ? pillX + pillWidth - 5.0F : pillX + 5.0F;
      Render2D.circle(context, knobX, pillY + 5.0F, 3.4F, this.booleanSetting.get() ? -16119281 : -1447440);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0 && this.isHovered(mouseX, mouseY)) {
         this.booleanSetting.toggle();
         return true;
      } else {
         return false;
      }
   }
}
