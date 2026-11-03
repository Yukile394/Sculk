package dev.sculk.mixin;

import dev.sculk.SculkClient;
import dev.sculk.module.modules.combat.Fps32;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({Entity.class})
public abstract class Mixin6 {
   @ModifyVariable(
      method = {"setVelocityClient"},
      at = @At("HEAD"),
      ordinal = 0,
      argsOnly = true
   )
   private double sculk$scaleVelocityX(double x) {
      Fps32 velocity = SculkClient.get(Fps32.class);
      return velocity != null && velocity.applies((Entity)(Object)this) ? x * velocity.horizontalScale() : x;
   }

   @ModifyVariable(
      method = {"setVelocityClient"},
      at = @At("HEAD"),
      ordinal = 1,
      argsOnly = true
   )
   private double sculk$scaleVelocityY(double y) {
      Fps32 velocity = SculkClient.get(Fps32.class);
      return velocity != null && velocity.applies((Entity)(Object)this) ? y * velocity.verticalScale() : y;
   }

   @ModifyVariable(
      method = {"setVelocityClient"},
      at = @At("HEAD"),
      ordinal = 2,
      argsOnly = true
   )
   private double sculk$scaleVelocityZ(double z) {
      Fps32 velocity = SculkClient.get(Fps32.class);
      return velocity != null && velocity.applies((Entity)(Object)this) ? z * velocity.horizontalScale() : z;
   }
}
