package net.voxelindustry.voidheart.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.voxelindustry.voidheart.common.content.armor.ArmorHotbar;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

// A Void Armor wearer's hotbar and off hand are the frame's, so no menu takes from them or puts in them
@Mixin(Slot.class)
public abstract class MixinSlotVoidArmor
{
    @Shadow
    @Final
    public Container container;

    @Shadow
    public abstract int getContainerSlot();

    @ModifyReturnValue(method = "mayPickup", at = @At("RETURN"))
    private boolean voidheart$keepRacks(boolean may)
    {
        return may && !voidheart$frames();
    }

    @ModifyReturnValue(method = "mayPlace", at = @At("RETURN"))
    private boolean voidheart$fillNoRacks(boolean may)
    {
        return may && !voidheart$frames();
    }

    @Unique
    private boolean voidheart$frames()
    {
        return container instanceof Inventory inventory && ArmorHotbar.locked(inventory.player, getContainerSlot());
    }
}
