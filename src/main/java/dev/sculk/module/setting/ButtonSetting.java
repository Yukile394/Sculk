package dev.sculk.module.setting;

import java.util.function.Supplier;

public final class ButtonSetting extends Setting<String> {
   private final Runnable action;
   private boolean confirm = true;

   public ButtonSetting(String name, Runnable action) {
      super(name, name);
      this.action = action;
   }

   public ButtonSetting instant() {
      this.confirm = false;
      return this;
   }

   public boolean needsConfirm() {
      return this.confirm;
   }

   public void run() {
      this.action.run();
   }

   public ButtonSetting describe(String description) {
      super.describe(description);
      return this;
   }

   public ButtonSetting visibleWhen(Supplier<Boolean> visible) {
      super.visibleWhen(visible);
      return this;
   }

   @Override
   public String serialize() {
      return "";
   }

   @Override
   public void deserialize(String raw) {
   }
}
