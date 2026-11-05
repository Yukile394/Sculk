package dev.sculk.module.modules.combat;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.world.ClientWorld;

public final class Fps31 extends Module {
   public Fps31() {
      super("ElyFix", "Fireworks boost through walls and cobwebs", Category.COMBAT);
      this.setEnabledSilently(true);
   }

   public boolean redirects(ItemStack stack, BlockPos target) {
      if (this.isEnabled() && stack.isOf(Items.FIREWORK_ROCKET)) {
         ClientWorld world = this.mc.world;
         return world == null || world.getBlockEntity(target) == null;
      } else {
         return false;
      }
   }

   public boolean ignoresCobweb(Entity entity) {
      if (this.isEnabled() && entity == this.mc.player && entity instanceof PlayerEntity player && player.isFallFlying()) {
         return true;
      }

      return false;
   }
}
