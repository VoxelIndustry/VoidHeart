package net.voxelindustry.voidheart.common.content.inventorymover;

import net.minecraft.world.item.ItemStack;

public interface SingleStackExtractable
{
    ItemStack getStack();

    void setStack(ItemStack stack);
}
