package dev.sculk.gui;

import dev.sculk.SculkClient;
import dev.sculk.gui.zx.Zx;
import dev.sculk.gui.zx.ZxFriendsPanel;
import dev.sculk.module.modules.client.Fps25;
import dev.sculk.gui.zx.ZxBackdrop;
import dev.sculk.gui.zx.ZxPanel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Stand alone ZeltrixWare friends window. */
public final class FriendsScreen extends Screen {
   private ZxFriendsPanel panel;

   public FriendsScreen() {
      super(Text.literal("Friends"));
   }

   protected void init() {
      this.panel = new ZxFriendsPanel(((float)this.width - ZxPanel.WIDTH) / 2.0F, Math.max(20.0F, (float)this.height / 2.0F - 110.0F));
      Zx.search = "";
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      Zx.frame();
      ZxBackdrop.render(context, this.width, this.height, mouseX, 1.0F);
      this.panel.render(context, mouseX, mouseY);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      Zx.clearFocus();
      return this.panel.click((int)mouseX, (int)mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.panel.release();
      return super.mouseReleased(mouseX, mouseY, button);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
      this.panel.scrollBy((float)(amount * 15.0));
      return true;
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (Zx.focus != null && Zx.focus.key(keyCode)) {
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
      Fps25 module = SculkClient.get(Fps25.class);
      if (module != null && module.isEnabled()) {
         module.setEnabled(false);
      } else {
         super.close();
      }
   }

   public void removed() {
      Zx.clearFocus();
      Fps25 module = SculkClient.get(Fps25.class);
      if (module != null) {
         module.onScreenClosed();
      }
   }

   public boolean shouldPause() {
      return false;
   }
}
