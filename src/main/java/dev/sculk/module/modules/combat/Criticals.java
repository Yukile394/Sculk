package dev.sculk.module.modules.combat;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.util.ServerClock;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;

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

   @Override
   public void onEnable() {
      this.cooldownTicks = 0;
      this.lastCritTick = Long.MIN_VALUE;
      this.lastCritMs = 0L;
   }

   @Override
   public void onTick() {
      if (this.cooldownTicks > 0) this.cooldownTicks--;
   }

   public void onAttack(Entity target) {
      if (cancelCrit || target == null || target instanceof EndCrystalEntity) return;
      ClientPlayerEntity player = this.getMC().player;
      if (player == null || this.getMC().world == null) return;

      // Vanilla crit'in imkansiz oldugu durumlar
      if (player.getAbilities().flying || player.isSpectator()) return;
      if (player.isInLava() || player.isSubmergedInWater() || player.isTouchingWater()) return;
      if (player.isFallFlying()) return;

      // Merdiven kontrolu: block state tag ile (mappings bagimsiz)
      if (this.isOnClimbable(player)) return;

      // Dogal dusuyorsak vanilla crit zaten calisacak -> paket spam etme
      boolean naturallyFalling = !player.isOnGround()
         && player.getVelocity().y < 0.0
         && player.fallDistance > 0.0f;
      if (naturallyFalling) return;

      // Cooldown gate
      if (this.cooldownTicks > 0) return;

      // Server tick gate
      long tick = ServerClock.tick();
      if (tick != Long.MIN_VALUE) {
         if (tick == this.lastCritTick) return;
         this.lastCritTick = tick;
      } else {
         long now = System.currentTimeMillis();
         if (now - this.lastCritMs < 100L) return;
         this.lastCritMs = now;
      }

      boolean onGround = player.isOnGround();
      boolean grim = this.mode.is(Criticals.Mode.Grim);
      boolean polar = this.mode.is(Criticals.Mode.Polar);

      if (grim || polar) {
         if (!onGround) {
            this.packet(player, 0.0);
         } else {
            double microJump = grim ? 0.08 : 0.01;
            this.packet(player, microJump);
            this.packet(player, 0.0);
         }
         this.cooldownTicks = 5;
      } else {
         if (!onGround) return;
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
            default -> { this.packet(player, 2.71875E-7); this.packet(player, 0.0); }
         }
         this.cooldownTicks = 3;
      }
   }

   /**
    * Oyuncunun ayaklarindaki ve gogsundeki bloklarin climbable tag'ine bakarak
    * merdiven/uzum/sarmaşık kontrolu yapar. Mappings farkindan bagimsiz calisir.
    */
   private boolean isOnClimbable(ClientPlayerEntity player) {
      if (this.getMC().world == null) return false;
      BlockPos feet = player.getBlockPos();
      BlockPos body = feet.up();
      BlockPos head = body.up();
      var world = this.getMC().world;
      return world.getBlockState(feet).isIn(BlockTags.CLIMBABLE)
         || world.getBlockState(body).isIn(BlockTags.CLIMBABLE)
         || world.getBlockState(head).isIn(BlockTags.CLIMBABLE)
         || world.getBlockState(feet).isOf(Blocks.SCAFFOLDING)
         || world.getBlockState(body).isOf(Blocks.SCAFFOLDING);
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
