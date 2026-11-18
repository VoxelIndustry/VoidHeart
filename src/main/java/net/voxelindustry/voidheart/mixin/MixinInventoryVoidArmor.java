package net.voxelindustry.voidheart.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.armor.ArmorHotbar;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// While the Void Armor is worn, its racks are the hotbar: the selection stays on them, and what the wearer picks up goes past the hotbar into the inventory
@Mixin(Inventory.class)
public abstract class MixinInventoryVoidArmor
{
    @Shadow
    @Final
    public Player player;

    @Shadow
    public abstract net.minecraft.core.NonNullList<ItemStack> getNonEquipmentItems();

    @Shadow
    private boolean hasRemainingSpaceForItem(ItemStack slotItemStack, ItemStack newItemStack)
    {
        throw new AssertionError();
    }

    @Inject(method = "setSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void voidheart$racksOnly(int selected, CallbackInfo info)
    {
        if (selected >= ArmorHotbar.RACKS && ArmorHotbar.locked(player, selected))
            info.cancel();
    }

    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void voidheart$freePastHotbar(CallbackInfoReturnable<Integer> info)
    {
        if (!ArmorWear.wearing(player))
            return;
        var items = getNonEquipmentItems();
        for (int i = Inventory.getSelectionSize(); i < items.size(); i++)
            if (items.get(i).isEmpty())
            {
                info.setReturnValue(i);
                return;
            }
        info.setReturnValue(-1);
    }

    @Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
    private void voidheart$roomPastHotbar(ItemStack added, CallbackInfoReturnable<Integer> info)
    {
        if (!ArmorWear.wearing(player))
            return;
        var items = getNonEquipmentItems();
        for (int i = Inventory.getSelectionSize(); i < items.size(); i++)
            if (hasRemainingSpaceForItem(items.get(i), added))
            {
                info.setReturnValue(i);
                return;
            }
        info.setReturnValue(-1);
    }
}
