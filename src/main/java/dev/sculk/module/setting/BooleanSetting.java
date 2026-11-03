package dev.sculk.module.setting;

import java.util.function.Supplier;

public final class BooleanSetting extends Setting<Boolean> {
   public BooleanSetting(String name, boolean defaultValue) {
      super(name, defaultValue);
   }

   public boolean get() {
      return this.value;
   }

   public void toggle() {
      this.value = !this.value;
   }

   public BooleanSetting describe(String description) {
      super.describe(description);
      return this;
   }

   public BooleanSetting visibleWhen(Supplier<Boolean> visible) {
      super.visibleWhen(visible);
      return this;
   }

   @Override
   public String serialize() {
      return Boolean.toString(this.value);
   }

   @Override
   public void deserialize(String raw) {
      this.value = Boolean.parseBoolean(raw);
   }
}
