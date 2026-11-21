package dev.sculk.module.modules.combat;

import com.mojang.blaze3d.platform.GlStateManager.DstFactor;
import com.mojang.blaze3d.platform.GlStateManager.SrcFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.sculk.SculkClient;
import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * ElytraTarget, ported from Tera Client (Aura.ElytraTarget).
 * While you and the Aura target both glide, the target hitbox is moved forward along its velocity by Predict blocks
 * (scaled by how much of its recent top speed it still has). Aura then aims, ray-checks and range-checks against that
 * predicted box. SharpRot forces Aura's HvH rotation while gliding, and Aura snaps onto the predicted centre when the
 * smoothed aim is not on the box yet.
 */
public final class ElytraTarget extends Module {
   private static final double DECAY_PER_MS = Math.pow(0.99, 0.02);
   private static final double MIN_PREDICT_SPEED = 0.45;

   private final NumberSetting predict = new NumberSetting("Predict", 2.0, 0.5, 6.0, 0.1);
   private final BooleanSetting sharpRot = new BooleanSetting("SharpRot", true);
   private final BooleanSetting visual = new BooleanSetting("Visual", true);
   private final NumberSetting red = new NumberSetting("Red", 0.0, 0.0, 255.0, 1.0).visibleWhen(() -> this.visual.get());
   private final NumberSetting green = new NumberSetting("Green", 255.0, 0.0, 255.0, 1.0).visibleWhen(() -> this.visual.get());
   private final NumberSetting blue = new NumberSetting("Blue", 200.0, 0.0, 255.0, 1.0).visibleWhen(() -> this.visual.get());
   private final NumberSetting alpha = new NumberSetting("Alpha", 100.0, 10.0, 255.0, 1.0).visibleWhen(() -> this.visual.get());
   private volatile Box cachedBox;
   private double maxSpeedSeen;
   private long lastTickMs;

   public ElytraTarget() {
      super("ElytraTarget", "Predicts the Aura target's hitbox while you both glide (Tera Client port)", Category.COMBAT);
      this.addSettings(new Setting[]{this.predict, this.sharpRot, this.visual, this.red, this.green, this.blue, this.alpha});
   }

   public boolean sharpRot() {
      return this.sharpRot.get();
   }

   public Box getPredictedBox() {
      return this.cachedBox;
   }

   @Override
   public void onEnable() {
      this.reset();
   }

   @Override
   public void onDisable() {
      this.reset();
   }

   private void reset() {
      this.cachedBox = null;
      this.maxSpeedSeen = 0.0;
      this.lastTickMs = 0L;
   }

   /** Both the player and the given target are gliding and the target is alive. */
   public boolean isActive(Entity target) {
      if (!this.isEnabled() || this.mc == null || this.mc.player == null || this.mc.world == null) {
         return false;
      }

      return target != null && target.isAlive() && this.mc.player.isFallFlying() && target instanceof LivingEntity living && living.isFallFlying();
   }

   private Entity auraTarget() {
      Aura aura = SculkClient.get(Aura.class);
      return aura != null && aura.isEnabled() ? aura.currentTarget() : null;
   }

   /** Runs before Aura in module order: refreshes the predicted box from the target's current velocity. */
   @Override
   public void onTick() {
      if (this.mc == null || this.mc.player == null || this.mc.world == null) {
         this.cachedBox = null;
         return;
      }

      long now = System.currentTimeMillis();
      double deltaMs = this.lastTickMs == 0L ? 50.0 : Math.min((double)(now - this.lastTickMs), 200.0);
      this.lastTickMs = now;
      double decay = Math.pow(DECAY_PER_MS, deltaMs);
      Entity target = this.auraTarget();
      if (!this.isActive(target)) {
         this.maxSpeedSeen *= decay;
         this.cachedBox = null;
         return;
      }

      Vec3d velocity = target.getVelocity();
      double speedSq = velocity.lengthSquared();
      if (speedSq < 1.0E-6) {
         this.maxSpeedSeen *= decay;
         this.cachedBox = null;
         return;
      }

      double speed = Math.sqrt(speedSq);
      this.maxSpeedSeen = Math.max(this.maxSpeedSeen * decay, speed);
      if (speed < MIN_PREDICT_SPEED) {
         this.cachedBox = null;
         return;
      }

      double ratio = this.maxSpeedSeen > 1.0E-9 ? Math.min(speed / this.maxSpeedSeen, 1.0) : 1.0;
      double distance = this.predict.getValue() * ratio;
      if (distance < 0.01) {
         this.cachedBox = null;
         return;
      }

      this.cachedBox = target.getBoundingBox().offset(new Vec3d(velocity.x / speed * distance, velocity.y / speed * distance, velocity.z / speed * distance));
   }

