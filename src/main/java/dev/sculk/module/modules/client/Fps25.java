package dev.sculk.module.modules.client;

import dev.sculk.gui.FriendsScreen;
import dev.sculk.module.Category;
import dev.sculk.module.Module;

public final class Fps25 extends Module {
   private boolean pending;
   private boolean closing;

   public Fps25() {
      super("Friends", "Arkadas listesini ac \u2014 secim bu isimlere vurmaz", Category.CLIENT);
   }

   @Override
   public void onEnable() {
      if (this.mc.currentScreen != null && !(this.mc.currentScreen instanceof FriendsScreen)) {
         this.pending = true;
         this.mc.setScreen(null);
      } else {
         this.mc.setScreen(new FriendsScreen());
      }
   }

   @Override
   public void onBufferUpdate() {
      if (this.pending) {
         this.pending = false;
         if (this.mc.currentScreen == null) {
            this.mc.setScreen(new FriendsScreen());
         }
      }
   }

   @Override
   public void onDisable() {
      if (this.mc.currentScreen instanceof FriendsScreen) {
         this.closing = true;
         this.mc.setScreen(null);
         this.closing = false;
      }
   }

   @Override
   protected boolean playsToggleSound() {
      return false;
   }

   @Override
   protected boolean persistsEnabled() {
      return false;
   }

   public void onScreenClosed() {
      if (!this.closing && this.isEnabled()) {
         this.setEnabled(false);
      }
   }
}
