package dev.sculk.mixin;

import dev.sculk.util.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Camera follows the camera rotation while the player entity carries the silent aim rotation.
 * The rotation is replaced where Camera.update reads it from the entity, so everything computed afterwards (the third person
 * pull-back along the view direction, the 180 degree flip of the front view) uses the same rotation. The old version set the
 * rotation again at the end of update(), after the camera had already been moved back along the aim direction, which made the
 * third person camera orbit around the player with every aim change.
 */
@Mixin({Camera.class})
public abstract class Mixin9 {
   @Redirect(
      method = {"update"},
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F")
   )
   private float sculk$cameraYaw(Entity entity, float tickDelta) {
      if (RotationManager.active() && entity == MinecraftClient.getInstance().player) {
         return RotationManager.camYaw();
      }

      return entity.getYaw(tickDelta);
   }

   @Redirect(
      method = {"update"},
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F")
   )
   private float sculk$cameraPitch(Entity entity, float tickDelta) {
      if (RotationManager.active() && entity == MinecraftClient.getInstance().player) {
         return RotationManager.camPitch();
      }

      return entity.getPitch(tickDelta);
   }
}
