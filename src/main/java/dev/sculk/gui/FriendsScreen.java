package dev.sculk.gui;

import dev.sculk.SculkClient;
import dev.sculk.module.modules.client.Fps25;
import dev.sculk.render.Render2D;
import dev.sculk.render.Theme;
import dev.sculk.util.Friends;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;

public final class FriendsScreen extends Screen {
   private static final int WIDTH = 172;
   private static final int HEADER_H = 20;
   private static final int ROW = 13;
   private static final int MAX_ONLINE_ROWS = 16;
   private final StringBuilder input = new StringBuilder();

   public FriendsScreen() {
      super(Text.literal("Friends"));
   }

   private FriendsScreen.Geo geo() {
      float x = (float)(this.width - 172) / 2.0F;
      float y = Math.max(20.0F, (float)this.height / 2.0F - 110.0F);
      float inputY = y + 20.0F + 5.0F;
      float friendsY = inputY + 18.0F;
      float onlineLabelY = friendsY + (float)(Friends.names().size() * 13) + 7.0F;
      float onlineY = onlineLabelY + 13.0F;
      int rows = Math.min(16, (this.onlineNames().size() + 1) / 2);
      int rest = this.onlineNames().size() > 32 ? 12 : 0;
      return new FriendsScreen.Geo(x, inputY, friendsY, onlineLabelY, onlineY, 80.0F, onlineY + (float)(rows * 13) + (float)rest + 10.0F - y);
   }

   private List<String> onlineNames() {
      List<String> names = new ArrayList<>();
      if (this.client.player != null && this.client.getNetworkHandler() != null) {
         String self = this.client.player.getName().getString();

         for (PlayerListEntry entry : this.client.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().getName();
            if (name != null && !name.equalsIgnoreCase(self) && Friends.isAddable(name) && !names.contains(name)) {
               names.add(name);
            }
         }

         names.sort(String.CASE_INSENSITIVE_ORDER);
         return names;
      } else {
         return names;
      }
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      context.fill(0, 0, this.width, this.height, -1778384896);
      FriendsScreen.Geo g = this.geo();
      float x = g.x();
      float y = g.inputY() - 20.0F - 5.0F;
      float total = g.height();
      Render2D.roundedRect(context, x, y, 172.0F, total, 8.0F, Theme.PANEL);
      Render2D.roundedRect(context, x, y, 172.0F, 20.0F, 8.0F, Theme.HEADER);
      context.fill((int)x, (int)(y + 20.0F - 6.0F), (int)(x + 172.0F), (int)(y + 20.0F), Theme.HEADER);
      context.fill((int)x + 8, (int)(y + 20.0F - 2.0F), (int)(x + 172.0F - 8.0F), (int)(y + 20.0F - 1.0F), Theme.ACCENT);
      String title = "Friends";
      String count = Integer.toString(Friends.names().size());
      Render2D.text(context, title, x + 9.0F, y + 6.0F, -1447440);
      Render2D.text(context, count, x + 9.0F + (float)Render2D.textWidth(title) + 5.0F, y + 6.0F, Theme.ACCENT);
      Render2D.text(context, "ESC: kapat", x + 172.0F - 8.0F - (float)Render2D.textWidth("ESC: kapat"), y + 6.0F, -8750457);
      this.renderInput(context, g);
      this.renderFriends(context, g, mouseX, mouseY);
      this.renderOnline(context, g, mouseX, mouseY);
      super.render(context, mouseX, mouseY, delta);
   }

   private void renderInput(DrawContext context, FriendsScreen.Geo g) {
      Render2D.roundedRect(context, g.x() + 6.0F, g.inputY(), 160.0F, 15.0F, 3.0F, -15000797);
      String caret = "|";
      String text = this.input.length() == 0 ? "isim yaz" : this.input + caret;
      int color = this.input.length() == 0 ? -8750457 : -1447440;
      Render2D.text(context, text, g.x() + 11.0F, g.inputY() + 3.0F, color);
      String enter = "Enter: ekle";
      Render2D.text(context, enter, g.x() + 172.0F - 8.0F - (float)Render2D.textWidth(enter), g.inputY() + 3.0F, -8750457);
   }

