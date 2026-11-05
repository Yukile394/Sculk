package dev.sculk.gui.zx;

import dev.sculk.SculkClient;
import dev.sculk.render.Fonts;
import dev.sculk.render.Render2D;
import dev.sculk.render.Toasts;
import dev.sculk.sound.Sounds;
import dev.sculk.util.ConfigManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

/** ZeltrixWare config list: left click loads, right click overwrites, the field saves a new config. */
public final class ZxConfigPanel extends ZxPanel {
   private static final float ROW = 14.0F;
   private final Zx.TextField input;
   private List<String> configs = new ArrayList<>();

   public ZxConfigPanel(float x, float y) {
      super("Configs", x, y);
      this.input = new Zx.TextField(24, this::create);
      this.refresh();
   }

   public void refresh() {
      this.configs = ConfigManager.list();
   }

   private void create() {
      if (ConfigManager.saveNamed(this.input.value(), SculkClient.modules())) {
         Toasts.show("Saved " + ConfigManager.clean(this.input.value()));
         Sounds.bool();
      }

      this.input.clear();
      this.refresh();
   }

   @Override
   protected float contentHeight() {
      return 17.0F + (float)this.configs.size() * ROW + 4.0F;
   }

   @Override
   protected void renderContent(DrawContext c, int mx, int my, float top, boolean mouseOk) {
      float fieldW = this.w - 24.0F;
      this.input.render(c, this.x + 4.0F, top + 2.0F, fieldW, 11.0F, "New config", mouseOk && Render2D.hovered(mx, my, this.x + 4.0F, top + 2.0F, fieldW, 11.0));
      boolean plus = mouseOk && Render2D.hovered(mx, my, this.x + this.w - 18.0F, top + 2.0F, 14.0, 11.0);
      Render2D.rect(c, this.x + this.w - 18.0F, top + 2.0F, 14.0F, 11.0F, plus ? 0xFF22222B : 0xFF181820);
      Fonts.drawCentered(c, "+", this.x + this.w - 11.0F, top + 3.0F, 0xFFFFFFFF, true);
      float cursor = top + 17.0F;

      for (String name : this.configs) {
         Render2D.rect(c, this.x, cursor, this.w, ROW, mouseOk && Render2D.hovered(mx, my, this.x, cursor, this.w, ROW) ? 0xFF22222A : 0xFF14141A);
         Fonts.draw(c, Render2D.truncate(name, (int)this.w - 12), this.x + 6.0F, Zx.textY(cursor, ROW), 0xFFFFFFFF);
         cursor += ROW;
      }
   }

   @Override
   protected boolean contentClick(int mx, int my, int button, float top) {
      if (Render2D.hovered(mx, my, this.x + 4.0F, top + 2.0F, this.w - 24.0F, 11.0)) {
         this.input.focus();
         return true;
      } else if (Render2D.hovered(mx, my, this.x + this.w - 18.0F, top + 2.0F, 14.0, 11.0)) {
         this.create();
         return true;
      }

      float cursor = top + 17.0F;

      for (String name : this.configs) {
         if (Render2D.hovered(mx, my, this.x, cursor, this.w, ROW)) {
            if (button == 0) {
               ConfigManager.loadNamed(name, SculkClient.modules());
               Toasts.show("Loaded " + name);
            } else if (button == 1) {
               ConfigManager.saveNamed(name, SculkClient.modules());
               Toasts.show("Saved " + name);
            }

            Sounds.bool();
            return true;
         }

         cursor += ROW;
      }

      return false;
   }
}
