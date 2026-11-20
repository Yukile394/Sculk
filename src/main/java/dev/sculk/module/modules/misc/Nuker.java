package dev.sculk.module.modules.misc;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.module.setting.StringSetting;
import dev.sculk.util.Rotation;
import dev.sculk.util.RotationManager;
import dev.sculk.util.RotationUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Nuker: breaks the blocks around you inside a radius and height window. Ported from Nursultan's Nuker.
 * 3x3 Mode: every chosen block also takes the 3x3 layer around it (the plane facing you) in the same pass.
 */
public final class Nuker extends Module {
   private final ModeSetting<Trigger> trigger = new ModeSetting<>("Trigger", Trigger.OnBreak)
      .describe("OnBreak = when you break a block, the 3x3 around it follows. Radius = breaks everything around you on its own");
   private final NumberSetting radius = new NumberSetting("Radius", 4.0, 1.0, 6.0, 0.5)
      .describe("Horizontal and vertical reach").visibleWhen(() -> this.trigger.getValue() == Trigger.Radius);
   private final NumberSetting minHeight = new NumberSetting("Min Height", 0.0, -4.0, 4.0, 1.0)
      .describe("Lowest layer relative to your feet").visibleWhen(() -> this.trigger.getValue() == Trigger.Radius);
   private final NumberSetting maxHeight = new NumberSetting("Max Height", 2.0, -4.0, 6.0, 1.0)
      .describe("Highest layer relative to your feet").visibleWhen(() -> this.trigger.getValue() == Trigger.Radius);
   private final BooleanSetting area3x3 = new BooleanSetting("3x3 Mode", true)
      .describe("A broken block also takes the 3x3 around it, on the face you are looking at");
   private final BooleanSetting area16 = new BooleanSetting("16x16 Mode", false)
      .describe("OnBreak only: takes a 16x16 wall/floor around the broken block (wins over 3x3). Blocks out of reach wait until you walk close")
      .visibleWhen(() -> this.trigger.getValue() == Trigger.OnBreak);
   private final BooleanSetting protectFloor = new BooleanSetting("Protect Floor", true)
      .describe("Never breaks the block you stand on or anything below it");
   private final NumberSetting perTick = new NumberSetting("Blocks Per Tick", 9.0, 1.0, 32.0, 1.0)
      .describe("Instant-break blocks sent per tick (creative / instant tools, Protection Off). Higher is faster but louder");
   private final BooleanSetting onlyAllowed = new BooleanSetting("Only Allowed Blocks", false).describe("Break only the blocks listed below");
   private final StringSetting allowed = new StringSetting("Allowed Blocks", "stone,dirt,cobblestone", 120)
      .describe("Comma separated block ids, e.g. diamond_ore,iron_ore").visibleWhen(() -> this.onlyAllowed.get());
   private final BooleanSetting rotate = new BooleanSetting("Rotate", false).describe("Look at the block you are breaking");
   private final BooleanSetting swing = new BooleanSetting("Swing", true);
   private final ModeSetting<Protection> protection = new ModeSetting<>("Protection", Protection.Off)
      .describe("Off = fast. Grim / Matrix = legit-style breaking: one visible block at a time, aimed first, paced (presets are uncalibrated)");
   private final NumberSetting aimSpeed = new NumberSetting("Aim Speed", 35.0, 5.0, 180.0, 5.0)
      .describe("Max degrees per tick the server-side rotation may turn").visibleWhen(() -> this.protection.getValue() != Protection.Off);
   private final NumberSetting extraDelay = new NumberSetting("Extra Delay", 0.0, 0.0, 10.0, 1.0)
      .describe("Ticks to wait after every broken block, on top of the mode preset")
      .visibleWhen(() -> this.protection.getValue() != Protection.Off);
   /** Survival only: blocks of the current group that still need real mining progress, in mining order. */
   private final List<BlockPos> queue = new ArrayList<>();
   /** OnBreak, Protection Off: blocks that break instantly (creative / fast tool), sent in per-tick batches. */
   private final List<BlockPos> rapid = new ArrayList<>();
   /** OnBreak: the rest of the 3x3 / 16x16 area, released in chunks as blocks come into reach. */
   private final List<BlockPos> pending = new ArrayList<>();
   private int pendingIdle;
   private BlockPos current;
   private int delay;
   private int aimTicks;
   /** OnBreak: block (and face) the player is currently mining, armed before the click is processed. */
   private BlockPos watch;
   private Direction watchSide;
   private int idleTicks;

