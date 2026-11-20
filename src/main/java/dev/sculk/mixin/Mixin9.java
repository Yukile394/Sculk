package dev.sculk.mixin;

import dev.sculk.util.RotationManager;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders the camera rotation while the player entity carries the silent aim rotation. */
@Mixin({Camera.class})
public abstract class Mixin9 {
   @Shadow
   protected abstract void setRotation(float yaw, float pitch);

   @Inject(
      method = {"update"},
      at = {@At("RETURN")}
   )
   private void sculk$silentCamera(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
      if (RotationManager.active() && focusedEntity == net.minecraft.client.MinecraftClient.getInstance().player) {
         this.setRotation(RotationManager.camYaw(), RotationManager.camPitch());
      }
   }
}
