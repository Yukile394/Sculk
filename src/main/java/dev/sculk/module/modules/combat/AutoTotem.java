package dev.sculk.module.modules.combat;

import dev.sculk.mixin.Mixin3;
import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.KeySetting;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.util.Friends;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PickFromInventoryC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import net.minecraft.item.ItemStack;

/**
 * AutoTotem, ported from Tera Client (jako.features.modules.combat.AutoTotem) onto Fabric 1.21.1 / Yarn.
 * Same settings, swap modes (Default / Alternative / Matrix / MatrixPick / NewVersion) and trigger rules as the original.
 * Inventory slot convention below: 0-8 hotbar, 9-35 main inventory (these equal the screen handler slot ids for 9-35); 45 = offhand.
 *
 * Differences from the original: the crystal / TNT damage estimate uses the vanilla linear distance falloff
 * (the original divided the squared distance by 144, which under-estimates damage) and does not subtract blast protection.
 * Server packets arrive through Mixin7 (crystal spawn, obsidian placed).
 */
public final class AutoTotem extends Module {
   private final ModeSetting<AutoTotem.Mode> mode = new ModeSetting<>("Mode", AutoTotem.Mode.Matrix);
   private final ModeSetting<AutoTotem.OffHand> offhand = new ModeSetting<>("Item", AutoTotem.OffHand.Totem);
   private final BooleanSetting bindSwap = new BooleanSetting("BindSwap", false).visibleWhen(() -> this.offhand.is(AutoTotem.OffHand.Totem));
   private final KeySetting swapButton = new KeySetting("SwapButton", 280)
      .visibleWhen(() -> this.offhand.is(AutoTotem.OffHand.Totem) && this.bindSwap.get());
   private final ModeSetting<AutoTotem.Swap> swapMode = new ModeSetting<>("Swap", AutoTotem.Swap.GappleShield)
      .visibleWhen(() -> this.offhand.is(AutoTotem.OffHand.Totem) && this.bindSwap.get());
   private final BooleanSetting ncpStrict = new BooleanSetting("NCPStrict", false);
   private final NumberSetting healthF = new NumberSetting("HP", 16.0, 0.0, 36.0, 0.5);
   private final NumberSetting healthS = new NumberSetting("ShieldGappleHp", 16.0, 0.0, 20.0, 0.5).visibleWhen(() -> this.offhand.is(AutoTotem.OffHand.Shield));
   private final BooleanSetting calcAbsorption = new BooleanSetting("CalcAbsorption", true);
   private final BooleanSetting stopMotion = new BooleanSetting("StopMotion", false);
   private final BooleanSetting resetAttackCooldown = new BooleanSetting("ResetAttackCooldown", false);
   private final BooleanSetting hotbarFallBack = new BooleanSetting("Safety: HotbarFallback", false);
   private final BooleanSetting fallBackCalc = new BooleanSetting("Safety: FallBackCalc", true).visibleWhen(() -> this.hotbarFallBack.get());
   private final BooleanSetting onElytra = new BooleanSetting("Safety: OnElytra", true);
   private final BooleanSetting onFall = new BooleanSetting("Safety: OnFall", true);
   private final BooleanSetting onCrystal = new BooleanSetting("Safety: OnCrystal", true);
   private final BooleanSetting onObsidianPlace = new BooleanSetting("Safety: OnObsidianPlace", false);
   private final BooleanSetting onCrystalInHand = new BooleanSetting("Safety: OnCrystalInHand", false);
   private final BooleanSetting onMinecartTnt = new BooleanSetting("Safety: OnMinecartTNT", true);
   private final BooleanSetting onCreeper = new BooleanSetting("Safety: OnCreeper", true);
   private final BooleanSetting onAnchor = new BooleanSetting("Safety: OnAnchor", true);
   private final BooleanSetting onTnt = new BooleanSetting("Safety: OnTNT", true);
   private final ModeSetting<AutoTotem.Always> rcGap = new ModeSetting<>("RightClickGapple", AutoTotem.Always.Off);
   private final BooleanSetting crappleSpoof = new BooleanSetting("CrappleSpoof", true).visibleWhen(() -> this.offhand.is(AutoTotem.OffHand.GApple));

