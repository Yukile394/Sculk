package dev.sculk.module.setting;

import java.util.function.Supplier;

public final class StringSetting extends Setting<String> {
   private final int maxLength;

   public StringSetting(String name, String defaultValue, int maxLength) {
      super(name, defaultValue);
      this.maxLength = maxLength;
   }

   public StringSetting describe(String description) {
      super.describe(description);
      return this;
   }

   public StringSetting visibleWhen(Supplier<Boolean> visible) {
      super.visibleWhen(visible);
      return this;
   }

   public void append(char c) {
      if (this.value.length() < this.maxLength && !Character.isISOControl(c)) {
         this.value = this.value + c;
      }
   }

   public void backspace() {
      if (!this.value.isEmpty()) {
         this.value = this.value.substring(0, this.value.length() - 1);
      }
   }

   @Override
   public String serialize() {
      return this.value;
   }

   @Override
   public void deserialize(String raw) {
      this.value = raw.length() > this.maxLength ? raw.substring(0, this.maxLength) : raw;
   }
}
