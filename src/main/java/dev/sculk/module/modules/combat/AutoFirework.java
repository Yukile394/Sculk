package dev.sculk.module.modules.combat;

import dev.sculk.mixin.Mixin13;
import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.util.Timer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * Fires elytra rockets. No combat, no auto-hit.
 *
 * A rocket fires the instant the elytra opens (fallFlying false->true edge), not on whatever phase the
 * interval timer happened to be in.
 *
 * Silent: while any other hotbar slot is selected, the rocket is used with slot packets only
 * (select rocket slot -> use item -> select the old slot, all inside one tick). The client-side selected
 * slot is restored before the tick ends, so your held item never visibly changes. The offhand is never touched.
 */
public final class AutoFirework extends Module {
   private static final long RESTORE_AFTER = 100L;

   private final ModeSetting<AutoFirework.Mode> mode = new ModeSetting<>("Mode", AutoFirework.Mode.Interval)
      .describe("Interval = every delay; Speed = only when you slow down");
   private final NumberSetting delay = new NumberSetting("Delay", 1200.0, 100.0, 5000.0, 50.0)
      .describe("Milliseconds between two rockets")
      .visibleWhen(() -> this.mode.is(AutoFirework.Mode.Interval));
   private final NumberSetting speed = new NumberSetting("Speed", 1.2, 0.2, 3.0, 0.1)
      .describe("Horizontal blocks per tick below which a rocket is fired")
      .visibleWhen(() -> this.mode.is(AutoFirework.Mode.Speed));
   private final BooleanSetting onlyGliding = new BooleanSetting("Only Gliding", true)
      .describe("Never use a rocket on the ground, where vanilla fireworks do nothing");
   private final BooleanSetting hotbar = new BooleanSetting("Hotbar", true)
      .describe("Allow switching to a rocket stored in the hotbar (ignored while Silent is on)");
   private final NumberSetting slot = new NumberSetting("Rocket Slot", 0.0, 0.0, 9.0, 1.0)
      .describe("0 = first firework in the hotbar");
   private final BooleanSetting silent = new BooleanSetting("Silent", true)
      .describe("Fires rockets from a hotbar slot without changing your selected slot (slot packets only, restored in the same tick)");

   private final Timer timer = new Timer();
   private int tempPrevious = -1;
   private long tempAt;
   private long failedAt;
   private boolean wasGliding = false;

   public AutoFirework() {
      super("Auto Firework", "Fires elytra rockets automatically; Silent fires them from any slot without switching", Category.COMBAT);
      this.addSettings(
         new Setting[]{
            this.mode,
            this.delay,
            this.speed,
            this.onlyGliding,
            this.hotbar,
            this.slot,
            this.silent
         }
      );
   }

   @Override
   public void onEnable() {
      this.timer.reset();
      this.tempPrevious = -1;
      this.wasGliding = this.getMC().player != null && this.getMC().player.isFallFlying();
   }

   @Override
   public void onDisable() {
      this.restoreTemp();
   }

   @Override
   public void onTick() {
      ClientPlayerEntity player = this.mc.player;
      if (player == null || this.mc.world == null || this.mc.interactionManager == null) {
         return;
      }

      this.restoreTempIfDue();
      if (this.mc.currentScreen != null || player.isDead()) {
         return;
      }

      // elytra-open edge: make the timer read as already elapsed so the first rocket is instant
      boolean gliding = player.isFallFlying();
      if (gliding && !this.wasGliding) {
         this.timer.forceElapsed((long) this.delay.getInt());
      }
      this.wasGliding = gliding;

      this.rocketTick(gliding);
   }

   // ───────────────────────────── rockets ─────────────────────────────

   private void rocketTick(boolean gliding) {
      ClientPlayerEntity player = this.mc.player;
      if (!player.isUsingItem()) {
         boolean allowed = gliding || !this.onlyGliding.get();
         if (allowed && this.ready() && this.useRocket()) {
            this.timer.reset();
         }
      }
   }

