package dev.sculk.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({ClientPlayerInteractionManager.class})
public interface Mixin13 {
   /** Sends UpdateSelectedSlotC2SPacket for the current selectedSlot if it differs from the last slot the server was told about. */
   @Invoker("syncSelectedSlot")
   void sculk$syncSelectedSlot();
}
