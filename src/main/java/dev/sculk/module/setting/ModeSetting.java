package dev.sculk.module.setting;

import java.util.Locale;
import java.util.function.Supplier;

public final class ModeSetting<E extends Enum<E>> extends Setting<E> {
   private final E[] values;

   public ModeSetting(String name, E defaultValue) {
      super(name, defaultValue);
      this.values = defaultValue.getDeclaringClass().getEnumConstants();
   }

   public E[] values() {
      return this.values;
   }

   public ModeSetting<E> describe(String description) {
      super.describe(description);
      return this;
   }

   public ModeSetting<E> visibleWhen(Supplier<Boolean> visible) {
      super.visibleWhen(visible);
      return this;
   }

   public void cycle(int direction) {
      int index = this.value.ordinal();
      int size = this.values.length;
      this.value = this.values[((index + direction) % size + size) % size];
   }

   public int index() {
      return this.value.ordinal();
   }

   public void select(int index) {
      if (index >= 0 && index < this.values.length) {
         this.value = this.values[index];
      }
   }

   public boolean is(E other) {
      return this.value == other;
   }

   public String display(int index) {
      String raw = this.values[index].name();
      return raw.charAt(0) + raw.substring(1).toLowerCase(Locale.ROOT);
   }

   public String display() {
      return this.display(this.value.ordinal());
   }

   @Override
   public String serialize() {
      return this.value.name();
   }

   @Override
   public void deserialize(String raw) {
      for (E candidate : this.values) {
         if (candidate.name().equalsIgnoreCase(raw)) {
            this.value = candidate;
            return;
         }
      }
   }
}
