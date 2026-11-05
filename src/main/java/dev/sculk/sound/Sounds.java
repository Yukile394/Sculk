package dev.sculk.sound;

import dev.sculk.SculkClient;
import dev.sculk.module.modules.client.SoundsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * ZeltrixWare sound set (enable / disable / click-gui sounds).
 * Played as plain UI sounds, so no registry entry is needed and it works on every server.
 */
public final class Sounds {
   public static final Identifier ENABLE = Identifier.of("sculk", "enable");
   public static final Identifier DISABLE = Identifier.of("sculk", "disable");
   public static final Identifier KEYPRESS = Identifier.of("sculk", "keypress");
   public static final Identifier KEYRELEASE = Identifier.of("sculk", "keyrelease");
   public static final Identifier BOOLEAN = Identifier.of("sculk", "boolean");
   public static final Identifier SCROLL = Identifier.of("sculk", "scroll");
   public static final Identifier SWIPE_IN = Identifier.of("sculk", "swipein");
   public static final Identifier SWIPE_OUT = Identifier.of("sculk", "swipeout");

   private static long lastScroll;
   private static long lastSlider;

   private Sounds() {
   }

   private static SoundsModule config() {
      return SculkClient.get(SoundsModule.class);
   }

   private static void play(Identifier id, float volume) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.getSoundManager() == null || volume <= 0.0F) {
         return;
      }

      mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(id), 1.0F, volume));
   }

   public static void toggle(boolean enabled) {
      SoundsModule cfg = config();
      if (cfg != null && cfg.isEnabled() && cfg.moduleSounds()) {
         play(enabled ? ENABLE : DISABLE, cfg.volume());
      }
   }

   private static void gui(Identifier id) {
      SoundsModule cfg = config();
      if (cfg != null && cfg.isEnabled() && cfg.guiSounds()) {
         play(id, cfg.volume());
      }
   }

   public static void bool() {
      gui(BOOLEAN);
   }

   public static void swipeIn() {
      gui(SWIPE_IN);
   }

   public static void swipeOut() {
      gui(SWIPE_OUT);
   }

   public static void keyPress() {
      gui(KEYPRESS);
   }

   public static void keyRelease() {
      gui(KEYRELEASE);
   }

   /** Hover tick, rate limited like ZeltrixWare (one every 50 ms). */
   public static void scroll() {
      long now = System.currentTimeMillis();
      if (now - lastScroll >= 50L) {
         lastScroll = now;
         gui(SCROLL);
      }
   }

   public static void slider() {
      long now = System.currentTimeMillis();
      if (now - lastSlider >= 50L) {
         lastSlider = now;
         gui(SCROLL);
      }
   }
}
