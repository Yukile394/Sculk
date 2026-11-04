package dev.sculk.gui;

import dev.sculk.SculkClient;
import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.modules.client.Fps15;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.ButtonSetting;
import dev.sculk.module.setting.KeySetting;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.module.setting.StringSetting;
import dev.sculk.render.Render2D;
import dev.sculk.render.Theme;
import dev.sculk.render.Toasts;
import dev.sculk.util.ConfigManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.screen.Screen;

public final class PanelScreen extends Screen {
   private static final int FRAME_W = 132;
   private static final int HEADER_H = 18;
   private static final int ROW_H = 15;
   private static final int GAP = 14;
   private static final int TOP = 40;
   private static final float CHIP_H = 11.0F;
   private static final float CHIP_GAP = 3.0F;
   private static final float CHIP_PAD = 4.0F;
   private final List<PanelScreen.Frame> frames = new ArrayList<>();
   private final Map<Module, float[]> anim = new HashMap<>();
   private PanelScreen.Frame draggingFrame;
   private int dragOffsetX;
   private int dragOffsetY;
   private NumberSetting draggingNumber;
   private float dragTrackX;
   private float dragTrackW;
   private Module bindingModule;
   private KeySetting bindingKey;
   private StringSetting focusedString;
   private ButtonSetting armedButton;
   private long armedAt;
   private final List<Module> expandedModules = new ArrayList<>();

   public PanelScreen() {
      super(Text.literal("sculk"));
   }

   protected void init() {
      this.frames.clear();
      int index = 0;

      for (Category category : Category.values()) {
         List<Module> modules = SculkClient.byCategory(category);
         if (!modules.isEmpty()) {
            float x = (float)(this.width - (this.totalFrames() * 132 + (this.totalFrames() - 1) * 14)) / 2.0F + (float)(index * 146);
            this.frames.add(new PanelScreen.Frame(category, x, 40.0F));
            index++;
         }
      }

      this.resetInteractionState();
   }

   private int totalFrames() {
      int count = 0;

      for (Category category : Category.values()) {
         if (!SculkClient.byCategory(category).isEmpty()) {
            count++;
         }
      }

      return Math.max(1, count);
   }

   private void resetInteractionState() {
      this.draggingFrame = null;
      this.draggingNumber = null;
      this.bindingModule = null;
      this.bindingKey = null;
      this.focusedString = null;
      this.armedButton = null;
   }

   private float[] animOf(Module module) {
      return this.anim.computeIfAbsent(module, m -> new float[]{m.isEnabled() ? 1.0F : 0.0F, 0.0F});
   }

   private void save() {
      ConfigManager.save(SculkClient.modules());
   }

<<<<<<< HEAD
=======
   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }

