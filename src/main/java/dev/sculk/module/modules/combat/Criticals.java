package dev.sculk.module.modules.combat;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.util.ServerClock;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;

public final class Criticals extends Module {
   public static boolean cancelCrit;
   private final ModeSetting<Criticals.Mode> mode = new ModeSetting<>("Mode", Criticals.Mode.UpdatedNCP)
      .describe("Packet pattern sent before every attack");

   public Criticals() {
      super("Criticals", "Turns every hit into a critical hit (ZeltrixWare modes)", Category.COMBAT);
      this.addSettings(new Setting[]{this.mode});
   }

   public void onAttack(Entity target) {
      if (cancelCrit || target == null || target instanceof EndCrystalEntity) {
         return;
      }

      ClientPlayerEntity player = this.getMC().player;
      if (player == null || this.getMC().world == null) {
         return;
      }

      boolean grim = this.mode.is(Criticals.Mode.Grim);
      if ((player.isOnGround() || player.getAbilities().flying || grim) && !player.isInLava() && !player.isSubmergedInWater()) {
         // ServerClock ile tick senkronizasyonu
         if (!grim && ServerClock.jammed()) {
            return;
         }
         switch (this.mode.getValue()) {
            case Ncp:
               this.packet(player, 0.0625, false);
               this.packet(player, 0.0, false);
               break;
            case Strict:
               this.packet(player, 0.062600301692775, false);
               this.packet(player, 0.07260029960661, false);
               this.packet(player, 0.0, false);
               this.packet(player, 0.0, false);
               break;
            case OldNCP:
               this.packet(player, 1.058293536E-5, false);
               this.packet(player, 9.16580235E-6, false);
               this.packet(player, 1.0371854E-7, false);
               break;
            case UpdatedNCP:
               // Polar/Grim icin daha guvenli mikro delta
               this.packet(player, 2.71875E-7, false);
               this.packet(player, 0.0, false);
               break;
            case Grim:
               // Grim icin onceki pozisyonu koru, sadece onGround false gonder
               if (!player.isOnGround()) {
                  this.packetFull(player, 0.0, false);
               } else {
                  // Grim icin yerden kalkma simulasyonu
                  this.packet(player, 1.0E-7, false);
                  this.packet(player, 0.0, false);
               }
               break;
            case Polar:
               // Polar icin 0.0001 delta + rotation sync
               this.packet(player, 1.0E-4, false);
               this.packetFull(player, 0.0, false);
               break;
         }
      }
   }

   private void packet(ClientPlayerEntity player, double yDelta, boolean full) {
      ClientPlayNetworkHandler network = this.getMC().getNetworkHandler();
      if (network == null) return;
      double y = player.getY() + yDelta;
      // NaN/inf korumasi
      if (Double.isNaN(y) || Double.isInfinite(y)) return;
      network.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
         player.getX(), y, player.getZ(), false));
   }

   private void packetFull(ClientPlayerEntity player, double yDelta, boolean onGround) {
      ClientPlayNetworkHandler network = this.getMC().getNetworkHandler();
      if (network == null) return;
      double y = player.getY() + yDelta;
      if (Double.isNaN(y) || Double.isInfinite(y)) return;
      // GCD uyumlu yaw/pitch
      float yaw = player.getYaw();
      float pitch = player.getPitch();
      network.sendPacket(new PlayerMoveC2SPacket.Full(
         player.getX(), y, player.getZ(), yaw, pitch, onGround));
   }

   public static enum Mode {
      Ncp, Strict, OldNCP, UpdatedNCP, Grim, Polar;
   }
}
