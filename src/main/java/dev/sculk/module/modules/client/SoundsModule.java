package dev.sculk.module.modules.client;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;

public final class SoundsModule extends Module {
   private final BooleanSetting moduleSounds = new BooleanSetting("Module Toggle", true).describe("enable.ogg / disable.ogg when a module is switched");
   private final BooleanSetting guiSounds = new BooleanSetting("Click GUI", true).describe("Hover, click, swipe and slider sounds in the GUI");
   private final NumberSetting volume = new NumberSetting("Volume", 60.0, 0.0, 100.0, 1.0);

   public SoundsModule() {
      super("Sounds", "ZeltrixWare enable / disable / click GUI sounds", Category.CLIENT);
      this.addSettings(new Setting[]{this.moduleSounds, this.guiSounds, this.volume});
      this.setEnabledSilently(true);
   }

   public boolean moduleSounds() {
      return this.moduleSounds.get();
   }

   public boolean guiSounds() {
      return this.guiSounds.get();
   }

   public float volume() {
      return this.volume.getFloat() / 100.0F;
   }

   @Override
   protected boolean playsToggleSound() {
      return false;
   }
}
