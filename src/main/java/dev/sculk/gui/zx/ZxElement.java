package dev.sculk.gui.zx;

import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.ButtonSetting;
import dev.sculk.module.setting.KeySetting;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.module.setting.StringSetting;
import dev.sculk.render.Fonts;
import dev.sculk.render.Render2D;
import dev.sculk.render.Theme;
import dev.sculk.sound.Sounds;
import net.minecraft.client.gui.DrawContext;

/** One setting row inside an opened module (ZeltrixWare element look). */
public abstract class ZxElement {
   public final Setting<?> setting;
   protected float x;
   protected float y;
   protected float w;
   protected float h = 13.0F;

   protected ZxElement(Setting<?> setting) {
      this.setting = setting;
   }

   public static ZxElement create(Setting<?> setting) {
      if (setting instanceof BooleanSetting b) {
         return new Bool(b);
      } else if (setting instanceof NumberSetting n) {
         return new Num(n);
      } else if (setting instanceof ModeSetting<?> m) {
         return new Mode(m);
      } else if (setting instanceof KeySetting k) {
         return new Key(k);
      } else if (setting instanceof StringSetting s) {
         return new Str(s);
      } else if (setting instanceof ButtonSetting btn) {
         return new Btn(btn);
      }

      return null;
   }

   public boolean visible() {
      return this.setting.isVisible();
   }

   public abstract float height();

   public void place(float x, float y, float w) {
      this.x = x;
      this.y = y;
      this.w = w;
   }

   public abstract void render(DrawContext context, int mouseX, int mouseY, boolean mouseOk);

   public boolean click(int mouseX, int mouseY, int button) {
      return false;
   }

   public void release() {
   }

   protected boolean hover(int mouseX, int mouseY, boolean mouseOk) {
      boolean hovered = mouseOk && Render2D.hovered(mouseX, mouseY, this.x, this.y, this.w, this.height());
      if (hovered && !this.setting.getDescription().isEmpty()) {
         Zx.description = this.setting.getDescription();
      }

      return hovered;
   }

   protected void label(DrawContext context, String text, float dy) {
      Fonts.draw(context, text, this.x + 6.0F, this.y + dy, 0xFFFFFFFF);
   }

   // ───────────────────────────── boolean ─────────────────────────────

   public static final class Bool extends ZxElement {
      private final BooleanSetting s;
      private float anim;

      Bool(BooleanSetting s) {
         super(s);
         this.s = s;
         this.anim = s.get() ? 1.0F : 0.0F;
      }

      @Override
      public float height() {
         return 13.0F;
      }

      @Override
      public void render(DrawContext c, int mx, int my, boolean ok) {
         this.hover(mx, my, ok);
         this.anim = Zx.fast(this.anim, this.s.get() ? 1.0F : 0.0F, 20.0F);
         float px = this.x + this.w - 21.0F;
         float py = this.y + this.h / 2.0F - 4.0F;
         Render2D.roundedRect(c, px, py, 15.0F, 8.0F, 2.0F, Theme.mix(0x28FFFFFF, Theme.ACCENT, this.anim));
         Render2D.roundedRect(c, px + 1.0F + 7.0F * this.anim, py + 1.0F, 6.0F, 6.0F, 2.0F, 0xFFFFFFFF);
         this.label(c, this.s.getName(), 3.0F);
      }

      @Override
      public boolean click(int mx, int my, int b) {
         if (b == 0 && Render2D.hovered(mx, my, this.x, this.y, this.w, this.h)) {
            this.s.toggle();
            Sounds.bool();
            return true;
         }

         return false;
      }
   }

   // ───────────────────────────── number ─────────────────────────────

   public static final class Num extends ZxElement implements Zx.Focusable {
      private final NumberSetting s;
      private float anim;
      private boolean dragging;
      private boolean typing;
      private final StringBuilder typed = new StringBuilder();
      private String lastShown = "";

      Num(NumberSetting s) {
         super(s);
         this.s = s;
         this.h = 18.0F;
         this.anim = (float)s.fraction();
      }

      @Override
      public float height() {
         return 18.0F;
      }