   public enum Trigger {
      OnBreak,
      Radius
   }

   /** Reach cap and pacing per preset. Values are untuned guesses, not measured against the real anticheats. */
   public enum Protection {
      Off(0.0, 0, 0),
      Grim(4.0, 0, 0),
      Matrix(3.5, 1, 1);

      private final double reach;
      private final int minDelay;
      private final int jitter;

      Protection(double reach, int minDelay, int jitter) {
         this.reach = reach;
         this.minDelay = minDelay;
         this.jitter = jitter;
      }
   }

   private record Aim(Vec3d point, Direction side, boolean onTarget) {
   }

   private record Scan(Vec3d eye, double reach, int feetY, int low, int high, Set<String> whitelist, int floorY) {
   }

   public Nuker() {
      super("Nuker", "Breaks blocks around you", Category.MISC);
      this.addSettings(
         new Setting[]{this.trigger, this.radius, this.minHeight, this.maxHeight, this.area3x3, this.area16, this.protectFloor, this.perTick, this.onlyAllowed, this.allowed, this.rotate, this.swing,
            this.protection, this.aimSpeed, this.extraDelay}
      );
   }

   @Override
   public void onDisable() {
      this.watch = null;
      this.queue.clear();
      this.rapid.clear();
      this.pending.clear();
      this.current = null;
      this.delay = 0;
      this.aimTicks = 0;
      if (this.mc != null && this.mc.interactionManager != null) {
         this.mc.interactionManager.cancelBlockBreaking();
      }
   }

   /** True while the Nuker is mining a queued block; the client's own LMB handling must stay out of the way then. */
   public boolean ownsBreaking() {
      return this.isEnabled() && (!this.queue.isEmpty() || !this.rapid.isEmpty()) && this.mc != null && this.mc.currentScreen == null;
   }

   /** Runs before the client processes the click, so the block is still there when it gets armed. */
   @Override
   public void onBufferUpdate() {
      if (this.trigger.getValue() != Trigger.OnBreak || this.mc == null || this.mc.world == null || this.mc.currentScreen != null) {
         return;
      }

      if (!this.mc.options.attackKey.isPressed()) {
         if (++this.idleTicks > 10 && this.queue.isEmpty()) {
            this.watch = null;
         }

         return;
      }

      this.idleTicks = 0;
      if (this.queue.isEmpty() && this.mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
         BlockPos pos = hit.getBlockPos();
         if (!this.mc.world.getBlockState(pos).isAir()) {
            this.watch = pos;
            this.watchSide = hit.getSide();
         }
      }
   }

   @Override
   public void onTick() {
      if (this.mc == null || this.mc.player == null || this.mc.world == null || this.mc.interactionManager == null) {
         this.queue.clear();
         this.rapid.clear();
         this.pending.clear();
         this.current = null;
         return;
      }

      if (this.mc.currentScreen != null || this.mc.player.isSpectator()) {
         return;
      }

      Scan scan = this.scan();
      boolean acted;
      boolean guarded = this.protection.getValue() != Protection.Off;
      boolean creativeNow = this.mc.interactionManager.getCurrentGameMode().isCreative();
      if (this.trigger.getValue() == Trigger.OnBreak) {
         acted = this.tickOnBreak(scan, creativeNow);
      } else if (guarded) {
         acted = this.tickProtected(scan, true);
      } else if (creativeNow) {
         this.queue.clear();
         acted = this.tickCreative(scan);
      } else {
         acted = this.tickSurvival(scan, true);
      }

      if (acted && (guarded || this.swing.get())) {
         this.mc.player.swingHand(Hand.MAIN_HAND);
      }
   }