   private void renderFriends(DrawContext context, FriendsScreen.Geo g, int mouseX, int mouseY) {
      List<String> names = Friends.names();

      for (int i = 0; i < names.size(); i++) {
         float rowY = g.friendsY() + (float)(i * 13);
         if (inside((double)mouseX, (double)mouseY, g.x() + 6.0F, rowY, 160.0F, 13.0F)) {
            context.fill((int)(g.x() + 6.0F), (int)rowY, (int)(g.x() + 172.0F - 6.0F), (int)(rowY + 13.0F), 352321535);
            Render2D.text(context, "kaldir", g.x() + 172.0F - 8.0F - (float)Render2D.textWidth("kaldir"), rowY + 2.0F, -30070);
         }

         Render2D.text(context, Render2D.truncate(names.get(i), 112), g.x() + 11.0F, rowY + 2.0F, -1447440);
      }
   }

   private void renderOnline(DrawContext context, FriendsScreen.Geo g, int mouseX, int mouseY) {
      List<String> names = this.onlineNames();
      String label = names.isEmpty() ? "tab listesi bos" : "oyundakiler: tikla = ekle / kaldir";
      Render2D.text(context, label, g.x() + 8.0F, g.onlineLabelY(), -8750457);
      int shown = Math.min(names.size(), 32);

      for (int i = 0; i < shown; i++) {
         float cellX = g.x() + 6.0F + (float)(i % 2) * g.cellW();
         float cellY = g.onlineY() + (float)(i / 2 * 13);
         String name = names.get(i);
         boolean friend = Friends.contains(name);
         if (inside((double)mouseX, (double)mouseY, cellX, cellY, g.cellW() - 2.0F, 13.0F)) {
            context.fill((int)cellX, (int)cellY, (int)(cellX + g.cellW() - 2.0F), (int)(cellY + 13.0F), 352321535);
         }

         Render2D.text(context, friend ? "+ " : "  ", cellX + 2.0F, cellY + 2.0F, Theme.ACCENT);
         Render2D.text(
            context,
            Render2D.truncate(name, (int)g.cellW() - 22),
            cellX + 3.0F + (float)Render2D.textWidth("+ "),
            cellY + 2.0F,
            friend ? Theme.ACCENT : -1447440
         );
      }

      if (names.size() > shown) {
         Render2D.text(context, "+" + (names.size() - shown) + " kisim daha", g.x() + 8.0F, g.onlineY() + (float)(shown / 2 * 13) + 2.0F, -8750457);
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      FriendsScreen.Geo g = this.geo();
      List<String> names = Friends.names();

      for (int i = 0; i < names.size(); i++) {
         float rowY = g.friendsY() + (float)(i * 13);
         if (inside(mouseX, mouseY, g.x() + 6.0F, rowY, 160.0F, 13.0F)) {
            Friends.remove(names.get(i));
            return true;
         }
      }

      List<String> online = this.onlineNames();
      int shown = Math.min(online.size(), 32);

      for (int ix = 0; ix < shown; ix++) {
         float cellX = g.x() + 6.0F + (float)(ix % 2) * g.cellW();
         float cellY = g.onlineY() + (float)(ix / 2 * 13);
         if (inside(mouseX, mouseY, cellX, cellY, g.cellW() - 2.0F, 13.0F)) {
            Friends.toggle(online.get(ix));
            return true;
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 257 || keyCode == 335) {
         Friends.add(this.input.toString());
         this.input.setLength(0);
         return true;
      } else if (keyCode == 259) {
         if (this.input.length() > 0) {
            this.input.deleteCharAt(this.input.length() - 1);
         }

         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public boolean charTyped(char chr, int modifiers) {
      if (!Character.isISOControl(chr) && chr != ' ' && this.input.length() < 16) {
         this.input.append(chr);
         return true;
      } else {
         return super.charTyped(chr, modifiers);
      }
   }

   private static boolean inside(double mx, double my, float x, float y, float w, float h) {
      return mx >= (double)x && mx < (double)(x + w) && my >= (double)y && my < (double)(y + h);
   }

   public void removed() {
      Friends.save();
      Fps25 module = SculkClient.get(Fps25.class);
      if (module != null) {
         module.onScreenClosed();
      }
   }

   public boolean shouldPause() {
      return false;
   }

   private static record Geo(float x, float inputY, float friendsY, float onlineLabelY, float onlineY, float cellW, float height) {
   }
}
