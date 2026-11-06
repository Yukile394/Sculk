package dev.sculk.module.modules.combat;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.util.CombatUtils;
import dev.sculk.util.Friends;
import dev.sculk.util.ServerClock;
import dev.sculk.util.Timer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.item.SwordItem;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public final class Fps12 extends Module {
   // Reach 3.0 -> 2.85 : Grim/Polar latency toleransinin altinda kal
   private static final double REACH = 2.85;
   private final BooleanSetting swordOnly = new BooleanSetting("Sword Only", false)
      .describe("Sadece kilic/balta/iksir tutarken calis");
   private final BooleanSetting waitCritical = new BooleanSetting("Wait Critical", false).describe("Duserken vur (critical)");
   private final BooleanSetting onLeftClick = new BooleanSetting("On Left Click", false).describe("Sadece LMB basiliyken calis");
   private final NumberSetting attackDelay = new NumberSetting("Attack Delay", 50.0, 0.0, 500.0, 1.0)
      .describe("500ms base'e eklenen ms (legit aralik icin 50 onerilir)");
   private final NumberSetting slot = new NumberSetting("Slot", 0.0, 0.0, 9.0, 1.0).describe("0=hepsi, 1-9=o slot");
   private final BooleanSetting lagGuard = new BooleanSetting("Lag Guard", true)
      .describe("Sunucu takildiginda vurusu bekle, tick basina tek vurus");
   private final Timer timer = new Timer();
   private Item lastItem;
   private boolean ready = true;
   private long firedTick = Long.MIN_VALUE;

   public Fps12() {
      super("Trigger Bot", "Nisandaki hedefe otomatik vurus", Category.COMBAT);
      this.addSettings(new Setting[]{this.swordOnly, this.waitCritical, this.onLeftClick, this.attackDelay, this.slot, this.lagGuard});
   }

   @Override
   public void onEnable() {
      this.timer.reset();
      this.ready = true;
      this.lastItem = null;
      this.firedTick = Long.MIN_VALUE;
   }

   @Override
   public void onBufferUpdate() {
      if (this.mc.player != null && this.mc.world != null && this.mc.interactionManager != null) {
         if (this.mc.currentScreen == null) {
            ItemStack held = this.mc.player.getMainHandStack();
            Item heldItem = held.getItem();
            boolean holdingSword = heldItem instanceof SwordItem || heldItem instanceof AxeItem;
            boolean holdingPotion = heldItem instanceof PotionItem;
            if (heldItem != this.lastItem) {
               this.timer.reset();
               this.ready = true;
            }
            this.lastItem = heldItem;
            if (!this.swordOnly.get() || holdingSword || holdingPotion) {
               int slotVal = this.slot.getInt();
               if (slotVal <= 0 || this.mc.player.getInventory().selectedSlot + 1 == slotVal) {
                  if (!this.onLeftClick.get() || GLFW.glfwGetMouseButton(this.mc.getWindow().getHandle(), 0) == 1) {
                     Entity target = this.findTarget();
                     if (target != null) {
                        if (this.ready || this.attackReady(holdingSword)) {
                           if (!this.lagGuard.get() || this.holdForServer()) {
                              this.ready = false;
                              CombatUtils.hitEntity(target, true);
                              this.timer.reset();
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean holdForServer() {
      if (ServerClock.jammed()) return false;
      long serverTick = ServerClock.tick();
      if (serverTick == Long.MIN_VALUE) return true;
      if (serverTick == this.firedTick) return false;
      this.firedTick = serverTick;
      return true;
   }

   private boolean attackReady(boolean holdingSword) {
      long base = 500L + (long)this.attackDelay.getInt();
      if (!holdingSword || !this.waitCritical.get() || this.mc.player.isFallFlying()) {
         return this.mc.player.getAttackCooldownProgress(0.5F) < 1.0F ? false : this.timer.delay(base);
      } else if (this.mc.player.isOnGround()) {
         return this.mc.player.getAttackCooldownProgress(0.5F) < 1.0F ? false : this.timer.delay(base);
      } else if (this.mc.player.getVelocity().y >= 0.0) {
         return false;
      } else {
         return this.mc.player.getAttackCooldownProgress(0.5F) < 0.85F ? false : this.timer.delay(base);
      }
   }

   private Entity findTarget() {
      Entity traced = this.findEntityTrace();
      return traced != null ? traced : this.findEntityScan();
   }

   private Entity findEntityTrace() {
      Vec3d eyePos = this.mc.player.getCameraPosVec(1.0F);
      Vec3d lookVec = this.mc.player.getRotationVec(1.0F);
      Vec3d endPos = eyePos.add(lookVec.x * REACH, lookVec.y * REACH, lookVec.z * REACH);
      Box box = new Box(
         Math.min(eyePos.x, endPos.x) - 1.0,
         Math.min(eyePos.y, endPos.y) - 1.0,
         Math.min(eyePos.z, endPos.z) - 1.0,
         Math.max(eyePos.x, endPos.x) + 1.0,
         Math.max(eyePos.y, endPos.y) + 1.0,
         Math.max(eyePos.z, endPos.z) + 1.0
      );
      EntityHitResult hit = ProjectileUtil.raycast(
         this.mc.player, eyePos, endPos, box,
         ex -> !ex.isSpectator() && ex.canHit() && ex.isAlive() && !Friends.isAlly(ex),
         REACH * REACH
      );
      if (hit == null) return null;
      Entity e = hit.getEntity();
      return e instanceof LivingEntity && e.isAlive() && !Friends.isAlly(e) ? e : null;
   }

   private Entity findEntityScan() {
      Vec3d eyePos = this.mc.player.getCameraPosVec(1.0F);
      Vec3d lookVec = this.mc.player.getRotationVec(1.0F);
      double reachSq = REACH * REACH;
      Entity best = null;
      double bestDist = Double.MAX_VALUE;
      for (Entity e : this.mc.world.getEntities()) {
         if (e != this.mc.player && e.isAlive() && e.canHit() && !e.isSpectator() && !Friends.isAlly(e)
            && e instanceof LivingEntity && !(e.squaredDistanceTo(eyePos) > reachSq)) {
            Box eBox = e.getBoundingBox();
            if (this.rayBoxIntersect(eyePos, lookVec, eBox, REACH)) {
               double dist = e.squaredDistanceTo(eyePos);
               if (dist < bestDist) { bestDist = dist; best = e; }
            }
         }
      }
      return best;
   }

   private boolean rayBoxIntersect(Vec3d origin, Vec3d dir, Box box, double maxDist) {
      double enter = Double.NEGATIVE_INFINITY, exit = Double.POSITIVE_INFINITY;
      double[] o = {origin.x, origin.y, origin.z};
      double[] d = {dir.x, dir.y, dir.z};
      double[] min = {box.minX, box.minY, box.minZ};
      double[] max = {box.maxX, box.maxY, box.maxZ};
      for (int i = 0; i < 3; i++) {
         if (d[i] != 0.0) {
            double t1 = (min[i] - o[i]) / d[i];
            double t2 = (max[i] - o[i]) / d[i];
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            enter = Math.max(enter, t1);
            exit = Math.min(exit, t2);
         } else if (o[i] < min[i] || o[i] > max[i]) return false;
      }
      return enter <= exit && exit >= 0.0 && enter <= maxDist;
   }
}
