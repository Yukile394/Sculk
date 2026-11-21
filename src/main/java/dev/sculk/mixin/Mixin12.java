package dev.sculk.mixin;

import dev.sculk.util.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First person hands: the held item sway is computed from (player rotation - smoothed render rotation). While a silent
 * rotation is active the player rotation is the aim, so every aim change made the items swing. The hands are drawn with the
 * camera rotation instead, and the real values are restored right after.
 */
@Mixin({HeldItemRenderer.class})
public abstract class Mixin12 {
   @Unique
   private boolean sculk$swapped;

   @Inject(
      method = {"Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"},
      at = {@At("HEAD")}
   )
   private void sculk$handHead(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate provider, ClientPlayerEntity player, int light, CallbackInfo ci) {
      this.sculk$swapped = false;
      if (player == MinecraftClient.getInstance().player && RotationManager.active()) {
         RotationManager.beginHandRender(player);
         this.sculk$swapped = true;
      }
   }

   @Inject(
      method = {"Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"},
      at = {@At("RETURN")}
   )
   private void sculk$handReturn(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate provider, ClientPlayerEntity player, int light, CallbackInfo ci) {
      if (this.sculk$swapped) {
         this.sculk$swapped = false;
         RotationManager.endHandRender(player);
      }
   }
}
