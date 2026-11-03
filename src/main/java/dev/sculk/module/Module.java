package dev.sculk.module;

import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.Setting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public abstract class Module {
   private String name;
   private String description;
   private final Category category;
   private final List<Setting<?>> settings = new ArrayList<>();
   private final BooleanSetting middleClick = new BooleanSetting("Middle Click", false).describe("Toggles this module with the middle mouse button");
   private boolean enabled;
   private int key = -1;
   protected MinecraftClient mc;

   protected MinecraftClient getMC() {
      if (this.mc == null) {
         this.mc = MinecraftClient.getInstance();
      }

      return this.mc;
   }

   protected Module(String name, String description, Category category) {
      this.name = name;
      this.description = description;
      this.category = category;
      this.settings.add(this.middleClick);
   }

   public String getName() {
      return this.name;
   }

   public String getDescription() {
      return this.description;
   }

   public Module setName(String name) {
      this.name = name;
      return this;
   }

   public Module setDescription(String description) {
      this.description = description;
      return this;
   }

   public Category getCategory() {
      return this.category;
   }

   public int getKey() {
      return this.key;
   }

   public Module key(int key) {
      this.key = key;
      return this;
   }

   public List<Setting<?>> settings() {
      return this.settings;
   }

   protected void addSettings(Setting<?>... settings) {
      this.settings.addAll(Arrays.asList(settings));
   }

   public BooleanSetting middleClick() {
      return this.middleClick;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void toggle() {
      this.setEnabled(!this.enabled);
   }

   public void setEnabled(boolean value) {
      if (this.enabled != value) {
         this.enabled = value;
         if (value) {
            this.onEnable();
         } else {
            this.onDisable();
         }
      }
   }

   public void setEnabledSilently(boolean value) {
      this.enabled = value;
   }

   public void onEnable() {
   }

   public void onDisable() {
   }

   public void onTick() {
   }

   public void onBufferUpdate() {
   }

   public void onRender2D(DrawContext context, float tickDelta) {
   }

   public void onRenderTick(float tickDelta) {
   }
}
