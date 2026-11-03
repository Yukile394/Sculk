package dev.sculk.module.setting;

import java.util.Locale;
import java.util.function.Supplier;

public final class NumberSetting extends Setting<Double> {
   private final double min;
   private final double max;
   private final double step;
   private final int decimals;

   public NumberSetting(String name, double defaultValue, double min, double max, double step) {
      super(name, defaultValue);
      this.min = min;
      this.max = max;
      this.step = step;
      this.decimals = step >= 1.0 ? 0 : (step >= 0.1 ? 1 : 2);
   }

   public double getMin() {
      return this.min;
   }

   public NumberSetting describe(String description) {
      super.describe(description);
      return this;
   }

   public NumberSetting visibleWhen(Supplier<Boolean> visible) {
      super.visibleWhen(visible);
      return this;
   }

   public double getMax() {
      return this.max;
   }

   public float getFloat() {
      return this.value.floatValue();
   }

   public int getInt() {
      return this.value.intValue();
   }

   public String display() {
      return this.decimals == 0 ? Integer.toString((int)Math.round(this.value)) : String.format(Locale.ROOT, "%." + this.decimals + "f", this.value);
   }

   public void setValue(Double raw) {
      double clamped = Math.max(this.min, Math.min(this.max, raw));
      if (this.step > 0.0) {
         clamped = this.min + (double)Math.round((clamped - this.min) / this.step) * this.step;
         clamped = Math.max(this.min, Math.min(this.max, clamped));
      }

      this.value = clamped;
   }

   public void setFromFraction(double fraction) {
      this.setValue(this.min + (this.max - this.min) * Math.max(0.0, Math.min(1.0, fraction)));
   }

   public double fraction() {
      return this.max - this.min == 0.0 ? 0.0 : (this.value - this.min) / (this.max - this.min);
   }

   @Override
   public String serialize() {
      return Double.toString(this.value);
   }

   @Override
   public void deserialize(String raw) {
      try {
         this.setValue(Double.parseDouble(raw));
      } catch (NumberFormatException var3) {
      }
   }
}
