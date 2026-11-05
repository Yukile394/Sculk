package dev.sculk.gui.zx;

import dev.sculk.render.Fonts;
import dev.sculk.render.Render2D;
import dev.sculk.sound.Sounds;
import dev.sculk.util.Friends;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

/** ZeltrixWare friends window: add by name, remove, online tag and quick add of the players on the server. */
public final class ZxFriendsPanel extends ZxPanel {
   private static final float ROW = 13.0F;
   private final Zx.TextField input;

   public ZxFriendsPanel(float x, float y) {
      super("Friends", x, y);
      this.input = new Zx.TextField(16, this::add);
   }

   private void add() {
      if (Friends.add(this.input.value())) {
         Sounds.bool();
      }

      this.input.clear();
   }

   private List<String> online() {
      List<String> names = new ArrayList<>();
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.getNetworkHandler() != null) {
         String self = mc.player.getName().getString();

         for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().getName();
            if (name != null && !name.equalsIgnoreCase(self) && Friends.isAddable(name) && !names.contains(name)) {
               names.add(name);
            }
         }

         names.sort(String.CASE_INSENSITIVE_ORDER);
      }

      return names;
   }

   private List<String> addable() {
      List<String> list = new ArrayList<>();

      for (String name : this.online()) {
         if (!Friends.contains(name) && list.size() < 12) {
            list.add(name);
         }
      }

      return list;
   }

   @Override
   protected String badge() {
      return Integer.toString(Friends.names().size());
   }

   @Override
   protected float contentHeight() {
      int friends = Friends.names().size();
      int quick = this.addable().size();
      return 17.0F + (float)friends * ROW + (quick > 0 ? 12.0F + (float)quick * ROW : 0.0F) + 4.0F;
   }

   @Override
   protected void renderContent(DrawContext c, int mx, int my, float top, boolean mouseOk) {
      float fieldW = this.w - 24.0F;
      this.input.render(c, this.x + 4.0F, top + 2.0F, fieldW, 11.0F, "Name", mouseOk && Render2D.hovered(mx, my, this.x + 4.0F, top + 2.0F, fieldW, 11.0));
      boolean plus = mouseOk && Render2D.hovered(mx, my, this.x + this.w - 18.0F, top + 2.0F, 14.0, 11.0);
      Render2D.rect(c, this.x + this.w - 18.0F, top + 2.0F, 14.0F, 11.0F, plus ? 0xFF22222B : 0xFF181820);
      Fonts.drawCentered(c, "+", this.x + this.w - 11.0F, top + 3.0F, 0xFFFFFFFF, true);
      float cursor = top + 17.0F;
      List<String> online = this.online();

      for (String name : Friends.names()) {
         boolean here = online.stream().anyMatch(n -> n.equalsIgnoreCase(name));
         Render2D.rect(c, this.x, cursor, this.w, ROW, mouseOk && Render2D.hovered(mx, my, this.x, cursor, this.w, ROW) ? 0xFF1E1E27 : 0xFF14141A);
         Fonts.draw(c, Render2D.truncate(name, 60), this.x + 6.0F, Zx.textY(cursor, ROW), 0xFFFFFFFF);
         if (here) {
            Fonts.draw(c, "on", this.x + this.w - 26.0F, Zx.textY(cursor, ROW), 0xFF55FF77);
         }

         Fonts.draw(c, "x", this.x + this.w - 10.0F, Zx.textY(cursor, ROW), 0xFFFF6666);
         cursor += ROW;
      }

      List<String> quick = this.addable();
      if (!quick.isEmpty()) {
         Fonts.draw(c, "Online", this.x + 6.0F, cursor + 2.0F, 0x80FFFFFF);
         cursor += 12.0F;

         for (String name : quick) {
            Render2D.rect(c, this.x, cursor, this.w, ROW, mouseOk && Render2D.hovered(mx, my, this.x, cursor, this.w, ROW) ? 0xFF1E1E27 : 0xFF101015);
            Fonts.draw(c, Render2D.truncate(name, 70), this.x + 6.0F, Zx.textY(cursor, ROW), 0xFFC8C8D0);
            Fonts.draw(c, "+", this.x + this.w - 10.0F, Zx.textY(cursor, ROW), 0xFF55FF77);
            cursor += ROW;
         }
      }
   }

   @Override
   protected boolean contentClick(int mx, int my, int button, float top) {
      if (Render2D.hovered(mx, my, this.x + 4.0F, top + 2.0F, this.w - 24.0F, 11.0)) {
         this.input.focus();
         return true;
      } else if (Render2D.hovered(mx, my, this.x + this.w - 18.0F, top + 2.0F, 14.0, 11.0)) {
         this.add();
         return true;
      }

      float cursor = top + 17.0F;

      for (String name : Friends.names()) {
         if (Render2D.hovered(mx, my, this.x, cursor, this.w, ROW)) {
            if (button == 0 || button == 1) {
               Friends.remove(name);
               Sounds.bool();
            }

            return true;
         }

         cursor += ROW;
      }

      List<String> quick = this.addable();
      if (!quick.isEmpty()) {
         cursor += 12.0F;

         for (String name : quick) {
            if (Render2D.hovered(mx, my, this.x, cursor, this.w, ROW)) {
               Friends.add(name);
               Sounds.bool();
               return true;
            }

            cursor += ROW;
         }
      }

      return false;
   }
}
