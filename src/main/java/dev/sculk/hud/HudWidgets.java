package dev.sculk.hud;

import dev.sculk.SculkClient;
import dev.sculk.gui.HudEditorScreen;
import dev.sculk.module.Module;
import dev.sculk.module.modules.client.Fps15;
import dev.sculk.module.modules.combat.AimAssistModule;
import dev.sculk.module.modules.combat.AttackAura;
import dev.sculk.render.Animation;
import dev.sculk.render.Render2D;
import dev.sculk.render.Theme;
import dev.sculk.util.CombatUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

/** Content based HUD widgets: Target Info, Potions, Armor and Hotkeys. */
public final class HudWidgets {
   private static final MinecraftClient MC = MinecraftClient.getInstance();
   private static final int TARGET_W = 150;
   private static final int TARGET_H = 42;
   private static final int ARMOR_W = 4 * 22 + 8;
   private static final int ARMOR_H = 30;
   private static final long HOLD_MS = 1500L;
   private static final Animation HEALTH = new Animation(1.0F);
   private static Entity lastTarget;
   private static long lastSeen;

   private HudWidgets() {
   }

   public static boolean isWidget(HudElement element) {
      return element == HudElement.TARGET_INFO || element == HudElement.POTIONS || element == HudElement.ARMOR || element == HudElement.HOTKEYS;
   }

   private static boolean editing() {
      return MC.currentScreen instanceof HudEditorScreen;
   }

   /** Whether the widget has something to show right now (the editor always shows a preview). */
   public static boolean hasContent(HudElement element) {
      if (editing()) {
         return true;
      }

      if (MC.player == null) {
         return false;
      }

      return switch (element) {
         case TARGET_INFO -> trackedTarget() != null;
         case POTIONS -> !MC.player.getStatusEffects().isEmpty();
         case ARMOR -> hasArmor();
         case HOTKEYS -> !hotkeyRows().isEmpty();
         default -> false;
      };
   }

   public static int width(HudElement element) {
      return switch (element) {
         case TARGET_INFO -> TARGET_W;
         case ARMOR -> ARMOR_W;
         case POTIONS -> listWidth(potionRows());
         case HOTKEYS -> listWidth(hotkeyRows());
         default -> 0;
      };
   }

   public static int height(HudElement element) {
      return switch (element) {
         case TARGET_INFO -> TARGET_H;
         case ARMOR -> ARMOR_H;
         case POTIONS -> listHeight(potionRows());
         case HOTKEYS -> listHeight(hotkeyRows());
         default -> 15;
      };
   }

   public static void draw(DrawContext context, HudElement element, float x, float y, float w, float h, float fade) {
      Render2D.roundedRect(context, x, y, w, h, 6.0F, Theme.scaleAlpha(Theme.PANEL, fade * 0.72F));
      Render2D.roundedOutline(context, x, y, w, h, 6.0F, 1.0F, Theme.scaleAlpha(Theme.BORDER, fade));
      switch (element) {
         case TARGET_INFO -> drawTarget(context, x, y, fade);
         case ARMOR -> drawArmor(context, x, y, fade);
         case POTIONS -> drawList(context, x, y, fade, "Potions", potionRows());
         case HOTKEYS -> drawList(context, x, y, fade, "Hotkeys", hotkeyRows());
         default -> {
         }
      }
   }

   // ---------------------------------------------------------------- target

   private static Entity currentTarget() {
      for (Module module : SculkClient.modules()) {
         if (!module.isEnabled()) {
            continue;
         }

         if (module instanceof AttackAura aura && aura.currentTarget() != null) {
            return aura.currentTarget();
         }

         if (module instanceof AimAssistModule assist && assist.currentTarget() != null) {
            return assist.currentTarget();
         }
      }

      Entity last = CombatUtils.getLastInteracted();
      if (last != null && last.isAlive() && !last.isRemoved() && MC.player != null && last.distanceTo(MC.player) < 12.0F) {
         return last;
      }

      return null;
   }

   private static Entity trackedTarget() {
      Entity now = currentTarget();
      long time = System.currentTimeMillis();
      if (now != null) {
         lastTarget = now;
         lastSeen = time;
         return now;
      }

      if (lastTarget != null && !lastTarget.isRemoved() && time - lastSeen < HOLD_MS) {
         return lastTarget;
      }

      lastTarget = null;
      return null;
   }

   private static void drawTarget(DrawContext context, float x, float y, float fade) {
      Entity entity = editing() ? null : trackedTarget();
      String name = "Target";
      float health = 20.0F;
      float maxHealth = 20.0F;
      float absorption = 0.0F;
      if (entity instanceof LivingEntity living) {
         name = living.getName().getString();
         health = living.getHealth();
         maxHealth = Math.max(1.0F, living.getMaxHealth());
         absorption = living.getAbsorptionAmount();
      } else if (!editing()) {
         return;
      }

      float fraction = MathHelper.clamp(health / maxHealth, 0.0F, 1.0F);
      HEALTH.animate(fraction, 12.0F);
      float bar = HEALTH.get();

      // avatar: circle with the initial letter
      float cx = x + 8.0F + 13.0F;
      float cy = y + 8.0F + 13.0F;
      Render2D.circle(context, cx, cy, 13.0F, Theme.scaleAlpha(Theme.GUI_HEADER, fade));
      String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
      Render2D.textCentered(context, initial, cx, cy - 4.0F, Theme.scaleAlpha(Theme.ACCENT, fade));

      float textX = x + 8.0F + 26.0F + 6.0F;
      Render2D.text(context, Render2D.truncate(name, TARGET_W - 48), textX, y + 8.0F, Theme.scaleAlpha(Theme.TEXT, fade), true);

      float barX = textX;
      float barY = y + 21.0F;
      float barW = x + TARGET_W - 8.0F - barX;
      Render2D.roundedRect(context, barX, barY, barW, 5.0F, 2.5F, Theme.scaleAlpha(Theme.TRACK, fade));
      int color = Theme.mix(0xFFFF3A3A, 0xFF3AFF6A, bar);
      if (bar > 0.02F) {
         Render2D.roundedRect(context, barX, barY, Math.max(4.0F, barW * bar), 5.0F, 2.5F, Theme.scaleAlpha(color, fade));
      }

      String hp = String.format(java.util.Locale.ROOT, "%.1f", health + absorption) + " HP";
      Render2D.text(context, hp, textX, y + 29.0F, Theme.scaleAlpha(Theme.TEXT_DIM, fade), true);
      if (absorption > 0.0F) {
         Render2D.text(context, "+" + Math.round(absorption), textX + Render2D.textWidth(hp) + 4.0F, y + 29.0F, Theme.scaleAlpha(0xFFFFD04A, fade), true);
      }
   }

