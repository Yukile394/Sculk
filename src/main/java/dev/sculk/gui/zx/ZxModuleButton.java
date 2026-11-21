package dev.sculk.gui.zx;

import dev.sculk.module.Module;
import dev.sculk.module.setting.Setting;
import dev.sculk.render.Fonts;
import dev.sculk.render.Render2D;
import dev.sculk.render.Theme;
import dev.sculk.sound.Sounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

/** Module row: left click toggles, right click opens the settings, middle click binds (toggle / hold). */
public final class ZxModuleButton implements Zx.Focusable {
   public static final float ROW = 15.0F;
   private static final float INSET = 3.0F;
   public final Module module;
   private final List<ZxElement> elements = new ArrayList<>();
   private boolean open;
   private boolean binding;
   private boolean hovered;
   private boolean prevHovered;
   private float enabledAnim;
   private float expand;

   public ZxModuleButton(Module module) {
      this.module = module;
      this.enabledAnim = module.isEnabled() ? 1.0F : 0.0F;

      for (Setting<?> setting : module.settings()) {
         ZxElement element = ZxElement.create(setting);
         if (element != null) {
            this.elements.add(element);
         }
      }
   }

   private float elementsHeight() {
      float total = 0.0F;

      for (ZxElement element : this.elements) {
         if (element.visible()) {
            total += element.height();
         }
      }

      return total;
   }

   /** Height this button takes right now (animated). */
   public float height() {
      return ROW + (this.elementsHeight() + 2.0F) * this.expand;
   }

   public boolean matches() {
      return Zx.search.isEmpty() || this.module.getName().toLowerCase().contains(Zx.search);
   }

   public void render(DrawContext c, int mx, int my, float x, float y, float w, boolean mouseOk) {
      float bx = x + INSET;
      float bw = w - INSET * 2.0F;
      this.hovered = mouseOk && Render2D.hovered(mx, my, x, y, w, ROW);
      if (this.hovered) {
         if (!this.prevHovered) {
            Sounds.scroll();
         }

         Zx.description = this.module.getDescription();
      }

      this.prevHovered = this.hovered;
      this.enabledAnim = Zx.fast(this.enabledAnim, this.module.isEnabled() ? 1.0F : 0.0F, 8.0F);
      this.expand = Zx.fast(this.expand, this.open ? 1.0F : 0.0F, 16.0F);

      if (this.expand > 0.01F) {
         float areaH = (this.elementsHeight() + 2.0F) * this.expand;
         Render2D.roundedRect(c, bx, y + ROW - 3.0F, bw, areaH + 3.0F, 4.0F, Theme.GUI_SETTINGS);
         Render2D.scissor(c, bx, y + ROW, bx + bw, y + ROW + areaH);
         float cursor = y + ROW + 2.0F;

         for (ZxElement element : this.elements) {
            if (element.visible()) {
               element.place(bx, cursor, bw);
               element.render(c, mx, my, mouseOk && Render2D.hovered(mx, my, x, y + ROW, w, areaH));
               cursor += element.height();
            }
         }

         Render2D.endScissor(c);
      }

      if (this.hovered) {
         Render2D.roundedRect(c, bx, y + 1.0F, bw, ROW - 2.0F, 4.0F, 0x16FFFFFF);
      }

      if (this.enabledAnim > 0.02F) {
         Render2D.roundedRect(c, bx, y + 1.0F, bw, ROW - 2.0F, 4.0F, Theme.scaleAlpha(Theme.ACCENT, 0.24F * this.enabledAnim));
         Render2D.roundedRect(c, bx + 1.5F, y + 3.5F, 2.0F, ROW - 7.0F, 1.0F, Theme.scaleAlpha(Theme.ACCENT, this.enabledAnim));
      }

      float ty = Zx.textY(y, ROW);
      float right = bx + bw - 6.0F;
      boolean hasSettings = !this.elements.isEmpty();
      if (hasSettings) {
         int dot = this.open ? Theme.ACCENT : 0x78FFFFFF;
         float cy = y + ROW / 2.0F;
         Render2D.rect(c, right - 1.0F, cy - 4.0F, 2.0F, 2.0F, dot);
         Render2D.rect(c, right - 1.0F, cy - 1.0F, 2.0F, 2.0F, dot);
         Render2D.rect(c, right - 1.0F, cy + 2.0F, 2.0F, 2.0F, dot);
         right -= 8.0F;
      }

      if (this.binding) {
         Fonts.draw(c, "Press a key", bx + 8.0F, ty, Theme.GUI_TEXT);
         String mode = this.module.isHoldBind() ? "Hold" : "Toggle";
         Fonts.draw(c, mode, right - (float)Fonts.width(mode), ty, Theme.ACCENT);
      } else {
         int nameColor = Theme.mix(0xFFA6AABA, 0xFFFFFFFF, this.enabledAnim);
         Fonts.draw(c, this.module.getName(), bx + 8.0F, ty, nameColor, this.enabledAnim > 0.5F);
         if (this.module.getKey() >= 0) {
            String bind = Zx.keyName(this.module.getKey()) + (this.module.isHoldBind() ? "*" : "");
            Fonts.draw(c, bind, right - (float)Fonts.width(bind), ty, 0x9EFFFFFF);
         }
      }
   }

   public boolean click(int mx, int my, int button, float x, float y, float w, boolean mouseOk) {
      if (mouseOk && Render2D.hovered(mx, my, x, y, w, ROW)) {
         if (this.binding && button == 0) {
            this.module.setHoldBind(!this.module.isHoldBind());
            Zx.setFocus(this);
            Sounds.bool();
            return true;
         }

         if (button == 0) {
            this.module.toggle();
         } else if (button == 1) {
            this.open = !this.open;
            if (this.open) {
               Sounds.swipeIn();
            } else {
               Sounds.swipeOut();
            }
         } else if (button == 2) {
            this.binding = true;
            Zx.setFocus(this);
         }

         return true;
      }

      if (this.open && this.expand > 0.5F) {
         for (ZxElement element : this.elements) {
            if (element.visible() && element.click(mx, my, button)) {
               return true;
            }
         }
      }

      return false;
   }

   public void release() {
      for (ZxElement element : this.elements) {
         element.release();
      }
   }

   @Override
   public boolean key(int code) {
      if (this.binding) {
         this.module.key(code == 256 || code == 261 || code == 259 ? -1 : code);
         this.binding = false;
         Zx.focus = null;
         return true;
      }

      return false;
   }

   @Override
   public void chr(char c) {
   }

   @Override
   public void blur() {
      this.binding = false;
   }
}
