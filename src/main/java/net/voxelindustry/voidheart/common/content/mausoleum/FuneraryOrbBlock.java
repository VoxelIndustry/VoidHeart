package net.voxelindustry.voidheart.common.content.mausoleum;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.VoidHeart;
import org.jspecify.annotations.Nullable;

/**
 * A ball of green funerary fire floating free in a mausoleum, never going out. Its renderer draws it bobbing in the air, so the block itself has
 * no model and nothing to bump into.
 */
public class FuneraryOrbBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = box(4, 4, 4, 12, 12, 12);
    /** One tick in this many, on average, gives off a flame rising off the top of the ball. */
    private static final int FLAME_EVERY = 3;

    public FuneraryOrbBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (random.nextInt(FLAME_EVERY) != 0)
            return;
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.35;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.35;
        level.addParticle(VoidHeart.FUNERARY_FLAME_PARTICLE, x, pos.getY() + 0.85, z, 0, 0.025 + random.nextDouble() * 0.02, 0);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new FuneraryOrbTile(pos, state);
    }
}