   /**
    * OnBreak: waits until the block you were mining is gone, then takes the 3x3 (or 16x16) area around it in the
    * plane you were facing. The area is released in chunks of blocks that are in reach (and visible, with
    * Protection on); the rest waits until you walk closer. Reach is never exceeded, so the server accepts every break.
    */
   private boolean tickOnBreak(Scan scan, boolean creative) {
      boolean acted = false;
      if (this.watch != null && this.mc.world.getBlockState(this.watch).isAir()) {
         BlockPos broken = this.watch;
         Direction side = this.watchSide != null ? this.watchSide : this.faceToward(broken);
         this.watch = null;
         this.pending.clear();
         this.pending.addAll(this.ring(broken, side.getAxis(), scan));
         this.pendingIdle = 0;
      }

      this.dispatchPending(scan, creative);
      if (this.protection.getValue() != Protection.Off) {
         acted = this.tickProtected(scan, false);
      } else if (!this.rapid.isEmpty()) {
         acted = this.drainRapid(scan);
      } else if (!creative) {
         acted = this.tickSurvival(scan, false);
      }

      return acted;
   }

   /** Blocks of the plane around {@code center} (16x16 or 3x3, center excluded) that may be broken at all, reach aside. */
   private List<BlockPos> ring(BlockPos center, Direction.Axis axis, Scan scan) {
      int lo;
      int hi;
      if (this.area16.get()) {
         lo = -8;
         hi = 7;
      } else if (this.area3x3.get()) {
         lo = -1;
         hi = 1;
      } else {
         return new ArrayList<>();
      }

      List<BlockPos> out = new ArrayList<>();
      for (int a = lo; a <= hi; a++) {
         for (int b = lo; b <= hi; b++) {
            if (a == 0 && b == 0) {
               continue;
            }

            BlockPos pos = switch (axis) {
               case X -> center.add(0, a, b);
               case Y -> center.add(a, 0, b);
               case Z -> center.add(a, b, 0);
            };
            if (this.eligible(pos, scan, false)) {
               out.add(pos);
            }
         }
      }

      return out;
   }

   /** Moves the nearest in-reach blocks of the pending area into the work queues, one chunk at a time. */
   private void dispatchPending(Scan scan, boolean creative) {
      if (this.pending.isEmpty()) {
         return;
      }

      this.pending.removeIf(pos -> !this.eligible(pos, scan, false));
      if (!this.queue.isEmpty() || !this.rapid.isEmpty()) {
         return;
      }

      if (++this.pendingIdle > 200) {
         this.pending.clear();
         return;
      }

      boolean guarded = this.protection.getValue() != Protection.Off;
      List<BlockPos> chunk = new ArrayList<>();
      for (BlockPos pos : this.pending) {
         if (this.eligible(pos, scan) && (!guarded || this.aim(pos, scan) != null)) {
            chunk.add(pos);
         }
      }

      if (chunk.isEmpty()) {
         return;
      }

      chunk.sort(Comparator.comparingDouble(p -> Vec3d.ofCenter(p).squaredDistanceTo(scan.eye())));
      List<BlockPos> take = new ArrayList<>(chunk.subList(0, Math.min(9, chunk.size())));
      this.pending.removeAll(take);
      this.pendingIdle = 0;
      this.startGroup(take, creative);
   }

   /** Hands a chunk to the right path. Instant-break blocks go to the batch list, everything else is mined in turn. */
   private boolean startGroup(List<BlockPos> group, boolean creative) {
      if (group.isEmpty()) {
         return false;
      }

      if (this.protection.getValue() != Protection.Off) {
         this.queue.addAll(group);
         return false;
      }

      for (BlockPos pos : group) {
         BlockState state = this.mc.world.getBlockState(pos);
         boolean instant = creative || state.calcBlockBreakingDelta(this.mc.player, this.mc.world, pos) >= 1.0F;
         if (instant) {
            this.rapid.add(pos);
         } else {
            this.queue.add(pos);
         }
      }

      return true;
   }

