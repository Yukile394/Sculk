package dev.sculk.module.modules.render;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/** Fullbright: keeps a client side night vision effect on so dark areas are fully lit. */
public final class Fullbright extends Module {
   private boolean applied;

   public Fullbright() {
      super("Fullbright", "See clearly in the dark", Category.RENDER);
   }

   @Override
   public void onTick() {
      if (this.mc == null || this.mc.player == null) {
         return;
      }

      StatusEffectInstance current = this.mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
      if (current == null || current.getDuration() < 400 && this.applied) {
         this.mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0, false, false, false));
         this.applied = true;
      }
   }

   @Override
   public void onDisable() {
      if (this.mc != null && this.mc.player != null && this.applied) {
         this.mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
      }

      this.applied = false;
   }
}
