package dev.sculk.module.modules.combat;

import dev.sculk.SculkClient;
import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.KeySetting;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.util.Rotation;
import dev.sculk.util.RotationUtils;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** ElytraTarget, ported from Nursultan: while gliding it steers towards the AttackAura target, extends the chase range and uses fireworks. */
public final class ElytraTarget extends Module {
   private final NumberSetting chaseDistance = new NumberSetting("Chase Distance", 50.0, 10.0, 200.0, 5.0).describe("AttackAura tracks targets this far while gliding");
   private final BooleanSetting overtake = new BooleanSetting("Overtake", true).describe("Aims as if you were ahead of your position so you fly past the target");
   private final NumberSetting overtakeDistance = new NumberSetting("Overtake Distance", 5.0, 0.0, 6.0, 1.0).visibleWhen(() -> this.overtake.get());
   private final ModeSetting<ElytraTarget.FireworkUse> fireworkUse = new ModeSetting<>("Firework Use", ElytraTarget.FireworkUse.None);
   private final NumberSetting delayTicks = new NumberSetting("Delay Ticks", 20.0, 2.0, 60.0, 1.0)
      .visibleWhen(() -> this.fireworkUse.getValue() == ElytraTarget.FireworkUse.Timing);
   private final KeySetting manualKey = new KeySetting("Manual Hotkey", -1)
      .visibleWhen(() -> this.fireworkUse.getValue() == ElytraTarget.FireworkUse.Bind);
   private int sinceFirework = 100;
   private boolean keyWasDown;

   public ElytraTarget() {
      super("ElytraTarget", "Chases the AttackAura target while flying with an elytra", Category.COMBAT);
      this.addSettings(new Setting[]{this.chaseDistance, this.overtake, this.overtakeDistance, this.fireworkUse, this.delayTicks, this.manualKey});
   }

   public double chaseDistance() {
      return this.chaseDistance.getValue();
   }

   /** True while this module is flying you towards a target (AttackAura then leaves the rotation to us). */
   public boolean steering() {
      if (!this.isEnabled() || this.mc == null || this.mc.player == null || !this.mc.player.isFallFlying()) {
         return false;
      }

      AttackAura aura = SculkClient.get(AttackAura.class);
      return aura != null && aura.isEnabled() && aura.currentTarget() != null;
   }

   @Override
   public void onEnable() {
      this.sinceFirework = 100;
      this.keyWasDown = false;
   }

   @Override
   public void onTick() {
      if (this.mc == null || this.mc.player == null || this.mc.world == null || this.mc.interactionManager == null) {
         return;
      }

      this.sinceFirework++;
      if (!this.steering()) {
         this.keyWasDown = false;
         return;
      }

      Entity target = SculkClient.get(AttackAura.class).currentTarget();
      this.steerTo(target);
      this.handleFirework();
   }

   private void steerTo(Entity target) {
      Vec3d from = this.mc.player.getEyePos();
      if (this.overtake.get()) {
         Vec3d look = this.mc.player.getRotationVec(1.0F).normalize();
         from = from.add(look.multiply(this.overtakeDistance.getValue()));
      }

      Box box = target.getBoundingBox();
      Vec3d point = new Vec3d((box.minX + box.maxX) / 2.0, (box.minY + box.maxY) / 2.0, (box.minZ + box.maxZ) / 2.0);
      double dx = point.x - from.x;
      double dy = point.y - from.y;
      double dz = point.z - from.z;
      double flat = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float pitch = (float)-MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dy, flat)));
      this.mc.player.setYaw(yaw);
      this.mc.player.setPitch(MathHelper.clamp(pitch, -90.0F, 90.0F));
   }

   private void handleFirework() {
      switch (this.fireworkUse.getValue()) {
         case None -> {
         }
         case Auto -> {
            double speed = this.mc.player.getVelocity().length();
            if (speed < 1.0 && this.sinceFirework >= 20) {
               this.useFirework();
            }
         }
         case Timing -> {
            if (this.sinceFirework >= this.delayTicks.getInt()) {
               this.useFirework();
            }
         }
         case Bind -> {
            int key = this.manualKey.getKey();
            boolean down = key >= 0 && InputUtil.isKeyPressed(this.mc.getWindow().getHandle(), key);
            if (down && !this.keyWasDown) {
               this.useFirework();
            }

            this.keyWasDown = down;
         }
      }
   }

   private void useFirework() {
      Hand hand = null;
      int previous = this.mc.player.getInventory().selectedSlot;
      int swapTo = -1;
      if (this.mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET)) {
         hand = Hand.MAIN_HAND;
      } else if (this.mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
         hand = Hand.OFF_HAND;
      } else {
         for (int i = 0; i < 9; i++) {
            if (this.mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
               swapTo = i;
               break;
            }
         }

         if (swapTo < 0) {
            return;
         }

         this.mc.player.getInventory().selectedSlot = swapTo;
         hand = Hand.MAIN_HAND;
      }

      this.mc.interactionManager.interactItem(this.mc.player, hand);
      this.mc.player.swingHand(hand);
      if (swapTo >= 0) {
         this.mc.player.getInventory().selectedSlot = previous;
      }

      this.sinceFirework = 0;
   }

   public static enum FireworkUse {
      None,
      Auto,
      Bind,
      Timing;
   }
}
