package net.voxelindustry.voidheart.common.content.armillary;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartMenus;

/**
 * Holds no slot: it tells the server which armillary a player is looking into, so that actions never have to name one.
 */
public class ArmillaryMenu extends AbstractContainerMenu
{
    private static final double REACH_SQUARED = 100;

    @Getter
    private final BlockPos pos;

    public ArmillaryMenu(int containerId, Inventory inventory, BlockPos pos)
    {
        super(VoidHeartMenus.ARMILLARY, containerId);
        this.pos = pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return player.level().getBlockState(pos).is(VoidHeartBlocks.ARMILLARY_CORE)
               && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH_SQUARED;
    }
}
