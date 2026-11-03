package dev.sculk.module.modules.client;

import dev.sculk.gui.HudEditorScreen;
import dev.sculk.hud.HudElement;
import dev.sculk.hud.HudLayout;
import dev.sculk.hud.HudRenderer;
import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.ButtonSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.render.Toasts;
import net.minecraft.client.gui.DrawContext;

public final class Fps16 extends Module {
   private final BooleanSetting watermark = new BooleanSetting("Watermark", true);
   private final BooleanSetting fps = new BooleanSetting("FPS", true);
   private final BooleanSetting coords = new BooleanSetting("Coordinates", true);
   private final BooleanSetting cps = new BooleanSetting("CPS", true);
   private final BooleanSetting ping = new BooleanSetting("Ping", true);
   private final BooleanSetting clock = new BooleanSetting("Clock", true);
   private final BooleanSetting moduleList = new BooleanSetting("Module List", true);
   private final ButtonSetting editor = new ButtonSetting("Edit HUD", this::openEditor).instant();

   public Fps16() {
      super("HUD", "On-screen overlay", Category.CLIENT);
      this.addSettings(new Setting[]{this.watermark, this.fps, this.coords, this.cps, this.ping, this.clock, this.moduleList, this.editor});
   }

   private void openEditor() {
      this.mc.setScreen(new HudEditorScreen());
   }

   @Override
   public void onRender2D(DrawContext context, float tickDelta) {
      if (!this.mc.options.hudHidden) {
         int screenWidth = context.getScaledWindowWidth();
         int screenHeight = context.getScaledWindowHeight();

         for (HudElement element : HudElement.values()) {
            HudRenderer.beginFrame(element, HudRenderer.active(element, this.enabled(element)));
            if (HudRenderer.visible(element)) {
               float elementWidth = HudRenderer.animatedWidth(element);
               float elementHeight = HudRenderer.animatedHeight(element);
               float x = HudLayout.x(element) * Math.max(0.0F, (float)screenWidth - elementWidth);
               float y = HudLayout.y(element) * Math.max(0.0F, (float)screenHeight - elementHeight);
               HudRenderer.draw(context, element, x, y);
            }
         }

         Toasts.render(context);
      }
   }

   private boolean enabled(HudElement element) {
      return switch (element) {
         case WATERMARK -> this.watermark.get();
         case FPS -> this.fps.get();
         case COORDS -> this.coords.get();
         case CPS -> this.cps.get();
         case PING -> this.ping.get();
         case CLOCK -> this.clock.get();
         case MODULE_LIST -> this.moduleList.get();
      };
   }
}
