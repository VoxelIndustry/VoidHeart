package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;

/**
 * Links the existing crystal bundle blocks to the socket bundles they become.
 */
public final class ConstructBundles
{
    private ConstructBundles()
    {
    }

    public static Block blockOf(SocketBundle bundle)
    {
        return switch (bundle)
        {
            case SMALL_RUBRUM -> VoidHeartBlocks.SMALL_RUBRUM_BUNDLE;
            case MEDIUM_RUBRUM -> VoidHeartBlocks.MEDIUM_RUBRUM_BUNDLE;
            case LARGE_RUBRUM -> VoidHeartBlocks.LARGE_RUBRUM_BUNDLE;
            case SMALL_AZURIUM -> VoidHeartBlocks.SMALL_AZURIUM_BUNDLE;
            case MEDIUM_AZURIUM -> VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE;
            case LARGE_AZURIUM -> VoidHeartBlocks.LARGE_AZURIUM_BUNDLE;
        };
    }

    public static @Nullable SocketBundle bundleOf(Block block)
    {
        for (var bundle : SocketBundle.values())
        {
            if (blockOf(bundle) == block)
                return bundle;
        }
        return null;
    }

    public static @Nullable SocketBundle bundleOf(ItemStack stack)
    {
        return stack.getItem() instanceof BlockItem blockItem ? bundleOf(blockItem.getBlock()) : null;
    }

    /**
     * The bundle's block pointing the given way, as a socket draws it.
     */
    public static BlockState stateOf(SocketBundle bundle, Direction pointing)
    {
        return blockOf(bundle).defaultBlockState().setValue(AmethystClusterBlock.FACING, pointing);
    }
}
