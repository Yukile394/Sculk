package dev.sculk.util;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Silent rotation, ported from Nursultan class11534.
 * While active the player entity carries the aim rotation (so the movement packets send it),
 * the camera and the mouse keep working on a separate camera rotation (see Mixin8 / Mixin9),
 * and movement input is corrected towards the camera yaw (see Mixin10).
 * When nobody asks for a rotation any more the entity is eased back to the camera rotation.
 */
public final class RotationManager {
   private static boolean active;
   private static float camYaw;
   private static float camPitch;
   private static int ticks;
   private static int lastSet = -100;
   private static boolean smooth;
   private static boolean correct;

   private RotationManager() {
   }

   public static boolean active() {
      return active;
   }

   public static float camYaw() {
      return camYaw;
   }

   public static float camPitch() {
      return camPitch;
   }

   /** Movement correction towards the camera yaw is wanted for the current rotation. */
   public static boolean correcting() {
      return active && correct;
   }

   /** Sets the aim rotation for this tick. smooth = ease back to the camera afterwards, correct = fix movement input. */
   public static void set(float yaw, float pitch, boolean smoothBack, boolean moveCorrection) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player == null) {
         return;
      }

      if (!active) {
         camYaw = player.getYaw();
         camPitch = player.getPitch();
         active = true;
      }

      player.setYaw(yaw);
      player.setPitch(MathHelper.clamp(pitch, -90.0F, 90.0F));
      lastSet = ticks;
      smooth = smoothBack;
      correct = moveCorrection;
   }

   /** Mouse movement while a silent rotation is active moves the camera, not the player. */
   public static void mouse(double dx, double dy) {
      camYaw += (float)dx * 0.15F;
      camPitch = MathHelper.clamp(camPitch + (float)dy * 0.15F, -90.0F, 90.0F);
   }

   /** Called once per client tick before the modules run. */
   public static void tick() {
      ticks++;
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player == null) {
         reset();
         return;
      }

      if (!active || ticks - 1 <= lastSet) {
         return;
      }

      if (ticks - 20 > lastSet || !smooth) {
         release();
         return;
      }

      float yaw = player.getYaw();
      float targetYaw = yaw + MathHelper.wrapDegrees(camYaw - yaw);
      if (Math.abs(targetYaw - yaw) < 2.0F && Math.abs(camPitch - player.getPitch()) < 2.0F) {
         release();
         return;
      }

      player.setYaw(MathHelper.lerp(0.5F, yaw, targetYaw));
      player.setPitch(MathHelper.clamp(MathHelper.lerp(0.5F, player.getPitch(), camPitch), -90.0F, 90.0F));
   }

   /** Puts the player entity back on the camera rotation and ends the silent rotation. */
   public static void release() {
      if (!active) {
         return;
      }

      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         player.setYaw(player.getYaw() + MathHelper.wrapDegrees(camYaw - player.getYaw()));
         player.prevYaw = player.getYaw();
         player.setPitch(MathHelper.clamp(camPitch, -90.0F, 90.0F));
         player.prevPitch = player.getPitch();
      }

      active = false;
   }

   public static void reset() {
      active = false;
      lastSet = -100;
   }

   /** Rotates the movement keys so the player moves along {@code desiredYaw} although the entity looks elsewhere. */
   public static void fixMovement(net.minecraft.client.input.Input input, float desiredYaw, boolean slowDown, float slowDownFactor) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player == null || player.isFallFlying()) {
         return;
      }

      float forward = (input.pressingForward ? 1.0F : 0.0F) - (input.pressingBack ? 1.0F : 0.0F);
      float strafe = (input.pressingLeft ? 1.0F : 0.0F) - (input.pressingRight ? 1.0F : 0.0F);
      if (forward == 0.0F && strafe == 0.0F) {
         return;
      }

      float playerYaw = MathHelper.wrapDegrees(player.getYaw());
      double wanted = desiredYaw + MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-strafe, forward)));
      float bestF = 0.0F;
      float bestS = 0.0F;
      double bestDiff = Double.MAX_VALUE;
      for (int f = -1; f <= 1; f++) {
         for (int s = -1; s <= 1; s++) {
            if (f == 0 && s == 0) {
               continue;
            }

            double cand = playerYaw + MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-s, f)));
            double diff = Math.abs(MathHelper.wrapDegrees((float)(wanted - cand)));
            if (diff < bestDiff) {
               bestDiff = diff;
               bestF = f;
               bestS = s;
            }
         }
      }

      input.pressingForward = bestF == 1.0F;
      input.pressingBack = bestF == -1.0F;
      input.pressingLeft = bestS == 1.0F;
      input.pressingRight = bestS == -1.0F;
      input.movementForward = bestF;
      input.movementSideways = bestS;
      if (slowDown) {
         input.movementForward *= slowDownFactor;
         input.movementSideways *= slowDownFactor;
      }
   }

   public static float jitter(float min, float max) {
      return min + (max - min) * ThreadLocalRandom.current().nextFloat();
   }
}
