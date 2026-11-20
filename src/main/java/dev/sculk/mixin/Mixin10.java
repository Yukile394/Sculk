package dev.sculk.mixin;

import dev.sculk.SculkClient;
import dev.sculk.module.modules.combat.AttackAura;
import dev.sculk.util.RotationManager;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Movement input hook: silent rotation move correction and AttackAura sprint / target-follow handling. */
@Mixin({KeyboardInput.class})
public abstract class Mixin10 {
   @Inject(
      method = {"tick"},
      at = {@At("RETURN")}
   )
   private void sculk$input(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
      Input input = (Input)(Object)this;
      AttackAura aura = SculkClient.get(AttackAura.class);
      if (aura != null && aura.isEnabled()) {
         aura.onInput(input, slowDown, slowDownFactor);
      }

      if (RotationManager.correcting()) {
         RotationManager.fixMovement(input, RotationManager.camYaw(), slowDown, slowDownFactor);
      }
   }
}
