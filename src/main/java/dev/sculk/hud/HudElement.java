package dev.sculk.hud;

public enum HudElement {
   WATERMARK("Watermark", 0.01F, 0.02F),
   FPS("FPS", 0.01F, 0.065F),
   COORDS("Coordinates", 0.01F, 0.11F),
   CPS("CPS", 0.01F, 0.155F),
   PING("Ping", 0.01F, 0.2F),
   CLOCK("Clock", 0.01F, 0.245F),
   MODULE_LIST("Module List", 0.72F, 0.02F);

   private final String label;
   private final float defaultX;
   private final float defaultY;

   private HudElement(String label, float defaultX, float defaultY) {
      this.label = label;
      this.defaultX = defaultX;
      this.defaultY = defaultY;
   }

   public String label() {
      return this.label;
   }

   public float defaultX() {
      return this.defaultX;
   }

   public float defaultY() {
      return this.defaultY;
   }
}
