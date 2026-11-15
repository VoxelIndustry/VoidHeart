package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A ventricle basin: a stone bowl whose essence the chamber draws from the top.
 */
public class VentricleBasinBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = Shapes.join(Shapes.block(), Block.box(2, 4, 2, 14, 16, 14), (a, b) -> a && !b);

    public VentricleBasinBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new VentricleBasinTile(pos, state);
    }
}
