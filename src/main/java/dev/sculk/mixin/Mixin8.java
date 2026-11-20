package dev.sculk.mixin;

import dev.sculk.util.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While a silent rotation is active the mouse moves the camera rotation instead of the player. */
@Mixin({Entity.class})
public abstract class Mixin8 {
   @Inject(
      method = {"changeLookDirection"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void sculk$silentLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
      if (RotationManager.active() && (Object)this == MinecraftClient.getInstance().player) {
         RotationManager.mouse(cursorDeltaX, cursorDeltaY);
         ci.cancel();
      }
   }
}
