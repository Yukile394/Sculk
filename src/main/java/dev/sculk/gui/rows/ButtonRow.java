package dev.sculk.gui.rows;

import dev.sculk.gui.ModuleButton;
import dev.sculk.module.setting.ButtonSetting;
import dev.sculk.render.Render2D;
import net.minecraft.client.gui.DrawContext;

public final class ButtonRow extends SettingRow {
   private static final long CONFIRM_WINDOW = 4000L;
   private final ButtonSetting buttonSetting;
   private long armedAt;

   public ButtonRow(ButtonSetting setting, ModuleButton parent) {
      super(setting, parent);
      this.buttonSetting = setting;
   }

   private boolean armed() {
      return this.buttonSetting.needsConfirm() && System.currentTimeMillis() - this.armedAt < 4000L;
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      boolean hovered = this.isHovered((double)mouseX, (double)mouseY);
      boolean armed = this.armed();
      int textColor = armed ? -38037 : (hovered ? -1447440 : -8750457);
      Render2D.roundedRect(context, this.x, this.y + 3.0F, this.width, 15.0F, 5.0F, hovered ? 352321535 : 268435455);
      Render2D.roundedOutline(context, this.x, this.y + 3.0F, this.width, 15.0F, 5.0F, 1.0F, armed ? -38037 : -14474452);
      String label = armed ? "click again to confirm" : this.setting.getName();
      Render2D.textCentered(context, label, this.x + this.width / 2.0F, this.y + 7.0F, textColor);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button != 0 || !this.isHovered(mouseX, mouseY)) {
         return false;
      } else if (!this.buttonSetting.needsConfirm()) {
         this.buttonSetting.run();
         return true;
      } else {
         if (this.armed()) {
            this.armedAt = 0L;
            this.buttonSetting.run();
         } else {
            this.armedAt = System.currentTimeMillis();
         }

         return true;
      }
   }
}
