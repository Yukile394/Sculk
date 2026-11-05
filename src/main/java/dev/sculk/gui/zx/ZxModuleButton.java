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
   public static final float ROW = 13.0F;
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
         Render2D.rect(c, x, y + ROW, w, areaH, Theme.GUI_SETTINGS);
         Render2D.scissor(c, x, y + ROW, x + w, y + ROW + areaH);
         float cursor = y + ROW + 2.0F;

         for (ZxElement element : this.elements) {
            if (element.visible()) {
               element.place(x, cursor, w);
               element.render(c, mx, my, mouseOk && Render2D.hovered(mx, my, x, y + ROW, w, areaH));
               cursor += element.height();
            }
         }

         Render2D.endScissor(c);
      }

      Render2D.rect(c, x, y, w, ROW, Theme.GUI_MODULE);
      if (this.hovered) {
         Render2D.rect(c, x, y, w, ROW, 0x0EFFFFFF);
      }

      if (this.enabledAnim > 0.02F) {
         Render2D.gradientH(
            c, x, y, x + w, y + ROW, Theme.scaleAlpha(Theme.ACCENT, this.enabledAnim * 0.85F), Theme.scaleAlpha(Theme.ACCENT2, this.enabledAnim * 0.55F)
         );
         Render2D.rect(c, x, y, 2.0F, ROW, Theme.scaleAlpha(0xFFFFFFFF, this.enabledAnim));
      }

      if (this.open) {
         Render2D.rect(c, x, y + ROW - 0.5F, w, 0.5F, 0x28FFFFFF);
      }

      float ty = Zx.textY(y, ROW);
      if (this.binding) {
         Fonts.draw(c, "PressKey", x + 6.0F, ty, 0xFFFFFFFF);
         String mode = this.module.isHoldBind() ? "Hold" : "Toggle";
         Fonts.draw(c, mode, x + w - 6.0F - (float)Fonts.width(mode), ty, 0xFFFFFFFF);
      } else {
         Fonts.draw(c, this.module.getName(), x + 6.0F, ty, 0xFFFFFFFF);
         if (this.module.getKey() >= 0) {
            String bind = Zx.keyName(this.module.getKey()) + (this.module.isHoldBind() ? "*" : "");
            Fonts.draw(c, bind, x + w - 6.0F - (float)Fonts.width(bind), ty, 0xB4FFFFFF);
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
