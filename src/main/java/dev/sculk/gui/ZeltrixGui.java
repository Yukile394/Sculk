package dev.sculk.gui;

import dev.sculk.SculkClient;
import dev.sculk.gui.zx.Zx;
import dev.sculk.gui.zx.ZxBackdrop;
import dev.sculk.gui.zx.ZxCategoryPanel;
import dev.sculk.gui.zx.ZxConfigPanel;
import dev.sculk.gui.zx.ZxFriendsPanel;
import dev.sculk.gui.zx.ZxPanel;
import dev.sculk.module.Category;
import dev.sculk.module.modules.client.Fps15;
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

/** Modern click GUI: night backdrop with falling snow, rounded draggable cards, search pill, colour swatches. */
public final class ZeltrixGui extends Screen {
   private static final List<ZxPanel> PANELS = new ArrayList<>();
   private static final int[] SWATCHES = new int[]{0xF519141E, 0xF58750FF, 0xF5FF5050, 0xF550FF50, 0xF55050FF};
   private static final float SWATCH_STEP = 20.0F;
   private static final float SEARCH_W = 170.0F;
   private static final float SEARCH_H = 15.0F;
   private static final float SEARCH_Y = 7.0F;
   private static final float PANEL_Y = 32.0F;
   private static final String CREDIT = "Made By Hikmet";
   private static boolean built;
   private static ZxConfigPanel configs;
   private final Zx.TextField searchField = new Zx.TextField(24, () -> {
   });
   private float fade;

   public ZeltrixGui() {
      super(Text.literal("sculk"));
      this.searchField.radius = SEARCH_H / 2.0F;
   }

   private static void build(int screenWidth) {
      PANELS.clear();
      int count = 2;

      for (Category category : Category.values()) {
         if (!SculkClient.byCategory(category).isEmpty()) {
            count++;
         }
      }

      float step = ZxPanel.WIDTH + ZxPanel.GAP;
      float x = Math.max(6.0F, ((float)screenWidth - ((float)count * step - ZxPanel.GAP)) / 2.0F);

      for (Category category : Category.values()) {
         if (!SculkClient.byCategory(category).isEmpty()) {
            PANELS.add(new ZxCategoryPanel(category, SculkClient.byCategory(category), x, PANEL_Y));
            x += step;
         }
      }

      PANELS.add(new ZxFriendsPanel(x, PANEL_Y));
      x += step;
      configs = new ZxConfigPanel(x, PANEL_Y);
      PANELS.add(configs);
      built = true;
   }

   protected void init() {
      if (!built) {
         build(this.width);
      }

      configs.refresh();
      this.fade = 0.0F;
      Zx.search = "";
      Zx.clearFocus();
   }

   // ---- swatch layout, shared by render and click so both always agree ----

   private float swatchLabelWidth() {
      return (float)Fonts.width("GUI Color");
   }

   private float swatchesTotalWidth() {
      return this.swatchLabelWidth() + 8.0F + (float)SWATCHES.length * SWATCH_STEP - 5.0F;
   }

   private float swatchesStart() {
      return ((float)this.width - this.swatchesTotalWidth()) / 2.0F;
   }

   private float swatchX(int index) {
      return this.swatchesStart() + this.swatchLabelWidth() + 8.0F + (float)index * SWATCH_STEP;
   }

   private float swatchY() {
      return (float)this.height - 36.0F;
   }

