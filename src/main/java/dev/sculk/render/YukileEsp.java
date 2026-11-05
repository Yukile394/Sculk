package dev.sculk.render;

import com.mojang.blaze3d.platform.GlStateManager.DstFactor;
import com.mojang.blaze3d.platform.GlStateManager.SrcFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Yukile target ESPs (ported from ZeltrixWare, same geometry) plus extra styles.
 *
 *  - renderHelix : double-strand DNA helix climbing the target with fading ribbon trails and glow nodes
 *  - renderOrbit : three tilted gyroscope rings with fading comet tails and a pulsing ground halo
 *  - renderHalo  : floating halo above the head with orbiting sparks
 *  - renderPulse : expanding shock rings on the ground with a soft glow disc
 *  - renderCage  : rotating light cage with scanning rings
 *
 * Everything is time driven and interpolated with the render tick delta, so it stays smooth at any FPS.
 */
public final class YukileEsp {
   private static final long START = System.currentTimeMillis();

   private YukileEsp() {
   }

   /** Colour source: maps an index to an ARGB colour (alpha is replaced by the ESP). */
   public interface Palette {
      int color(int count);
   }

   /** Per frame options. */
   public record Params(float alpha, float speed, float scale, boolean walls, Palette palette) {
      public static Params defaults() {
         return new Params(1.0F, 1.0F, 1.0F, true, count -> Theme.accent((float)(count % 360) / 360.0F));
      }
   }

   private static double seconds(Params p) {
      return (System.currentTimeMillis() - START) / 1000.0 * (double)p.speed();
   }

   private static int rgba(int color, float alpha) {
      int a = MathHelper.clamp((int)alpha, 0, 255);
      return a << 24 | color & 0xFFFFFF;
   }

   private static int col(Params p, int count, float alpha) {
      return rgba(p.palette().color(count), alpha * p.alpha());
   }

   // ───────────────────────────── HELIX ─────────────────────────────

   private static final int HELIX_STRANDS = 2;
   private static final int HELIX_SEGMENTS = 96;
   private static final int HELIX_TRAIL = 22;
   private static final float HELIX_TURNS = 2.5F;
   private static final float HELIX_RADIUS_SCALE = 0.85F;

