package dev.sculk.hud;

import dev.sculk.SculkClient;
import dev.sculk.module.Module;
import dev.sculk.module.modules.client.Fps15;
import dev.sculk.module.modules.client.Fps16;
import dev.sculk.module.modules.client.Fps24;
import dev.sculk.render.Animation;
import dev.sculk.render.Render2D;
import dev.sculk.render.Theme;
import dev.sculk.util.CpsTracker;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

public final class HudRenderer {
   private static final MinecraftClient MC = MinecraftClient.getInstance();
   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
   private static final int PAD = 6;
   private static final int STRIPE = 2;
   private static final int STRIPE_GAP = 3;
   private static final int CHIP_HEIGHT = 15;
   private static final int LINE_HEIGHT = 11;
   private static final int LIST_INSET = 4;
   private static final float RADIUS = 6.0F;
   private static final float TEXT_H = 9.0F;
   private static final float SLIDE_PX = 14.0F;
   private static final float FADE_IN = 13.0F;
   private static final float FADE_OUT = 8.0F;
   private static final float SIZE = 16.0F;
   private static final float ROW_IN = 18.0F;
   private static final float ROW_OUT = 11.0F;
   private static final Map<HudElement, HudRenderer.Box> BOXES = new EnumMap<>(HudElement.class);
   private static final Map<String, HudRenderer.Row> ROWS = new LinkedHashMap<>();

   private HudRenderer() {
   }

   public static void beginFrame(HudElement element, boolean active) {
      HudRenderer.Box box = BOXES.get(element);
      box.fade.animate(active ? 1.0F : 0.0F, active ? 13.0F : 8.0F);
      box.width.animate((float)width(element), 16.0F);
      box.height.animate((float)height(element), 16.0F);
      if (element == HudElement.MODULE_LIST) {
         stepRows(active);
      }
   }

   public static boolean visible(HudElement element) {
      return BOXES.get(element).fade.get() > 0.02F;
   }

   public static float animatedWidth(HudElement element) {
      return Math.max(1.0F, BOXES.get(element).width.get());
   }

   public static float animatedHeight(HudElement element) {
      return Math.max(1.0F, BOXES.get(element).height.get());
   }

   public static boolean active(HudElement element, boolean enabled) {
      return element != HudElement.MODULE_LIST ? enabled : enabled && !moduleNames().isEmpty();
   }

   public static int width(HudElement element) {
      if (element != HudElement.MODULE_LIST) {
         if (element == HudElement.WATERMARK) {
            return 17 + Render2D.textWidth("SculkClient") + 3 + Render2D.textWidth("1.0.0-beta");
         } else {
            String[] parts = parts(element);
            return 17 + Render2D.textWidth(parts[0]) + Render2D.textWidth(parts[1]);
         }
      } else {
         int max = 0;

         for (String name : moduleNames()) {
            max = Math.max(max, Render2D.textWidth(name));
         }

         return Math.max(max, 24) + 3 + 2 + 3 + 8;
      }
   }

   public static int height(HudElement element) {
      return element == HudElement.MODULE_LIST ? Math.max(13, moduleNames().size() * 11 + 8) : 15;
   }

   public static void draw(DrawContext context, HudElement element, float x, float y) {
      if (element == HudElement.MODULE_LIST) {
         drawModuleList(context, x, y);
      } else {
         HudRenderer.Box box = BOXES.get(element);
         float fade = box.fade.get();
         float width = animatedWidth(element);
         float height = animatedHeight(element);
         float textY = y + (float)Math.round((height - 9.0F) / 2.0F);
         Render2D.roundedRect(context, x, y, width, height, 6.0F, fade(Theme.PANEL, fade * 0.72F));
         Render2D.roundedOutline(context, x, y, width, height, 6.0F, 1.0F, fade(-14474452, fade));
         Render2D.roundedRect(context, x + 6.0F, y + (float)Math.round(height / 2.0F) - 3.0F, 2.0F, 6.0F, 1.0F, fade(Theme.ACCENT, fade));
         float textX = x + 6.0F + 2.0F + 3.0F;
         if (element == HudElement.WATERMARK) {
            Render2D.text(context, "SculkClient", textX, textY, fade(Theme.ACCENT, fade), true);
            Render2D.text(context, "1.0.0-beta", textX + (float)Render2D.textWidth("SculkClient") + 3.0F, textY, fade(-8750457, fade), true);
         } else {
            String[] parts = parts(element);
            Render2D.text(context, parts[0], textX, textY, fade(-1447440, fade), true);
            if (!parts[1].isEmpty()) {
               Render2D.text(context, parts[1], textX + (float)Render2D.textWidth(parts[0]), textY, fade(-8750457, fade), true);
            }
         }
      }
   }

