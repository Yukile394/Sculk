package dev.sculk.util;

import dev.sculk.module.modules.combat.Criticals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Critical hit helpers used by Auto Firework's trigger.
 * EclipseCrit = classic packet crit (0.0625 / 0.0 offsets).
 * PhantomCrit = micro offset packet crit (2.71875E-7 / 0.0), almost invisible to movement checks.
 * Both skip the packets when the player already is falling (natural crit) and drop sprint for the hit.
 */
public final class CritUtils {
   private static final double ECLIPSE_OFFSET = 0.0625;
   private static final double PHANTOM_OFFSET = 2.71875E-7;

   private CritUtils() {
   }

   /** Vanilla crit conditions that no packet can fix. If this is false the hit is skipped. */
   public static boolean canCrit(ClientPlayerEntity player) {
      return !player.isTouchingWater()
         && !player.isSubmergedInWater()
         && !player.isInLava()
         && !player.isClimbing()
         && !player.isSwimming()
         && !player.hasVehicle()
         && !player.hasStatusEffect(StatusEffects.BLINDNESS);
   }

   public static boolean fallingCrit(ClientPlayerEntity player) {
      return !player.isOnGround() && player.fallDistance > 0.0F;
   }

   public static void attack(Entity target, boolean eclipse, boolean sprintReset) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      ClientPlayNetworkHandler network = mc.getNetworkHandler();
      if (player == null || network == null || mc.interactionManager == null) {
         return;
      }

      boolean resetSprint = sprintReset && player.isSprinting();
      if (resetSprint) {
         network.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
      }

      if (!fallingCrit(player)) {
         double offset = eclipse ? ECLIPSE_OFFSET : PHANTOM_OFFSET;
         network.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(player.getX(), player.getY() + offset, player.getZ(), false));
         network.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(player.getX(), player.getY(), player.getZ(), false));
      }

      Criticals.cancelCrit = true;

      try {
         CombatUtils.hitEntity(target, true);
      } finally {
         Criticals.cancelCrit = false;
      }

      if (resetSprint && player.isSprinting()) {
         network.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_SPRINTING));
      }
   }
}
