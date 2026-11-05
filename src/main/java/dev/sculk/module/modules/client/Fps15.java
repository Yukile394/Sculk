package dev.sculk.module.modules.client;

import dev.sculk.gui.HudEditorScreen;
import dev.sculk.gui.ZeltrixGui;
import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.Setting;

public final class Fps15 extends Module {
   private final BooleanSetting blur = new BooleanSetting("Blur", true).describe("Blurred game behind the GUI");
   private final BooleanSetting descriptions = new BooleanSetting("Descriptions", true).describe("Tooltip with the module / setting description");
   private boolean closing;

   public Fps15() {
      super("ClickGUI", "Opens the sculk interface", Category.CLIENT);
      this.addSettings(new Setting[]{this.blur, this.descriptions});
      this.key(-1);
   }

   public boolean blur() {
      return this.blur.get();
   }

   public boolean descriptions() {
      return this.descriptions.get();
   }

   @Override
   protected boolean playsToggleSound() {
      return false;
   }

   @Override
   protected boolean persistsEnabled() {
      return false;
   }

   @Override
   public void onEnable() {
      if (!(this.mc.currentScreen instanceof ZeltrixGui) && !(this.mc.currentScreen instanceof HudEditorScreen)) {
         this.mc.setScreen(new ZeltrixGui());
      }
   }

   @Override
   public void onDisable() {
      if (this.mc.currentScreen instanceof ZeltrixGui) {
         this.closing = true;
         this.mc.setScreen(null);
         this.closing = false;
      }
   }

   public void onScreenClosed() {
      if (!this.closing && this.isEnabled()) {
         this.setEnabled(false);
      }
   }
}