      @Override
      public void render(DrawContext c, int mx, int my, boolean ok) {
         this.hover(mx, my, ok);
         if (this.dragging) {
            this.s.setFromFraction((double)(mx - (this.x + 7.0F)) / (double)(this.w - 14.0F));
            String shown = this.s.display();
            if (!shown.equals(this.lastShown)) {
               this.lastShown = shown;
               Sounds.slider();
            }
         }

         this.anim = Zx.fast(this.anim, (float)this.s.fraction(), 22.0F);
         String value = this.typing ? (this.typed.length() == 0 ? "..." : this.typed.toString()) : this.s.display();
         this.label(c, this.s.getName(), 3.0F);
         Fonts.draw(c, value, this.x + this.w - 6.0F - (float)Fonts.width(value), this.y + 3.0F, 0xFFFFFFFF);
         Render2D.rect(c, this.x + 6.0F, this.y + this.h - 6.0F, this.w - 12.0F, 2.0F, 0x28FFFFFF);
         Render2D.gradientH(c, this.x + 6.0F, this.y + this.h - 6.0F, this.x + 6.0F + (this.w - 12.0F) * this.anim, this.y + this.h - 4.0F, Theme.ACCENT2, Theme.ACCENT);
         Render2D.rect(c, this.x + 6.0F + (this.w - 14.0F) * this.anim, this.y + this.h - 7.5F, 2.0F, 5.0F, 0xFFE0E0E0);
      }

      @Override
      public boolean click(int mx, int my, int b) {
         if (Render2D.hovered(mx, my, this.x, this.y, this.w, this.h)) {
            if (b == 0) {
               this.dragging = true;
            } else {
               this.typed.setLength(0);
               this.typing = true;
               Zx.setFocus(this);
            }

            return true;
         }

         return false;
      }

      @Override
      public void release() {
         this.dragging = false;
      }

      @Override
      public boolean key(int code) {
         if (code == 257 || code == 335) {
            try {
               this.s.setValue(Double.parseDouble(this.typed.toString()));
            } catch (NumberFormatException var3) {
            }

            this.blur();
            Zx.focus = null;
            return true;
         } else if (code == 256) {
            this.blur();
            Zx.focus = null;
            return true;
         } else if (code == 259) {
            if (this.typed.length() > 0) {
               this.typed.setLength(this.typed.length() - 1);
            }

            return true;
         }

         return false;
      }

      @Override
      public void chr(char c) {
         if (Character.isDigit(c) || c == '.' || c == '-') {
            this.typed.append(c);
         }
      }

      @Override
      public void blur() {
         this.typing = false;
      }
   }

   // ───────────────────────────── mode ─────────────────────────────

   public static final class Mode extends ZxElement {
      private final ModeSetting<?> s;
      private boolean open;
      private float rotate;

      Mode(ModeSetting<?> s) {
         super(s);
         this.s = s;
      }

      @Override
      public float height() {
         return this.open ? 13.0F + 12.0F * (float)this.s.values().length : 13.0F;
      }

      @Override
      public void render(DrawContext c, int mx, int my, boolean ok) {
         this.hover(mx, my, ok);
         this.rotate = Zx.fast(this.rotate, this.open ? 1.0F : 0.0F, 15.0F);
         this.label(c, this.s.getName(), 3.0F);
         String value = this.s.display();
         Fonts.draw(c, value, this.x + this.w - 14.0F - (float)Fonts.width(value), this.y + 3.0F, 0xFFFFFFFF);
         Fonts.draw(c, this.open ? "-" : "+", this.x + this.w - 9.0F, this.y + 3.0F, 0xFFB0B0BA);
         if (this.open) {
            for (int i = 0; i < this.s.values().length; i++) {
               String name = this.s.display(i);
               int color = i == this.s.index() ? Theme.ACCENT : 0xFFFFFFFF;
               Fonts.draw(c, name, this.x + this.w / 2.0F - (float)Fonts.width(name) / 2.0F, this.y + 15.0F + (float)(i * 12), color);
            }
         }
      }

      @Override
      public boolean click(int mx, int my, int b) {
         if (Render2D.hovered(mx, my, this.x, this.y, this.w, 13.0)) {
            if (b == 0) {
               this.s.cycle(1);
               Sounds.bool();
            } else if (b == 1) {
               this.open = !this.open;
               if (this.open) {
                  Sounds.swipeIn();
               } else {
                  Sounds.swipeOut();
               }
            }

            return true;
         }

         if (this.open && b == 0) {
            for (int i = 0; i < this.s.values().length; i++) {
               if (Render2D.hovered(mx, my, this.x, this.y + 13.0F + (float)(i * 12), this.w, 12.0)) {
                  this.s.select(i);
                  Sounds.bool();
                  return true;
               }
            }
         }

         return false;
      }
   }

   // ───────────────────────────── key ─────────────────────────────

