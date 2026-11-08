package dev.sculk.module.modules.combat;

import dev.sculk.SculkClient;
import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.util.CombatUtils;
import dev.sculk.util.Friends;
import dev.sculk.util.Timer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Fires elytra rockets and keeps the player locked on the rocket slot.
 *
 * Auto-flight timing: a rocket fires the instant the elytra opens (fallFlying false->true edge),
 * not on whatever phase the interval timer happened to be in -- no more 1-2s delay after opening.
 *
 * Combat loop: when an enemy is within Combat Range, swap to weapon slot, land one cooldown-synced
 * hit (edge-gated, no multi-hit spam even across several frames), swap back to rocket slot, fire a
 * rocket to close back in, repeat. No swap/hit happens while on the ground or while already parked
 * on the rocket slot with no enemy in range.
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
      .describe("Stay on the rocket slot. Selecting another slot is remembered as the weapon slot and snaps back");
   private final NumberSetting weaponSlot = new NumberSetting("Weapon Slot", 0.0, 0.0, 9.0, 1.0)
      .describe("0 = the slot you last selected")
      .visibleWhen(() -> this.lockSlot.get());
   private final NumberSetting range = new NumberSetting("Combat Range", 3.2, 1.0, 6.0, 0.1)
      .describe("Enemy distance (blocks) that starts the rocket slot / weapon slot loop");
   private final BooleanSetting playersOnly = new BooleanSetting("Players Only", true);
   private final NumberSetting rocketWindow = new NumberSetting("Rocket Window", 2.0, 0.0, 10.0, 1.0)
      .describe("Ticks spent on the rocket slot between two hits")
      .visibleWhen(() -> this.lockSlot.get());
   private final BooleanSetting aimSync = new BooleanSetting("Aim Sync", true)
      .describe("Also hits the entity Aim Assist is locked on");
   private final NumberSetting cooldownThreshold = new NumberSetting("CD Threshold", 1.0, 0.85, 1.0, 0.01)
      .describe("Vurus icin gereken minimum item cooldown progress (1.0 = tam sifirlanmis)");

   private final Timer timer = new Timer();
   private int tempPrevious = -1;
   private long tempAt;
   private long failedAt;
   private int rememberedWeapon = -1;
   private int rocketSlotIndex = -1;
   private boolean inCombat;
   private int sinceReturn = 99;
   private Entity focus;
   private long nextSlotSwapAt = 0L;
   private boolean wasGliding = false;
   // trigger-style edge gate: item cooldown resetlenene kadar ayni cycle'da ikinci vurusu engeller
   private boolean firedThisCycle = false;
   private float lastObservedProgress = 1.0F;

   public AutoFirework() {
      super("Auto Firework", "Fires elytra rockets on open, locks the rocket slot and crits in close combat", Category.COMBAT);
      this.addSettings(
         new Setting[]{
            this.mode,
            this.delay,
            this.speed,
            this.onlyGliding,
            this.hotbar,
            this.slot,
            this.lockSlot,
            this.weaponSlot,
            this.range,
            this.playersOnly,
            this.rocketWindow,
            this.aimSync,
            this.cooldownThreshold
         }
      );
   }

   @Override
   public void onEnable() {
      this.timer.reset();
      this.tempPrevious = -1;
      this.inCombat = false;
      this.sinceReturn = 99;
      this.rememberedWeapon = -1;
      this.nextSlotSwapAt = 0L;
      this.wasGliding = this.getMC().player != null && this.getMC().player.isFallFlying();
      this.firedThisCycle = false;
      this.lastObservedProgress = 1.0F;
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
      this.inCombat = false;
      this.focus = null;
      this.rocketSlotIndex = -1;
      this.rememberedWeapon = -1;
      this.nextSlotSwapAt = 0L;
   }

   /** Closest enemy inside Combat Range as of the last tick (used by the target ESP). */
   public Entity focus() {
      return this.isEnabled() && this.focus != null && this.focus.isAlive() ? this.focus : null;
   }

   /** Slot (0-8) that Aim Assist should treat as the selected one. */
   public int effectiveSlot() {
      ClientPlayerEntity player = this.getMC().player;
      if (player == null) {
         return 0;
      }

      int selected = player.getInventory().selectedSlot;
      if (this.isEnabled() && this.lockSlot.get() && this.rocketSlotIndex >= 0 && selected == this.rocketSlotIndex) {
         int weapon = this.weapon();
         if (weapon >= 0) {
            return weapon;
         }
      }

      return selected;
   }

   @Override
   public void onTick() {
      ClientPlayerEntity player = this.mc.player;
      if (player != null && this.mc.world != null && this.mc.interactionManager != null) {
         this.restoreTempIfDue();
         if (this.mc.currentScreen == null && !player.isDead()) {
            this.sinceReturn++;
            this.rocketSlotIndex = this.findRocketSlot();
            boolean grounded = player.isOnGround();

            // kanat acma edge'i: fallFlying false -> true gectigi tam tick'te timer'i sifirla,
            // ready() bir sonraki rocketTick cagrisinda hemen true donsun -> 1-2sn gecikme gider
            boolean gliding = player.isFallFlying();
            if (gliding && !this.wasGliding) {
               this.timer.reset();
               this.timer.setLastMs(this.timer.getLastMs() - (long) this.delay.getInt()); // delay'i dolmus say
            }
            this.wasGliding = gliding;

            Entity enemy = this.nearestEnemy(player);
            this.focus = enemy;
            PlayerInventory inventory = player.getInventory();

            if (!this.lockSlot.get() || this.rocketSlotIndex < 0) {
               this.inCombat = false;
               if (!grounded && enemy != null) {
                  this.tryHit(player, enemy);
               }

               this.rocketTick(gliding);
               return;
            }

            if (this.inCombat) {
               int selected = inventory.selectedSlot;
               if (selected != this.weapon()) {
                  if (selected == this.rocketSlotIndex) {
                     this.inCombat = false;
                  } else {
                     this.rememberedWeapon = selected;
                  }
               }

               if (this.inCombat) {
                  if (enemy == null) {
                     this.returnToRocket(inventory);
                  } else if (this.tryHit(player, enemy)) {
                     this.returnToRocket(inventory);
                  }

                  return;
               }
            }

            // yerdeyken slot swap yok (fisek zaten islevsiz, instant-switch pattern da olusmaz)
            if (grounded) {
               this.rocketTick(false);
               return;
            }

            if (inventory.selectedSlot != this.rocketSlotIndex) {
               long now = System.currentTimeMillis();
               if (now >= this.nextSlotSwapAt) {
                  this.rememberedWeapon = inventory.selectedSlot;
                  inventory.selectedSlot = this.rocketSlotIndex;
                  this.scheduleNextSwap(now);
               } else {
                  this.rocketTick(gliding);
                  return;
               }
            }

            int weapon = this.weapon();
            if (enemy != null && weapon >= 0 && weapon != this.rocketSlotIndex && this.sinceReturn >= this.rocketWindow.getInt()) {
               this.inCombat = true;
               this.firedThisCycle = false; // yeni combat girisi, cycle-gate'i acik birak
               inventory.selectedSlot = weapon;
               return;
            }

            this.rocketTick(gliding);
         }
      }
   }

   private void scheduleNextSwap(long now) {
      long span = SLOT_SWAP_MAX_MS - SLOT_SWAP_MIN_MS;
      this.nextSlotSwapAt = now + SLOT_SWAP_MIN_MS + (long) (Math.random() * (double) span);
   }

   private void returnToRocket(PlayerInventory inventory) {
      if (this.rocketSlotIndex >= 0) {
         inventory.selectedSlot = this.rocketSlotIndex;
      }

      this.inCombat = false;
      this.sinceReturn = 0;
      this.firedThisCycle = false;
   }

   // ───────────────────────────── combat ─────────────────────────────

   private boolean tryHit(ClientPlayerEntity player, Entity enemy) {
      // item cooldown edge-gate: trigger'daki spam fix'in ayni mantigi.
      // progress threshold'u gectiginde SADECE bir kez vurur, progress tekrar dusene
      // (gercek swing cycle'i bitene) kadar susar -> fisek slotundayken asla vurmaz
      // cunku bu metot zaten sadece inCombat (silah slotunda) iken cagriliyor.
      float progress = player.getAttackCooldownProgress(0.0F);
      boolean cycleReset = progress < this.lastObservedProgress - 0.01F;
      if (cycleReset) this.firedThisCycle = false;
      this.lastObservedProgress = progress;

      if (progress < this.cooldownThreshold.getFloat()) return false;
      if (this.firedThisCycle) return false;

      Entity target = this.findTarget(player, enemy);
      if (target == null) {
         return false;
      }

      this.firedThisCycle = true;
      CombatUtils.hitEntity(target, true);
      return true;
   }

   private Entity findTarget(ClientPlayerEntity player, Entity nearest) {
      double reach = this.range.getValue();
      Vec3d eye = player.getCameraPosVec(1.0F);
      Vec3d look = player.getRotationVec(1.0F);
      Vec3d end = eye.add(look.multiply(reach));
      Box box = player.getBoundingBox().stretch(look.multiply(reach)).expand(1.0, 1.0, 1.0);
      EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, box, e -> this.valid(player, e), reach * reach);
      if (hit != null) {
         return hit.getEntity();
      }

      if (this.aimSync.get()) {
         Entity aimed = this.aimAssistTarget();
         if (aimed != null && this.valid(player, aimed) && this.boxDistance(player, aimed) <= reach) {
            java.util.Optional<Vec3d> hitPoint = aimed.getBoundingBox().expand(0.3).raycast(eye, end);
            if (hitPoint.isPresent()) {
               return aimed;
            }
         }
      }

      return null;
   }

   private Entity aimAssistTarget() {
      for (Module module : SculkClient.modules()) {
         if (module instanceof AimAssistModule assist && module.isEnabled() && assist.currentTarget() != null) {
            return assist.currentTarget();
         }
      }

      return null;
   }

   private Entity nearestEnemy(ClientPlayerEntity player) {
      double reach = this.range.getValue();
      Entity best = null;
      double bestDistance = Double.MAX_VALUE;

      for (Entity entity : this.mc.world.getEntities()) {
         if (this.valid(player, entity)) {
            double distance = this.boxDistance(player, entity);
            if (distance <= reach && distance < bestDistance) {
               bestDistance = distance;
               best = entity;
            }
         }
      }

      return best;
   }

   private boolean valid(ClientPlayerEntity player, Entity entity) {
      return entity != player
         && entity instanceof LivingEntity living
         && living.isAlive()
         && !entity.isSpectator()
         && !(entity instanceof ArmorStandEntity)
         && !Friends.isAlly(entity)
         && (!this.playersOnly.get() || entity instanceof PlayerEntity);
   }

   private double boxDistance(ClientPlayerEntity player, Entity entity) {
      Box box = entity.getBoundingBox();
      Vec3d eye = player.getEyePos();
      double dx = Math.max(Math.max(box.minX - eye.x, 0.0), eye.x - box.maxX);
      double dy = Math.max(Math.max(box.minY - eye.y, 0.0), eye.y - box.maxY);
      double dz = Math.max(Math.max(box.minZ - eye.z, 0.0), eye.z - box.maxZ);
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   /** Slot (0-8) that is swapped to for the hit, or -1. */
   private int weapon() {
      int configured = this.weaponSlot.getInt();
      if (configured > 0) {
         return configured - 1;
      } else if (this.rememberedWeapon >= 0 && this.rememberedWeapon != this.rocketSlotIndex) {
         return this.rememberedWeapon;
      } else {
         ClientPlayerEntity player = this.getMC().player;
         if (player == null) {
            return -1;
         }

         for (int i = 0; i < 9; i++) {
            Item item = player.getInventory().getStack(i).getItem();
            if (item instanceof SwordItem || item instanceof AxeItem || item instanceof MaceItem || item instanceof TridentItem) {
               return i;
            }
         }

         return -1;
      }
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