   // ----------------------------------------------------------------- armor

   private static boolean hasArmor() {
      for (int i = 0; i < 4; i++) {
         if (!MC.player.getInventory().getArmorStack(i).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private static void drawArmor(DrawContext context, float x, float y, float fade) {
      for (int slot = 0; slot < 4; slot++) {
         // armor stack 3 = helmet, 0 = boots; show helmet first
         ItemStack stack = MC.player == null ? ItemStack.EMPTY : MC.player.getInventory().getArmorStack(3 - slot);
         int ix = Math.round(x + 6.0F + slot * 22.0F);
         int iy = Math.round(y + 4.0F);
         if (stack.isEmpty()) {
            Render2D.roundedRect(context, ix + 1.0F, iy + 1.0F, 14.0F, 14.0F, 3.0F, Theme.scaleAlpha(Theme.GUI_HEADER, fade * 0.8F));
            continue;
         }

         context.drawItem(stack, ix, iy);
         if (stack.isDamageable()) {
            float left = 1.0F - (float)stack.getDamage() / (float)stack.getMaxDamage();
            Render2D.roundedRect(context, ix, y + 23.0F, 16.0F, 3.0F, 1.5F, Theme.scaleAlpha(Theme.TRACK, fade));
            int c = Theme.mix(0xFFFF3A3A, 0xFF3AFF6A, MathHelper.clamp(left, 0.0F, 1.0F));
            Render2D.roundedRect(context, ix, y + 23.0F, Math.max(2.0F, 16.0F * left), 3.0F, 1.5F, Theme.scaleAlpha(c, fade));
         }
      }
   }

   // ---------------------------------------------------------------- lists

   private record Row(String left, String right, boolean on) {
   }

   private static int listWidth(List<Row> rows) {
      int max = Render2D.textWidth("Hotkeys");
      for (Row row : rows) {
         max = Math.max(max, Render2D.textWidth(row.left()) + 10 + Render2D.textWidth(row.right()));
      }

      return max + 16;
   }

   private static int listHeight(List<Row> rows) {
      return 24 + Math.max(1, rows.size()) * 11 - 2;
   }

   private static void drawList(DrawContext context, float x, float y, float fade, String title, List<Row> rows) {
      Render2D.text(context, title, x + 8.0F, y + 6.0F, Theme.scaleAlpha(Theme.ACCENT, fade), true);
      Render2D.rect(context, x + 6.0F, y + 17.0F, listWidth(rows) - 12.0F, 1.0F, Theme.scaleAlpha(Theme.BORDER, fade));
      float rowY = y + 21.0F;
      for (Row row : rows) {
         int color = row.on() ? Theme.TEXT : Theme.TEXT_DIM;
         Render2D.text(context, row.left(), x + 8.0F, rowY, Theme.scaleAlpha(color, fade), true);
         float rightX = x + listWidth(rows) - 8.0F - Render2D.textWidth(row.right());
         Render2D.text(context, row.right(), rightX, rowY, Theme.scaleAlpha(Theme.TEXT_DIM, fade), true);
         rowY += 11.0F;
      }
   }

   private static List<Row> potionRows() {
      List<Row> rows = new ArrayList<>();
      if (MC.player != null) {
         Collection<StatusEffectInstance> effects = MC.player.getStatusEffects();
         for (StatusEffectInstance effect : effects) {
            String name = effect.getEffectType().value().getName().getString();
            if (effect.getAmplifier() > 0) {
               name = name + " " + (effect.getAmplifier() + 1);
            }

            rows.add(new Row(name, duration(effect.getDuration()), true));
         }
      }

      if (rows.isEmpty() && editing()) {
         rows.add(new Row("Speed 2", "0:42", true));
         rows.add(new Row("Strength", "1:15", true));
      }

      return rows;
   }

   private static String duration(int ticks) {
      if (ticks < 0) {
         return "inf";
      }

      int seconds = ticks / 20;
      return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
   }

   private static List<Row> hotkeyRows() {
      List<Row> rows = new ArrayList<>();
      for (Module module : SculkClient.modules()) {
         if (module.getKey() >= 0 && !(module instanceof Fps15)) {
            String key = InputUtil.fromKeyCode(module.getKey(), 0).getLocalizedText().getString();
            rows.add(new Row(module.getName(), "[" + key + "]", module.isEnabled()));
         }
      }

      if (rows.isEmpty() && editing()) {
         rows.add(new Row("AttackAura", "[R]", true));
         rows.add(new Row("Aim Assist", "[M]", false));
      }

      return rows;
   }
}
