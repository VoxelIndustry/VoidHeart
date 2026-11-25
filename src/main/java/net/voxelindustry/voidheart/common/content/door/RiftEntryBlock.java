package net.voxelindustry.voidheart.common.content.door;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The diaphane door a Rift Key raises out of thin air, always open onto a void door of its owner's pocket. It hangs where it was raised without support, can't be shut or broken, and collapses when its rift closes.
 * Its lower half checks now and then that its rift still stands for it, so an entry left in an unloaded chunk when the rift closed collapses once it loads.
 */
public class RiftEntryBlock extends DoorBlock
{
    private static final int CHECK_TICKS = 20;
    private static final float COLLAPSE_VOLUME = 0.8F;
    private static final float COLLAPSE_PITCH = 1.4F;

    public RiftEntryBlock(Properties properties)
    {
        super(BlockSetType.STONE, properties);
    }

    public static BlockState lower(Block block, net.minecraft.core.Direction facing)
    {
        return block.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(OPEN, true)
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    /**
     * Removes both halves of the entry whose lower half is at this position, if it is one.
     */
    public static void collapse(Level level, BlockPos lower)
    {
        var state = level.getBlockState(lower);
        if (!(state.getBlock() instanceof RiftEntryBlock) || state.getValue(HALF) != DoubleBlockHalf.LOWER)
            return;

        level.removeBlock(lower.above(), false);
        level.removeBlock(lower, false);
        level.playSound(null, lower, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, COLLAPSE_VOLUME, COLLAPSE_PITCH);
        if (level instanceof ServerLevel serverLevel)
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, lower.getX() + 0.5, lower.getY() + 1, lower.getZ() + 0.5, 40, 0.25, 0.6, 0.25, 0.05);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER)
            return level.getBlockState(pos.below()).is(this);
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        return InteractionResult.PASS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston)
    {
        // Redstone would close it, and only its pocket door may
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston)
    {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER && !oldState.is(this))
            level.scheduleTick(pos, this, CHECK_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER)
            return;

        if (RiftPairs.get(level.getServer()).doorOf(GlobalPos.of(level.dimension(), pos)).isEmpty())
            collapse(level, pos);
        else
            level.scheduleTick(pos, this, CHECK_TICKS);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER || random.nextInt(2) != 0)
            return;

        level.addParticle(ParticleTypes.REVERSE_PORTAL,
                pos.getX() + random.nextDouble(),
                pos.getY() + random.nextDouble() * 2,
                pos.getZ() + random.nextDouble(),
                0, 0.02, 0);
    }
}
