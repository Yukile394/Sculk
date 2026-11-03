package dev.sculk.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({MinecraftClient.class})
public interface Mixin3 {
   @Accessor("mouse")
   Mouse sculk$getMouse();

   @Accessor("itemUseCooldown")
   int sculk$getItemUseCooldown();

   @Accessor("itemUseCooldown")
   void sculk$setItemUseCooldown(int var1);
}
