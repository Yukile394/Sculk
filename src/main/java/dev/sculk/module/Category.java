package dev.sculk.module;

public enum Category {
   COMBAT("Combat"),
   MISC("Misc"),
   RENDER("Render"),
   CLIENT("Client");

   public final String label;

   private Category(String label) {
      this.label = label;
   }
}
