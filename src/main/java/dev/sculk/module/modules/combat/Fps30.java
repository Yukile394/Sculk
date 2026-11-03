package dev.sculk.module.modules.combat;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.util.Timer;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

public final class Fps30 extends Module {
   private final ModeSetting<Fps30.Mode> mode = new ModeSetting<>("Mode", Fps30.Mode.Interval)
      .describe("Interval = every delay; Speed = only when you slow down");
   private final NumberSetting delay = new NumberSetting("Delay", 1200.0, 100.0, 5000.0, 50.0)
      .describe("Milliseconds between two rockets")
      .visibleWhen(() -> this.mode.is(Fps30.Mode.Interval));
   private final NumberSetting speed = new NumberSetting("Speed", 1.2, 0.2, 3.0, 0.1)
      .describe("Horizontal blocks per tick below which a rocket is fired")
      .visibleWhen(() -> this.mode.is(Fps30.Mode.Speed));
   private final BooleanSetting onlyGliding = new BooleanSetting("Only Gliding", true)
      .describe("Never use a rocket on the ground, where vanilla fireworks do nothing");
   private final BooleanSetting hotbar = new BooleanSetting("Hotbar", true).describe("Allow switching to a rocket stored in the hotbar");
   private final NumberSetting slot = new NumberSetting("Slot", 0.0, 0.0, 9.0, 1.0)
      .describe("0 = first firework in the hotbar")
      .visibleWhen(() -> this.hotbar.get());
   private final BooleanSetting restore = new BooleanSetting("Restore Slot", true)
      .describe("Switch back to the previous slot after the rocket")
      .visibleWhen(() -> this.hotbar.get());
   private final Timer timer = new Timer();
   private int previousSlot = -1;
   private long previousAt;
   private long failedAt;
   private static final long RESTORE_AFTER = 100L;

   public Fps30() {
      super("Auto Firework", "Fires elytra rockets for you", Category.COMBAT);
      this.addSettings(new Setting[]{this.mode, this.delay, this.speed, this.onlyGliding, this.hotbar, this.slot, this.restore});
   }

   @Override
   public void onEnable() {
      this.timer.reset();
      this.previousSlot = -1;
   }

   @Override
   public void onDisable() {
      this.restoreSlot();
   }

   @Override
   public void onTick() {
      if (this.mc.player != null && this.mc.world != null && this.mc.interactionManager != null) {
         this.restoreIfDue();
         if (this.mc.currentScreen == null && !this.mc.player.isDead() && !this.mc.player.isUsingItem()) {
            if (!this.onlyGliding.get() || this.mc.player.isFallFlying()) {
               if (this.ready()) {
                  if (this.useRocket()) {
                     this.timer.reset();
                  }
               }
            }
         }
      }
   }

   private void restoreIfDue() {
      if (this.previousSlot >= 0 && System.currentTimeMillis() - this.previousAt >= 100L) {
         if (this.restore.get()) {
            this.mc.player.getInventory().selectedSlot = this.previousSlot;
         }

         this.previousSlot = -1;
      }
   }

   private boolean ready() {
      if (this.mode.is(Fps30.Mode.Speed)) {
         Vec3d velocity = this.mc.player.getVelocity();
         return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) < (double)this.speed.getFloat();
      } else {
         return this.timer.delay((long)this.delay.getInt());
      }
   }

   private boolean useRocket() {
      if (System.currentTimeMillis() - this.failedAt < 500L) {
         return false;
      } else if (this.mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
         return this.accepted(this.mc.interactionManager.interactItem(this.mc.player, Hand.OFF_HAND));
      } else if (!this.hotbar.get()) {
         return false;
      } else {
         int rocket = this.findFirework();
         if (rocket < 0) {
            return false;
         } else {
            if (this.previousSlot < 0) {
               this.previousSlot = this.mc.player.getInventory().selectedSlot;
            }

            this.mc.player.getInventory().selectedSlot = rocket;
            boolean used = this.accepted(this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND));
            if (!used) {
               this.failedAt = System.currentTimeMillis();
               this.restoreSlot();
               return false;
            } else {
               this.previousAt = System.currentTimeMillis();
               return true;
            }
         }
      }
   }

   private void restoreSlot() {
      if (this.previousSlot >= 0) {
         if (this.restore.get()) {
            this.mc.player.getInventory().selectedSlot = this.previousSlot;
         }

         this.previousSlot = -1;
      }
   }

   private boolean accepted(ActionResult result) {
      return result == ActionResult.SUCCESS || result == ActionResult.CONSUME;
   }

   private int findFirework() {
      int configured = this.slot.getInt();
      if (configured > 0) {
         return configured - 1;
      } else {
         for (int i = 0; i < 9; i++) {
            if (this.mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
               return i;
            }
         }

         return -1;
      }
   }

   public static enum Mode {
      Interval,
      Speed;
   }
}
