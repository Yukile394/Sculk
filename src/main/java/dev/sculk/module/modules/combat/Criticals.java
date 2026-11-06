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
   private final ModeSetting<Criticals.Mode> mode = new ModeSetting<>("Mode", Criticals.Mode.Grim)
      .describe("Packet pattern sent before every attack");
   private long lastCritMs = 0L;
   private long lastCritTick = Long.MIN_VALUE;
   private double simulatedVelY = 0.0;
   private boolean wasOnGround = true;

   public Criticals() {
      super("Criticals", "Turns every hit into a critical hit", Category.COMBAT);
      this.addSettings(new Setting[]{this.mode});
   }

   @Override
   public void onEnable() {
      this.lastCritMs = 0L;
      this.lastCritTick = Long.MIN_VALUE;
      this.simulatedVelY = 0.0;
      this.wasOnGround = true;
   }

   @Override
   public void onTick() {
      ClientPlayerEntity p = this.getMC().player;
      if (p == null) return;
      if (p.isOnGround()) {
         this.simulatedVelY = 0.0;
         this.wasOnGround = true;
      } else {
         this.simulatedVelY = (this.simulatedVelY - 0.08) * 0.98;
         this.wasOnGround = false;
      }
   }

   public void onAttack(Entity target) {
      if (cancelCrit || target == null || target instanceof EndCrystalEntity) return;
      ClientPlayerEntity player = this.getMC().player;
      if (player == null || this.getMC().world == null) return;

      if (player.getAbilities().flying || player.isSpectator()) return;
      if (player.isInLava() || player.isSubmergedInWater() || player.isTouchingWater()) return;
      if (player.isFallFlying()) return;
      if (this.isOnClimbable(player)) return;

      long now = System.currentTimeMillis();
      if (now - this.lastCritMs < 150L) return;

      long tick = ServerClock.tick();
      if (tick != Long.MIN_VALUE) {
         if (tick == this.lastCritTick) return;
         this.lastCritTick = tick;
      }

      boolean onGround = player.isOnGround();
      Criticals.Mode m = this.mode.getValue();

      if (m == Criticals.Mode.Grim || m == Criticals.Mode.Polar) {
         this.bypassPhysics(player, m, onGround);
      } else if (m == Criticals.Mode.Legit) {
         return;
      } else {
         if (!onGround) return;
         switch (m) {
            case Ncp -> {
               this.packet(player, 0.0625);
               this.packet(player, 0.0);
            }
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
            case UpdatedNCP -> {
               this.packet(player, 2.71875E-7);
               this.packet(player, 0.0);
            }
            default -> {
               this.packet(player, 2.71875E-7);
               this.packet(player, 0.0);
            }
         }
      }
      this.lastCritMs = now;
   }

   private void bypassPhysics(ClientPlayerEntity player, Criticals.Mode m, boolean onGround) {
      if (onGround) {
         double jumpStart = (m == Criticals.Mode.Grim) ? 0.32 : 0.42;
         this.packet(player, jumpStart);
         double tick2 = (jumpStart - 0.08) * 0.98;
         this.packet(player, tick2);
         double tick3 = (tick2 - 0.08) * 0.98;
         this.packet(player, tick3);
         this.packet(player, 0.0);
         this.simulatedVelY = tick3;
         this.wasOnGround = false;
      } else {
         double next = (this.simulatedVelY - 0.08) * 0.98;
         if (next < -3.92) next = -3.92;
         this.packet(player, next);
         this.simulatedVelY = next;
      }
   }

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

   public static enum Mode { Ncp, Strict, OldNCP, UpdatedNCP, Grim, Polar, Legit; }
}
