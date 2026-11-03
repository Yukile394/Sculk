package dev.sculk.module.modules.combat;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BooleanSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import net.minecraft.entity.Entity;

public final class Fps32 extends Module {
   private final NumberSetting horizontal = new NumberSetting("Horizontal", 0.0, 0.0, 100.0, 1.0).describe("Percent of horizontal knockback kept (0 = none)");
   private final NumberSetting vertical = new NumberSetting("Vertical", 100.0, 0.0, 100.0, 1.0).describe("Percent of vertical knockback kept");
   private final BooleanSetting onlyKnockback = new BooleanSetting("Only Knockback", true)
      .describe("Ignore velocity that is not caused by damage (rockets, pistons, ...)");
   private final NumberSetting chance = new NumberSetting("Chance", 100.0, 0.0, 100.0, 1.0).describe("Chance that a hit is reduced");

   public Fps32() {
      super("Velocity", "Reduces incoming knockback", Category.COMBAT);
      this.addSettings(new Setting[]{this.horizontal, this.vertical, this.onlyKnockback, this.chance});
   }

   public boolean applies(Entity entity) {
      if (!this.isEnabled() || this.mc.player == null || entity != this.mc.player) {
         return false;
      } else if (this.onlyKnockback.get() && this.mc.player.hurtTime <= 0) {
         return false;
      } else {
         int chanceValue = this.chance.getInt();
         return chanceValue >= 100 ? true : Math.floorMod(entity.age + entity.getId() * 31, 100) < chanceValue;
      }
   }

   public double horizontalScale() {
      return (double)this.horizontal.getFloat() / 100.0;
   }

   public double verticalScale() {
      return (double)this.vertical.getFloat() / 100.0;
   }
}