   /** Sends up to "Blocks Per Tick" instant breaks; survival mining of the queue waits until the batch list is empty. */
   private boolean drainRapid(Scan scan) {
      this.rapid.removeIf(pos -> !this.eligible(pos, scan));
      int budget = this.perTick.getInt();
      boolean acted = false;
      while (budget-- > 0 && !this.rapid.isEmpty()) {
         BlockPos pos = this.rapid.remove(0);
         this.mc.interactionManager.attackBlock(pos, this.faceToward(pos));
         acted = true;
      }

      return acted;
   }

   /** Creative: every block dies on the first attack packet; "Blocks Per Tick" limits how many go out per tick. */
   private boolean tickCreative(Scan scan) {
      List<BlockPos> targets = this.collect(scan);
      if (targets.isEmpty()) {
         this.stopMining();
         return false;
      }

      Set<BlockPos> done = new HashSet<>();
      int budget = this.perTick.getInt();
      int count = 0;
      for (BlockPos center : targets) {
         if (count >= budget) {
            break;
         }

         if (done.contains(center)) {
            continue;
         }

         if (count == 0 && this.rotate.get()) {
            this.lookAt(center);
         }

         for (BlockPos pos : this.group(center, scan)) {
            if (count >= budget) {
               break;
            }

            if (done.add(pos)) {
               this.mc.interactionManager.attackBlock(pos, this.faceToward(pos));
               count++;
            }
         }

         this.current = center;
      }

      return count > 0;
   }

   /**
    * Survival: only one block can be mined at a time on a vanilla-validated server, so a group is mined as a queue
    * and the target stays the same until it is gone (no progress resets). Blocks that break instantly with the
    * current tool are taken in the same tick, which is the only extra speed the server accepts without ghost blocks.
    */
   private boolean tickSurvival(Scan scan, boolean autoFill) {
      this.queue.removeIf(pos -> !this.eligible(pos, scan));
      boolean acted = false;

      if (this.queue.isEmpty()) {
         if (!autoFill) {
            this.current = null;
            return false;
         }

         List<BlockPos> targets = this.collect(scan);
         if (targets.isEmpty()) {
            this.stopMining();
            return false;
         }

         BlockPos center = targets.get(0);
         if (this.rotate.get()) {
            this.lookAt(center);
         }

         for (BlockPos pos : this.group(center, scan)) {
            BlockState state = this.mc.world.getBlockState(pos);
            if (this.area3x3.get() && state.calcBlockBreakingDelta(this.mc.player, this.mc.world, pos) >= 1.0F) {
               this.mc.interactionManager.attackBlock(pos, this.faceToward(pos));
               acted = true;
            } else {
               this.queue.add(pos);
            }
         }
      }

      if (!this.queue.isEmpty()) {
         BlockPos head = this.queue.get(0);
         if (this.rotate.get()) {
            this.lookAt(head);
         }

         this.mc.interactionManager.updateBlockBreakingProgress(head, this.faceToward(head));
         this.current = head;
         acted = true;
      }

      return acted;
   }

   private void stopMining() {
      if (this.current != null) {
         this.mc.interactionManager.cancelBlockBreaking();
         this.current = null;
      }

      this.queue.clear();
      this.rapid.clear();
      this.pending.clear();
      this.delay = 0;
      this.aimTicks = 0;
   }

