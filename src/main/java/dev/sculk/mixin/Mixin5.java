package dev.sculk.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Mouse.class})
public interface Mixin5 {
   @Invoker("onMouseButton")
   void sculk$onMouseButton(long var1, int var3, int var4, int var5);
}
