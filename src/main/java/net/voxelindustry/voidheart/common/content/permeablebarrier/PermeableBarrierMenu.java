package net.voxelindustry.voidheart.common.content.permeablebarrier;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartMenus;

import java.util.Optional;

/**
 * Only holds the player inventory: the appearance slot is a ghost drawn by the screen, nothing is stored.
 */
public class PermeableBarrierMenu extends AbstractContainerMenu
{
    public static final int INVENTORY_TOP = 90;

    @Getter
    private final BlockPos pos;

    public PermeableBarrierMenu(int containerId, Inventory inventory, BlockPos pos)
    {
        super(VoidHeartMenus.PERMEABLE_BARRIER, containerId);
        this.pos = pos;

        addStandardInventorySlots(inventory, 8, INVENTORY_TOP);
    }

    /**
     * Picks the block of the given stack as the barrier look, leaving the stack untouched.
     */
    public void trySetAppearance(Player player, ItemStack stack)
    {
        if (player.level().isClientSide() || !(stack.getItem() instanceof BlockItem blockItem))
            return;

        var appearance = blockItem.getBlock().defaultBlockState();
        if (appearance.getRenderShape() != RenderShape.MODEL)
            return;

        var tile = PermeableBarrierStructure.getTile(player.level(), pos);
        if (tile != null && tile.getConfig().isAlly(player))
            PermeableBarrierStructure.updateConfig(player.level(), pos, config -> config.withAppearance(Optional.of(appearance)));
    }

    /**
     * Shift-clicking an inventory block picks it as the look instead of moving it.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex)
    {
        if (slotIndex >= 0 && slotIndex < slots.size())
            trySetAppearance(player, slots.get(slotIndex).getItem());
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return player.level().getBlockState(pos).is(VoidHeartBlocks.PERMEABLE_BARRIER)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
