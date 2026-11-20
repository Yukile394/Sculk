package dev.sculk.module.modules.combat.spooky;

import net.minecraft.entity.LivingEntity;

/**
 * Nursultan class11087: shared state of the SpookyTime aim system (original method names kept).
 * AttackAura refreshes sinceHit / interval / critOff / throughWalls before every run.
 */
public class C11087 {
   public final C11063 y_0 = new C11063();
   public final C09124 y_1 = new C09124();
   public float range;
   public boolean flag;
   public int hits;
   public long y_4 = System.currentTimeMillis();
   /** Ticks since the last attack (class11799.N). */
   public int sinceHit = 100;
   /** Current hit interval in ticks. */
   public int interval = 10;
   /** Critical hit is switched off (AttackAura B_3). */
   public boolean critOff;
   public boolean throughWalls;

   public boolean L() {
      return this.throughWalls;
   }

   public int i() {
      return 800;
   }

   public int u() {
      return this.hits;
   }

   public C09124 y() {
      return this.y_1;
   }

   /** Rotation towards the best visible candidate point of the target, or the player's rotation. */
   public Rot y(LivingEntity target) {
      net.minecraft.util.math.Vec3d p = C11069.N(target, Rot.current(), this.range);
      return p != null ? C09170.N(p) : Rot.current();
   }

   public void N(float f2, boolean bl, boolean bl2) {
      this.range = f2;
      this.flag = bl;
   }

   /** The ray along rot reaches the target first. */
   public boolean N(LivingEntity target, Rot rot) {
      return C11069.y(target, rot, this.range);
   }

   public float N(LivingEntity target) {
      return this.range;
   }

   /** Attack window: the next hit is at most one tick away. */
   public boolean N() {
      return this.sinceHit >= Math.max(1, this.interval - 1);
   }

   /** Called after every hit. */
   public void N(float f2) {
      this.range = f2;
      this.y_1.N();
      this.hits++;
   }
}
