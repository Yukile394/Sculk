package dev.sculk.mixin;

import dev.sculk.util.ServerClock;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPlayNetworkHandler.class})
public abstract class Mixin7 {
   @Inject(
      method = {"Lnet/minecraft/client/network/ClientPlayNetworkHandler;onWorldTimeUpdate(Lnet/minecraft/network/packet/s2c/play/WorldTimeUpdateS2CPacket;)V"},
      at = {@At("HEAD")}
   )
   private void sculk$trackServerTick(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
      ServerClock.record(packet.getTime());
   }
}
