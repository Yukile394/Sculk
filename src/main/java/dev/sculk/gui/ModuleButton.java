package dev.sculk.gui;

import dev.sculk.SculkClient;
import dev.sculk.gui.rows.SettingRow;
import dev.sculk.module.Module;
import dev.sculk.module.setting.Setting;
import dev.sculk.render.Animation;
import dev.sculk.render.Render2D;
import dev.sculk.render.Theme;
import dev.sculk.util.ConfigManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

public final class ModuleButton {
   private final CategoryWindow parent;
   private final Module module;
   private final List<SettingRow> rows = new ArrayList<>();
   private final Animation expand = new Animation(0.0F);
   private final Animation hover = new Animation(0.0F);
   private float offsetY;
   private boolean expanded;

   public ModuleButton(CategoryWindow parent, Module module) {
      this.parent = parent;
      this.module = module;

      for (Setting<?> setting : module.settings()) {
         this.rows.add(SettingRow.create(setting, this));
      }
   }

   public Module module() {
      return this.module;
   }

   public float offsetY() {
      return this.offsetY;
   }

   public void setOffsetY(float offsetY) {
      this.offsetY = offsetY;
   }

   public float x() {
      return this.parent.x();
   }

   public float y() {
      return this.parent.y() + this.offsetY;
   }

   public boolean isExpanded() {
      return this.expanded;
   }

   public void setExpanded(boolean expanded) {
      this.expanded = expanded;
   }

   public float settingsHeight() {
      float height = 0.0F;

      for (SettingRow row : this.rows) {
         if (row.setting().isVisible()) {
            height += 22.0F;
         }
      }

      return height;
   }

   public float height() {
      return 22.0F + this.expand.get();
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      float y = this.y();
      float x = this.parent.x();
      float width = 176.0F;
      float targetHover = this.isMouseOver((double)mouseX, (double)mouseY) ? 1.0F : 0.0F;
      float h = this.hover.animate(targetHover, 12.0F);
      this.expand.animate(this.expanded ? this.settingsHeight() : 0.0F, 14.0F);
      boolean hovered = h > 0.02F;
      if (hovered) {
         Render2D.roundedRect(context, x + 6.0F, y + 2.0F, width - 12.0F, 18.0F, 6.0F, 352321535);
         int glow = Theme.mix(0, Theme.ACCENT, h * 0.4F);
         Render2D.roundedRect(context, x + 6.0F, y + 2.0F, width - 12.0F, 18.0F, 6.0F, glow);
      }

      if (this.module.isEnabled()) {
         context.fill((int)x + 8, (int)y + 7, (int)x + 10, (int)y + 15, Theme.ACCENT);
      }

      String label = this.module.getName();
      int color;
      if (this.module.isEnabled()) {
         color = Theme.ACCENT;
      } else {
         color = Theme.mix(-8750457, -1447440, h);
      }

      Render2D.textCentered(context, label, x + width / 2.0F, y + 6.0F, color);
      if (!this.module.settings().isEmpty()) {
         boolean onChevron = (float)mouseX >= x + width - 24.0F && (float)mouseX < x + width && (float)mouseY >= y && (float)mouseY < y + 22.0F;
         if (onChevron) {
            Render2D.roundedRect(context, x + width - 24.0F, y + 2.0F, 18.0F, 18.0F, 5.0F, 352321535);
         }

         Render2D.circle(context, x + width - 12.0F, y + 11.0F, 1.7F, this.expanded ? Theme.ACCENT : Theme.mix(-8750457, -1447440, h));
      }

      if (this.expand.get() > 0.5F) {
         float rowY = y + 22.0F;

         for (SettingRow row : this.rows) {
            if (row.setting().isVisible()) {
               row.setBounds(x + 10.0F, rowY, width - 20.0F);
               if (rowY < y + this.height()) {
                  row.render(context, mouseX, mouseY, delta);
               }

               rowY += 22.0F;
            }
         }
      }
   }

   private boolean isMouseOver(double mouseX, double mouseY) {
      float x = this.parent.x();
      float y = this.y();
      float width = 176.0F;
      return mouseX >= (double)x && mouseX <= (double)(x + width) && mouseY >= (double)y && mouseY < (double)(y + 22.0F);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      float x = this.parent.x();
      float y = this.y();
      float width = 176.0F;
      if (this.expanded && mouseY > (double)(y + 22.0F) && mouseY < (double)(y + this.height())) {
         for (SettingRow row : this.rows) {
            if (row.setting().isVisible() && row.mouseClicked(mouseX, mouseY, button)) {
               return true;
            }
         }
      }

      boolean onRow = mouseX >= (double)x && mouseX <= (double)(x + width) && mouseY >= (double)y && mouseY < (double)(y + 22.0F);
      if (!onRow) {
         return false;
      } else {
         boolean onChevron = mouseX >= (double)(x + width - 24.0F);
         if (this.rows.isEmpty() || button != 1 && (button != 0 || !onChevron)) {
            if (button == 0) {
               this.module.toggle();
               ConfigManager.save(SculkClient.modules());
            }

            return true;
         } else {
            if (!this.expanded) {
               this.parent.collapseOthers(this);
            }

            this.expanded = !this.expanded;
            return true;
         }
      }
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button) {
      if (!this.expanded) {
         return false;
      } else {
         for (SettingRow row : this.rows) {
            if (row.setting().isVisible()) {
               row.mouseDragged(mouseX, mouseY, button);
            }
         }

         return true;
      }
   }

   public void mouseReleased() {
      for (SettingRow row : this.rows) {
         row.mouseReleased();
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      for (SettingRow row : this.rows) {
         if (row.setting().isVisible() && row.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
         }
      }

      return false;
   }

   public boolean charTyped(char chr) {
      for (SettingRow row : this.rows) {
         if (row.setting().isVisible() && row.charTyped(chr)) {
            return true;
         }
      }

      return false;
   }

   public void resetAnimations() {
      this.expand.set(this.expanded ? this.settingsHeight() : 0.0F);
      this.hover.set(this.isMouseOver(0.0, 0.0) ? 1.0F : 0.0F);
   }
}