   public static final class Key extends ZxElement implements Zx.Focusable {
      private final KeySetting s;

      Key(KeySetting s) {
         super(s);
         this.s = s;
      }

      @Override
      public float height() {
         return 13.0F;
      }

      @Override
      public void render(DrawContext c, int mx, int my, boolean ok) {
         this.hover(mx, my, ok);
         this.label(c, this.s.getName(), 3.0F);
         String text = this.s.isListening() ? "..." : Zx.keyName(this.s.getKey());
         float tw = (float)Fonts.width(text);
         Render2D.rect(c, this.x + this.w - tw - 11.0F, this.y + 2.0F, tw + 6.0F, 10.0F, 0x94000000);
         Fonts.draw(c, text, this.x + this.w - tw - 8.0F, this.y + 3.0F, 0xFFFFFFFF);
      }

      @Override
      public boolean click(int mx, int my, int b) {
         if (b == 0 && Render2D.hovered(mx, my, this.x, this.y, this.w, this.h)) {
            this.s.setListening(true);
            Zx.setFocus(this);
            return true;
         }

         return false;
      }

      @Override
      public boolean key(int code) {
         this.s.setKey(code == 256 || code == 261 || code == 259 ? -1 : code);
         Zx.focus = null;
         return true;
      }

      @Override
      public void chr(char c) {
      }

      @Override
      public void blur() {
         this.s.setListening(false);
      }
   }

   // ───────────────────────────── string ─────────────────────────────

   public static final class Str extends ZxElement implements Zx.Focusable {
      private final StringSetting s;
      private boolean focused;

      Str(StringSetting s) {
         super(s);
         this.s = s;
      }

      @Override
      public float height() {
         return 13.0F;
      }

      @Override
      public void render(DrawContext c, int mx, int my, boolean ok) {
         this.hover(mx, my, ok);
         this.label(c, this.s.getName(), 3.0F);
         String text = this.s.getValue() + (this.focused && System.currentTimeMillis() / 400L % 2L == 0L ? "_" : "");
         float tw = (float)Math.min(60, Fonts.width(text));
         Render2D.rect(c, this.x + this.w - tw - 11.0F, this.y + 2.0F, tw + 6.0F, 10.0F, 0x94000000);
         Fonts.draw(c, Render2D.truncate(text, 60), this.x + this.w - tw - 8.0F, this.y + 3.0F, 0xFFFFFFFF);
      }

      @Override
      public boolean click(int mx, int my, int b) {
         if (b == 0 && Render2D.hovered(mx, my, this.x, this.y, this.w, this.h)) {
            this.focused = true;
            Zx.setFocus(this);
            return true;
         }

         return false;
      }

      @Override
      public boolean key(int code) {
         if (code == 259) {
            this.s.backspace();
            return true;
         } else if (code == 257 || code == 256) {
            this.blur();
            Zx.focus = null;
            return true;
         }

         return false;
      }

      @Override
      public void chr(char c) {
         this.s.append(c);
      }

      @Override
      public void blur() {
         this.focused = false;
      }
   }

   // ───────────────────────────── button ─────────────────────────────

   public static final class Btn extends ZxElement {
      private final ButtonSetting s;
      private long armedAt;

      Btn(ButtonSetting s) {
         super(s);
         this.s = s;
      }

      @Override
      public float height() {
         return 13.0F;
      }

      @Override
      public void render(DrawContext c, int mx, int my, boolean ok) {
         boolean hovered = this.hover(mx, my, ok);
         boolean armed = System.currentTimeMillis() - this.armedAt < 3000L;
         Render2D.rect(c, this.x + 4.0F, this.y + 1.0F, this.w - 8.0F, this.h - 2.0F, hovered ? 0xFF22222B : 0xFF181820);
         String text = armed ? "Click again" : this.s.getName();
         Fonts.drawCentered(c, text, this.x + this.w / 2.0F, this.y + 3.0F, armed ? Theme.ACCENT : 0xFFFFFFFF, false);
      }

      @Override
      public boolean click(int mx, int my, int b) {
         if (b == 0 && Render2D.hovered(mx, my, this.x, this.y, this.w, this.h)) {
            boolean armed = System.currentTimeMillis() - this.armedAt < 3000L;
            if (!this.s.needsConfirm() || armed) {
               this.s.run();
               this.armedAt = 0L;
            } else {
               this.armedAt = System.currentTimeMillis();
            }

            Sounds.bool();
            return true;
         }

         return false;
      }
   }
}
