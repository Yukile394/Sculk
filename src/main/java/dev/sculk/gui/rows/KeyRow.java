package dev.sculk.gui.rows;

import dev.sculk.SculkClient;
import dev.sculk.gui.ModuleButton;
import dev.sculk.module.setting.KeySetting;
import dev.sculk.render.Render2D;
import dev.sculk.render.Theme;
import dev.sculk.util.ConfigManager;
import net.minecraft.client.gui.DrawContext;

public final class KeyRow extends SettingRow {
   private final KeySetting keySetting;

   public KeyRow(KeySetting setting, ModuleButton parent) {
      super(setting, parent);
      this.keySetting = setting;
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      Render2D.text(context, this.setting.getName(), this.x, this.y + 7.0F, -8750457);
      String value = this.keySetting.isListening() ? "press a key" : this.keySetting.display();
      int color = this.keySetting.isListening() ? Theme.ACCENT : -1447440;
      Render2D.text(context, value, this.x + this.width - (float)Render2D.textWidth(value), this.y + 7.0F, color);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.isHovered(mouseX, mouseY)) {
         return false;
      } else if (this.keySetting.isListening()) {
         this.keySetting.setKey(button);
         ConfigManager.save(SculkClient.modules());
         return true;
      } else if (button == 0) {
         this.keySetting.setListening(true);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.keySetting.isListening()) {
         return false;
      } else {
         this.keySetting.setKey(keyCode == 256 ? -1 : keyCode);
         ConfigManager.save(SculkClient.modules());
         return true;
      }
   }
}
