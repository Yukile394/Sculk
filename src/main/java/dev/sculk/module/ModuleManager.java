package dev.sculk.module;

import dev.sculk.module.modules.client.Fps15;
import dev.sculk.module.modules.client.Fps16;
import dev.sculk.module.modules.client.Fps24;
import dev.sculk.module.modules.client.Fps25;
import dev.sculk.module.modules.client.SoundsModule;
import dev.sculk.module.modules.combat.AutoFirework;
import dev.sculk.module.modules.combat.AttackAura;
import dev.sculk.module.modules.misc.Nuker;
import dev.sculk.module.modules.render.Fullbright;
import dev.sculk.module.modules.combat.Criticals;
import dev.sculk.module.modules.combat.Fps12;
import dev.sculk.module.modules.combat.Fps13;
import dev.sculk.module.modules.combat.Fps14;
import dev.sculk.module.modules.combat.Fps31;
import dev.sculk.module.modules.combat.Fps32;
import dev.sculk.module.modules.render.TargetEsp;
import dev.sculk.render.Toasts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class ModuleManager {
   public static final ArrayList<Module> MODULAR = new ArrayList<>();
   private static final Map<Integer, Boolean> keyState = new HashMap<>();
   private static final int MIDDLE_BUTTON = -1002;

   public static ArrayList<Module> getModules() {
      return MODULAR;
   }

   public static List<Module> modules() {
      return MODULAR;
   }

   public static <T extends Module> T get(Class<T> type) {
      for (Module module : MODULAR) {
         if (type.isInstance(module)) {
            return (T)module;
         }
      }

      return null;
   }

   public static List<Module> byCategory(Category cat) {
      List<Module> out = new ArrayList<>();

      for (Module module : MODULAR) {
         if (module.getCategory() == cat) {
            out.add(module);
         }
      }

      return out;
   }

   public static void init() {
      for (Module module : MODULAR) {
         module.mc = MinecraftClient.getInstance();
      }
   }

   public static void tick() {
      MinecraftClient mc = MinecraftClient.getInstance();
      pollKeys();
      if (mc.player != null && mc.world != null) {
         for (Module module : MODULAR) {
            if (module.isEnabled()) {
               module.onTick();
            }
         }
      }
   }

   public static void renderHUD(DrawContext context, float tickDelta) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         for (Module module : MODULAR) {
            if (module.isEnabled()) {
               module.onRender2D(context, tickDelta);
            }
         }
      }
   }

   public static void bufferUpdate() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.currentScreen == null) {
         for (Module module : MODULAR) {
            if (module.isEnabled()) {
               module.onBufferUpdate();
            }
         }
      }
   }

   public static void renderTick(float tickDelta) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.world != null) {
         for (Module module : MODULAR) {
            if (module.isEnabled()) {
               module.onRenderTick(tickDelta);
            }
         }
      }
   }

   private static void pollKeys() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.getWindow() != null) {
         long window = mc.getWindow().getHandle();
         boolean screenOpen = mc.currentScreen != null;
         boolean inWorld = mc.player != null && mc.world != null;
         Set<Integer> watched = new HashSet<>();

         for (Module module : MODULAR) {
            if (module.getKey() >= 0) {
               watched.add(module.getKey());
            }
         }

         watched.add(340);
         watched.add(75);
         Map<Integer, Boolean> next = new HashMap<>();

         for (int key : watched) {
            next.put(key, InputUtil.isKeyPressed(window, key));
         }

         if (!screenOpen && inWorld) {
            boolean combo = isDown(next, 75) && isDown(next, 340) && !isDown(keyState, 75);
            if (combo) {
               Fps15 gui = get(Fps15.class);
               if (gui != null) {
                  if (!gui.isEnabled()) {
                     Toasts.show("made by bluerr");
                  }

                  gui.toggle();
               }
            } else {
               for (Module modulex : MODULAR) {
                  int key = modulex.getKey();
                  if (key >= 0) {
                     boolean down = isDown(next, key);
                     boolean was = isDown(keyState, key);
                     if (modulex.isHoldBind()) {
                        if (down != was) {
                           modulex.setEnabled(down);
                        }
                     } else if (down && !was) {
                        modulex.toggle();
                     }
                  }
               }
            }
         }

         boolean middleDown = GLFW.glfwGetMouseButton(window, 2) == 1;
         boolean middleWasDown = Boolean.TRUE.equals(keyState.get(-1002));
         if (middleDown && !middleWasDown && !screenOpen && inWorld) {
            for (Module modulexx : MODULAR) {
               if (modulexx.middleClick().get() && modulexx != get(Fps15.class)) {
                  modulexx.toggle();
               }
            }
         }

         next.put(-1002, middleDown);
         keyState.clear();
         keyState.putAll(next);
      }
   }

   private static boolean isDown(Map<Integer, Boolean> state, int key) {
      return Boolean.TRUE.equals(state.get(key));
   }

   static {
      MODULAR.add(new Fps12());
      MODULAR.add(new Fps13());
      MODULAR.add(new Fps14());
      MODULAR.add(new AutoFirework());
      MODULAR.add(new Fps31());
      MODULAR.add(new Fps32());
      MODULAR.add(new Criticals());
      MODULAR.add(new AttackAura());
      MODULAR.add(new Nuker());
      MODULAR.add(new TargetEsp());
      MODULAR.add(new Fullbright());
      MODULAR.add(new Fps16());
      MODULAR.add(new Fps24());
      MODULAR.add(new Fps25());
      MODULAR.add(new SoundsModule());
      MODULAR.add(new Fps15());
   }
}