   public static void renderHelix(MatrixStack stack, Entity target, float delta, Params p) {
      if (target == null) {
         return;
      }

      Vec3d pos = interpolatedPos(target, delta);
      float height = target.getHeight();
      float radius = (target.getWidth() * HELIX_RADIUS_SCALE + 0.1F) * p.scale();
      double time = seconds(p);
      float spin = (float)(time * 2.2);
      float breathe = 1.0F + 0.06F * (float)Math.sin(time * 3.0);

      stack.push();
      stack.translate(pos.x, pos.y, pos.z);
      begin(p);
      Matrix4f matrix = stack.peek().getPositionMatrix();

      // ribbon between the two strands (rungs of the helix)
      BufferBuilder rungs = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      for (int i = 0; i < HELIX_SEGMENTS; i += 4) {
         float t = i / (float)(HELIX_SEGMENTS - 1);
         float y = t * height;
         float ang = spin + t * HELIX_TURNS * MathHelper.TAU;
         float rr = radius * breathe * envelope(t);
         float x1 = (float)Math.cos(ang) * rr;
         float z1 = (float)Math.sin(ang) * rr;
         float x2 = (float)Math.cos(ang + Math.PI) * rr;
         float z2 = (float)Math.sin(ang + Math.PI) * rr;
         float a = 110.0F * envelope(t);
         int c1 = col(p, i * 3, a);
         int c2 = col(p, i * 3 + 90, a);
         float th = 0.012F;
         rungs.vertex(matrix, x1, y - th, z1).color(c1);
         rungs.vertex(matrix, x1, y + th, z1).color(c1);
         rungs.vertex(matrix, x2, y + th, z2).color(c2);
         rungs.vertex(matrix, x2, y - th, z2).color(c2);
      }

      draw(rungs);

      // the strands themselves: triangle-strip ribbons that fade toward the tail
      for (int s = 0; s < HELIX_STRANDS; s++) {
         float phase = s * (float)Math.PI;
         BufferBuilder ribbon = Tessellator.getInstance().begin(DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
         for (int i = 0; i < HELIX_SEGMENTS; i++) {
            float t = i / (float)(HELIX_SEGMENTS - 1);
            float y = t * height;
            float ang = spin + phase + t * HELIX_TURNS * MathHelper.TAU;
            float rr = radius * breathe * envelope(t);
            float x = (float)Math.cos(ang) * rr;
            float z = (float)Math.sin(ang) * rr;
            float thick = 0.05F + 0.035F * (float)Math.sin(t * Math.PI);
            // head of the strand glows, tail fades
            float head = (float)((time * 0.45 + s * 0.5) % 1.0);
            float dist = Math.abs(t - head);
            dist = Math.min(dist, 1.0F - dist);
            float glow = MathHelper.clamp(1.0F - dist * 3.5F, 0.0F, 1.0F);
            float alpha = MathHelper.clamp(70.0F + 185.0F * glow, 0.0F, 255.0F);
            int c = col(p, i * 4 + s * 180, alpha);
            ribbon.vertex(matrix, x, y - thick, z).color(c);
            ribbon.vertex(matrix, x, y + thick, z).color(c);
         }

         draw(ribbon);

         // comet trail behind the strand head
         float headT = (float)((time * 0.45 + s * 0.5) % 1.0);
         BufferBuilder trail = Tessellator.getInstance().begin(DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
         for (int k = 0; k < HELIX_TRAIL; k++) {
            float f = k / (float)(HELIX_TRAIL - 1);
            float t = MathHelper.clamp(headT - f * 0.12F, 0.0F, 1.0F);
            float y = t * height;
            float ang = spin + phase + t * HELIX_TURNS * MathHelper.TAU;
            float rr = radius * breathe * envelope(t);
            float x = (float)Math.cos(ang) * rr;
            float z = (float)Math.sin(ang) * rr;
            float w = 0.11F * (1.0F - f);
            int c = col(p, k * 6 + s * 180, 190.0F * (1.0F - f) * (1.0F - f));
            trail.vertex(matrix, x, y - w, z).color(c);
            trail.vertex(matrix, x, y + w, z).color(c);
         }

         draw(trail);

         // glowing node at the strand head
         float ang = spin + phase + headT * HELIX_TURNS * MathHelper.TAU;
         float rr = radius * breathe * envelope(headT);
         drawNode(matrix, (float)Math.cos(ang) * rr, headT * height, (float)Math.sin(ang) * rr, 0.14F * p.scale(), p, s * 180, 230.0F);
      }

      end();
      stack.pop();
   }

   /** Radius envelope: pinched at the feet and the head so the helix looks like it wraps the body. */
   private static float envelope(float t) {
      return 0.55F + 0.45F * (float)Math.sin(t * Math.PI);
   }

   // ───────────────────────────── ORBIT ─────────────────────────────

   private static final int ORBIT_RINGS = 3;
   private static final int ORBIT_SEGMENTS = 72;
   private static final int ORBIT_TAIL = 28;

   public static void renderOrbit(MatrixStack stack, Entity target, float delta, Params p) {
      if (target == null) {
         return;
      }

      Vec3d pos = interpolatedPos(target, delta);
      float height = target.getHeight();
      float radius = (target.getWidth() * 1.05F + 0.25F) * p.scale();
      double time = seconds(p);

      stack.push();
      stack.translate(pos.x, pos.y + height * 0.5F, pos.z);
      begin(p);

      // ground halo (drawn in its own space at the feet)
      stack.push();
      stack.translate(0.0, -height * 0.5F + 0.02F, 0.0);
      drawHalo(stack.peek().getPositionMatrix(), target.getWidth() * 1.3F * p.scale(), time, p);
      stack.pop();

      for (int r = 0; r < ORBIT_RINGS; r++) {
         stack.push();

         // each ring has its own tilt and spin axis, giving the gyroscope look
         float baseTilt = r * (180.0F / ORBIT_RINGS);
         float tiltWobble = (float)Math.sin(time * 0.8 + r * 1.7) * 14.0F;
         stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(baseTilt + (float)(time * 28.0)));
         stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(58.0F + tiltWobble));

         Matrix4f matrix = stack.peek().getPositionMatrix();
         float ringRadius = radius * (1.0F + r * 0.12F);
         float speed = (float)(time * (1.9 + r * 0.55) * (r % 2 == 0 ? 1.0 : -1.0));

         // faint full ring
         BufferBuilder ring = Tessellator.getInstance().begin(DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
         for (int i = 0; i <= ORBIT_SEGMENTS; i++) {
            double a = i * MathHelper.TAU / ORBIT_SEGMENTS;
            float x = (float)Math.cos(a) * ringRadius;
            float z = (float)Math.sin(a) * ringRadius;
            int c = col(p, i * 5 + r * 120, 55.0F);
            ring.vertex(matrix, x, -0.012F, z).color(c);
            ring.vertex(matrix, x, 0.012F, z).color(c);
         }

         draw(ring);

         // comet tail
         BufferBuilder tail = Tessellator.getInstance().begin(DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
         for (int k = 0; k < ORBIT_TAIL; k++) {
            float f = k / (float)(ORBIT_TAIL - 1);
            double a = speed - f * 1.15F * Math.signum(speed == 0 ? 1 : speed);
            float x = (float)Math.cos(a) * ringRadius;
            float z = (float)Math.sin(a) * ringRadius;
            float w = 0.06F * (1.0F - f * 0.7F);
            int c = col(p, k * 7 + r * 120, (float)(230.0 * Math.pow(1.0F - f, 1.8)));
            tail.vertex(matrix, x, -w, z).color(c);
            tail.vertex(matrix, x, w, z).color(c);
         }

         draw(tail);

         // orb at the head of the comet
         drawNode(matrix, (float)Math.cos(speed) * ringRadius, 0.0F, (float)Math.sin(speed) * ringRadius, 0.12F * p.scale(), p, r * 120, 240.0F);

         stack.pop();
      }

      end();
      stack.pop();
   }

   private static void drawHalo(Matrix4f matrix, float radius, double time, Params p) {
      float pulse = 0.85F + 0.15F * (float)Math.sin(time * 3.2);
      int steps = 48;
      BufferBuilder halo = Tessellator.getInstance().begin(DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
      for (int i = 0; i <= steps; i++) {
         double a = i * MathHelper.TAU / steps;
         float cos = (float)Math.cos(a);
         float sin = (float)Math.sin(a);
         int inner = col(p, i * 7, 0.0F);
         int outer = col(p, i * 7, 120.0F * pulse);
         halo.vertex(matrix, cos * radius * 0.55F * pulse, 0.0F, sin * radius * 0.55F * pulse).color(inner);
         halo.vertex(matrix, cos * radius * pulse, 0.0F, sin * radius * pulse).color(outer);
      }

      draw(halo);
   }

   // ───────────────────────────── HALO ─────────────────────────────

   public static void renderHalo(MatrixStack stack, Entity target, float delta, Params p) {
      if (target == null) {
         return;
      }

      Vec3d pos = interpolatedPos(target, delta);
      double time = seconds(p);
      float bob = (float)Math.sin(time * 2.4) * 0.05F;
      float radius = (target.getWidth() * 0.75F + 0.12F) * p.scale();

      stack.push();
      stack.translate(pos.x, pos.y + target.getHeight() + 0.28F + bob, pos.z);
      begin(p);
      Matrix4f matrix = stack.peek().getPositionMatrix();

      // main ring, brightness runs around it
      BufferBuilder ring = Tessellator.getInstance().begin(DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
      int steps = 80;
      for (int i = 0; i <= steps; i++) {
         double a = i * MathHelper.TAU / steps;
         float x = (float)Math.cos(a) * radius;
         float z = (float)Math.sin(a) * radius;
         double run = (a - time * 2.0) % MathHelper.TAU;
         float glow = (float)(0.5 + 0.5 * Math.cos(run));
         int c = col(p, i * 4, 90.0F + 150.0F * glow);
         ring.vertex(matrix, x, -0.022F, z).color(c);
         ring.vertex(matrix, x, 0.022F, z).color(c);
      }

      draw(ring);

      // soft outer glow band
      BufferBuilder glowBand = Tessellator.getInstance().begin(DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
      for (int i = 0; i <= steps; i++) {
         double a = i * MathHelper.TAU / steps;
         float cos = (float)Math.cos(a);
         float sin = (float)Math.sin(a);
         glowBand.vertex(matrix, cos * radius, 0.0F, sin * radius).color(col(p, i * 4, 70.0F));
         glowBand.vertex(matrix, cos * (radius + 0.13F * p.scale()), 0.0F, sin * (radius + 0.13F * p.scale())).color(col(p, i * 4, 0.0F));
      }

      draw(glowBand);

      // sparks orbiting the ring
      for (int s = 0; s < 4; s++) {
         double a = time * (1.6 + s * 0.35) + s * (MathHelper.TAU / 4.0);
         float y = (float)Math.sin(time * 3.0 + s * 1.9) * 0.07F;
         drawNode(matrix, (float)Math.cos(a) * radius, y, (float)Math.sin(a) * radius, 0.09F * p.scale(), p, s * 90, 230.0F);
      }

      end();
      stack.pop();
   }

   // ───────────────────────────── PULSE ─────────────────────────────

   public static void renderPulse(MatrixStack stack, Entity target, float delta, Params p) {
      if (target == null) {
         return;
      }

      Vec3d pos = interpolatedPos(target, delta);
      double time = seconds(p);
      float maxRadius = (target.getWidth() * 1.9F + 0.35F) * p.scale();

      stack.push();
      stack.translate(pos.x, pos.y + 0.03, pos.z);
      begin(p);
      Matrix4f matrix = stack.peek().getPositionMatrix();
      drawHalo(matrix, target.getWidth() * 1.2F * p.scale(), time, p);

      for (int r = 0; r < 3; r++) {
         float t = (float)((time * 0.55 + r / 3.0) % 1.0);
         float radius = maxRadius * (0.25F + 0.75F * t);
         float fade = (1.0F - t) * (1.0F - t);
         BufferBuilder ring = Tessellator.getInstance().begin(DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
         int steps = 64;
         for (int i = 0; i <= steps; i++) {
            double a = i * MathHelper.TAU / steps;
            float cos = (float)Math.cos(a);
            float sin = (float)Math.sin(a);
            int c = col(p, i * 5 + r * 100, 220.0F * fade);
            int edge = col(p, i * 5 + r * 100, 0.0F);
            ring.vertex(matrix, cos * (radius - 0.07F), 0.0F, sin * (radius - 0.07F)).color(edge);
            ring.vertex(matrix, cos * radius, 0.0F, sin * radius).color(c);
            ring.vertex(matrix, cos * (radius + 0.025F), 0.0F, sin * (radius + 0.025F)).color(edge);
            ring.vertex(matrix, cos * radius, 0.0F, sin * radius).color(c);
         }

         draw(ring);
      }

      end();
      stack.pop();
   }

   // ───────────────────────────── CAGE ─────────────────────────────

   public static void renderCage(MatrixStack stack, Entity target, float delta, Params p) {
      if (target == null) {
         return;
      }

      Vec3d pos = interpolatedPos(target, delta);
      float height = target.getHeight();
      float radius = (target.getWidth() * 0.95F + 0.2F) * p.scale();
      double time = seconds(p);

      stack.push();
      stack.translate(pos.x, pos.y, pos.z);
      begin(p);
      Matrix4f matrix = stack.peek().getPositionMatrix();

      // vertical bars fading towards the top
      int bars = 10;
      BufferBuilder quads = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      for (int i = 0; i < bars; i++) {
         double a = time * 1.1 + i * (MathHelper.TAU / bars);
         float x = (float)Math.cos(a) * radius;
         float z = (float)Math.sin(a) * radius;
         float w = 0.012F;
         float px = -(float)Math.sin(a) * w;
         float pz = (float)Math.cos(a) * w;
         int low = col(p, i * 36, 200.0F);
         int high = col(p, i * 36 + 60, 0.0F);
         quads.vertex(matrix, x - px, 0.0F, z - pz).color(low);
         quads.vertex(matrix, x + px, 0.0F, z + pz).color(low);
         quads.vertex(matrix, x + px, height, z + pz).color(high);
         quads.vertex(matrix, x - px, height, z - pz).color(high);
      }

      draw(quads);

      // scanning rings sliding up and down
      for (int r = 0; r < 3; r++) {
         float t = (float)((time * 0.5 + r / 3.0) % 1.0);
         float y = t * height;
         float edgeFade = (float)Math.sin(t * Math.PI);
         BufferBuilder ring = Tessellator.getInstance().begin(DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
         int steps = 56;
         for (int i = 0; i <= steps; i++) {
            double a = i * MathHelper.TAU / steps;
            float x = (float)Math.cos(a) * radius;
            float z = (float)Math.sin(a) * radius;
            int c = col(p, i * 6 + r * 120, 200.0F * edgeFade);
            ring.vertex(matrix, x, y - 0.018F, z).color(c);
            ring.vertex(matrix, x, y + 0.018F, z).color(c);
         }

         draw(ring);
      }

      end();
      stack.pop();
   }

   // ───────────────────────────── SHARED ─────────────────────────────

   /** Camera-facing glow: a soft additive disc made from a fan, billboarded against the current view. */
   private static void drawNode(Matrix4f matrix, float x, float y, float z, float size, Params p, int count, float alpha) {
      Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
      float yaw = (float)Math.toRadians(-camera.getYaw());
      float pitch = (float)Math.toRadians(camera.getPitch());
      // billboard basis (right / up) relative to the camera
      float rx = (float)Math.cos(yaw);
      float rz = (float)Math.sin(yaw);
      float ux = (float)(Math.sin(pitch) * -Math.sin(yaw));
      float uy = (float)Math.cos(pitch);
      float uz = (float)(Math.sin(pitch) * Math.cos(yaw));

      int steps = 20;
      for (int layer = 0; layer < 2; layer++) {
         float s = layer == 0 ? size : size * 0.45F;
         float a = layer == 0 ? alpha / 3.0F : alpha;
         BufferBuilder fan = Tessellator.getInstance().begin(DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
         fan.vertex(matrix, x, y, z).color(col(p, count, a));
         int edge = col(p, count, 0.0F);
         for (int i = 0; i <= steps; i++) {
            double ang = i * MathHelper.TAU / steps;
            float c = (float)Math.cos(ang) * s;
            float sn = (float)Math.sin(ang) * s;
            fan.vertex(matrix, x + rx * c + ux * sn, y + uy * sn, z + rz * c + uz * sn).color(edge);
         }

         draw(fan);
      }
   }

   private static void begin(Params p) {
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      if (p.walls()) {
         RenderSystem.disableDepthTest();
      } else {
         RenderSystem.enableDepthTest();
      }

      RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE);
   }

   private static void draw(BufferBuilder builder) {
      RenderSystem.setShader(GameRenderer::getPositionColorProgram);
      BufferRenderer.drawWithGlobalProgram(builder.end());
   }

   private static void end() {
      RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE_MINUS_SRC_ALPHA);
      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private static Vec3d interpolatedPos(Entity target, float delta) {
      Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
      return new Vec3d(
         MathHelper.lerp((double)delta, target.prevX, target.getX()) - camera.getPos().x,
         MathHelper.lerp((double)delta, target.prevY, target.getY()) - camera.getPos().y,
         MathHelper.lerp((double)delta, target.prevZ, target.getZ()) - camera.getPos().z
      );
   }

   /** Health fraction of a living target, 1 for anything else. */
   public static float health(Entity target) {
      if (target instanceof LivingEntity living && living.getMaxHealth() > 0.0F) {
         return MathHelper.clamp(living.getHealth() / living.getMaxHealth(), 0.0F, 1.0F);
      }

      return 1.0F;
   }
}
