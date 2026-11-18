package net.voxelindustry.voidheart.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.blight.BlightCrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The crossbow blight loads faster. A crossbow's load time is worked out in one static place, read by the item, the mobs that use one and the
 * client's pull animation alike, so it is shortened here for all of them.
 */
@Mixin(CrossbowItem.class)
public class MixinCrossbowItemCharge
{
    @ModifyReturnValue(method = "getChargeDuration", at = @At("RETURN"))
    private static int voidheart$blightCharge(int ticks, ItemStack crossbow, LivingEntity user)
    {
        return crossbow.getItem() instanceof BlightCrossbowItem ? BlightCrossbowItem.chargeDuration(ticks) : ticks;
    }
}
