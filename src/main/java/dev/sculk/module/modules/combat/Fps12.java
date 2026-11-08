// Java 17+, Fabric/Yarn mappings, Minecraft 1.20.x client
// file: dev/sculk/module/modules/combat/Fps12.java
// target: Sculk client trigger-bot module, item-aware cooldown-accurate auto attack

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
import net.minecraft.item.TridentItem;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public final class Fps12 extends Module {
   private static final double REACH = 2.85;
   // item cooldown'u bypass etmeyen tek sacma degil: bu sadece "ayni frame'de 2 kere ates etme" guard'i
   private static final long MIN_FRAME_GAP_MS = 10L;

   private final BooleanSetting swordOnly = new BooleanSetting("Sword Only", false)
      .describe("Sadece kilic/balta/trident/iksir tutarken calis");
   private final BooleanSetting waitCritical = new BooleanSetting("Critical", false)
      .describe("Havadayken (dusme fazinda) vur, yerdeyken bekle - crit hasari");
   private final BooleanSetting onLeftClick = new BooleanSetting("On Left Click", false).describe("Sadece LMB basiliyken calis");
   private final NumberSetting slot = new NumberSetting("Slot", 0.0, 0.0, 9.0, 1.0).describe("0=hepsi, 1-9=o slot");
   private final BooleanSetting lagGuard = new BooleanSetting("Lag Guard", true)
      .describe("Sunucu takildiginda vurusu bekle");
   private final NumberSetting cooldownThreshold = new NumberSetting("CD Threshold", 1.0, 0.85, 1.0, 0.01)
      .describe("Vurus icin gereken minimum cooldown progress (1.0 = tam sifirlanmis, legit)");
   private final BooleanSetting blockBreak = new BooleanSetting("Ignore Blocking", true)
      .describe("Hedef shield/blok tutuyorsa yine de vur (swing waste engellenmez ama legit davranis)");

   private long lastFrameMs = 0L;
   private long lastFallbackMs = 0L;
   private long firedTick = Long.MIN_VALUE;
   // item cooldown resetlendi mi kontrolu icin: ayni "cooldown cycle" icinde 2. ates engellenir
   private boolean firedThisCycle = false;
   private float lastObservedProgress = 1.0F;

   public Fps12() {
      super("Trigger Bot", "Nisandaki hedefe item cooldown'una senkron otomatik vurus", Category.COMBAT);
      this.addSettings(new Setting[]{this.swordOnly, this.waitCritical, this.onLeftClick, this.slot,
         this.lagGuard, this.cooldownThreshold, this.blockBreak});
   }

   @Override
   public void onEnable() {
      this.lastFrameMs = 0L;
      this.lastFallbackMs = 0L;
      this.firedTick = Long.MIN_VALUE;
      this.firedThisCycle = false;
      this.lastObservedProgress = 1.0F;
   }

   @Override
   public void onBufferUpdate() {
      if (this.mc.player == null || this.mc.world == null || this.mc.interactionManager == null) return;
      if (this.mc.currentScreen != null) return;

      ItemStack held = this.mc.player.getMainHandStack();
      Item heldItem = held.getItem();
      boolean holdingSword = heldItem instanceof SwordItem || heldItem instanceof AxeItem || heldItem instanceof TridentItem;
      boolean holdingPotion = heldItem instanceof PotionItem;
      if (this.swordOnly.get() && !holdingSword && !holdingPotion) return;

      int slotVal = this.slot.getInt();
      if (slotVal > 0 && this.mc.player.getInventory().selectedSlot + 1 != slotVal) return;

      if (this.onLeftClick.get() && GLFW.glfwGetMouseButton(this.mc.getWindow().getHandle(), 0) != 1) return;

      // ===== GATE 1: ayni frame'de cift tetiklenmeyi engelle (spam fix'in koku burada) =====
      long now = System.currentTimeMillis();
      if (now - this.lastFrameMs < MIN_FRAME_GAP_MS) return;

      // ===== GATE 2: item'in gercek cooldown progress'i, edge-trigger =====
      // onBufferUpdate her frame cagrilir; progress 0.98+ oldugu surece eski kod HER FRAME ates ediyordu.
      // duzeltme: progress'in 1.0'dan dusup tekrar threshold'a CIKTIGI ani yakala (rising edge),
      // sonra bir sonraki swing (progress tekrar dusene kadar) kilitli kal.
      float progress = this.mc.player.getAttackCooldownProgress(0.0F);
      boolean cycleReset = progress < this.lastObservedProgress - 0.01F; // progress dustu = yeni swing basladi
      if (cycleReset) this.firedThisCycle = false;
      this.lastObservedProgress = progress;

      if (progress < this.cooldownThreshold.getFloat()) return;
      if (this.firedThisCycle) return; // bu cycle'da zaten vurduk, tekrar vurma -> spam'in asil sebebi buydu

      // ===== GATE 3: Critical (sadece dusme fazinda vur) =====
      if (this.waitCritical.get() && holdingSword && !this.mc.player.isFallFlying()) {
         if (!(!this.mc.player.isOnGround() && this.mc.player.getVelocity().y < 0.0)) return;
      }

      // ===== GATE 4: Hedef bul =====
      Entity target = this.findTarget();
      if (target == null) return;
      if (!this.blockBreak.get() && this.isBlocking(target)) return;

      // ===== GATE 5: Server tick senkronizasyonu =====
      if (this.lagGuard.get() && !this.holdForServer()) return;

      // ===== ATES =====
      this.lastFrameMs = now;
      this.firedThisCycle = true;
      CombatUtils.hitEntity(target, true);
   }

   private boolean isBlocking(Entity e) {
      return e instanceof LivingEntity le && le.isBlocking();
   }

   private boolean holdForServer() {
      if (ServerClock.jammed()) return false;
      long serverTick = ServerClock.tick();
      if (serverTick == Long.MIN_VALUE) {
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
