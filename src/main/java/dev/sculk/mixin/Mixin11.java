package dev.sculk.mixin;

import dev.sculk.util.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the local player model (third person, front view, other angles) with a filtered rotation while a silent rotation
 * is active, so the per-tick aim jumps do not make the model shake. Only render state is touched; the real rotation that
 * goes to the server is restored right after the model is drawn.
 */
@Mixin({LivingEntityRenderer.class})
public abstract class Mixin11 {
   @Unique
   private boolean sculk$swapped;

   @Inject(
      method = {"Lnet/minecraft/client/render/entity/LivingEntityRenderer;render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"},
      at = {@At("HEAD")}
   )
   private void sculk$smoothHead(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider provider, int light, CallbackInfo ci) {
      this.sculk$swapped = false;
      ClientPlayerEntity self = MinecraftClient.getInstance().player;
      if (self != null && entity == self) {
         this.sculk$swapped = RotationManager.beginModelRender(self, tickDelta);
      }
   }

   @Inject(
      method = {"Lnet/minecraft/client/render/entity/LivingEntityRenderer;render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"},
      at = {@At("RETURN")}
   )
   private void sculk$restoreHead(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider provider, int light, CallbackInfo ci) {
      if (this.sculk$swapped) {
         this.sculk$swapped = false;
         ClientPlayerEntity self = MinecraftClient.getInstance().player;
         if (self != null && entity == self) {
            RotationManager.endModelRender(self);
         }
      }
   }
}
