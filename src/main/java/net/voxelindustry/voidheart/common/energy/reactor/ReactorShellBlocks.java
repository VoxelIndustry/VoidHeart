package net.voxelindustry.voidheart.common.energy.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * What every block of a reactor does when used or changed: an empty hand acts on the core, and any change asks the core to look at its shell again.
 */
final class ReactorShellBlocks
{
    private ReactorShellBlocks()
    {
    }

    /**
     * An item in hand passes, so blocks can still be placed against the shell.
     */
    static InteractionResult useItemOn(ItemStack stack)
    {
        return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
    }

    static InteractionResult useWithoutItem(Level level, BlockPos pos, Player player)
    {
        var core = level.getBlockEntity(pos) instanceof ContravoidReactorTile tile ? tile : ContravoidReactorTile.coreAround(level, pos);
        if (core == null)
            return InteractionResult.PASS;
        if (!level.isClientSide())
            core.use(player);
        return InteractionResult.SUCCESS;
    }

    static void changed(Level level, BlockPos pos)
    {
        if (level.isClientSide())
            return;
        for (var dx = -1; dx <= 1; dx++)
        {
            for (var dy = -1; dy <= 1; dy++)
            {
                for (var dz = -1; dz <= 1; dz++)
                {
                    if (level.getBlockEntity(pos.offset(dx, dy, dz)) instanceof ContravoidReactorTile core)
                        core.requestCheck();
                }
            }
        }
    }
}
