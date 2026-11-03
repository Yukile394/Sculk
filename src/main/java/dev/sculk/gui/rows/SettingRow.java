package dev.sculk.gui.rows;

import dev.sculk.gui.ModuleButton;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.ButtonSetting;
import dev.sculk.module.setting.KeySetting;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.module.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;

public abstract class SettingRow {
   public static final int HEIGHT = 22;
   protected final Setting<?> setting;
   protected final ModuleButton parent;
   protected float x;
   protected float y;
   protected float width;

   protected SettingRow(Setting<?> setting, ModuleButton parent) {
      this.setting = setting;
      this.parent = parent;
   }

   public static SettingRow create(Setting<?> setting, ModuleButton parent) {
      if (setting instanceof BooleanSetting booleanSetting) {
         return new BooleanRow(booleanSetting, parent);
      } else if (setting instanceof NumberSetting numberSetting) {
         return new NumberRow(numberSetting, parent);
      } else if (setting instanceof ModeSetting<?> modeSetting) {
         return new ModeRow(modeSetting, parent);
      } else if (setting instanceof KeySetting keySetting) {
         return new KeyRow(keySetting, parent);
      } else if (setting instanceof StringSetting stringSetting) {
         return new StringRow(stringSetting, parent);
      } else if (setting instanceof ButtonSetting buttonSetting) {
         return new ButtonRow(buttonSetting, parent);
      } else {
         throw new IllegalArgumentException("Unsupported setting: " + setting.getClass().getSimpleName());
      }
   }

   public Setting<?> setting() {
      return this.setting;
   }

   public void setBounds(float x, float y, float width) {
      this.x = x;
      this.y = y;
      this.width = width;
   }

   public boolean isHovered(double mouseX, double mouseY) {
      return mouseX >= (double)this.x && mouseX <= (double)(this.x + this.width) && mouseY >= (double)this.y && mouseY <= (double)(this.y + 22.0F);
   }

   public abstract void render(DrawContext var1, int var2, int var3, float var4);

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      return false;
   }

   public void mouseDragged(double mouseX, double mouseY, int button) {
   }

   public void mouseReleased() {
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      return false;
   }

   public boolean charTyped(char chr) {
      return false;
   }
}
