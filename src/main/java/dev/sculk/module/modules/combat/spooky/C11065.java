package dev.sculk.module.modules.combat.spooky;

import net.minecraft.util.math.Vec3d;

/** Nursultan class11065 (record). */
public final class C11065 {
   public Vec3d point;
   public boolean detached;
   public boolean pullback;

   public C11065(Vec3d point, boolean detached) {
      this(point, detached, false);
   }

   public C11065(Vec3d point, boolean detached, boolean pullback) {
      this.point = point;
      this.detached = detached;
      this.pullback = pullback;
   }

   public Vec3d L() {
      return this.point;
   }

   public boolean y() {
      return this.detached;
   }

   public boolean N() {
      return this.pullback;
   }
}
