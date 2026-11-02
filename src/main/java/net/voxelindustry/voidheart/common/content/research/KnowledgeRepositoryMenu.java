package net.voxelindustry.voidheart.common.content.research;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartMenus;

/**
 * Holds no slot: its only use is telling the server who is looking at a repository, and for how long.
 */
public class KnowledgeRepositoryMenu extends AbstractContainerMenu
{
    @Getter
    private final BlockPos pos;

    public KnowledgeRepositoryMenu(int containerId, Inventory inventory, BlockPos pos)
    {
        super(VoidHeartMenus.KNOWLEDGE_REPOSITORY, containerId);
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
        return player.level().getBlockState(pos).is(VoidHeartBlocks.KNOWLEDGE_REPOSITORY)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
