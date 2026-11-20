package dev.sculk.module.modules.combat.spooky;

import net.minecraft.util.math.Vec3d;

/** Nursultan class11104 (record). */
public final class C11104 {
   public Vec3d point;
   public Rot rotation;
   public boolean released;
   public boolean flicking;

   public C11104(Vec3d point, Rot rotation, boolean released, boolean flicking) {
      this.point = point;
      this.rotation = rotation;
      this.released = released;
      this.flicking = flicking;
   }

   public Rot L() {
      return this.rotation;
   }

   public Vec3d u() {
      return this.point;
   }

   public boolean y() {
      return this.flicking;
   }

   public static C11104 N(Vec3d point, Rot rotation) {
      return new C11104(point, rotation, false, false);
   }

   public boolean N() {
      return this.released;
   }
}
