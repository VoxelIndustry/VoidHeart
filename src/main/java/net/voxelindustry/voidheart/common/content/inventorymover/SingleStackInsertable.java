package net.voxelindustry.voidheart.common.content.inventorymover;

import net.minecraft.item.ItemStack;

public interface SingleStackInsertable
{
    ItemStack getStack();

    void setStack(ItemStack stack);
}
