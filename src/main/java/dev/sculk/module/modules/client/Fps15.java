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
   private final BooleanSetting fpsBoost = new BooleanSetting("FPS Boost", true)
      .describe("Skips the blur and uses lighter snow so the GUI stays smooth on weak devices");
   private final BooleanSetting snow = new BooleanSetting("Snow", true).describe("Falling snow behind the GUI");
   private boolean closing;

   public Fps15() {
      super("ClickGUI", "Opens the sculk interface", Category.CLIENT);
      this.addSettings(new Setting[]{this.fpsBoost, this.blur, this.snow, this.descriptions});
      this.key(-1);
   }

   public boolean blur() {
      return this.blur.get();
   }

   public boolean fpsBoost() {
      return this.fpsBoost.get();
   }

   public boolean snow() {
      return this.snow.get();
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
