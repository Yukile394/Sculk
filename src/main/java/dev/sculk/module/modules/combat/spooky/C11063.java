package dev.sculk.module.modules.combat.spooky;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Box;

/** Nursultan class11063: is the player inside a critical fall window (original method names kept). */
public class C11063 {
   private boolean fall(float fallDistance, double vy) {
      return vy < -0.008 && fallDistance + 0.018F >= 0.08F;
   }

   public boolean N(C11087 ctx, LivingEntity target) {
      if (ctx.critOff) {
         return true;
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity p = mc.player;
      if (p == null || mc.world == null || target == null || this.R()) {
         return false;
      }

      return this.fall(p.fallDistance, p.getVelocity().y) || this.N();
   }

   private boolean N() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity p = mc.player;
      double vy = p.getVelocity().y;
      if (vy >= -0.008) {
         return false;
      }

      Box box = p.getBoundingBox();
      float fall = p.fallDistance;
      for (int i = 0; i < 2; i++) {
         box = box.offset(0.0, vy - 0.001, 0.0);
         if (!mc.world.getBlockCollisions(p, box).iterator().hasNext()) {
            return false;
         }

         fall += (float)Math.max(0.0, -vy);
         if (this.fall(fall, vy)) {
            return true;
         }

         vy = (vy - 0.08) * 0.98;
      }

      return false;
   }

   private boolean R() {
      ClientPlayerEntity p = MinecraftClient.getInstance().player;
      return p.isClimbing() || p.isTouchingWater() || p.isSwimming() || p.isInLava() || p.hasVehicle()
         || p.hasStatusEffect(StatusEffects.BLINDNESS) || p.hasStatusEffect(StatusEffects.LEVITATION);
   }
}
