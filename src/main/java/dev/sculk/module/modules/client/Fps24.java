package dev.sculk.module.modules.client;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.ButtonSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.render.Theme;

public final class Fps24 extends Module {
   private static final double DEFAULT_HUE = 248.0;
   private static final double DEFAULT_SATURATION = 52.0;
   private static final double DEFAULT_BRIGHTNESS = 100.0;
   private static final double DEFAULT_OPACITY = 95.0;
   private final BooleanSetting rainbow = new BooleanSetting("Rainbow", false).describe("Cycles the accent hue");
   private final NumberSetting hue = new NumberSetting("Hue", 248.0, 0.0, 360.0, 1.0).visibleWhen(() -> !this.rainbow.get());
   private final NumberSetting saturation = new NumberSetting("Saturation", 52.0, 0.0, 100.0, 1.0);
   private final NumberSetting brightness = new NumberSetting("Brightness", 100.0, 20.0, 100.0, 1.0);
   private final NumberSetting opacity = new NumberSetting("Panel Opacity", 95.0, 20.0, 100.0, 1.0);
   private final BooleanSetting sfFont = new BooleanSetting("SF Font", false).describe("ZeltrixWare SF font in the GUI (reopen the GUI)");
   private final ButtonSetting reset = new ButtonSetting("Reset colours", this::resetDefaults);

   public Fps24() {
      super("GUI Colors", "Changes the click GUI palette", Category.CLIENT);
      this.addSettings(new Setting[]{this.rainbow, this.hue, this.saturation, this.brightness, this.opacity, this.sfFont, this.reset});
   }

   public boolean sfFont() {
      return this.sfFont.get();
   }

   @Override
   public void onEnable() {
      this.apply();
   }

   @Override
   public void onDisable() {
      Theme.reset();
   }

   @Override
   public void onTick() {
      this.apply();
   }

   private void apply() {
      int currentHue = this.rainbow.get() ? (int)(System.currentTimeMillis() / 12L % 360L) : this.hue.getInt();
      Theme.apply(currentHue, this.saturation.getFloat(), this.brightness.getFloat(), this.opacity.getFloat());
   }

   private void resetDefaults() {
      this.rainbow.setValue(Boolean.valueOf(false));
      this.hue.setValue(248.0);
      this.saturation.setValue(52.0);
      this.brightness.setValue(100.0);
      this.opacity.setValue(95.0);
      Theme.reset();
   }
}
