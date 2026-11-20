package dev.sculk.module.modules.combat.spooky;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Nursultan class11064: hitbox helpers (original method names kept). */
public final class C11064 {
   private C11064() {
   }

   /** The raw hitbox. */
   public static Box L(Entity e) {
      return e.getBoundingBox();
   }

   /** The hitbox shrunk by a small margin on every side (0.04 / 0.12 / 0.02 clamps of the original). */
   public static Box u(Entity e) {
      Box b = L(e);
      double dx = Math.min(0.04, Math.max(0.0, (b.maxX - b.minX) * 0.12));
      double dz = Math.min(0.04, Math.max(0.0, (b.maxZ - b.minZ) * 0.12));
      double dy = Math.min(0.02, Math.max(0.0, (b.maxY - b.minY) * 0.04));
      if (b.maxX - b.minX <= dx * 2.0 || b.maxY - b.minY <= dy * 2.0 || b.maxZ - b.minZ <= dz * 2.0) {
         return b;
      }

      return new Box(b.minX + dx, b.minY + dy, b.minZ + dz, b.maxX - dx, b.maxY - dy, b.maxZ - dz);
   }

   /** Center of the hitbox. */
   public static Vec3d y(Entity e) {
      return L(e).getCenter();
   }

   /** Point clamped into the hitbox. */
   public static Vec3d y(Entity e, Vec3d p) {
      Box b = L(e);
      return new Vec3d(MathHelper.clamp(p.x, b.minX, b.maxX), MathHelper.clamp(p.y, b.minY, b.maxY), MathHelper.clamp(p.z, b.minZ, b.maxZ));
   }

   public static Vec3d N(Entity e, Vec3d p) {
      return y(e, p);
   }

   /** The point of the hitbox closest to the player's eye. */
   public static Vec3d N(Entity e) {
      return N(e, MinecraftClient.getInstance().player.getEyePos());
   }
}