   private float searchX() {
      return ((float)this.width - SEARCH_W) / 2.0F;
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

      ZxBackdrop.render(context, this.width, this.height, mouseX, this.fade);
      Zx.description = "";
      Zx.search = this.searchField.value().toLowerCase();

      for (ZxPanel panel : PANELS) {
         panel.render(context, mouseX, mouseY);
      }

      this.renderTopBar(context, mouseX, mouseY);
      this.renderBottomBar(context, mouseX, mouseY);

      if (module != null && module.descriptions() && !Zx.description.isEmpty()) {
         float tw = (float)Fonts.width(Zx.description);
         float tx = (float)(mouseX + 8);
         float ty = (float)(mouseY + 6);
         Render2D.roundedRect(context, tx, ty, tw + 10.0F, 14.0F, 4.0F, 0xEE12131A);
         Render2D.roundedOutline(context, tx, ty, tw + 10.0F, 14.0F, 4.0F, 1.0F, Theme.scaleAlpha(Theme.ACCENT, 0.55F));
         Fonts.draw(context, Zx.description, tx + 5.0F, Zx.textY(ty, 14.0F), Theme.GUI_TEXT);
      }

      Toasts.render(context);
   }

   private void renderTopBar(DrawContext context, int mouseX, int mouseY) {
      Fonts.drawScaled(context, "Sculk", 12.0F, 6.0F, 1.5F, Theme.ACCENT, true);
      Fonts.draw(context, "v" + SculkClient.VERSION, 12.0F + (float)Fonts.width("Sculk", true) * 1.5F + 5.0F, 13.0F, Theme.GUI_TEXT_DIM);

      float sx = this.searchX();
      this.searchField.render(context, sx, SEARCH_Y, SEARCH_W, SEARCH_H, "Search modules...", Render2D.hovered(mouseX, mouseY, sx, SEARCH_Y, SEARCH_W, SEARCH_H));

      String hint = "LMB toggle   RMB settings   MMB bind";
      Fonts.draw(context, hint, (float)this.width - 12.0F - (float)Fonts.width(hint), 11.0F, Theme.scaleAlpha(Theme.GUI_TEXT_DIM, this.fade));
   }

   private void renderBottomBar(DrawContext context, int mouseX, int mouseY) {
      float cy = this.swatchY();
      float start = this.swatchesStart();
      Render2D.roundedRect(context, start - 9.0F, cy - 4.0F, this.swatchesTotalWidth() + 18.0F, 23.0F, 11.5F, 0xB410121A);
      Render2D.roundedOutline(context, start - 9.0F, cy - 4.0F, this.swatchesTotalWidth() + 18.0F, 23.0F, 11.5F, 1.0F, Theme.GUI_LINE);
      Fonts.draw(context, "GUI Color", start, cy + 3.5F, Theme.GUI_TEXT_DIM);

      for (int i = 0; i < SWATCHES.length; i++) {
         float x = this.swatchX(i);
         boolean selected = (Theme.BODY & 0xFFFFFF) == (SWATCHES[i] & 0xFFFFFF);
         Render2D.circle(context, x + 7.5F, cy + 7.5F, 7.5F, SWATCHES[i] | 0xFF000000);
         if (Render2D.hovered(mouseX, mouseY, x, cy, 15.0, 15.0)) {
            Render2D.circle(context, x + 7.5F, cy + 7.5F, 7.5F, 0x40FFFFFF);
         }

         if (selected) {
            Render2D.roundedOutline(context, x - 1.5F, cy - 1.5F, 18.0F, 18.0F, 9.0F, 1.0F, Theme.ACCENT);
         }
      }

      float creditX = ((float)this.width - (float)Fonts.width(CREDIT, true)) / 2.0F;
      Fonts.draw(context, CREDIT, creditX, (float)this.height - 13.0F, Theme.ACCENT, true);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      int mx = (int)mouseX;
      int my = (int)mouseY;
      Zx.clearFocus();
      float cy = this.swatchY();

      for (int i = 0; i < SWATCHES.length; i++) {
         if (Render2D.hovered(mouseX, mouseY, this.swatchX(i), cy, 15.0, 15.0)) {
            Theme.BODY = SWATCHES[i];
            return true;
         }
      }

      if (Render2D.hovered(mouseX, mouseY, this.searchX(), SEARCH_Y, SEARCH_W, SEARCH_H)) {
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
