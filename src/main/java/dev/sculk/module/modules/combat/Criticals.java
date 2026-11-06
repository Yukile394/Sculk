package dev.sculk.module.modules.combat;

import dev.sculk.SculkClient;
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

public final class Criticals extends Module {
   public static boolean cancelCrit;
   private final ModeSetting<Criticals.Mode> mode = new ModeSetting<>("Mode", Criticals.Mode.UpdatedNCP)
      .describe("Packet pattern sent before every attack");
   private long lastCritTick = Long.MIN_VALUE;
   private long lastCritMs = 0L;
   private int cooldownTicks = 0;

   public Criticals() {
      super("Criticals", "Turns every hit into a critical hit", Category.COMBAT);
      this.addSettings(new Setting[]{this.mode});
   }

   public void onAttack(Entity target) {
      if (cancelCrit || target == null || target instanceof EndCrystalEntity) return;
      ClientPlayerEntity player = this.getMC().player;
      if (player == null || this.getMC().world == null) return;

      // AimAssist aktifse crit'i daha muhafazakar yap -> Polar ML flag engeli
      boolean aimActive = this.isAimAssistActive();
      int baseCooldown = aimActive ? 6 : 3;

      if (this.cooldownTicks > 0) {
         this.cooldownTicks--;
         return;
      }

      long tick = ServerClock.tick();
      if (tick != Long.MIN_VALUE) {
         if (tick == this.lastCritTick) return;
         this.lastCritTick = tick;
      } else {
         long now = System.currentTimeMillis();
         long minGap = aimActive ? 200L : 100L;
         if (now - this.lastCritMs < minGap) return;
         this.lastCritMs = now;
      }

      boolean grim = this.mode.is(Criticals.Mode.Grim);
      if ((player.isOnGround() || player.getAbilities().flying || grim)
         && !player.isInLava() && !player.isSubmergedInWater()) {
         switch (this.mode.getValue()) {
            case Ncp -> { this.packet(player, 0.0625); this.packet(player, 0.0); }
            case Strict -> {
               this.packet(player, 0.062600301692775);
               this.packet(player, 0.07260029960661);
               this.packet(player, 0.0);
            }
            case OldNCP -> {
               this.packet(player, 1.058293536E-5);
               this.packet(player, 9.16580235E-6);
               this.packet(player, 1.0371854E-7);
            }
            case UpdatedNCP -> { this.packet(player, 2.71875E-7); this.packet(player, 0.0); }
            case Grim -> {
               if (!player.isOnGround()) this.packet(player, -1.0E-6);
               else { this.packet(player, 1.0E-7); this.packet(player, 0.0); }
            }
            case Polar -> { this.packet(player, 1.0E-4); this.packet(player, 0.0); }
         }
         this.cooldownTicks = baseCooldown;
      }
   }

   private boolean isAimAssistActive() {
      for (Module module : SculkClient.modules()) {
         if (module instanceof AimAssistModule && module.isEnabled()) return true;
      }
      return false;
   }

   private void packet(ClientPlayerEntity player, double yDelta) {
      ClientPlayNetworkHandler network = this.getMC().getNetworkHandler();
      if (network == null) return;
      double y = player.getY() + yDelta;
      if (Double.isNaN(y) || Double.isInfinite(y)) return;
      network.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
         player.getX(), y, player.getZ(), false));
   }

   public static enum Mode { Ncp, Strict, OldNCP, UpdatedNCP, Grim, Polar; }
}
