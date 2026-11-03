package dev.sculk.mixin;

import dev.sculk.SculkClient;
import dev.sculk.module.modules.combat.Fps31;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.CobwebBlock;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({CobwebBlock.class})
public abstract class Mixin1 {
   @Inject(
      method = {"onEntityCollision"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void sculk$skipCobwebSlow(BlockState state, World world, BlockPos pos, Entity entity, CallbackInfo ci) {
      Fps31 elyFix = SculkClient.get(Fps31.class);
      if (elyFix != null && elyFix.ignoresCobweb(entity)) {
         ci.cancel();
      }
   }
}
