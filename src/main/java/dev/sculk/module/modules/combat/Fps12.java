package dev.sculk.module.modules.combat;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.util.CombatUtils;
import dev.sculk.util.Friends;
import dev.sculk.util.ServerClock;
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
   private static final double REACH = 2.85;
   // Hard CPS cap: 100ms = max 10 CPS (legit oyuncu 1-4 CPS, guvenli tavan)
   private static final long MIN_ATTACK_GAP_MS = 100L;

   private final BooleanSetting swordOnly = new BooleanSetting("Sword Only", false)
      .describe("Sadece kilic/balta/iksir tutarken calis");
   private final BooleanSetting waitCritical = new BooleanSetting("Wait Critical", false).describe("Duserken vur (critical)");
   private final BooleanSetting onLeftClick = new BooleanSetting("On Left Click", false).describe("Sadece LMB basiliyken calis");
   private final NumberSetting slot = new NumberSetting("Slot", 0.0, 0.0, 9.0, 1.0).describe("0=hepsi, 1-9=o slot");
   private final BooleanSetting lagGuard = new BooleanSetting("Lag Guard", true)
      .describe("Sunucu takildiginda vurusu bekle");

   private long lastAttackMs = 0L;
   private long lastFallbackMs = 0L;
   private long firedTick = Long.MIN_VALUE;

   public Fps12() {
      super("Trigger Bot", "Nisandaki hedefe otomatik vurus", Category.COMBAT);
      this.addSettings(new Setting[]{this.swordOnly, this.waitCritical, this.onLeftClick, this.slot, this.lagGuard});
   }

   @Override
   public void onEnable() {
      this.lastAttackMs = 0L;
      this.lastFallbackMs = 0L;
      this.firedTick = Long.MIN_VALUE;
   }

   @Override
   public void onBufferUpdate() {
      if (this.mc.player == null || this.mc.world == null || this.mc.interactionManager == null) return;
      if (this.mc.currentScreen != null) return;

      ItemStack held = this.mc.player.getMainHandStack();
      Item heldItem = held.getItem();
      boolean holdingSword = heldItem instanceof SwordItem || heldItem instanceof AxeItem;
      boolean holdingPotion = heldItem instanceof PotionItem;
      if (this.swordOnly.get() && !holdingSword && !holdingPotion) return;

      int slotVal = this.slot.getInt();
      if (slotVal > 0 && this.mc.player.getInventory().selectedSlot + 1 != slotVal) return;

      if (this.onLeftClick.get() && GLFW.glfwGetMouseButton(this.mc.getWindow().getHandle(), 0) != 1) return;

      // ===== GATE 1: Esyanin gercek cooldown'u =====
      // Sword: ~1.6 CPS, Axe: ~1.0-1.25 CPS, Fist: ~4 CPS
      // Bu gate tek basina 100 CPS'i imkansiz kilar
      if (this.mc.player.getAttackCooldownProgress(0.0F) < 0.98F) return;

      // ===== GATE 2: Hard CPS cap (bug defensive) =====
      long now = System.currentTimeMillis();
      if (now - this.lastAttackMs < MIN_ATTACK_GAP_MS) return;

      // ===== GATE 3: Wait Critical (yukselirken vurma) =====
      if (this.waitCritical.get() && holdingSword && !this.mc.player.isFallFlying()) {
         if (!this.mc.player.isOnGround() && this.mc.player.getVelocity().y >= 0.0) return;
      }

      // ===== GATE 4: Hedef bul =====
      Entity target = this.findTarget();
      if (target == null) return;

      // ===== GATE 5: Server tick senkronizasyonu =====
      if (this.lagGuard.get() && !this.holdForServer()) return;

      // ===== ATES =====
      this.lastAttackMs = now;
      CombatUtils.hitEntity(target, true);
   }

   private boolean holdForServer() {
      if (ServerClock.jammed()) return false;
      long serverTick = ServerClock.tick();
      if (serverTick == Long.MIN_VALUE) {
         // ServerClock yoksa frame-time fallback (max 20/s)
         long now = System.currentTimeMillis();
         if (now - this.lastFallbackMs < 50L) return false;
         this.lastFallbackMs = now;
         return true;
      }
      if (serverTick == this.firedTick) return false;
      this.firedTick = serverTick;
      return true;
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
