package dev.sculk.gui.zx;

import dev.sculk.render.Fonts;
import dev.sculk.render.Render2D;
import dev.sculk.render.Theme;
import dev.sculk.sound.Sounds;
import net.minecraft.client.gui.DrawContext;

/** Draggable rounded card: soft shadow, header with accent line and count pill, scrollable body. */
public abstract class ZxPanel {
   public static final float HEADER = 22.0F;
   public static final float MAX_BODY = 260.0F;
   public static final float WIDTH = 116.0F;
   public static final float GAP = 6.0F;
   private static final float PAD = 3.0F;
   private static final float RADIUS = 7.0F;
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

   /** @param top y of the first content pixel (already scrolled and padded) */
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

      float content = this.contentHeight() + PAD * 2.0F;
      float target = this.open ? Math.min(content, MAX_BODY) : 0.0F;
      this.bodyAnim = Zx.fast(this.bodyAnim, target, 25.0F);
      float minScroll = Math.min(0.0F, MAX_BODY - content);
      this.scroll = Math.max(minScroll, Math.min(0.0F, this.scroll));
      float bodyTop = this.y + HEADER;
      float total = HEADER + this.bodyAnim;

      for (int i = 4; i >= 1; i--) {
         Render2D.roundedRect(c, this.x - (float)i, this.y - (float)i + 2.0F, this.w + (float)(i * 2), total + (float)(i * 2), RADIUS + (float)i, 0x0C000000);
      }

      Render2D.roundedRect(c, this.x, this.y, this.w, total, RADIUS, Theme.BODY);

      if (this.bodyAnim > 0.5F) {
         Render2D.scissor(c, this.x, bodyTop, this.x + this.w, bodyTop + this.bodyAnim);
         this.renderContent(c, mx, my, bodyTop + this.scroll + PAD, Render2D.hovered(mx, my, this.x, bodyTop, this.w, this.bodyAnim));
         Render2D.endScissor(c);

         if (content > MAX_BODY + 0.5F) {
            float track = this.bodyAnim - 8.0F;
            float thumb = Math.max(14.0F, track * MAX_BODY / content);
            float progress = -this.scroll / (content - MAX_BODY);
            Render2D.roundedRect(c, this.x + this.w - 4.0F, bodyTop + 4.0F + (track - thumb) * progress, 2.0F, thumb, 1.0F, 0x44FFFFFF);
         }
      }

      Render2D.roundedRect(c, this.x, this.y, this.w, HEADER, RADIUS, Theme.GUI_HEADER);
      Render2D.rect(c, this.x, this.y + HEADER - RADIUS, this.w, RADIUS, Theme.scaleAlpha(Theme.GUI_HEADER, Math.min(1.0F, this.bodyAnim / 6.0F)));
      Render2D.gradientH(c, this.x + RADIUS, this.y + HEADER - 1.5F, this.x + this.w - RADIUS, this.y + HEADER, Theme.ACCENT, Theme.ACCENT2);
      Render2D.circle(c, this.x + 11.0F, this.y + (HEADER - 1.5F) / 2.0F, 2.5F, Theme.ACCENT);
      Fonts.draw(c, this.name, this.x + 18.0F, Zx.textY(this.y, HEADER - 1.5F), Theme.GUI_TEXT, true);

      String badge = this.badge();
      if (!badge.isEmpty()) {
         float bw = (float)Fonts.width(badge) + 9.0F;
         float bx = this.x + this.w - 7.0F - bw;
         float by = this.y + (HEADER - 1.5F - 11.0F) / 2.0F;
         Render2D.roundedRect(c, bx, by, bw, 11.0F, 5.5F, 0x1CFFFFFF);
         Fonts.draw(c, badge, bx + 4.5F, Zx.textY(by, 11.0F), 0xB4FFFFFF);
      }

      Render2D.roundedOutline(c, this.x, this.y, this.w, total, RADIUS, 1.0F, Theme.GUI_LINE);
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
         this.contentClick(mx, my, button, this.y + HEADER + this.scroll + PAD);
         return true;
      }

      return false;
   }

   public void release() {
      this.dragging = false;
      this.contentRelease();
   }
}
