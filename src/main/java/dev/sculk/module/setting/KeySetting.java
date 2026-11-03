package dev.sculk.module.setting;

import java.util.function.Supplier;
import net.minecraft.client.util.InputUtil;

public final class KeySetting extends Setting<Integer> {
   private boolean listening;

   public KeySetting(String name, int defaultKey) {
      super(name, defaultKey);
   }

   public int getKey() {
      return this.value;
   }

   public KeySetting describe(String description) {
      super.describe(description);
      return this;
   }

   public KeySetting visibleWhen(Supplier<Boolean> visible) {
      super.visibleWhen(visible);
      return this;
   }

   public void setKey(int key) {
      this.value = key;
      this.listening = false;
   }

   public boolean isListening() {
      return this.listening;
   }

   public void setListening(boolean listening) {
      this.listening = listening;
   }

   public String display() {
      if (this.listening) {
         return "...";
      } else {
         return this.value != -1 && this.value >= 0 ? InputUtil.fromKeyCode(this.value, 0).getLocalizedText().getString() : "none";
      }
   }

   @Override
   public String serialize() {
      return Integer.toString(this.value);
   }

   @Override
   public void deserialize(String raw) {
      try {
         this.value = Integer.parseInt(raw);
      } catch (NumberFormatException var3) {
      }
   }
}
