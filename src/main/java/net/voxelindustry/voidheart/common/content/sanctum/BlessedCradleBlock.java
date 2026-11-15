package net.voxelindustry.voidheart.common.content.sanctum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A gilt cradle on a column's capital. Whatever it holds floats over it, and a hand takes it.
 */
public class BlessedCradleBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = Shapes.or(box(1, 0, 1, 15, 4, 15), box(3, 4, 3, 13, 7, 13));

    public BlessedCradleBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof BlessedCradleTile cradle) || cradle.getContents().isEmpty())
            return InteractionResult.PASS;
        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        var taken = cradle.take();
        if (!player.getInventory().add(taken))
            Block.popResource(level, pos.above(), taken);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1, 0.6F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (!(level.getBlockEntity(pos) instanceof BlessedCradleTile cradle) || cradle.getContents().isEmpty() || random.nextInt(3) != 0)
            return;
        level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 0.9, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                0, 0.02, 0);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new BlessedCradleTile(pos, state);
    }
}
