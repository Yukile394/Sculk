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

      if (active && MinecraftClient.getInstance().currentScreen != null) {
         release();
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
         player.bodyYaw = player.getYaw();
         player.prevBodyYaw = player.getYaw();
         player.headYaw = player.getYaw();
         player.prevHeadYaw = player.getYaw();
      }

      active = false;
   }

   // ---------------------------------------------------------------- model smoothing (3rd person / other angles)

   private static boolean smoothing;
   private static float smoothYaw;
   private static float smoothPitch;
   private static long smoothAt;
   private static final float[] SAVED = new float[10];

   /**
    * Called before the local player model is rendered. While a silent rotation is active (and until the model has caught up
    * after it ended) the model is drawn with a low-pass filtered rotation instead of the raw per-tick aim, with head, body and
    * the previous-tick values forced equal so the renderer has nothing to interpolate. Returns true when values were swapped.
    */
   public static boolean beginModelRender(ClientPlayerEntity player, float tickDelta) {
      float rawYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevYaw, player.getYaw());
      float rawPitch = MathHelper.lerp(tickDelta, player.prevPitch, player.getPitch());
      long now = System.nanoTime();
      if (!active && !smoothing) {
         return false;
      }

      if (!smoothing) {
         smoothing = true;
         smoothYaw = rawYaw;
         smoothPitch = rawPitch;
         smoothAt = now;
      }

      float dt = Math.min((float)(now - smoothAt) / 1.0E9F, 0.1F);
      smoothAt = now;
      float alpha = 1.0F - (float)Math.exp(-dt * 16.0F);
      smoothYaw += MathHelper.wrapDegrees(rawYaw - smoothYaw) * alpha;
      smoothPitch += (rawPitch - smoothPitch) * alpha;
      if (!active && Math.abs(MathHelper.wrapDegrees(rawYaw - smoothYaw)) < 0.5F && Math.abs(rawPitch - smoothPitch) < 0.5F) {
         smoothing = false;
         return false;
      }

      SAVED[0] = player.getYaw();
      SAVED[1] = player.prevYaw;
      SAVED[2] = player.getPitch();
      SAVED[3] = player.prevPitch;
      SAVED[4] = player.bodyYaw;
      SAVED[5] = player.prevBodyYaw;
      SAVED[6] = player.headYaw;
      SAVED[7] = player.prevHeadYaw;
      player.setYaw(smoothYaw);
      player.prevYaw = smoothYaw;
      player.setPitch(smoothPitch);
      player.prevPitch = smoothPitch;
      player.bodyYaw = smoothYaw;
      player.prevBodyYaw = smoothYaw;
      player.headYaw = smoothYaw;
      player.prevHeadYaw = smoothYaw;
      return true;
   }

   private static final float[] HAND_SAVED = new float[8];

   /** First person hands: draw them with the camera rotation (no sway caused by the aim rotation). */
   public static void beginHandRender(ClientPlayerEntity player) {
      HAND_SAVED[0] = player.getYaw();
      HAND_SAVED[1] = player.prevYaw;
      HAND_SAVED[2] = player.getPitch();
      HAND_SAVED[3] = player.prevPitch;
      HAND_SAVED[4] = player.renderYaw;
      HAND_SAVED[5] = player.lastRenderYaw;
      HAND_SAVED[6] = player.renderPitch;
      HAND_SAVED[7] = player.lastRenderPitch;
      float yaw = camYaw;
      float pitch = MathHelper.clamp(camPitch, -90.0F, 90.0F);
      player.setYaw(yaw);
      player.prevYaw = yaw;
      player.setPitch(pitch);
      player.prevPitch = pitch;
      player.renderYaw = yaw;
      player.lastRenderYaw = yaw;
      player.renderPitch = pitch;
      player.lastRenderPitch = pitch;
   }

   public static void endHandRender(ClientPlayerEntity player) {
      player.setYaw(HAND_SAVED[0]);
      player.prevYaw = HAND_SAVED[1];
      player.setPitch(HAND_SAVED[2]);
      player.prevPitch = HAND_SAVED[3];
      player.renderYaw = HAND_SAVED[4];
      player.lastRenderYaw = HAND_SAVED[5];
      player.renderPitch = HAND_SAVED[6];
      player.lastRenderPitch = HAND_SAVED[7];
   }

   public static void endModelRender(ClientPlayerEntity player) {
      player.setYaw(SAVED[0]);
      player.prevYaw = SAVED[1];
      player.setPitch(SAVED[2]);
      player.prevPitch = SAVED[3];
      player.bodyYaw = SAVED[4];
      player.prevBodyYaw = SAVED[5];
      player.headYaw = SAVED[6];
      player.prevHeadYaw = SAVED[7];
   }

   public static void reset() {
      smoothing = false;
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
