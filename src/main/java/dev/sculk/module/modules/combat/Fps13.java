package dev.sculk.module.modules.combat;

public final class Fps13 extends AimAssistModule {
   public Fps13() {
      super("Aim Assist", "Smoothly aims at the closest player");
      this.defaultKey(org.lwjgl.glfw.GLFW.GLFW_KEY_M);
   }
}