   /**
    * Protection modes: one block at a time, only blocks with a visible face, the rotation is turned there first
    * (GCD-snapped, speed limited) and the break starts only on a tick where the current look ray already hits the
    * block, so the server has seen the rotation before the dig packet. The swing follows every break tick.
    */
   private boolean tickProtected(Scan scan, boolean autoFill) {
      if (this.delay > 0) {
         this.delay--;
         return false;
      }

      BlockPos prevHead = this.queue.isEmpty() ? null : this.queue.get(0);
      this.queue.removeIf(pos -> !this.eligible(pos, scan));
      if (prevHead != null && (this.queue.isEmpty() || !this.queue.get(0).equals(prevHead))) {
         this.aimTicks = 0;
         this.delay = this.rollDelay();
         if (this.delay > 0) {
            this.delay--;
            return false;
         }
      }

      if (this.queue.isEmpty() && !autoFill) {
         this.current = null;
         this.aimTicks = 0;
         return false;
      }

      if (this.queue.isEmpty()) {
         BlockPos center = null;
         for (BlockPos target : this.collect(scan)) {
            if (this.aim(target, scan) != null) {
               center = target;
               break;
            }
         }

         if (center == null) {
            this.stopMining();
            return false;
         }

         this.queue.addAll(this.group(center, scan));
      }

      BlockPos head = this.queue.get(0);
      Aim aim = this.aim(head, scan);
      if (aim == null || ++this.aimTicks > 40) {
         this.queue.remove(0);
         this.aimTicks = 0;
         return false;
      }

      if (!aim.onTarget()) {
         this.rotateToward(aim.point());
         return false;
      }

      if (RotationManager.active()) {
         RotationManager.set(this.mc.player.getYaw(), this.mc.player.getPitch(), true, true);
      }

      if (this.mc.interactionManager.getCurrentGameMode().isCreative()) {
         this.mc.interactionManager.attackBlock(head, aim.side());
         this.queue.remove(0);
         this.aimTicks = 0;
         this.delay = this.rollDelay();
      } else {
         this.mc.interactionManager.updateBlockBreakingProgress(head, aim.side());
         this.current = head;
      }

      return true;
   }

   private int rollDelay() {
      Protection mode = this.protection.getValue();
      int jitter = mode.jitter > 0 ? ThreadLocalRandom.current().nextInt(mode.jitter + 1) : 0;
      return mode.minDelay + this.extraDelay.getInt() + jitter;
   }

