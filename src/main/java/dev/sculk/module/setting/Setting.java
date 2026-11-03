package dev.sculk.module.setting;

import java.util.function.Supplier;

public abstract class Setting<T> {
   private String name;
   private String description = "";
   private Supplier<Boolean> visible = () -> true;
   protected T value;

   protected Setting(String name, T defaultValue) {
      this.name = name;
      this.value = defaultValue;
   }

   public String getName() {
      return this.name;
   }

   public String getDescription() {
      return this.description;
   }

   public Setting<T> setName(String name) {
      this.name = name;
      return this;
   }

   public Setting<T> setDescription(String description) {
      this.description = description;
      return this;
   }

   public Setting<T> describe(String description) {
      this.description = description;
      return this;
   }

   public Setting<T> visibleWhen(Supplier<Boolean> visible) {
      this.visible = visible;
      return this;
   }

   public boolean isVisible() {
      return this.visible.get();
   }

   public T getValue() {
      return this.value;
   }

   public void setValue(T value) {
      this.value = value;
   }

   public abstract String serialize();

   public abstract void deserialize(String var1);
}
