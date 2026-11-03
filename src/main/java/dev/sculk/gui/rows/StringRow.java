package dev.sculk.gui.rows;

import dev.sculk.gui.ModuleButton;
import dev.sculk.module.setting.StringSetting;
import dev.sculk.render.Render2D;
import dev.sculk.render.Theme;
import net.minecraft.client.gui.DrawContext;

public final class StringRow extends SettingRow {
   private final StringSetting stringSetting;
   private boolean focused;

   public StringRow(StringSetting setting, ModuleButton parent) {
      super(setting, parent);
      this.stringSetting = setting;
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      Render2D.text(context, this.setting.getName(), this.x, this.y + 3.0F, -8750457);
      String value = this.stringSetting.getValue().isEmpty() && !this.focused ? "empty" : this.stringSetting.getValue();
      int color = this.stringSetting.getValue().isEmpty() && !this.focused ? -8750457 : -1447440;
      Render2D.text(context, Render2D.truncate(value, (int)this.width), this.x, this.y + 12.0F, color);
      if (this.focused) {
         context.fill((int)this.x, (int)(this.y + 22.0F) - 1, (int)(this.x + this.width), (int)(this.y + 22.0F), Theme.ACCENT);
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.isHovered(mouseX, mouseY)) {
         this.focused = true;
         return true;
      } else {
         this.focused = false;
         return false;
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.focused) {
         return false;
      } else if (keyCode == 259) {
         this.stringSetting.backspace();
         return true;
      } else if (keyCode != 257 && keyCode != 256) {
         return false;
      } else {
         this.focused = false;
         return true;
      }
   }

   @Override
   public boolean charTyped(char chr) {
      if (!this.focused) {
         return false;
      } else {
         this.stringSetting.append(chr);
         return true;
      }
   }
}
