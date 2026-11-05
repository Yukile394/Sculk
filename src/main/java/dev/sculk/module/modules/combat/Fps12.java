package dev.sculk.module.modules.combat;

import dev.sculk.SculkClient;
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
   private static final double REACH = 3.0;
   private static final double TRACE_MARGIN = 1.5;
   private final BooleanSetting swordOnly = new BooleanSetting("Sword Only", false)
      .describe("Sadece k\u0131l\u0131\u00e7/balta/iksir tutarken \u00e7al\u0131\u015f");
   private final BooleanSetting waitCritical = new BooleanSetting("Wait Critical", false).describe("D\u00fc\u015ferken vur (critical)");
   private final BooleanSetting onLeftClick = new BooleanSetting("On Left Click", false).describe("Sadece LMB bas\u0131l\u0131yken \u00e7al\u0131\u015f");
   private final NumberSetting attackDelay = new NumberSetting("Attack Delay", 0.0, 0.0, 500.0, 1.0).describe("500ms base'e eklenen ms (smoothhud default=0)");
   private final NumberSetting slot = new NumberSetting("Slot", 0.0, 0.0, 9.0, 1.0).describe("0=hepsi, 1-9=o slot");
   private final BooleanSetting lagGuard = new BooleanSetting("Lag Guard", false)
      .describe("Sunucu tak\u0131ld\u0131\u011f\u0131nda vuru\u015fu bekle, tick ba\u015f\u0131na tek vuru\u015f");
   private final Timer timer = new Timer();
   private Item lastItem;
   private boolean ready = true;
   private long firedTick = Long.MIN_VALUE;

   public Fps12() {
      super("Trigger Bot", "Ni\u015fandaki hedefe otomatik vuru\u015f", Category.COMBAT);
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
      AutoFirework firework = SculkClient.get(AutoFirework.class);
      if (firework != null && firework.takesOverTrigger()) {
         return;
      }

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
      if (ServerClock.jammed()) {
         return false;
      } else {
         long serverTick = ServerClock.tick();
         if (serverTick == Long.MIN_VALUE) {
            return true;
         } else if (serverTick == this.firedTick) {
            return false;
         } else {
            this.firedTick = serverTick;
            return true;
         }
      }
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
      Vec3d endPos = eyePos.add(lookVec.x * 3.0, lookVec.y * 3.0, lookVec.z * 3.0);
      Box box = new Box(
         Math.min(eyePos.x, endPos.x) - 1.5,
         Math.min(eyePos.y, endPos.y) - 1.5,
         Math.min(eyePos.z, endPos.z) - 1.5,
         Math.max(eyePos.x, endPos.x) + 1.5,
         Math.max(eyePos.y, endPos.y) + 1.5,
         Math.max(eyePos.z, endPos.z) + 1.5
      );
      EntityHitResult hit = ProjectileUtil.raycast(
         this.mc.player, eyePos, endPos, box, ex -> !ex.isSpectator() && ex.canHit() && ex.isAlive() && !Friends.isAlly(ex), 9.0
      );
      if (hit == null) {
         return null;
      } else {
         Entity e = hit.getEntity();
         return e instanceof LivingEntity && e.isAlive() && !Friends.isAlly(e) ? e : null;
      }
   }

   private Entity findEntityScan() {
      Vec3d eyePos = this.mc.player.getCameraPosVec(1.0F);
      Vec3d lookVec = this.mc.player.getRotationVec(1.0F);
      double reachSq = 9.0;
      Entity best = null;
      double bestDist = Double.MAX_VALUE;

      for (Entity e : this.mc.world.getEntities()) {
         if (e != this.mc.player
            && e.isAlive()
            && e.canHit()
            && !e.isSpectator()
            && !Friends.isAlly(e)
            && e instanceof LivingEntity
            && !(e.squaredDistanceTo(eyePos) > reachSq)) {
            Box eBox = e.getBoundingBox();
            if (this.rayBoxIntersect(eyePos, lookVec, eBox, 3.0)) {
               double dist = e.squaredDistanceTo(eyePos);
               if (dist < bestDist) {
                  bestDist = dist;
                  best = e;
               }
            }
         }
      }

      return best;
   }

   private boolean rayBoxIntersect(Vec3d origin, Vec3d dir, Box box, double maxDist) {
      double enter = Double.NEGATIVE_INFINITY;
      double exit = Double.POSITIVE_INFINITY;
      double[] o = new double[]{origin.x, origin.y, origin.z};
      double[] d = new double[]{dir.x, dir.y, dir.z};
      double[] min = new double[]{box.minX, box.minY, box.minZ};
      double[] max = new double[]{box.maxX, box.maxY, box.maxZ};

      for (int i = 0; i < 3; i++) {
         if (d[i] != 0.0) {
            double t1 = (min[i] - o[i]) / d[i];
            double t2 = (max[i] - o[i]) / d[i];
            if (t1 > t2) {
               double tmp = t1;
               t1 = t2;
               t2 = tmp;
            }

            enter = Math.max(enter, t1);
            exit = Math.min(exit, t2);
         } else if (o[i] < min[i] || o[i] > max[i]) {
            return false;
         }
      }

      return enter <= exit && exit >= 0.0 && enter <= maxDist;
   }
}