   private boolean ready() {
      if (this.mode.is(AutoFirework.Mode.Speed)) {
         Vec3d velocity = this.mc.player.getVelocity();
         return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) < (double) this.speed.getFloat();
      } else {
         return this.timer.delay((long) this.delay.getInt());
      }
   }

   private boolean useRocket() {
      ClientPlayerEntity player = this.mc.player;
      if (System.currentTimeMillis() - this.failedAt < 500L) {
         return false;
      } else if (player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
         return this.useHand(player, Hand.OFF_HAND);
      } else if (player.getMainHandStack().isOf(Items.FIREWORK_ROCKET)) {
         boolean used = this.useHand(player, Hand.MAIN_HAND);
         if (!used) {
            this.failedAt = System.currentTimeMillis();
         }

         return used;
      } else if (this.silent.get()) {
         return this.useViaSilentSwap(player);
      } else if (!this.hotbar.get()) {
         return false;
      } else {
         int rocket = this.findRocketSlot();
         if (rocket < 0) {
            return false;
         } else {
            if (this.tempPrevious < 0) {
               this.tempPrevious = player.getInventory().selectedSlot;
            }

            player.getInventory().selectedSlot = rocket;
            boolean used = this.useHand(player, Hand.MAIN_HAND);
            if (!used) {
               this.failedAt = System.currentTimeMillis();
               this.restoreTemp();
               return false;
            } else {
               this.tempAt = System.currentTimeMillis();
               return true;
            }
         }
      }
   }

   /**
    * Silent use from another hotbar slot. interactItem() first syncs the (temporarily changed) selected slot to the
    * server, then sends the use packet; the original slot is put back and synced straight away. The three packets go out
    * in order within one client tick and the player's visible selected slot never changes.
    */
   private boolean useViaSilentSwap(ClientPlayerEntity player) {
      int rocket = this.findRocketSlot();
      if (rocket < 0) {
         return false;
      }

      PlayerInventory inventory = player.getInventory();
      int original = inventory.selectedSlot;
      boolean used;
      inventory.selectedSlot = rocket;
      try {
         used = this.useHand(player, Hand.MAIN_HAND);
      } finally {
         inventory.selectedSlot = original;
         ((Mixin13) this.mc.interactionManager).sculk$syncSelectedSlot();
      }

      if (!used) {
         this.failedAt = System.currentTimeMillis();
      }

      return used;
   }

   /** Vanilla use: the hand swings after a successful use, which the server-side shoot check expects. */
   private boolean useHand(ClientPlayerEntity player, Hand hand) {
      ActionResult result = this.mc.interactionManager.interactItem(player, hand);
      if (result == ActionResult.SUCCESS) {
         player.swingHand(hand);
      }

      return this.accepted(result);
   }

   private void restoreTempIfDue() {
      if (this.tempPrevious >= 0 && System.currentTimeMillis() - this.tempAt >= RESTORE_AFTER) {
         this.restoreTemp();
      }
   }

   private void restoreTemp() {
      if (this.tempPrevious >= 0 && this.getMC().player != null) {
         this.getMC().player.getInventory().selectedSlot = this.tempPrevious;
      }

      this.tempPrevious = -1;
   }

   private boolean accepted(ActionResult result) {
      return result == ActionResult.SUCCESS || result == ActionResult.CONSUME;
   }

   private int findRocketSlot() {
      ClientPlayerEntity player = this.getMC().player;
      if (player == null) {
         return -1;
      }

      int configured = this.slot.getInt();
      if (configured > 0 && player.getInventory().getStack(configured - 1).isOf(Items.FIREWORK_ROCKET)) {
         return configured - 1;
      }

      for (int i = 0; i < 9; i++) {
         if (player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
            return i;
         }
      }

      return -1;
   }

   public static enum Mode {
      Interval,
      Speed;
   }
}
