package dev.sculk.mixin;

import dev.sculk.SculkClient;
import dev.sculk.module.modules.combat.AutoTotem;
import dev.sculk.util.ServerClock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
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

   /** AutoTotem: end crystal spawned next to the player. Handlers run twice (network thread, then client thread); only the client thread call is used. */
   @Inject(
      method = {"Lnet/minecraft/client/network/ClientPlayNetworkHandler;onEntitySpawn(Lnet/minecraft/network/packet/s2c/play/EntitySpawnS2CPacket;)V"},
      at = {@At("HEAD")}
   )
   private void sculk$totemCrystalSpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
      AutoTotem totem = SculkClient.get(AutoTotem.class);
      if (totem != null && totem.isEnabled() && MinecraftClient.getInstance().isOnThread()) {
         totem.onEntitySpawn(packet);
      }
   }

   /** AutoTotem: obsidian placed next to the player. */
   @Inject(
      method = {"Lnet/minecraft/client/network/ClientPlayNetworkHandler;onBlockUpdate(Lnet/minecraft/network/packet/s2c/play/BlockUpdateS2CPacket;)V"},
      at = {@At("HEAD")}
   )
   private void sculk$totemBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
      AutoTotem totem = SculkClient.get(AutoTotem.class);
      if (totem != null && totem.isEnabled() && MinecraftClient.getInstance().isOnThread()) {
         totem.onBlockUpdate(packet);
      }
   }
}
