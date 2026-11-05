package dev.sculk;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.ModuleManager;
import dev.sculk.module.modules.client.Fps15;
import dev.sculk.module.modules.client.Fps25;
import dev.sculk.util.ConfigManager;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import dev.sculk.module.modules.render.TargetEsp;
import net.minecraft.client.render.RenderTickCounter;

public class SculkClient implements ClientModInitializer {
   public static final String MOD_ID = "sculk";
   public static final String VERSION = "1.0.0-beta";
   public static final String NAME = "SculkClient";
   public static final ModuleManager MANAGER = new ModuleManager();

   public static ModuleManager get() {
      return MANAGER;
   }

   public static List<Module> modules() {
      return ModuleManager.modules();
   }

   public static <T extends Module> T get(Class<T> type) {
      return ModuleManager.get(type);
   }

   public static List<Module> byCategory(Category cat) {
      return ModuleManager.byCategory(cat);
   }

   public void onInitializeClient() {
      ModuleManager.init();
      ConfigManager.load(modules());
      Fps15 gui = get(Fps15.class);
      if (gui != null) {
         gui.setEnabledSilently(false);
      }

      Fps25 friends = get(Fps25.class);
      if (friends != null) {
         friends.setEnabledSilently(false);
      }

      WorldRenderEvents.LAST.register(context -> {
         TargetEsp esp = get(TargetEsp.class);
         if (esp != null && esp.isEnabled()) {
            esp.render(context.matrixStack(), net.minecraft.client.MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true));
         }
      });
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> ModuleManager.tick());
      HudRenderCallback.EVENT.register((HudRenderCallback)(ctx, tickCounter) -> ModuleManager.renderHUD(ctx, tickCounter.getTickDelta(true)));
   }
}