   private static void drawModuleList(DrawContext context, float x, float y) {
      HudRenderer.Box box = BOXES.get(HudElement.MODULE_LIST);
      float fade = box.fade.get();
      float width = animatedWidth(HudElement.MODULE_LIST);
      float height = animatedHeight(HudElement.MODULE_LIST);
      Render2D.roundedRect(context, x, y, width, height, 6.0F, fade(Theme.PANEL, fade * 0.62F));
      Render2D.roundedOutline(context, x, y, width, height, 6.0F, 1.0F, fade(-14474452, fade));

      for (Entry<String, HudRenderer.Row> entry : ROWS.entrySet()) {
         HudRenderer.Row row = entry.getValue();
         float presence = row.presence.get();
         if (!(presence < 0.02F)) {
            float alpha = fade * presence;
            float rowX = x + (1.0F - presence) * 14.0F;
            float rowY = y + 4.0F + row.offset.get();
            Render2D.roundedRect(context, rowX + 4.0F, rowY + 1.0F, 2.0F, 8.0F, 1.0F, fade(Theme.ACCENT, alpha));
            Render2D.text(context, entry.getKey(), rowX + 4.0F + 2.0F + 3.0F, rowY, fade(-1447440, alpha), true);
         }
      }
   }

   private static void stepRows(boolean active) {
      List<String> names = moduleNames();

      for (int i = 0; i < names.size(); i++) {
         HudRenderer.Row row = ROWS.computeIfAbsent(names.get(i), key -> new HudRenderer.Row());
         row.presence.animate(active ? 1.0F : 0.0F, 18.0F);
         row.offset.animate((float)(i * 11), 16.0F);
      }

      Set<String> present = new HashSet<>(names);
      Iterator<Entry<String, HudRenderer.Row>> iterator = ROWS.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<String, HudRenderer.Row> entry = iterator.next();
         if (!present.contains(entry.getKey())) {
            HudRenderer.Row row = entry.getValue();
            row.presence.animate(0.0F, 11.0F);
            if (row.presence.get() < 0.02F) {
               iterator.remove();
            }
         }
      }
   }

   public static List<String> moduleNames() {
      List<String> names = new ArrayList<>();

      for (Module module : SculkClient.modules()) {
         if (module.isEnabled() && !(module instanceof Fps15) && !(module instanceof Fps16) && !(module instanceof Fps24)) {
            names.add(module.getName());
         }
      }

      names.sort(Comparator.comparing(String::toLowerCase));
      return names;
   }

   private static String[] parts(HudElement element) {
      return switch (element) {
         case FPS -> new String[]{Integer.toString(MC.getCurrentFps()), " fps"};
         case COORDS -> new String[]{
         MC.player == null
            ? "0, 64, 0"
            : (int)Math.floor(MC.player.getX())
               + ", "
               + (int)Math.floor(MC.player.getY())
               + ", "
               + (int)Math.floor(MC.player.getZ()),
         ""
      };
         case CPS -> new String[]{Integer.toString(CpsTracker.cps()), " cps"};
         case PING -> new String[]{Integer.toString(latency()), " ms"};
         case CLOCK -> new String[]{LocalTime.now().format(TIME), ""};
         default -> new String[]{"", ""};
      };
   }

   private static int latency() {
      if (MC.player != null && MC.getNetworkHandler() != null) {
         PlayerListEntry entry = MC.getNetworkHandler().getPlayerListEntry(MC.player.getUuid());
         return entry == null ? 0 : entry.getLatency();
      } else {
         return 0;
      }
   }

   private static int fade(int color, float alphaScale) {
      return Theme.scaleAlpha(color, alphaScale);
   }

   static {
      for (HudElement element : HudElement.values()) {
         BOXES.put(element, new HudRenderer.Box());
      }
   }

   private static final class Box {
      private final Animation fade = new Animation(0.0F);
      private final Animation width = new Animation(0.0F);
      private final Animation height = new Animation(0.0F);
   }

   private static final class Row {
      private final Animation presence = new Animation(0.0F);
      private final Animation offset = new Animation(0.0F);
   }
}
