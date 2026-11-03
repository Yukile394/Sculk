package dev.sculk.util;

import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;

public final class CombatUtils {
   private static final MinecraftClient MC = MinecraftClient.getInstance();
   private static Entity lastInteracted;

   private CombatUtils() {
   }

   public static void hitEntity(Entity entity, boolean swingHand) {
      if (MC.interactionManager != null && MC.player != null) {
         MC.interactionManager.attackEntity(MC.player, entity);
         if (swingHand) {
            MC.player.swingHand(Hand.MAIN_HAND);
         }
      }
   }

   public static void noteInteracted(Entity entity) {
      if (MC.player != null && entity != null && entity != MC.player && entity instanceof PlayerEntity) {
         lastInteracted = entity;
      }
   }

   public static Entity getLastInteracted() {
      return lastInteracted;
   }

   public static void resetInteracted() {
      lastInteracted = null;
   }

   public static boolean isShieldFacingAway(PlayerEntity player) {
      if (MC.player != null && player != null) {
         Vec3d toUs = MC.player.getPos().subtract(player.getPos()).normalize();
         double yaw = Math.toRadians((double)player.getYaw());
         double pitch = Math.toRadians((double)player.getPitch());
         Vec3d facing = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).normalize();
         return facing.dotProduct(toUs) < 0.0;
      } else {
         return false;
      }
   }
}
