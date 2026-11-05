package dev.sculk.gui.zx;

import dev.sculk.render.Fonts;
import dev.sculk.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;

/** Shared state and helpers of the ZeltrixWare style click GUI. */
public final class Zx {
   public static float dt = 0.016F;
   public static String description = "";
   public static String search = "";
   public static Focusable focus;
   private static long last = System.nanoTime();

   private Zx() {
   }

   public interface Focusable {
      /** @return true when the key was consumed */
      boolean key(int code);

      void chr(char c);

      void blur();
   }

   public static void frame() {
      long now = System.nanoTime();
      dt = Math.min((float)(now - last) / 1.0E9F, 0.1F);
      last = now;
   }

   public static float fast(float current, float target, float speed) {
      float value = current + (target - current) * (1.0F - (float)Math.exp((double)(-speed * dt)));
      return Math.abs(target - value) < 0.01F ? target : value;
   }

   public static void setFocus(Focusable next) {
      if (focus != null && focus != next) {
         focus.blur();
      }

      focus = next;
   }

   public static void clearFocus() {
      if (focus != null) {
         focus.blur();
         focus = null;
      }
   }

   public static String keyName(int key) {
      if (key < 0) {
         return "none";
      }

      String name = InputUtil.fromKeyCode(key, 0).getLocalizedText().getString();
      return name.replace("Left ", "L").replace("Right ", "R").replace("Control", "Ctrl");
   }

   public static float textY(float y, float height) {
      return y + (height - 8.0F) / 2.0F;
   }

   /** Single line text input in the ZeltrixWare look. */
   public static final class TextField implements Focusable {
      public final StringBuilder text = new StringBuilder();
      public boolean focused;
      private final int max;
      private final Runnable onEnter;

      public TextField(int max, Runnable onEnter) {
         this.max = max;
         this.onEnter = onEnter;
      }

      public void render(DrawContext context, float x, float y, float w, float h, String placeholder, boolean hovered) {
         Render2D.rect(context, x, y, w, h, hovered || this.focused ? 0xFF22222B : 0xFF181820);
         Render2D.rect(context, x, y + h - 1.0F, w, 1.0F, this.focused ? dev.sculk.render.Theme.ACCENT : 0x22FFFFFF);
         boolean blink = System.currentTimeMillis() / 400L % 2L == 0L;
         String shown = this.text.length() == 0 && !this.focused ? placeholder : this.text + (this.focused && blink ? "_" : "");
         int color = this.text.length() == 0 && !this.focused ? 0xFF7A7A86 : 0xFFFFFFFF;
         Fonts.draw(context, Render2D.truncate(shown, (int)w - 6), x + 3.0F, textY(y, h), color);
      }

      public void focus() {
         this.focused = true;
         setFocus(this);
      }

      public String value() {
         return this.text.toString().trim();
      }

      public void clear() {
         this.text.setLength(0);
      }

      @Override
      public boolean key(int code) {
         if (code == 259) {
            if (this.text.length() > 0) {
               this.text.setLength(this.text.length() - 1);
            }

            return true;
         } else if (code == 257 || code == 335) {
            this.onEnter.run();
            return true;
         } else if (code == 256) {
            this.blur();
            focus = null;
            return true;
         } else {
            return false;
         }
      }

      @Override
      public void chr(char c) {
         if (this.text.length() < this.max && !Character.isISOControl(c)) {
            this.text.append(c);
         }
      }

      @Override
      public void blur() {
         this.focused = false;
      }
   }
}
