package dev.sculk.module.modules.combat.spooky;

import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Static helpers of Nursultan class11069 (the SpookyTime mode). */
public final class C11069 {
   private C11069() {
   }

   /** True when a ray from the eye along rot reaches the target first (no block and no other entity in front). */
   public static boolean y(LivingEntity target, Rot rot, float range) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity p = mc.player;
      if (p == null || mc.world == null || target == null || rot == null || range <= 0.0F) {
         return false;
      }

      Vec3d eye = p.getEyePos();
      Vec3d dir = Vec3d.fromPolar(rot.R(), rot.y());
      Vec3d end = eye.add(dir.multiply((double)range));
      Box sweep = new Box(eye, eye).stretch(dir.multiply((double)range)).expand(1.0);
      Entity best = null;
      double bestDist = (double)(range * range);
      for (Entity e : mc.world.getOtherEntities(p, sweep, x -> !x.isSpectator() && x.canHit())) {
         Box box = e == target ? C11064.u(e) : C11064.L(e).expand(Math.max(1.0E-4, (double)e.getTargetingMargin()));
         Vec3d hit = box.contains(eye) ? eye : box.raycast(eye, end).orElse(null);
         if (hit == null) {
            continue;
         }

         double d = eye.squaredDistanceTo(hit);
         if (best != null && d >= bestDist) {
            continue;
         }

         best = e;
         bestDist = d;
      }

      HitResult block = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
      boolean blocked = block != null && block.getType() == HitResult.Type.BLOCK && eye.squaredDistanceTo(block.getPos()) <= bestDist;
      return !blocked && best == target;
   }

   /** Cheapest visible one of nine fixed points of the target hitbox, relative to the reference rotation. */
   public static Vec3d N(LivingEntity target, Rot reference, float range) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity p = mc.player;
      if (p == null || target == null) {
         return null;
      }

      Box box = C11064.L(target);
      double cx = (box.minX + box.maxX) * 0.5;
      double cz = (box.minZ + box.maxZ) * 0.5;
      double mx = Math.max(0.015, (box.maxX - box.minX) * 0.18);
      double mz = Math.max(0.015, (box.maxZ - box.minZ) * 0.18);
      double x0 = box.minX + mx;
      double x1 = box.maxX - mx;
      double z0 = box.minZ + mz;
      double z1 = box.maxZ - mz;
      Vec3d near = C11064.N(target);
      double y28 = MathHelper.lerp(0.28, box.minY, box.maxY);
      double y18 = MathHelper.lerp(0.18, box.minY, box.maxY);
      double y38 = MathHelper.lerp(0.38, box.minY, box.maxY);
      double y46 = MathHelper.lerp(0.46, box.minY, box.maxY);
      Vec3d[] candidates = new Vec3d[]{
         new Vec3d(cx, y28, cz),
         new Vec3d(MathHelper.clamp(near.x, x0, x1), MathHelper.clamp(near.y, y18, y38), MathHelper.clamp(near.z, z0, z1)),
         new Vec3d(x0, y28, cz),
         new Vec3d(x1, y28, cz),
         new Vec3d(cx, y28, z0),
         new Vec3d(cx, y28, z1),
         new Vec3d(cx, y18, cz),
         new Vec3d(cx, y38, cz),
         new Vec3d(cx, y46, cz)
      };
      Vec3d best = candidates[0];
      double bestCost = Double.MAX_VALUE;
      Vec3d eye = p.getEyePos();
      for (Vec3d c : candidates) {
         Rot r = C09170.N(c);
         if (!y(target, r, range)) {
            continue;
         }

         double cost = (double)Math.abs(C09170.N(reference.y(), r.y())) * 0.86
            + (double)Math.abs(reference.R() - r.R()) * 4.2
            + Math.max(0.0, c.y - y38) * 3.8
            + eye.squaredDistanceTo(c) * 0.01;
         if (cost < bestCost) {
            bestCost = cost;
            best = c;
         }
      }

      return best;
   }

   /** Unused helper kept for symmetry with the original (optional hit point on a box). */
   public static Optional<Vec3d> hit(Box box, Vec3d from, Vec3d to) {
      return box.raycast(from, to);
   }
}
