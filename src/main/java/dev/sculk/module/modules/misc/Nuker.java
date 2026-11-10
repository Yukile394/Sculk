package dev.sculk.module.modules.misc;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.module.setting.StringSetting;
import dev.sculk.util.Rotation;
import dev.sculk.util.RotationUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Nuker: breaks the blocks around you inside a radius and height window. Ported from Nursultan's Nuker. */
public final class Nuker extends Module {
   private final NumberSetting radius = new NumberSetting("Radius", 4.0, 1.0, 6.0, 0.5).describe("Horizontal and vertical reach");
   private final NumberSetting minHeight = new NumberSetting("Min Height", 0.0, -4.0, 4.0, 1.0).describe("Lowest layer relative to your feet");
   private final NumberSetting maxHeight = new NumberSetting("Max Height", 2.0, -4.0, 6.0, 1.0).describe("Highest layer relative to your feet");
   private final NumberSetting perTick = new NumberSetting("Blocks Per Tick", 1.0, 1.0, 8.0, 1.0).describe("Only used in creative mode");
   private final BooleanSetting onlyAllowed = new BooleanSetting("Only Allowed Blocks", false).describe("Break only the blocks listed below");
   private final StringSetting allowed = new StringSetting("Allowed Blocks", "stone,dirt,cobblestone", 120)
      .describe("Comma separated block ids, e.g. diamond_ore,iron_ore").visibleWhen(() -> this.onlyAllowed.get());
   private final BooleanSetting rotate = new BooleanSetting("Rotate", false).describe("Look at the block you are breaking");
   private final BooleanSetting swing = new BooleanSetting("Swing", true);
   private BlockPos current;

   public Nuker() {
      super("Nuker", "Breaks blocks around you", Category.MISC);
      this.addSettings(new Setting[]{this.radius, this.minHeight, this.maxHeight, this.perTick, this.onlyAllowed, this.allowed, this.rotate, this.swing});
   }

   @Override
   public void onDisable() {
      this.current = null;
      if (this.mc != null && this.mc.interactionManager != null) {
         this.mc.interactionManager.cancelBlockBreaking();
      }
   }

   @Override
   public void onTick() {
      if (this.mc == null || this.mc.player == null || this.mc.world == null || this.mc.interactionManager == null) {
         return;
      }

      if (this.mc.currentScreen != null || this.mc.player.isSpectator()) {
         return;
      }

      List<BlockPos> targets = this.collect();
      if (targets.isEmpty()) {
         if (this.current != null) {
            this.mc.interactionManager.cancelBlockBreaking();
            this.current = null;
         }

         return;
      }

      boolean creative = this.mc.interactionManager.getCurrentGameMode().isCreative();
      int count = creative ? this.perTick.getInt() : 1;
      for (int i = 0; i < Math.min(count, targets.size()); i++) {
         BlockPos pos = targets.get(i);
         Direction side = this.faceToward(pos);
         if (i == 0 && this.rotate.get()) {
            this.lookAt(pos);
         }

         if (creative) {
            this.mc.interactionManager.attackBlock(pos, side);
         } else {
            this.mc.interactionManager.updateBlockBreakingProgress(pos, side);
         }

         if (i == 0) {
            this.current = pos;
         }
      }

      if (this.swing.get()) {
         this.mc.player.swingHand(Hand.MAIN_HAND);
      }
   }

   private List<BlockPos> collect() {
      List<BlockPos> out = new ArrayList<>();
      double reach = Math.min(this.radius.getValue(), this.mc.player.getBlockInteractionRange());
      int r = (int)Math.ceil(reach);
      Vec3d eye = this.mc.player.getEyePos();
      BlockPos feet = this.mc.player.getBlockPos();
      int low = (int)Math.min(this.minHeight.getValue(), this.maxHeight.getValue());
      int high = (int)Math.max(this.minHeight.getValue(), this.maxHeight.getValue());
      Set<String> whitelist = this.onlyAllowed.get() ? this.parseAllowed() : null;

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = low; dy <= high; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               BlockPos pos = feet.add(dx, dy, dz);
               Vec3d center = Vec3d.ofCenter(pos);
               if (center.distanceTo(eye) > reach) {
                  continue;
               }

               BlockState state = this.mc.world.getBlockState(pos);
               if (state.isAir() || !state.getFluidState().isEmpty()) {
                  continue;
               }

               if (state.getHardness(this.mc.world, pos) < 0.0F) {
                  continue;
               }

               if (whitelist != null) {
                  String id = Registries.BLOCK.getId(state.getBlock()).getPath();
                  if (!whitelist.contains(id)) {
                     continue;
                  }
               }

               out.add(pos);
            }
         }
      }

      out.sort(Comparator.comparingDouble(p -> Vec3d.ofCenter(p).squaredDistanceTo(eye)));
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
