package dev.sculk.gui;

import dev.sculk.SculkClient;
import dev.sculk.gui.zx.Zx;
import dev.sculk.gui.zx.ZxCategoryPanel;
import dev.sculk.gui.zx.ZxConfigPanel;
import dev.sculk.gui.zx.ZxFriendsPanel;
import dev.sculk.gui.zx.ZxPanel;
import dev.sculk.module.Category;
import dev.sculk.module.modules.client.Fps15;
import dev.sculk.module.modules.client.Fps24;
import dev.sculk.render.Fonts;
import dev.sculk.render.Render2D;
import dev.sculk.render.Theme;
import dev.sculk.render.Toasts;
import dev.sculk.util.ConfigManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** ZeltrixWare style click GUI: draggable category windows, search, friends, configs, gui colour swatches. */
public final class ZeltrixGui extends Screen {
   private static final List<ZxPanel> PANELS = new ArrayList<>();
   private static final int[] SWATCHES = new int[]{0xF519141E, 0xF58750FF, 0xF5FF5050, 0xF550FF50, 0xF55050FF};
   private static boolean built;
   private static ZxConfigPanel configs;
   private final Zx.TextField searchField = new Zx.TextField(24, () -> {
   });
   private float fade;

   public ZeltrixGui() {
      super(Text.literal("sculk"));
   }

   private static void build() {
      PANELS.clear();
      float x = 10.0F;

      for (Category category : Category.values()) {
         if (!SculkClient.byCategory(category).isEmpty()) {
            PANELS.add(new ZxCategoryPanel(category, SculkClient.byCategory(category), x, 24.0F));
            x += ZxPanel.WIDTH + 1.0F;
         }
      }

      PANELS.add(new ZxFriendsPanel(x, 24.0F));
      x += ZxPanel.WIDTH + 1.0F;
      configs = new ZxConfigPanel(x, 24.0F);
      PANELS.add(configs);
      built = true;
   }

   protected void init() {
      if (!built) {
         build();
      }

      Fps24 colors = SculkClient.get(Fps24.class);
      Fonts.custom = colors != null && colors.sfFont();
      configs.refresh();
      this.fade = 0.0F;
      Zx.search = "";
      Zx.clearFocus();
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      Zx.frame();
      Fps15 module = SculkClient.get(Fps15.class);
      this.fade = Zx.fast(this.fade, 1.0F, 14.0F);
      if (module != null && module.blur()) {
         this.applyBlur(delta);
      }

      context.fill(0, 0, this.width, this.height, Theme.scaleAlpha(0xA0000000, this.fade));
      Zx.description = "";
      Zx.search = this.searchField.value().toLowerCase();

      for (ZxPanel panel : PANELS) {
         panel.render(context, mouseX, mouseY);
      }

      Fonts.draw(context, "Sculk", 10.0F, 9.0F, Theme.ACCENT, true);

      float sw = 120.0F;
      float sx = ((float)this.width - sw) / 2.0F;
      this.searchField.render(context, sx, 6.0F, sw, 12.0F, "Search...", Render2D.hovered(mouseX, mouseY, sx, 6.0, sw, 12.0));

      float cx = (float)this.width / 2.0F - (float)SWATCHES.length * 20.0F / 2.0F;
      float cy = (float)this.height - 30.0F;
      Fonts.draw(context, "GUI Color", cx - 50.0F, cy + 4.0F, 0xFFFFFFFF);

      for (int i = 0; i < SWATCHES.length; i++) {
         float x = cx + (float)(i * 20);
         Render2D.roundedRect(context, x, cy, 15.0F, 15.0F, 7.5F, SWATCHES[i] | 0xFF000000);
         if (Render2D.hovered(mouseX, mouseY, x, cy, 15.0, 15.0)) {
            Render2D.roundedRect(context, x, cy, 15.0F, 15.0F, 7.5F, 0x64FFFFFF);
         }
      }

      if (module != null && module.descriptions() && !Zx.description.isEmpty()) {
         float tw = (float)Fonts.width(Zx.description);
         Render2D.roundedRect(context, (float)(mouseX + 7), (float)(mouseY + 5), tw + 8.0F, 12.0F, 3.0F, 0xE6101014);
         Render2D.roundedOutline(context, (float)(mouseX + 7), (float)(mouseY + 5), tw + 8.0F, 12.0F, 3.0F, 1.0F, 0x33FFFFFF);
         Fonts.draw(context, Zx.description, (float)(mouseX + 11), (float)(mouseY + 8), Theme.ACCENT);
      }

      Toasts.render(context);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      int mx = (int)mouseX;
      int my = (int)mouseY;
      Zx.clearFocus();
      float cx = (float)this.width / 2.0F - (float)SWATCHES.length * 20.0F / 2.0F;
      float cy = (float)this.height - 30.0F;

      for (int i = 0; i < SWATCHES.length; i++) {
         if (Render2D.hovered(mouseX, mouseY, cx + (float)(i * 20), cy, 15.0, 15.0)) {
            Theme.BODY = SWATCHES[i];
            return true;
         }
      }

      float sw = 120.0F;
      float sx = ((float)this.width - sw) / 2.0F;
      if (Render2D.hovered(mouseX, mouseY, sx, 6.0, sw, 12.0)) {
         this.searchField.focus();
         return true;
      }

      for (int i = PANELS.size() - 1; i >= 0; i--) {
         ZxPanel panel = PANELS.get(i);
         if (panel.click(mx, my, button)) {
            PANELS.remove(i);
            PANELS.add(panel);
            return true;
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      for (ZxPanel panel : PANELS) {
         panel.release();
      }

      return super.mouseReleased(mouseX, mouseY, button);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
      for (int i = PANELS.size() - 1; i >= 0; i--) {
         ZxPanel panel = PANELS.get(i);
         if (panel.over((int)mouseX, (int)mouseY)) {
            panel.scrollBy((float)(amount * 15.0));
            return true;
         }
      }

      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (Zx.focus != null && Zx.focus.key(keyCode)) {
         return true;
      } else if (keyCode == 70 && (modifiers & 2) != 0) {
         this.searchField.focus();
         return true;
      } else if (keyCode == 256) {
         this.close();
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public boolean charTyped(char chr, int modifiers) {
      if (Zx.focus != null) {
         Zx.focus.chr(chr);
         return true;
      } else {
         return super.charTyped(chr, modifiers);
      }
   }

   public void close() {
      Fps15 module = SculkClient.get(Fps15.class);
      if (module != null && module.isEnabled()) {
         module.setEnabled(false);
      } else {
         super.close();
      }
   }

   public void removed() {
      Zx.clearFocus();
      ConfigManager.save(SculkClient.modules());
      Fps15 module = SculkClient.get(Fps15.class);
      if (module != null) {
         module.onScreenClosed();
      }
   }

   public boolean shouldPause() {
      return false;
   }
}
