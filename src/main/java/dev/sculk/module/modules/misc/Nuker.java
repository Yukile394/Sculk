package dev.sculk.module.modules.misc;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.module.setting.StringSetting;
import dev.sculk.util.Rotation;
import dev.sculk.util.RotationUtils;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

/** Nuker: breaks one block per tick around you, optionally only from a whitelist of block ids. */
public final class Nuker extends Module {
   private final NumberSetting radius = new NumberSetting("Radius", 4.5, 1.0, 6.0, 0.5);
   private final NumberSetting minHeight = new NumberSetting("Min Height", 0.0, -1.0, 4.0, 1.0)
      .describe("Lowest layer relative to your feet");
   private final NumberSetting maxHeight = new NumberSetting("Max Height", 2.0, -1.0, 4.0, 1.0)
      .describe("Highest layer relative to your feet");
   private final BooleanSetting onlyAllowed = new BooleanSetting("Only Allowed Blocks", false)
      .describe("Break only the block ids listed below");
   private final StringSetting allowed = new StringSetting("Allowed Blocks", "stone,dirt,cobblestone", 200)
      .describe("Comma separated block ids, e.g. stone,oak_log,minecraft:sand")
      .visibleWhen(this.onlyAllowed::get);
   private final BooleanSetting swing = new BooleanSetting("Swing", true);
   private BlockPos current;
   private String cachedList = "";
   private final Set<Identifier> cachedIds = new HashSet<>();

   public Nuker() {
      super("Nuker", "Breaks blocks around you", Category.MISC);
      this.addSettings(new Setting[]{this.radius, this.minHeight, this.maxHeight, this.onlyAllowed, this.allowed, this.swing});
   }

   @Override
   public void onEnable() {
      this.current = null;
   }

   @Override
   public void onDisable() {
      this.current = null;
   }

   @Override
   public void onTick() {
      var mc = this.getMC();
      if (mc.player == null || mc.world == null || mc.interactionManager == null
         || mc.currentScreen != null || mc.player.isUsingItem()) {
         return;
      }

      if (this.current == null || this.invalid(this.current)) {
         this.current = this.pickTarget();
         if (this.current == null) {
            return;
         }
      }

      Vec3d eye = mc.player.getEyePos();
      Optional<Vec3d> point = this.closestPoint(this.current, eye);
      if (point.isEmpty()) {
         this.current = null;
         return;
      }

      Rotation wanted = RotationUtils.direction(mc.player, point.get());
      float yaw = Math.abs(wanted.pitch()) >= 90.0 ? mc.player.getYaw() : (float)wanted.yaw();
      mc.player.setYaw(yaw);
      mc.player.setPitch((float)Math.max(-90.0, Math.min(90.0, wanted.pitch())));

      double reach = mc.player.getBlockInteractionRange();
      Vec3d end = eye.add(RotationUtils.lookVec(mc.player.getYaw(), mc.player.getPitch()).multiply(reach));
      BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
      if (hit.getType() == HitResult.Type.MISS || this.invalid(hit.getBlockPos())) {
         return;
      }

      if (mc.interactionManager.updateBlockBreakingProgress(hit.getBlockPos(), hit.getSide()) && this.swing.get()) {
         mc.player.swingHand(Hand.MAIN_HAND);
      }
   }

   private BlockPos pickTarget() {
      var mc = this.getMC();
      BlockPos feet = BlockPos.ofFloored(mc.player.getX(), Math.ceil(mc.player.getY()), mc.player.getZ());
      BlockPos floor = BlockPos.ofFloored(mc.player.getPos());
      BlockPos below = floor.down();
      int r = (int)Math.ceil(this.radius.getValue());
      int lo = Math.min(this.minHeight.getInt(), this.maxHeight.getInt());
      int hi = Math.max(this.minHeight.getInt(), this.maxHeight.getInt());

      BlockPos best = null;
      Comparator<BlockPos> order = Comparator.<BlockPos>comparingInt(p -> p.getY() > floor.getY() ? 0 : 1)
         .thenComparingDouble(p -> p.getSquaredDistance(below));
      for (BlockPos pos : BlockPos.iterate(feet.add(-r, lo, -r), feet.add(r, hi, r))) {
         if (!this.invalid(pos) && (best == null || order.compare(pos, best) < 0)) {
            best = pos.toImmutable();
         }
      }
      return best;
   }

   private boolean invalid(BlockPos pos) {
      var mc = this.getMC();
      if (pos == null) {
         return true;
      }

      double dx = pos.getX() + 0.5 - mc.player.getX();
      double dz = pos.getZ() + 0.5 - mc.player.getZ();
      if (dx * dx + dz * dz > this.radius.getValue() * this.radius.getValue()) {
         return true;
      }

      BlockState state = mc.world.getBlockState(pos);
      if (state.isAir() || state.getHardness(mc.world, pos) == -1.0F) {
         return true;
      }
      if (mc.player.isBlockBreakingRestricted(mc.world, pos, mc.interactionManager.getCurrentGameMode())) {
         return true;
      }
      if (this.onlyAllowed.get() && !this.allowedIds().contains(Registries.BLOCK.getId(state.getBlock()))) {
         return true;
      }

      Optional<Vec3d> point = this.closestPoint(pos, mc.player.getEyePos());
      return point.isEmpty() || point.get().distanceTo(mc.player.getEyePos()) > mc.player.getBlockInteractionRange();
   }

   private Optional<Vec3d> closestPoint(BlockPos pos, Vec3d eye) {
      VoxelShape shape = this.getMC().world.getBlockState(pos).getOutlineShape(this.getMC().world, pos);
      if (shape.isEmpty()) {
         return Optional.empty();
      }
      return shape.offset(pos.getX(), pos.getY(), pos.getZ()).getClosestPointTo(eye);
   }

   private Set<Identifier> allowedIds() {
      String raw = this.allowed.getValue();
      if (!raw.equals(this.cachedList)) {
         this.cachedList = raw;
         this.cachedIds.clear();
         for (String part : raw.split(",")) {
            Identifier id = Identifier.tryParse(part.trim().toLowerCase());
            if (id != null) {
               this.cachedIds.add(id);
            }
         }
      }
      return this.cachedIds;
   }
}
