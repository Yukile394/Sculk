package dev.sculk.util;

import net.minecraft.util.math.Vec3d;

/**
 * Pure elytra / interception math for ElytraBooster. No game state is read here, so every function can be checked
 * without the game.
 *
 * Coordinates: Minecraft world space, Y up, right-handed. Velocities are blocks per tick. Angles are not used.
 */
public final class ElytraMath {
   /** Below this speed (blocks per tick) a target has no usable heading. */
   public static final double HEADING_MIN_SPEED = 0.15;

   private ElytraMath() {
   }

   public enum Phase {
      Idle,
      Approach,
      Strike,
      Lead
   }

   /**
    * @param distance    centre-to-centre distance me -> target
    * @param ahead       how far I am in front of the target along its heading (negative = behind it)
    * @param lateral     distance of my centre from the target's heading line
    * @param closing     rate at which the distance shrinks (positive = getting closer)
    * @param hasHeading  the target moves fast enough for ahead / lateral to mean anything
    * @param heading     unit vector of the target's movement (zero when !hasHeading)
    * @param toTarget    unit vector me -> target (zero when the centres coincide)
    */
   public record Situation(double distance, double ahead, double lateral, double closing, double mySpeed, double targetSpeed,
                           boolean hasHeading, Vec3d heading, Vec3d toTarget) {
   }

   public static Situation analyse(Vec3d me, Vec3d myVelocity, Vec3d target, Vec3d targetVelocity) {
      Vec3d rel = me.subtract(target);
      double distance = rel.length();
      double targetSpeed = targetVelocity.length();
      boolean hasHeading = targetSpeed >= HEADING_MIN_SPEED;
      Vec3d heading = hasHeading ? targetVelocity.multiply(1.0 / targetSpeed) : Vec3d.ZERO;
      double ahead = hasHeading ? rel.dotProduct(heading) : 0.0;
      double lateral = hasHeading ? Math.sqrt(Math.max(0.0, distance * distance - ahead * ahead)) : distance;
      Vec3d toTarget = distance > 1.0E-6 ? rel.multiply(-1.0 / distance) : Vec3d.ZERO;
      double closing = distance > 1.0E-6 ? -rel.dotProduct(myVelocity.subtract(targetVelocity)) / distance : 0.0;
      return new Situation(distance, ahead, lateral, closing, myVelocity.length(), targetSpeed, hasHeading, heading, toTarget);
   }

   /**
    * Idle     : too far away, ElytraBooster does nothing and AutoFirework runs by itself.
    * Lead     : I am at least {@code lead} blocks in front of the target and close to its line -> hold speed, do not overshoot.
    * Strike   : within {@code strike} blocks but not in front yet -> controlled closing speed so Aura gets swings in reach.
    * Approach : between strike and engage range -> close in as fast as the rockets allow.
    */
   public static Phase phase(Situation s, double engage, double strike, double lead) {
      if (s.distance() > engage) {
         return Phase.Idle;
      }

      if (s.hasHeading() && s.ahead() >= lead && s.lateral() <= strike) {
         return Phase.Lead;
      }

      return s.distance() <= strike ? Phase.Strike : Phase.Approach;
   }

   /**
    * One vanilla firework-rocket application on a gliding shooter (FireworkRocketEntity.tick):
    * v += look * 0.1 + (look * 1.5 - v) * 0.5. Recalled from the vanilla code, not copied from your Yarn sources:
    * compare with FireworkRocketEntity.tick in 1.21.1 once.
    */
   public static Vec3d rocketStep(Vec3d velocity, Vec3d look) {
      return velocity.add(
         look.x * 0.1 + (look.x * 1.5 - velocity.x) * 0.5,
         look.y * 0.1 + (look.y * 1.5 - velocity.y) * 0.5,
         look.z * 0.1 + (look.z * 1.5 - velocity.z) * 0.5
      );
   }

   /** Vanilla rocket life: 10 * (1 + flight) + nextInt(6) + nextInt(7) ticks. Same caveat as rocketStep. */
   public static int rocketMinLifeTicks(int flightDuration) {
      return 10 * (1 + Math.max(0, flightDuration));
   }

   public static int rocketMaxLifeTicks(int flightDuration) {
      return rocketMinLifeTicks(flightDuration) + 5 + 6;
   }

   /** Limits {@code after} to {@code cap} blocks per tick, but never below the speed {@code before} already had. */
   public static Vec3d capSpeed(Vec3d before, Vec3d after, double cap) {
      double limit = Math.max(cap, before.length());
      double length = after.length();
      return length > limit && length > 1.0E-9 ? after.multiply(limit / length) : after;
   }

   public static boolean finite(Vec3d v) {
      return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
   }
}