>>>>>>> a073ff2 (Guncelleme)
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      context.fill(0, 0, this.width, this.height, -1778384896);

      for (PanelScreen.Frame frame : this.frames) {
         frame.render(context, mouseX, mouseY);
      }

      super.render(context, mouseX, mouseY, delta);
      Toasts.render(context);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      this.focusedString = null;

      for (int i = this.frames.size() - 1; i >= 0; i--) {
         if (this.frames.get(i).mouseClicked(mouseX, mouseY, button)) {
            if (button == 0 && i != this.frames.size() - 1) {
               PanelScreen.Frame frame = this.frames.remove(i);
               this.frames.add(frame);
            }

            return true;
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (this.draggingFrame != null) {
         this.draggingFrame.x = (float)mouseX - (float)this.dragOffsetX;
         this.draggingFrame.y = (float)mouseY - (float)this.dragOffsetY;
         return true;
      } else if (this.draggingNumber != null) {
         this.updateNumber(this.draggingNumber, mouseX);
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.draggingFrame = null;
      this.draggingNumber = null;
      return super.mouseReleased(mouseX, mouseY, button);
   }

<<<<<<< HEAD
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
=======
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
>>>>>>> a073ff2 (Guncelleme)
      Iterator var7 = this.frames.iterator();
      if (var7.hasNext()) {
         PanelScreen.Frame frame = (PanelScreen.Frame)var7.next();
         frame.scroll((float)amount * 18.0F);
         return true;
      } else {
<<<<<<< HEAD
         return super.mouseScrolled(mouseX, mouseY, amount);
=======
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
>>>>>>> a073ff2 (Guncelleme)
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.bindingModule != null) {
         this.bindingModule.key(keyCode == 256 ? -1 : keyCode);
         this.bindingModule = null;
         this.save();
         return true;
      } else if (this.bindingKey != null) {
         this.bindingKey.setKey(keyCode == 256 ? -1 : keyCode);
         this.bindingKey = null;
         this.save();
         return true;
      } else if (this.focusedString == null) {
         if (keyCode == 256) {
            this.close();
            return true;
         } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
         }
      } else {
         if (keyCode == 259) {
            this.focusedString.backspace();
         } else if (keyCode == 256 || keyCode == 257) {
            this.focusedString = null;
         }

         this.save();
         return true;
      }
   }

   public boolean charTyped(char chr, int modifiers) {
      if (this.focusedString != null) {
         this.focusedString.append(chr);
         this.save();
         return true;
      } else {
         return super.charTyped(chr, modifiers);
      }
   }

   private void updateNumber(NumberSetting number, double mouseX) {
      number.setFromFraction((mouseX - (double)this.dragTrackX) / (double)this.dragTrackW);
      this.save();
   }

   private boolean armed(ButtonSetting button) {
      return button == this.armedButton && System.currentTimeMillis() - this.armedAt <= 4000L;
   }

   public void close() {
      Fps15 module = SculkClient.get(Fps15.class);
      if (module != null && module.isEnabled()) {
         module.setEnabled(false);
      } else {
         super.close();
      }
   }

   public void removed() {
      this.save();
      Fps15 module = SculkClient.get(Fps15.class);
      if (module != null) {
         module.onScreenClosed();
      }
   }

   public boolean shouldPause() {
      return false;
   }

   private static String keyName(int key) {
      return key < 0 ? "none" : InputUtil.fromKeyCode(key, 0).getLocalizedText().getString();
   }

   private static boolean inside(double mx, double my, float x, float y, float w, float h) {
      return mx >= (double)x && mx < (double)(x + w) && my >= (double)y && my < (double)(y + h);
   }

   private static record Chip(float x, float y, float w, int index) {
   }

   private final class Frame {
      private final Category category;
      private float x;
      private float y;
      private boolean extended = true;
      private float scroll;

      private Frame(Category category, float x, float y) {
         this.category = category;
         this.x = x;
         this.y = y;
      }

      private List<Module> modules() {
         return SculkClient.byCategory(this.category);
      }

      private void scroll(float amount) {
         float max = Math.max(0.0F, this.contentHeight() - this.maxHeight());
         this.scroll = Math.max(0.0F, Math.min(max, this.scroll - amount));
      }

      private float maxHeight() {
         return (float)PanelScreen.this.height - this.y - 40.0F + 20.0F;
      }

      private float contentHeight() {
         float total = 0.0F;

         for (Module module : this.modules()) {
            total += 15.0F;
            if (this.isExpanded(module)) {
               total += this.settingsHeight(module);
            }
         }

         return total;
      }

      private float settingsHeight(Module module) {
         float total = 6.0F;

         for (Setting<?> setting : module.settings()) {
            if (setting.isVisible()) {
               total += this.rowHeight(setting);
            }
         }

         return total;
      }

      private float rowHeight(Setting<?> setting) {
         if (setting instanceof NumberSetting) {
            return 19.0F;
         } else if (!(setting instanceof ModeSetting<?> mode)) {
            return 14.0F;
         } else {
            float lowest = 0.0F;

            for (PanelScreen.Chip chip : this.modeChips(mode, 0.0F, 0.0F, 120.0F)) {
               lowest = Math.max(lowest, chip.y());
            }

            return 12.0F + lowest + 11.0F + 3.0F;
         }
      }

      private List<PanelScreen.Chip> modeChips(ModeSetting<?> setting, float bx, float by, float bw) {
         List<PanelScreen.Chip> chips = new ArrayList<>();
         float x = bx;
         float y = by;

         for (int i = 0; i < setting.values().length; i++) {
            float w = (float)Render2D.textWidth(setting.display(i)) + 8.0F;
            if (x > bx && x + w > bx + bw) {
               x = bx;
               y += 14.0F;
            }

            chips.add(new PanelScreen.Chip(x, y, w, i));
            x += w + 3.0F;
         }

         return chips;
      }

      private int chipAt(ModeSetting<?> setting, double mx, double my, float bx, float by, float bw) {
         for (PanelScreen.Chip chip : this.modeChips(setting, bx, by, bw)) {
            if (PanelScreen.inside(mx, my, chip.x(), chip.y(), chip.w(), 11.0F)) {
               return chip.index();
            }
         }

         return -1;
      }

      private boolean isExpanded(Module module) {
         return PanelScreen.this.animOf(module)[1] > 0.02F;
      }

      private void render(DrawContext context, int mouseX, int mouseY) {
         float bodyH = 0.0F;
         if (this.extended) {
            bodyH = this.contentHeight() + 6.0F;
         }

         float totalH = 18.0F + bodyH;
         Render2D.roundedRect(context, this.x, this.y, 132.0F, totalH, 8.0F, Theme.PANEL);
         Render2D.roundedRect(context, this.x, this.y, 132.0F, 18.0F, 8.0F, Theme.HEADER);
         context.fill((int)this.x, (int)(this.y + 18.0F - 6.0F), (int)(this.x + 132.0F), (int)(this.y + 18.0F), Theme.HEADER);
         context.fill((int)this.x + 8, (int)(this.y + 18.0F - 2.0F), (int)(this.x + 132.0F - 8.0F), (int)(this.y + 18.0F - 1.0F), Theme.ACCENT);
         Render2D.textCentered(context, this.category.label, this.x + 66.0F, this.y + 5.0F, -1447440);
         if (this.extended) {
            context.enableScissor((int)this.x, (int)(this.y + 18.0F), (int)(this.x + 132.0F), (int)(this.y + totalH));
            float rowY = this.y + 18.0F + 3.0F - this.scroll;

            for (Module module : this.modules()) {
               if (rowY + 15.0F >= this.y + 18.0F && rowY <= this.y + totalH) {
                  this.renderModule(context, module, rowY, mouseX, mouseY);
               }

               rowY += 15.0F;
               if (PanelScreen.this.animOf(module)[1] > 0.02F) {
                  rowY += this.renderSettings(context, module, rowY, mouseX, mouseY);
               }
            }

            context.disableScissor();
         }
      }

      private void renderModule(DrawContext context, Module module, float rowY, int mouseX, int mouseY) {
         float[] state = PanelScreen.this.animOf(module);
         state[0] += ((module.isEnabled() ? 1.0F : 0.0F) - state[0]) * 0.25F;
         state[1] += ((this.isExpandedTarget(module) ? 1.0F : 0.0F) - state[1]) * 0.25F;
         boolean hovered = PanelScreen.inside((double)mouseX, (double)mouseY, this.x + 2.0F, rowY, 128.0F, 15.0F);
         int bg = hovered ? Theme.mix(Theme.PANEL, -1447440, 0.06F) : Theme.PANEL;
         context.fill((int)(this.x + 2.0F), (int)rowY, (int)(this.x + 132.0F - 2.0F), (int)(rowY + 15.0F), bg);
         if (state[0] > 0.02F) {
            context.fill(
               (int)(this.x + 2.0F), (int)rowY, (int)(this.x + 4.0F), (int)(rowY + 15.0F), Theme.withAlpha(Theme.ACCENT, (int)(255.0F * state[0]))
            );
         }

         String label = module.getName();
         if (PanelScreen.this.bindingModule == module) {
            label = "...";
         } else if (module.getKey() >= 0) {
            String kn = PanelScreen.keyName(module.getKey());
            if (!kn.isEmpty()) {
               label = label + " [" + kn.toUpperCase() + "]";
            }
         }

         Render2D.text(context, Render2D.truncate(label, 116), this.x + 7.0F, rowY + 4.0F, module.isEnabled() ? -1447440 : -8750457);
      }

      private boolean isExpandedTarget(Module module) {
         return PanelScreen.this.expandedModules.contains(module);
      }

      private float renderSettings(DrawContext context, Module module, float startY, int mouseX, int mouseY) {
         float pad = 6.0F;
         float innerX = this.x + 6.0F;
         float innerW = 120.0F;
         float alpha = PanelScreen.this.animOf(module)[1];
         float rowY = startY + 3.0F;

         for (Setting<?> setting : module.settings()) {
            if (setting.isVisible()) {
               float h = this.rowHeight(setting);
               if (setting instanceof BooleanSetting bool) {
                  this.renderBoolean(context, bool, innerX, rowY, innerW, mouseX, mouseY);
               } else if (setting instanceof NumberSetting number) {
                  this.renderNumber(context, number, innerX, rowY, innerW, mouseX, mouseY);
               } else if (setting instanceof ModeSetting<?> mode) {
                  this.renderMode(context, mode, innerX, rowY, innerW, mouseX, mouseY);
               } else if (setting instanceof KeySetting key) {
                  this.renderKey(context, key, innerX, rowY, innerW, mouseX, mouseY);
               } else if (setting instanceof StringSetting text) {
                  this.renderString(context, text, innerX, rowY, innerW, mouseX, mouseY);
               } else if (setting instanceof ButtonSetting button) {
                  this.renderButton(context, button, innerX, rowY, innerW, mouseX, mouseY);
               }

               rowY += h;
            }
         }

         return rowY - startY + pad;
      }

      private void renderBoolean(DrawContext context, BooleanSetting setting, float bx, float by, float bw, int mouseX, int mouseY) {
         boolean hovered = PanelScreen.inside((double)mouseX, (double)mouseY, bx, by, bw, 14.0F);
         int box = setting.get() ? Theme.ACCENT : -15000797;
         context.fill((int)bx, (int)(by + 2.0F), (int)(bx + 8.0F), (int)(by + 10.0F), box);
         if (setting.get()) {
            Render2D.text(context, "x", bx + 1.0F, by + 1.0F, -16250868);
         }

         Render2D.text(
            context, Render2D.truncate(setting.getName(), (int)(bw - 14.0F)), bx + 12.0F, by + 3.0F, setting.get() ? -1447440 : (hovered ? -1447440 : -8750457)
         );
      }

      private void renderNumber(DrawContext context, NumberSetting setting, float bx, float by, float bw, int mouseX, int mouseY) {
         Render2D.text(context, Render2D.truncate(setting.getName(), (int)(bw - 40.0F)), bx, by, -8750457);
         String value = setting.display();
         Render2D.text(context, value, bx + bw - (float)Render2D.textWidth(value), by, Theme.ACCENT);
         float trackY = by + 13.0F;
         float fill = (float)setting.fraction() * bw;
         context.fill((int)bx, (int)trackY, (int)(bx + bw), (int)(trackY + 2.0F), -15000797);
         if (fill > 0.0F) {
            context.fill((int)bx, (int)trackY, (int)(bx + fill), (int)(trackY + 2.0F), Theme.ACCENT);
         }
      }

      private void renderMode(DrawContext context, ModeSetting<?> setting, float bx, float by, float bw, int mouseX, int mouseY) {
         Render2D.text(context, Render2D.truncate(setting.getName(), (int)bw), bx, by, -8750457);

         for (PanelScreen.Chip chip : this.modeChips(setting, bx, by + 12.0F, bw)) {
            boolean selected = chip.index() == setting.index();
            boolean hovered = PanelScreen.inside((double)mouseX, (double)mouseY, chip.x(), chip.y(), chip.w(), 11.0F);
            int bg = selected ? Theme.withAlpha(Theme.ACCENT, 90) : (hovered ? Theme.withAlpha(Theme.ACCENT, 35) : -15000797);
            int fg = selected ? Theme.ACCENT : (hovered ? -1447440 : -8750457);
            Render2D.roundedRect(context, chip.x(), chip.y(), chip.w(), 11.0F, 3.0F, bg);
            Render2D.text(context, setting.display(chip.index()), chip.x() + 4.0F, chip.y() + 1.0F, fg);
         }
      }

      private void renderKey(DrawContext context, KeySetting setting, float bx, float by, float bw, int mouseX, int mouseY) {
         Render2D.text(context, Render2D.truncate(setting.getName(), (int)(bw - 40.0F)), bx, by, -8750457);
         String value = PanelScreen.this.bindingKey == setting ? "..." : setting.display();
         int vw = Render2D.textWidth(value);
         context.fill(
            (int)(bx + bw - (float)vw - 8.0F),
            (int)(by - 1.0F),
            (int)(bx + bw),
            (int)(by + 11.0F),
            PanelScreen.this.bindingKey == setting ? Theme.withAlpha(Theme.ACCENT, 60) : -15000797
         );
         Render2D.text(context, value, bx + bw - (float)vw - 4.0F, by, PanelScreen.this.bindingKey == setting ? Theme.ACCENT : -1447440);
      }

      private void renderString(DrawContext context, StringSetting setting, float bx, float by, float bw, int mouseX, int mouseY) {
         Render2D.text(context, Render2D.truncate(setting.getName(), (int)(bw - 40.0F)), bx, by, -8750457);
         boolean focused = PanelScreen.this.focusedString == setting;
         String value = setting.getValue().isEmpty() && !focused ? "empty" : setting.getValue();
         if (focused) {
            value = value + "|";
         }

         int color = focused ? Theme.ACCENT : (setting.getValue().isEmpty() ? -8750457 : -1447440);
         int vw = Render2D.textWidth(value);
         Render2D.text(context, value, bx + bw - (float)vw, by, color);
      }

      private void renderButton(DrawContext context, ButtonSetting setting, float bx, float by, float bw, int mouseX, int mouseY) {
         boolean confirm = PanelScreen.this.armed(setting);
         String label = confirm ? "confirm" : (setting.needsConfirm() ? "run" : "open");
         int lw = Render2D.textWidth(label);
         float lx = bx + bw - (float)lw - 10.0F;
         context.fill(
            (int)lx, (int)(by - 1.0F), (int)(lx + (float)lw + 8.0F), (int)(by + 11.0F), confirm ? -10738909 : Theme.withAlpha(Theme.ACCENT, 40)
         );
         Render2D.text(context, label, lx + 4.0F, by, confirm ? -30070 : Theme.ACCENT);
      }

      private boolean mouseClicked(double mx, double my, int button) {
         if (PanelScreen.inside(mx, my, this.x, this.y, 132.0F, 18.0F)) {
            if (button == 0) {
               PanelScreen.this.draggingFrame = this;
               PanelScreen.this.dragOffsetX = (int)(mx - (double)this.x);
               PanelScreen.this.dragOffsetY = (int)(my - (double)this.y);
            } else if (button == 1) {
               this.extended = !this.extended;
            }

            return true;
         } else if (!this.extended) {
            return false;
         } else {
            float rowY = this.y + 18.0F + 3.0F - this.scroll;

            for (Module module : this.modules()) {
               if (PanelScreen.inside(mx, my, this.x + 2.0F, rowY, 128.0F, 15.0F)) {
                  if (button == 0) {
                     module.toggle();
                     PanelScreen.this.save();
                  } else if (button == 1) {
                     this.toggleExpanded(module);
                  } else if (button == 2) {
                     PanelScreen.this.bindingModule = module;
                     PanelScreen.this.bindingKey = null;
                     PanelScreen.this.focusedString = null;
                  }

                  return true;
               }

               rowY += 15.0F;
               if (PanelScreen.this.animOf(module)[1] > 0.02F) {
                  float consumed = this.settingsMouseClicked(module, mx, my, button, rowY);
                  if (consumed > 0.0F) {
                     return true;
                  }

                  rowY += consumed;
               }
            }

            return false;
         }
      }

      private float settingsMouseClicked(Module module, double mx, double my, int button, float startY) {
         float innerX = this.x + 6.0F;
         float innerW = 120.0F;
         float rowY = startY + 3.0F;
         Iterator var11 = module.settings().iterator();

         float h;
         while (true) {
            if (!var11.hasNext()) {
               return 0.0F;
            }

            Setting<?> setting = (Setting<?>)var11.next();
            if (setting.isVisible()) {
               h = this.rowHeight(setting);
               if (PanelScreen.inside(mx, my, innerX, rowY, innerW, h)) {
                  if (setting instanceof ModeSetting<?> mode) {
                     int picked = this.chipAt(mode, mx, my, innerX, rowY + 12.0F, innerW);
                     if (picked < 0) {
                        rowY += h;
                        continue;
                     }

                     mode.select(picked);
                     break;
                  }

                  if (setting instanceof BooleanSetting bool) {
                     bool.toggle();
                  } else if (setting instanceof NumberSetting number) {
                     PanelScreen.this.draggingNumber = number;
                     PanelScreen.this.dragTrackX = innerX;
                     PanelScreen.this.dragTrackW = innerW;
                     PanelScreen.this.updateNumber(number, mx);
                  } else if (setting instanceof KeySetting key) {
                     PanelScreen.this.bindingKey = key;
                     PanelScreen.this.bindingModule = null;
                     PanelScreen.this.focusedString = null;
                  } else if (setting instanceof StringSetting text) {
                     PanelScreen.this.focusedString = text;
                     PanelScreen.this.bindingKey = null;
                     PanelScreen.this.bindingModule = null;
                  } else if (setting instanceof ButtonSetting action) {
                     if (PanelScreen.this.armed(action)) {
                        PanelScreen.this.armedButton = null;
                        action.run();
                     } else {
                        PanelScreen.this.armedButton = action;
                        PanelScreen.this.armedAt = System.currentTimeMillis();
                     }
                  }
                  break;
               } else {
                  rowY += h;
               }
            }
         }

         PanelScreen.this.save();
         return h;
      }

      private void toggleExpanded(Module module) {
         List<Module> expanded = PanelScreen.this.expandedModules;
         if (expanded.contains(module)) {
            expanded.remove(module);
         } else {
            expanded.add(module);
         }
      }
   }
}
