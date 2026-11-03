package dev.sculk.render;

public final class Theme {
   private static final int PANEL_BASE = 657935;
   private static final int HEADER_BASE = 1052694;
   public static int PANEL = -234223089;
   public static int HEADER = -15724522;
   public static final int BORDER = -14474452;
   public static final int ROW_HOVER = 352321535;
   public static final int TRACK = -15000797;
   public static final int TEXT = -1447440;
   public static final int TEXT_DIM = -8750457;
   public static int ACCENT = -7635969;
   public static int ACCENT_DIM = 1435204607;

   private Theme() {
   }

   public static void apply(int hue, float saturation, float brightness, float opacity) {
      int rgb = hsv((float)(hue % 360) / 360.0F, clamp01(saturation / 100.0F), clamp01(brightness / 100.0F));
      ACCENT = 0xFF000000 | rgb;
      ACCENT_DIM = 1426063360 | rgb;
      int alpha = Math.round(clamp01(opacity / 100.0F) * 255.0F);
      PANEL = alpha << 24 | 657935;
      HEADER = Math.min(255, alpha + 13) << 24 | 1052694;
   }

   public static void reset() {
      PANEL = -234223089;
      HEADER = -15724522;
      ACCENT = -7635969;
      ACCENT_DIM = 1435204607;
   }

   private static float clamp01(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }

   private static int hsv(float h, float s, float v) {
      float scaled = h * 6.0F;
      int sector = (int)scaled % 6;
      float fraction = scaled - (float)Math.floor((double)scaled);
      int p = Math.round(v * (1.0F - s) * 255.0F);
      int q = Math.round(v * (1.0F - fraction * s) * 255.0F);
      int t = Math.round(v * (1.0F - (1.0F - fraction) * s) * 255.0F);
      int vv = Math.round(v * 255.0F);

      return switch (sector) {
         case 0 -> vv << 16 | t << 8 | p;
         case 1 -> q << 16 | vv << 8 | p;
         case 2 -> p << 16 | vv << 8 | t;
         case 3 -> p << 16 | q << 8 | vv;
         case 4 -> t << 16 | p << 8 | vv;
         default -> vv << 16 | p << 8 | q;
      };
   }

   public static int withAlpha(int color, int alpha) {
      return (alpha & 0xFF) << 24 | color & 16777215;
   }

   public static int scaleAlpha(int color, float factor) {
      float clamped = Math.max(0.0F, Math.min(1.0F, factor));
      int alpha = Math.round((float)(color >> 24 & 0xFF) * clamped);
      return color & 16777215 | alpha << 24;
   }

   public static int mix(int a, int b, float t) {
      int aa = (int)((float)(a >> 24 & 0xFF) * (1.0F - t) + (float)(b >> 24 & 0xFF) * t);
      int ar = (int)((float)(a >> 16 & 0xFF) * (1.0F - t) + (float)(b >> 16 & 0xFF) * t);
      int ag = (int)((float)(a >> 8 & 0xFF) * (1.0F - t) + (float)(b >> 8 & 0xFF) * t);
      int ab = (int)((float)(a & 0xFF) * (1.0F - t) + (float)(b & 0xFF) * t);
      return aa << 24 | ar << 16 | ag << 8 | ab;
   }
}