   private int delay;
   private long lastBindMs;
   private Item prevItem;
   private int restoreSlot = -1;
   private long restoreAtMs;

   public AutoTotem() {
      super("AutoTotem", "Keeps a totem (or your chosen item) in the offhand (Tera Client port)", Category.COMBAT);
      this.addSettings(new Setting[]{this.mode, this.offhand, this.bindSwap, this.swapButton, this.swapMode, this.ncpStrict, this.healthF,
         this.healthS, this.calcAbsorption, this.stopMotion, this.resetAttackCooldown, this.hotbarFallBack, this.fallBackCalc, this.onElytra,
         this.onFall, this.onCrystal, this.onObsidianPlace, this.onCrystalInHand, this.onMinecartTnt, this.onCreeper, this.onAnchor,
         this.onTnt, this.rcGap, this.crappleSpoof});
   }

   @Override
   public void onEnable() {
      this.delay = 0;
      this.restoreSlot = -1;
   }

   @Override
   public void onTick() {
      if (this.mc == null || this.mc.player == null || this.mc.world == null || this.mc.interactionManager == null) {
         return;
      }

      if (this.restoreSlot >= 0 && System.currentTimeMillis() >= this.restoreAtMs) {
         this.mc.player.getInventory().selectedSlot = this.restoreSlot;
         this.restoreSlot = -1;
      }

      this.swapTo(this.getItemSlot());
      if (this.rcGap.getValue() != AutoTotem.Always.Off
         && this.mc.player.getMainHandStack().getItem() instanceof SwordItem
         && this.mc.options.useKey.isPressed()
         && !this.mc.player.isUsingItem()) {
         ((Mixin3)this.mc).sculk$doItemUse();
      }

      this.delay--;
   }

   // ------------------------------------------------------------ server packets (called from Mixin7 on the client thread)

   public void onEntitySpawn(EntitySpawnS2CPacket packet) {
      if (this.mc == null || this.mc.player == null || packet.getEntityType() != EntityType.END_CRYSTAL) {
         return;
      }

      Vec3d crystal = new Vec3d(packet.getX(), packet.getY(), packet.getZ());
      if (this.playerPos().squaredDistanceTo(crystal) >= 36.0) {
         return;
      }

      if (this.hotbarFallBack.get()) {
         if (this.fallBackCalc.get() && this.explosionDamage(crystal, this.mc.player.getBoundingBox()) < this.triggerHealth() + 4.0F) {
            return;
         }

         this.runInstant();
      }

      if (this.onCrystal.get() && (double)(this.triggerHealth() - this.explosionDamage(crystal, this.mc.player.getBoundingBox())) < 0.5) {
         this.swapTo(this.findInInventory(Items.TOTEM_OF_UNDYING));
      }
   }

   public void onBlockUpdate(BlockUpdateS2CPacket packet) {
      if (this.mc == null || this.mc.player == null) {
         return;
      }

      if (packet.getState().getBlock() == Blocks.OBSIDIAN
         && this.onObsidianPlace.get()
         && this.playerPos().squaredDistanceTo(packet.getPos().toCenterPos()) < 36.0
         && this.delay <= 0) {
         this.runInstant();
      }
   }

   // ------------------------------------------------------------------- swapping

   private float triggerHealth() {
      return this.mc.player.getHealth() + (this.calcAbsorption.get() ? this.mc.player.getAbsorptionAmount() : 0.0F);
   }

   private Vec3d playerPos() {
      return this.mc.player.getPos();
   }

   private void sendPacket(Packet<?> packet) {
      if (this.mc.getNetworkHandler() != null) {
         this.mc.getNetworkHandler().sendPacket(packet);
      }
   }

   private void clickSlot(int slot) {
      this.clickSlot(slot, 0, SlotActionType.PICKUP);
   }

