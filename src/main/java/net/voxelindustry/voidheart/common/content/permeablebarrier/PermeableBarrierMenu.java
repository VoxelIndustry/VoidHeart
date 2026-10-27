package net.voxelindustry.voidheart.common.content.permeablebarrier;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartMenus;

/**
 * Only holds the player inventory: the appearance slot is a ghost drawn by the screen, nothing is stored.
 */
public class PermeableBarrierMenu extends AbstractContainerMenu
{
    public static final int INVENTORY_TOP = 124;

    @Getter
    private final BlockPos pos;

    public PermeableBarrierMenu(int containerId, Inventory inventory, BlockPos pos)
    {
        super(VoidHeartMenus.PERMEABLE_BARRIER, containerId);
        this.pos = pos;

        addStandardInventorySlots(inventory, 8, INVENTORY_TOP);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return player.level().getBlockState(pos).is(VoidHeartBlocks.PERMEABLE_BARRIER)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
