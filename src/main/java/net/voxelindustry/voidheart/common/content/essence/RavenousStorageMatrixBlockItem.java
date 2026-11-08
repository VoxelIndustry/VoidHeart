package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * Places the matrix core one block out from the face clicked, leaving the space between free for the column on that
 * side.
 */
public class RavenousStorageMatrixBlockItem extends RavenousStorageBlockItem
{
    public RavenousStorageMatrixBlockItem(Block block, Item.Properties properties)
    {
        super(block, properties);
    }

    @Override
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext context)
    {
        var face = context.getClickedFace();
        return BlockPlaceContext.at(context, context.getClickedPos().relative(face), face);
    }
}