   private BlockHitResult cast(Vec3d from, Vec3d to) {
      BlockHitResult hit = this.mc.world.raycast(
         new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, this.mc.player)
      );
      return hit.getType() == HitResult.Type.BLOCK ? hit : null;
   }

   /**
    * Finds a point on a visible face of the block. If the current look ray already hits the block that wins
    * (onTarget); otherwise the sample point that needs the smallest turn is returned. Null = not visible in reach.
    */
   private Aim aim(BlockPos pos, Scan scan) {
      Vec3d eye = scan.eye();
      Vec3d look = RotationUtils.lookVec(this.mc.player.getYaw(), this.mc.player.getPitch());
      BlockHitResult now = this.cast(eye, eye.add(look.multiply(scan.reach() + 0.5)));
      if (now != null && now.getBlockPos().equals(pos) && now.getPos().distanceTo(eye) <= scan.reach()) {
         return new Aim(now.getPos(), now.getSide(), true);
      }

      Vec3d center = Vec3d.ofCenter(pos);
      double[][] samples = {{0.0, 0.0}, {0.25, 0.25}, {-0.25, 0.25}, {0.25, -0.25}, {-0.25, -0.25}};
      Aim best = null;
      double bestAngle = Double.MAX_VALUE;
      for (Direction face : Direction.values()) {
         Vec3d normal = new Vec3d(face.getOffsetX(), face.getOffsetY(), face.getOffsetZ());
         if (normal.dotProduct(eye.subtract(center)) <= 0.0) {
            continue;
         }

         Vec3d u;
         Vec3d v;
         switch (face.getAxis()) {
            case X -> {
               u = new Vec3d(0.0, 1.0, 0.0);
               v = new Vec3d(0.0, 0.0, 1.0);
            }
            case Y -> {
               u = new Vec3d(1.0, 0.0, 0.0);
               v = new Vec3d(0.0, 0.0, 1.0);
            }
            default -> {
               u = new Vec3d(1.0, 0.0, 0.0);
               v = new Vec3d(0.0, 1.0, 0.0);
            }
         }

         Vec3d faceCenter = center.add(normal.multiply(0.5));
         for (double[] s : samples) {
            Vec3d point = faceCenter.add(u.multiply(s[0])).add(v.multiply(s[1]));
            Vec3d dir = point.subtract(eye).normalize();
            BlockHitResult hit = this.cast(eye, point.add(dir.multiply(0.1)));
            if (hit == null || !hit.getBlockPos().equals(pos) || hit.getPos().distanceTo(eye) > scan.reach()) {
               continue;
            }

            Rotation want = RotationUtils.direction(this.mc.player, hit.getPos());
            double dYaw = MathHelper.wrapDegrees(want.yaw() - this.mc.player.getYaw());
            double dPitch = want.pitch() - this.mc.player.getPitch();
            double angle = Math.hypot(dYaw, dPitch);
            if (angle < bestAngle) {
               bestAngle = angle;
               best = new Aim(hit.getPos(), hit.getSide(), false);
            }
         }
      }

      return best;
   }

   private void rotateToward(Vec3d point) {
      Rotation want = RotationUtils.direction(this.mc.player, point);
      float curYaw = this.mc.player.getYaw();
      float curPitch = this.mc.player.getPitch();
      float dYaw = MathHelper.wrapDegrees((float)want.yaw() - curYaw);
      float dPitch = (float)want.pitch() - curPitch;
      float dist = (float)Math.hypot(dYaw, dPitch);
      float max = this.aimSpeed.getFloat();
      if (dist > max) {
         float k = max / dist;
         dYaw *= k;
         dPitch *= k;
      }

      float sens = this.mc.options.getMouseSensitivity().getValue().floatValue();
      float f = sens * 0.6F + 0.2F;
      float gcd = Math.max(0.0001F, f * f * f * 8.0F * 0.15F);
      RotationManager.set(curYaw + this.snap(dYaw, gcd), curPitch + this.snap(dPitch, gcd), true, true);
   }

   private float snap(float delta, float gcd) {
      int steps = Math.round(delta / gcd);
      if (steps == 0 && Math.abs(delta) > gcd * 0.1F) {
         steps = delta > 0.0F ? 1 : -1;
      }

      return steps * gcd;
   }

   private Scan scan() {
      boolean onBreak = this.trigger.getValue() == Trigger.OnBreak;
      double reach = this.mc.player.getBlockInteractionRange();
      if (!onBreak) {
         reach = Math.min(reach, this.radius.getValue());
      }

      Protection mode = this.protection.getValue();
      if (mode != Protection.Off) {
         reach = Math.min(reach, mode.reach);
      }

      int low = onBreak ? -100000 : (int)Math.min(this.minHeight.getValue(), this.maxHeight.getValue());
      int high = onBreak ? 100000 : (int)Math.max(this.minHeight.getValue(), this.maxHeight.getValue());
      Set<String> whitelist = this.onlyAllowed.get() ? this.parseAllowed() : null;
      int floorY = Integer.MIN_VALUE;
      if (this.protectFloor.get()) {
         floorY = this.mc.player.isOnGround()
            ? (int)Math.floor(this.mc.player.getY() - 1.0E-4)
            : this.mc.player.getBlockPos().getY() - 1;
      }

      return new Scan(this.mc.player.getEyePos(), reach, this.mc.player.getBlockPos().getY(), low, high, whitelist, floorY);
   }

   /** Single rule for "may the Nuker touch this block right now"; used for targets, 3x3 neighbours and queue pruning. */
   private boolean eligible(BlockPos pos, Scan scan) {
      return this.eligible(pos, scan, true);
   }

   private boolean eligible(BlockPos pos, Scan scan, boolean checkReach) {
      if (pos.getY() <= scan.floorY()) {
         return false;
      }

      if (this.mc.world.isOutOfHeightLimit(pos) || !this.mc.world.getWorldBorder().contains(pos)) {
         return false;
      }

      int dy = pos.getY() - scan.feetY();
      if (dy < scan.low() || dy > scan.high()) {
         return false;
      }

      if (checkReach && Vec3d.ofCenter(pos).distanceTo(scan.eye()) > scan.reach()) {
         return false;
      }

      BlockState state = this.mc.world.getBlockState(pos);
      if (state.isAir() || !state.getFluidState().isEmpty()) {
         return false;
      }

      if (state.getHardness(this.mc.world, pos) < 0.0F) {
         return false;
      }

      if (scan.whitelist() != null) {
         String id = Registries.BLOCK.getId(state.getBlock()).getPath();
         return scan.whitelist().contains(id);
      }

      return true;
   }

   /** The block itself first, then (3x3 Mode) its eligible neighbours in the plane facing the player, nearest first. */
   private List<BlockPos> group(BlockPos center, Scan scan) {
      List<BlockPos> out = new ArrayList<>(9);
      out.add(center);
      if (this.area3x3.get()) {
         out.addAll(this.neighbours(center, this.faceToward(center).getAxis(), scan));
      }

      return out;
   }

   /** The up to 8 blocks around {@code center} in the plane perpendicular to {@code axis}, nearest first. */
   private List<BlockPos> neighbours(BlockPos center, Direction.Axis axis, Scan scan) {
      List<BlockPos> out = new ArrayList<>(8);
      for (int a = -1; a <= 1; a++) {
         for (int b = -1; b <= 1; b++) {
            if (a == 0 && b == 0) {
               continue;
            }

            BlockPos pos = switch (axis) {
               case X -> center.add(0, a, b);
               case Y -> center.add(a, 0, b);
               case Z -> center.add(a, b, 0);
            };
            if (this.eligible(pos, scan) && (this.protection.getValue() == Protection.Off || this.aim(pos, scan) != null)) {
               out.add(pos);
            }
         }
      }

      out.sort(Comparator.comparingDouble(p -> Vec3d.ofCenter(p).squaredDistanceTo(scan.eye())));
      return out;
   }

   private List<BlockPos> collect(Scan scan) {
      List<BlockPos> out = new ArrayList<>();
      int r = (int)Math.ceil(scan.reach());
      BlockPos feet = this.mc.player.getBlockPos();
      for (int dx = -r; dx <= r; dx++) {
         for (int dy = scan.low(); dy <= scan.high(); dy++) {
            for (int dz = -r; dz <= r; dz++) {
               BlockPos pos = feet.add(dx, dy, dz);
               if (this.eligible(pos, scan)) {
                  out.add(pos);
               }
            }
         }
      }

      out.sort(Comparator.comparingDouble(p -> Vec3d.ofCenter(p).squaredDistanceTo(scan.eye())));
      return out;
   }

   private Set<String> parseAllowed() {
      Set<String> ids = new HashSet<>();
      for (String part : this.allowed.getValue().split(",")) {
         String id = part.trim().toLowerCase(Locale.ROOT);
         if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
         }

         if (!id.isEmpty()) {
            ids.add(id);
         }
      }

      return ids;
   }

   private Direction faceToward(BlockPos pos) {
      Vec3d eye = this.mc.player.getEyePos();
      Vec3d center = Vec3d.ofCenter(pos);
      return Direction.getFacing((float)(eye.x - center.x), (float)(eye.y - center.y), (float)(eye.z - center.z));
   }

   private void lookAt(BlockPos pos) {
      Rotation wanted = RotationUtils.direction(this.mc.player, Vec3d.ofCenter(pos));
      this.mc.player.setYaw((float)wanted.yaw());
      this.mc.player.setPitch(MathHelper.clamp((float)wanted.pitch(), -90.0F, 90.0F));
   }
}
