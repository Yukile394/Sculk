package dev.sculk.module.modules.combat;

import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public final class Fps14 extends AimAssistModule {
   private final NumberSetting height = new NumberSetting("Height", 0.0, -1.0, 2.0, 0.05).describe("Se\u00e7ilen b\u00f6lgeye eklenen dikey ofset");

   public Fps14() {
      super("Aim Assist 2", "Locks onto the closest player smoothly");
      this.addSettings(new Setting[]{this.height});
   }

   @Override
   protected Vec3d aimPoint(Entity entity) {
      return super.aimPoint(entity).add(0.0, this.height.getValue(), 0.0);
   }
}