   private void clickSlot(int slot, int button, SlotActionType type) {
      this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, slot, button, type, this.mc.player);
   }

   private void closeScreenPacket() {
      this.sendPacket(new CloseHandledScreenC2SPacket(this.mc.player.currentScreenHandler.syncId));
   }

   private int ping() {
      if (this.mc.getNetworkHandler() == null) {
         return 0;
      }

      PlayerListEntry entry = this.mc.getNetworkHandler().getPlayerListEntry(this.mc.player.getUuid());
      return entry == null ? 0 : entry.getLatency();
   }

   /** First inventory index (hotbar slots are searched last, like the original) holding the item, or -1. */
   private int findInInventory(Item item) {
      for (int i = 9; i < 45; i++) {
         int index = i >= 36 ? i - 36 : i;
         if (this.mc.player.getInventory().getStack(index).getItem() == item) {
            return index;
         }
      }

      return -1;
   }

   private int findInHotbar(Item item) {
      for (int i = 0; i < 9; i++) {
         if (this.mc.player.getInventory().getStack(i).getItem() == item) {
            return i;
         }
      }

      return -1;
   }

   private void runInstant() {
      int hotbar = this.findInHotbar(Items.TOTEM_OF_UNDYING);
      int inventory = this.findInInventory(Items.TOTEM_OF_UNDYING);
      if (hotbar >= 0) {
         this.mc.player.getInventory().selectedSlot = hotbar;
         this.sendPacket(new UpdateSelectedSlotC2SPacket(hotbar));
         this.delay = 20;
      } else if (inventory >= 0) {
         if (!this.hotbarFallBack.get()) {
            this.swapTo(inventory);
         } else {
            this.sendPacket(new PickFromInventoryC2SPacket(inventory));
         }

         this.delay = 20;
      }
   }

   public void swapTo(int slot) {
      if (slot == -1 || this.delay > 0) {
         return;
      }

      if (this.mc.currentScreen instanceof GenericContainerScreen) {
         return;
      }

      if (this.stopMotion.get()) {
         this.mc.player.setVelocity(0.0, this.mc.player.getVelocity().getY(), 0.0);
      }

      int nearestSlot = findNearestCurrentItem(this.mc.player.getInventory().selectedSlot);
      int prevCurrentItem = this.mc.player.getInventory().selectedSlot;
      if (slot >= 9) {
         switch (this.mode.getValue()) {
            case Default -> {
               this.stopSprintIfStrict();
               this.clickSlot(slot);
               this.clickSlot(45);
               this.clickSlot(slot);
               this.closeScreenPacket();
            }
            case Alternative -> {
               this.stopSprintIfStrict();
               this.clickSlot(slot, nearestSlot, SlotActionType.SWAP);
               this.clickSlot(45, nearestSlot, SlotActionType.SWAP);
               this.clickSlot(slot, nearestSlot, SlotActionType.SWAP);
               this.closeScreenPacket();
            }
            case Matrix -> {
               this.stopSprintIfStrict();
               this.clickSlot(slot, nearestSlot, SlotActionType.SWAP);
               this.sendPacket(new UpdateSelectedSlotC2SPacket(nearestSlot));
               this.mc.player.getInventory().selectedSlot = nearestSlot;
               ItemStack offhandStack = this.mc.player.getOffHandStack();
               this.mc.player.setStackInHand(Hand.OFF_HAND, this.mc.player.getMainHandStack());
               this.mc.player.setStackInHand(Hand.MAIN_HAND, offhandStack);
               this.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
               this.sendPacket(new UpdateSelectedSlotC2SPacket(prevCurrentItem));
               this.mc.player.getInventory().selectedSlot = prevCurrentItem;
               this.clickSlot(slot, nearestSlot, SlotActionType.SWAP);
               this.closeScreenPacket();
               if (this.resetAttackCooldown.get()) {
                  this.mc.player.resetLastAttackedTicks();
               }
            }
            case MatrixPick -> {
               this.sendPacket(new PickFromInventoryC2SPacket(slot));
               this.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
               this.restoreSlot = this.mc.player.getInventory().selectedSlot;
               this.restoreAtMs = System.currentTimeMillis() + 300L;
            }
            case NewVersion -> {
               this.clickSlot(slot, 40, SlotActionType.SWAP);
               this.closeScreenPacket();
            }
         }
      } else {
         this.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
         this.mc.player.getInventory().selectedSlot = slot;
         this.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
         this.sendPacket(new UpdateSelectedSlotC2SPacket(prevCurrentItem));
         this.mc.player.getInventory().selectedSlot = prevCurrentItem;
         if (this.resetAttackCooldown.get()) {
            this.mc.player.resetLastAttackedTicks();
         }
      }

      this.delay = (int)(2.0F + (float)this.ping() / 25.0F);
   }

   private void stopSprintIfStrict() {
      if (this.ncpStrict.get()) {
         this.sendPacket(new ClientCommandC2SPacket(this.mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
      }
   }

   private static int findNearestCurrentItem(int selected) {
      if (selected == 8) {
         return 7;
      }

      return selected == 0 ? 1 : selected - 1;
   }

   private boolean isKeyPressed(KeySetting key) {
      int code = key.getKey();
      return code >= 0 && this.mc.currentScreen == null && InputUtil.isKeyPressed(this.mc.getWindow().getHandle(), code);
   }

   // ------------------------------------------------------------- item choice

   /** Inventory index of the item that should go to the offhand now, or -1 when nothing has to move. */
   public int getItemSlot() {
      if (this.mc.player == null || this.mc.world == null) {
         return -1;
      }

      boolean gappleFound = this.findInInventory(Items.ENCHANTED_GOLDEN_APPLE) >= 0;
      boolean crappleFound = this.findInInventory(Items.GOLDEN_APPLE) >= 0;
      boolean shieldFound = this.findInInventory(Items.SHIELD) >= 0;
      Item offHandItem = this.mc.player.getOffHandStack().getItem();
      boolean offhandEmpty = this.mc.player.getOffHandStack().isEmpty();
      Item item = null;
      switch (this.offhand.getValue()) {
         case Totem -> {
            if (offHandItem != Items.TOTEM_OF_UNDYING && !offhandEmpty) {
               this.prevItem = offHandItem;
            }

            item = this.prevItem;
            long now = System.currentTimeMillis();
            if (this.bindSwap.get() && this.isKeyPressed(this.swapButton) && now - this.lastBindMs >= 250L) {
               this.lastBindMs = now;
               switch (this.swapMode.getValue()) {
                  case BallShield -> item = offhandEmpty || offHandItem == Items.SHIELD ? Items.PLAYER_HEAD : Items.SHIELD;
                  case GappleBall -> item = offhandEmpty || offHandItem == Items.GOLDEN_APPLE ? Items.PLAYER_HEAD : Items.GOLDEN_APPLE;
                  case GappleShield -> item = offhandEmpty || offHandItem == Items.SHIELD ? Items.GOLDEN_APPLE : Items.SHIELD;
                  case BallTotem -> item = offhandEmpty || offHandItem == Items.TOTEM_OF_UNDYING ? Items.PLAYER_HEAD : Items.TOTEM_OF_UNDYING;
               }

               this.prevItem = item;
            }
         }
         case Crystal -> item = Items.END_CRYSTAL;
         case GApple -> {
            if (this.crappleSpoof.get()) {
               var absorption = this.mc.player.getStatusEffect(StatusEffects.ABSORPTION);
               if (absorption != null && absorption.getAmplifier() > 2) {
                  if (crappleFound || offHandItem == Items.GOLDEN_APPLE) {
                     item = Items.GOLDEN_APPLE;
                  } else if (gappleFound || offHandItem == Items.ENCHANTED_GOLDEN_APPLE) {
                     item = Items.ENCHANTED_GOLDEN_APPLE;
                  }
               } else if (gappleFound || offHandItem == Items.ENCHANTED_GOLDEN_APPLE) {
                  item = Items.ENCHANTED_GOLDEN_APPLE;
               } else if (crappleFound || offHandItem == Items.GOLDEN_APPLE) {
                  item = Items.GOLDEN_APPLE;
               }
            } else if (crappleFound || offHandItem == Items.GOLDEN_APPLE) {
               item = Items.GOLDEN_APPLE;
            } else if (gappleFound || offHandItem == Items.ENCHANTED_GOLDEN_APPLE) {
               item = Items.ENCHANTED_GOLDEN_APPLE;
            }
         }
         case Shield -> {
            if (shieldFound || offHandItem == Items.SHIELD) {
               if (this.triggerHealth() <= this.healthS.getFloat()) {
                  if (crappleFound || offHandItem == Items.GOLDEN_APPLE) {
                     item = Items.GOLDEN_APPLE;
                  } else if (gappleFound || offHandItem == Items.ENCHANTED_GOLDEN_APPLE) {
                     item = Items.ENCHANTED_GOLDEN_APPLE;
                  }
               } else if (!this.mc.player.getItemCooldownManager().isCoolingDown(Items.SHIELD)) {
                  item = Items.SHIELD;
               } else if (crappleFound || offHandItem == Items.GOLDEN_APPLE) {
                  item = Items.GOLDEN_APPLE;
               } else if (gappleFound || offHandItem == Items.ENCHANTED_GOLDEN_APPLE) {
                  item = Items.ENCHANTED_GOLDEN_APPLE;
               }
            } else if (crappleFound || offHandItem == Items.GOLDEN_APPLE) {
               item = Items.GOLDEN_APPLE;
            }
         }
      }

      if (this.triggerHealth() <= this.healthF.getFloat() && (this.findInInventory(Items.TOTEM_OF_UNDYING) >= 0 || offHandItem == Items.TOTEM_OF_UNDYING)) {
         item = Items.TOTEM_OF_UNDYING;
      }

      if (this.rcGap.getValue() != AutoTotem.Always.Off
         && this.mc.player.getMainHandStack().getItem() instanceof SwordItem
         && this.mc.options.useKey.isPressed()
         && !(offHandItem instanceof ShieldItem)
         && (this.rcGap.getValue() == AutoTotem.Always.Always
            || this.rcGap.getValue() == AutoTotem.Always.OnlySafe && this.triggerHealth() > this.healthF.getFloat())) {
         if (crappleFound || offHandItem == Items.GOLDEN_APPLE) {
            item = Items.GOLDEN_APPLE;
         }

         if (gappleFound || offHandItem == Items.ENCHANTED_GOLDEN_APPLE) {
            item = Items.ENCHANTED_GOLDEN_APPLE;
         }
      }

      if (this.onFall.get() && (double)(this.triggerHealth() - ((this.mc.player.fallDistance - 3.0F) / 2.0F + 3.5F)) < 0.5) {
         item = Items.TOTEM_OF_UNDYING;
      }

      if (this.onElytra.get() && this.mc.player.isFallFlying()) {
         item = Items.TOTEM_OF_UNDYING;
      }

      if (this.onCrystalInHand.get()) {
         for (PlayerEntity other : this.mc.world.getPlayers()) {
            if (other == this.mc.player || Friends.isAlly(other) || !(this.playerPos().squaredDistanceTo(other.getPos()) < 36.0)) {
               continue;
            }

            Item main = other.getMainHandStack().getItem();
            Item off = other.getOffHandStack().getItem();
            if (main == Items.OBSIDIAN || main == Items.END_CRYSTAL || off == Items.OBSIDIAN || off == Items.END_CRYSTAL) {
               item = Items.TOTEM_OF_UNDYING;
            }
         }
      }

      for (Entity entity : this.mc.world.getEntities()) {
         if (entity == null || !entity.isAlive() || this.playerPos().squaredDistanceTo(entity.getPos()) > 36.0) {
            continue;
         }

         if (this.onCrystal.get() && entity instanceof EndCrystalEntity
            && (double)(this.triggerHealth() - this.explosionDamage(entity.getPos(), this.mc.player.getBoundingBox())) < 0.5) {
            item = Items.TOTEM_OF_UNDYING;
            break;
         }

         if (this.onTnt.get() && entity instanceof TntEntity) {
            item = Items.TOTEM_OF_UNDYING;
            break;
         }

         if (this.onMinecartTnt.get() && entity instanceof TntMinecartEntity) {
            item = Items.TOTEM_OF_UNDYING;
            break;
         }

         if (this.onCreeper.get() && entity instanceof CreeperEntity) {
            item = Items.TOTEM_OF_UNDYING;
            break;
         }
      }

      if (this.onAnchor.get() && this.anchorNearby()) {
         item = Items.TOTEM_OF_UNDYING;
      }

      if (item == null || this.mc.player.getOffHandStack().getItem() == item) {
         return -1;
      }

      if (item == this.mc.player.getMainHandStack().getItem() && this.mc.options.useKey.isPressed()) {
         return -1;
      }

      return this.findInInventory(item);
   }

   /** Respawn anchor within 6 blocks of the player (original scan region: -6..6 on every axis). */
   private boolean anchorNearby() {
      BlockPos base = this.mc.player.getBlockPos();
      for (int x = -6; x <= 6; x++) {
         for (int y = -6; y <= 6; y++) {
            for (int z = -6; z <= 6; z++) {
               if (this.mc.world.getBlockState(base.add(x, y, z)).getBlock() == Blocks.RESPAWN_ANCHOR) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   // --------------------------------------------------------- explosion damage

   /** Vanilla end crystal / TNT style damage (power 6) on the player standing in {@code box}. Blast protection is not subtracted. */
   private float explosionDamage(Vec3d center, Box box) {
      Difficulty difficulty = this.mc.world.getDifficulty();
      if (difficulty == Difficulty.PEACEFUL || this.mc.player.isInvulnerable()) {
         return 0.0F;
      }

      Vec3d feet = new Vec3d(box.getCenter().x, box.minY, box.getCenter().z);
      double distance = Math.sqrt(feet.squaredDistanceTo(center)) / 12.0;
      if (distance > 1.0) {
         return 0.0F;
      }

      double impact = (1.0 - distance) * this.exposure(center, box);
      float damage = (float)Math.floor((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
      if (difficulty == Difficulty.EASY) {
         damage = Math.min(damage / 2.0F + 1.0F, damage);
      } else if (difficulty == Difficulty.HARD) {
         damage = damage * 3.0F / 2.0F;
      }

      float armor = (float)this.mc.player.getArmor();
      float toughness = (float)this.mc.player.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS);
      float f = 2.0F + toughness / 4.0F;
      float g = MathHelper.clamp(armor - damage / f, armor * 0.2F, 20.0F);
      damage *= 1.0F - g / 25.0F;
      var resistance = this.mc.player.getStatusEffect(StatusEffects.RESISTANCE);
      if (resistance != null) {
         int level = 25 - (resistance.getAmplifier() + 1) * 5;
         damage = Math.max(damage * (float)level / 25.0F, 0.0F);
      }

      return Math.max(damage, 0.0F);
   }

   /** Share of sample points of the box with a free line to the explosion (vanilla Explosion.getExposure grid). */
   private double exposure(Vec3d source, Box box) {
      double dx = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
      double dy = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
      double dz = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
      double ox = (1.0 - Math.floor(1.0 / dx) * dx) / 2.0;
      double oz = (1.0 - Math.floor(1.0 / dz) * dz) / 2.0;
      if (dx < 0.0 || dy < 0.0 || dz < 0.0) {
         return 0.0;
      }

      int hit = 0;
      int total = 0;
      for (double x = 0.0; x <= 1.0; x += dx) {
         for (double y = 0.0; y <= 1.0; y += dy) {
            for (double z = 0.0; z <= 1.0; z += dz) {
               Vec3d point = new Vec3d(
                  MathHelper.lerp(x, box.minX, box.maxX) + ox,
                  MathHelper.lerp(y, box.minY, box.maxY),
                  MathHelper.lerp(z, box.minZ, box.maxZ) + oz
               );
               if (this.mc.world.raycast(new RaycastContext(point, source, ShapeType.COLLIDER, FluidHandling.NONE, this.mc.player)).getType()
                  == net.minecraft.util.hit.HitResult.Type.MISS) {
                  hit++;
               }

               total++;
            }
         }
      }

      return total == 0 ? 0.0 : (double)hit / (double)total;
   }

   public static enum Mode {
      Default,
      Alternative,
      Matrix,
      MatrixPick,
      NewVersion;
   }

   public static enum OffHand {
      Totem,
      Crystal,
      GApple,
      Shield;
   }

   public static enum Swap {
      GappleShield,
      BallShield,
      GappleBall,
      BallTotem;
   }

   public static enum Always {
      Off,
      Always,
      OnlySafe;
   }
}