   /** Called every frame from WorldRenderEvents.LAST (see SculkClient). */
   public void render(MatrixStack stack) {
      Box box = this.cachedBox;
      if (!this.visual.get() || box == null || stack == null || this.mc == null || this.mc.gameRenderer == null) {
         return;
      }

      Vec3d cam = this.mc.gameRenderer.getCamera().getPos();
      float x1 = (float)(box.minX - cam.x);
      float y1 = (float)(box.minY - cam.y);
      float z1 = (float)(box.minZ - cam.z);
      float x2 = (float)(box.maxX - cam.x);
      float y2 = (float)(box.maxY - cam.y);
      float z2 = (float)(box.maxZ - cam.z);
      int r = this.red.getInt();
      int g = this.green.getInt();
      int b = this.blue.getInt();
      int fill = this.alpha.getInt() << 24 | r << 16 | g << 8 | b;
      int line = 0xFF000000 | r << 16 | g << 8 | b;
      Matrix4f m = stack.peek().getPositionMatrix();

      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      RenderSystem.disableDepthTest();
      RenderSystem.setShader(GameRenderer::getPositionColorProgram);

      BufferBuilder quads = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      face(quads, m, fill, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
      face(quads, m, fill, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
      face(quads, m, fill, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
      face(quads, m, fill, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
      face(quads, m, fill, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
      face(quads, m, fill, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
      BufferRenderer.drawWithGlobalProgram(quads.end());

      RenderSystem.lineWidth(2.0F);
      BufferBuilder lines = Tessellator.getInstance().begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
      edge(lines, m, line, x1, y1, z1, x2, y1, z1);
      edge(lines, m, line, x2, y1, z1, x2, y1, z2);
      edge(lines, m, line, x2, y1, z2, x1, y1, z2);
      edge(lines, m, line, x1, y1, z2, x1, y1, z1);
      edge(lines, m, line, x1, y2, z1, x2, y2, z1);
      edge(lines, m, line, x2, y2, z1, x2, y2, z2);
      edge(lines, m, line, x2, y2, z2, x1, y2, z2);
      edge(lines, m, line, x1, y2, z2, x1, y2, z1);
      edge(lines, m, line, x1, y1, z1, x1, y2, z1);
      edge(lines, m, line, x2, y1, z1, x2, y2, z1);
      edge(lines, m, line, x2, y1, z2, x2, y2, z2);
      edge(lines, m, line, x1, y1, z2, x1, y2, z2);
      BufferRenderer.drawWithGlobalProgram(lines.end());

      RenderSystem.lineWidth(1.0F);
      RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE_MINUS_SRC_ALPHA);
      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
   }

   private static void face(BufferBuilder b, Matrix4f m, int color, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
      b.vertex(m, ax, ay, az).color(color);
      b.vertex(m, bx, by, bz).color(color);
      b.vertex(m, cx, cy, cz).color(color);
      b.vertex(m, dx, dy, dz).color(color);
   }

   private static void edge(BufferBuilder b, Matrix4f m, int color, float ax, float ay, float az, float bx, float by, float bz) {
      b.vertex(m, ax, ay, az).color(color);
      b.vertex(m, bx, by, bz).color(color);
   }
}
