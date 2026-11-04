package dev.sculk.mixin;

import dev.sculk.SculkClient;
import dev.sculk.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MinecraftClient.class})
public abstract class Mixin4 {
   @Inject(
      method = {"Lnet/minecraft/client/MinecraftClient;handleInputEvents()V"},
      at = {@At("HEAD")}
   )
   private void sculk$onBufferUpdate(CallbackInfo ci) {
      SculkClient.get();
      ModuleManager.bufferUpdate();
   }

   @Inject(
      method = {"Lnet/minecraft/client/MinecraftClient;render(Z)V"},
      at = {@At("HEAD")}
   )
   private void sculk$onRenderTick(boolean tick, CallbackInfo ci) {
      SculkClient.get();
<<<<<<< HEAD
      ModuleManager.renderTick(MinecraftClient.getInstance().getTickDelta());
=======
      ModuleManager.renderTick(MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true));
>>>>>>> a073ff2 (Guncelleme)
   }
}
