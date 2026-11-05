package dev.sculk.gui.zx;

import dev.sculk.render.Fonts;
import dev.sculk.render.Render2D;
import dev.sculk.render.Theme;
import dev.sculk.sound.Sounds;
import net.minecraft.client.gui.DrawContext;

/** Draggable ZeltrixWare style category window: dark header with accent underline, scrollable body. */
public abstract class ZxPanel {
   public static final float HEADER = 18.0F;
   public static final float MAX_BODY = 260.0F;
   public static final float WIDTH = 108.0F;
   public final String name;
   public float x;
   public float y;
   public float w = WIDTH;
   public boolean open = true;
   protected float scroll;
   private boolean dragging;
   private float dragX;
   private float dragY;
   private float bodyAnim;

   protected ZxPanel(String name, float x, float y) {
      this.name = name;
      this.x = x;
      this.y = y;
   }

   protected abstract float contentHeight();

   /** @param top y of the first content pixel (already scrolled) */
   protected abstract void renderContent(DrawContext c, int mx, int my, float top, boolean mouseOk);

   protected abstract boolean contentClick(int mx, int my, int button, float top);

   protected void contentRelease() {
   }

   protected String badge() {
      return "";
   }

   public void render(DrawContext c, int mx, int my) {
      if (this.dragging) {
         this.x = (float)mx + this.dragX;
         this.y = (float)my + this.dragY;
      }

      float content = this.contentHeight();
      float target = this.open ? Math.min(content, MAX_BODY) : 0.0F;
      this.bodyAnim = Zx.fast(this.bodyAnim, target, 25.0F);
      float minScroll = Math.min(0.0F, MAX_BODY - content);
      this.scroll = Math.max(minScroll, Math.min(0.0F, this.scroll));
      float bodyTop = this.y + HEADER;

      if (this.bodyAnim > 0.5F) {
         Render2D.roundedRect(c, this.x, this.y, this.w, HEADER + this.bodyAnim, 3.0F, Theme.BODY);
         Render2D.scissor(c, this.x, bodyTop, this.x + this.w, bodyTop + this.bodyAnim);
         this.renderContent(c, mx, my, bodyTop + this.scroll, Render2D.hovered(mx, my, this.x, bodyTop, this.w, this.bodyAnim));
         Render2D.endScissor(c);
      }

      Render2D.roundedRect(c, this.x, this.y, this.w, HEADER, 3.0F, Theme.GUI_HEADER);
      Render2D.gradientH(c, this.x, this.y + HEADER - 1.5F, this.x + this.w, this.y + HEADER, Theme.ACCENT, Theme.ACCENT2);
      Fonts.draw(c, this.name, this.x + 8.0F, Zx.textY(this.y, HEADER - 1.0F), 0xFFFFFFFF, true);
      String badge = this.badge();
      if (!badge.isEmpty()) {
         Fonts.draw(c, badge, this.x + this.w - 6.0F - (float)Fonts.width(badge), Zx.textY(this.y, HEADER - 1.0F), 0x6EFFFFFF);
      }
   }

   public boolean over(int mx, int my) {
      return Render2D.hovered(mx, my, this.x, this.y, this.w, HEADER + (this.open ? this.bodyAnim : 0.0F));
   }

   public void scrollBy(float amount) {
      this.scroll += amount;
   }

   public boolean click(int mx, int my, int button) {
      if (Render2D.hovered(mx, my, this.x, this.y, this.w, HEADER)) {
         if (button == 0) {
            this.dragging = true;
            this.dragX = this.x - (float)mx;
            this.dragY = this.y - (float)my;
         } else if (button == 1) {
            this.open = !this.open;
            if (this.open) {
               Sounds.swipeIn();
            } else {
               Sounds.swipeOut();
            }
         }

         return true;
      }

      if (this.open && Render2D.hovered(mx, my, this.x, this.y + HEADER, this.w, this.bodyAnim)) {
         this.contentClick(mx, my, button, this.y + HEADER + this.scroll);
         return true;
      }

      return false;
   }

   public void release() {
      this.dragging = false;
      this.contentRelease();
   }
}
