package dev.sculk.module.modules.combat;

import dev.sculk.SculkClient;
import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.util.Friends;
import dev.sculk.util.Timer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Fires elytra rockets and keeps the player locked on the rocket slot.
 *
 * Slot lock: whenever another slot is selected it is remembered as the weapon slot and the
 * selection snaps back to the rocket slot, at a human-paced interval, never while grounded.
 *
 * Auto-flight: while gliding with an open elytra, rockets fire automatically to sustain flight.
 */
public final class AutoFirework extends Module {
   private static final long RESTORE_AFTER = 100L;
   // Orta hizli slot swap: anlik degil, 180-320ms arasi rastgele -> instant-switch heuristic bypass
   private static final long SLOT_SWAP_MIN_MS = 180L;
   private static final long SLOT_SWAP_MAX_MS = 320L;

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
   private final BooleanSetting hotbar = new BooleanSetting("Hotbar", true).describe("Allow switching to a rocket stored in the hotbar");
   private final NumberSetting slot = new NumberSetting("Rocket Slot", 0.0, 0.0, 9.0, 1.0).describe("0 = first firework in the hotbar");
   private final BooleanSetting lockSlot = new BooleanSetting("Lock Slot", true)
      .describe("Stay on the rocket slot. Selecting another slot is remembered as the weapon slot and snaps back (airborne only, paced)");
   private final NumberSetting weaponSlot = new NumberSetting("Weapon Slot", 0.0, 0.0, 9.0, 1.0)
      .describe("0 = the slot you last selected")
      .visibleWhen(() -> this.lockSlot.get());

   private final Timer timer = new Timer();
   private int tempPrevious = -1;
   private long tempAt;
   private long failedAt;
   private int rememberedWeapon = -1;
   private int rocketSlotIndex = -1;
   private long nextSlotSwapAt = 0L;

   public AutoFirework() {
      super("Auto Firework", "Fires elytra rockets automatically while gliding and locks the rocket slot", Category.COMBAT);
      this.addSettings(
         new Setting[]{
            this.mode,
            this.delay,
            this.speed,
            this.onlyGliding,
            this.hotbar,
            this.slot,
            this.lockSlot,
            this.weaponSlot
         }
      );
   }

   @Override
   public void onEnable() {
      this.timer.reset();
      this.tempPrevious = -1;
      this.rememberedWeapon = -1;
      this.nextSlotSwapAt = 0L;
      if (this.getMC().player != null) {
         int rocket = this.findRocketSlot();
         int selected = this.getMC().player.getInventory().selectedSlot;
         if (rocket >= 0 && selected != rocket) {
            this.rememberedWeapon = selected;
         }
      }
   }

   @Override
   public void onDisable() {
      this.restoreTemp();
      this.rocketSlotIndex = -1;
      this.rememberedWeapon = -1;
      this.nextSlotSwapAt = 0L;
   }

   /** Slot (0-8) that Aim Assist should treat as the selected one. No combat swap anymore, so this is just the raw selection. */
   public int effectiveSlot() {
      ClientPlayerEntity player = this.getMC().player;
      return player == null ? 0 : player.getInventory().selectedSlot;
   }

   @Override
   public void onTick() {
      ClientPlayerEntity player = this.mc.player;
      if (player != null && this.mc.world != null && this.mc.interactionManager != null) {
         this.restoreTempIfDue();
         if (this.mc.currentScreen == null && !player.isDead()) {
            this.rocketSlotIndex = this.findRocketSlot();
            PlayerInventory inventory = player.getInventory();

            boolean grounded = player.isOnGround();
            boolean gliding = player.isFallFlying();

            // yerdeyse slot swap tamamen durur, mevcut secimde kal
            if (this.lockSlot.get() && this.rocketSlotIndex >= 0 && !grounded) {
               long now = System.currentTimeMillis();
               if (inventory.selectedSlot != this.rocketSlotIndex) {
                  if (now >= this.nextSlotSwapAt) {
                     this.rememberedWeapon = inventory.selectedSlot;
                     inventory.selectedSlot = this.rocketSlotIndex;
                     this.scheduleNextSwap(now);
                  }
               }
            }

            // elytra acikken kanat acma durumunda otomatik roket zorunlu aktif
            boolean forceAutoFlight = gliding;
            if (forceAutoFlight || (!this.onlyGliding.get())) {
               this.rocketTick(true);
            } else {
               this.rocketTick(false);
            }
         }
      }
   }

   private void scheduleNextSwap(long now) {
      long span = SLOT_SWAP_MAX_MS - SLOT_SWAP_MIN_MS;
      this.nextSlotSwapAt = now + SLOT_SWAP_MIN_MS + (long)(Math.random() * (double)span);
   }

   // ───────────────────────────── rockets ─────────────────────────────

   private void rocketTick(boolean glidingForced) {
      ClientPlayerEntity player = this.mc.player;
      if (!player.isUsingItem()) {
         boolean allowed = glidingForced || !this.onlyGliding.get() || player.isFallFlying();
         if (allowed) {
            if (this.ready()) {
               if (this.useRocket()) {
                  this.timer.reset();
               }
            }
         }
      }
   }

   private boolean ready() {
      if (this.mode.is(AutoFirework.Mode.Speed)) {
         Vec3d velocity = this.mc.player.getVelocity();
         return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) < (double)this.speed.getFloat();
      } else {
         return this.timer.delay((long)this.delay.getInt());
      }
   }

   private boolean useRocket() {
      ClientPlayerEntity player = this.mc.player;
      if (System.currentTimeMillis() - this.failedAt < 500L) {
         return false;
      } else if (player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
         return this.accepted(this.mc.interactionManager.interactItem(player, Hand.OFF_HAND));
      } else if (player.getMainHandStack().isOf(Items.FIREWORK_ROCKET)) {
         boolean used = this.accepted(this.mc.interactionManager.interactItem(player, Hand.MAIN_HAND));
         if (!used) {
            this.failedAt = System.currentTimeMillis();
         }

         return used;
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
            boolean used = this.accepted(this.mc.interactionManager.interactItem(player, Hand.MAIN_HAND));
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
