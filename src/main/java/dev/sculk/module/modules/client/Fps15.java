package dev.sculk.module.modules.client;

import dev.sculk.gui.ClickGuiScreen;
import dev.sculk.gui.PanelScreen;
import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.Setting;
import net.minecraft.client.gui.screen.Screen;

public final class Fps15 extends Module {
   private final ModeSetting<Fps15.Layout> layout = new ModeSetting<>("Layout", Fps15.Layout.Panel)
      .describe("Panel = smoothhud-style frames, Classic = four category panels");
   private boolean closing;

   public Fps15() {
      super("ClickGUI", "Opens the sculk interface", Category.CLIENT);
      this.addSettings(new Setting[]{this.layout});
      this.key(-1);
   }

   @Override
   public void onEnable() {
      if (!(this.mc.currentScreen instanceof PanelScreen) && !(this.mc.currentScreen instanceof ClickGuiScreen)) {
         this.mc.setScreen((Screen)(this.layout.is(Fps15.Layout.Classic) ? new ClickGuiScreen() : new PanelScreen()));
      }
   }

   @Override
   public void onDisable() {
      if (this.mc.currentScreen instanceof PanelScreen || this.mc.currentScreen instanceof ClickGuiScreen) {
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

   public static enum Layout {
      Panel,
      Classic;
   }
}
