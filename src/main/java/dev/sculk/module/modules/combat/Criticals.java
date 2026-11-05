package dev.sculk.module.modules.combat;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.Setting;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/** ZeltrixWare Criticals, same packet modes. Hooked from ClientPlayerInteractionManager#attackEntity. */
public final class Criticals extends Module {
   public static boolean cancelCrit;
   private final ModeSetting<Criticals.Mode> mode = new ModeSetting<>("Mode", Criticals.Mode.UpdatedNCP).describe("Packet pattern sent before every attack");

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
               this.packet(player, 2.71875E-7, false);
               this.packet(player, 0.0, false);
               break;
            case Grim:
               if (!player.isOnGround()) {
                  this.packet(player, -1.0E-6, true);
               }
         }
      }
   }

   private void packet(ClientPlayerEntity player, double yDelta, boolean full) {
      ClientPlayNetworkHandler network = this.getMC().getNetworkHandler();
      if (network == null) {
         return;
      }

      if (full) {
         network.sendPacket(
            new PlayerMoveC2SPacket.Full(player.getX(), player.getY() + yDelta, player.getZ(), player.getYaw(), player.getPitch(), false)
         );
      } else {
         network.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(player.getX(), player.getY() + yDelta, player.getZ(), false));
      }
   }

   public static enum Mode {
      Ncp,
      Strict,
      OldNCP,
      UpdatedNCP,
      Grim;
   }
}
