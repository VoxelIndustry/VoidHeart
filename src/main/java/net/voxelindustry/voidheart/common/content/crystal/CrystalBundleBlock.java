package net.voxelindustry.voidheart.common.content.crystal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.ley.LeyNode;
import org.jspecify.annotations.Nullable;

/**
 * A crystal bundle that remembers how it grew, so a habit survives being placed, set in a pylon and broken again.
 * <p>
 * Scoured rubrum set down near a ley node draws the node's taint in once a minute, on a scheduled tick saved with the chunk, until it is full and turns
 * Fouled again. What it holds is kept in its state, and on the item when taken whole.
 */
public class CrystalBundleBlock extends AmethystClusterBlock
{
    public static final EnumProperty<CrystalHabit> HABIT  = EnumProperty.create("habit", CrystalHabit.class);
    public static final IntegerProperty            SOAKED = IntegerProperty.create("soaked", 0, ScouredSoak.MAX);

    public CrystalBundleBlock(float height, float width, Properties properties)
    {
        super(height, width, properties);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var state = super.getStateForPlacement(context);
        if (state == null)
            return null;
        var item = context.getItemInHand();
        // A placed bundle has nowhere to keep a recorded place, so a Deep memoriam holding one stays an item, for an Oculus Table
        if (item.has(net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents.CRYSTAL_RECORDING))
        {
            if (context.getPlayer() != null && !context.getLevel().isClientSide())
                context.getPlayer().sendOverlayMessage(net.minecraft.network.chat.Component.translatable(net.voxelindustry.voidheart.VoidHeart.MODID + ".crystal.recorded.unplaceable"));
            return null;
        }
        return state.setValue(HABIT, CrystalBundles.habitOf(item)).setValue(SOAKED, CrystalBundles.soakOf(item));
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData)
    {
        return CrystalBundles.itemOf(state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston)
    {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (soaks(state) && !level.isClientSide() && !level.getBlockTicks().hasScheduledTick(pos, this))
            level.scheduleTick(pos, this, ScouredSoak.SOAK_TICKS);
    }

    /**
     * Only Scoured rubrum soaks, and a random tick only makes sure its minute keeps coming, in case its scheduled tick was ever lost.
     */
    @Override
    protected boolean isRandomlyTicking(BlockState state)
    {
        return soaks(state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (soaks(state) && !level.getBlockTicks().hasScheduledTick(pos, this))
            level.scheduleTick(pos, this, ScouredSoak.SOAK_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (!soaks(state))
            return;
        var size = CrystalBundles.sizeOf(this);
        var node = nodeNear(level, pos);
        if (size != null && node != null)
        {
            var step = ScouredSoak.step(size, state.getValue(SOAKED), Ley.taint(level, node.id()));
            if (step.taken() > 0)
                Ley.addTaint(level, node.id(), -step.taken());
            var next = step.fouled() ? state.setValue(HABIT, CrystalHabit.FOULED).setValue(SOAKED, 0) : state.setValue(SOAKED, step.soaked());
            if (next != state)
                level.setBlock(pos, next, Block.UPDATE_ALL);
            if (step.fouled())
                return;
        }
        level.scheduleTick(pos, this, ScouredSoak.SOAK_TICKS);
    }

    /**
     * The ley node within reach across the ground, or null. Nodes only exist in the overworld.
     */
    public static @Nullable LeyNode nodeNear(ServerLevel level, BlockPos pos)
    {
        double x = pos.getX() + 0.5, z = pos.getZ() + 0.5;
        return LeyLattice.get(level)
                .flatMap(lattice -> lattice.nearestNode(Mth.floor(x), Mth.floor(z)))
                .filter(node -> node.distanceSq(x, z) <= (double) ScouredSoak.NODE_REACH * ScouredSoak.NODE_REACH)
                .orElse(null);
    }

    public static boolean soaks(BlockState state)
    {
        return state.getBlock() instanceof CrystalBundleBlock bundle && CrystalBundles.kindOfBundle(bundle) == CrystalKind.RUBRUM
                && CrystalBundles.habitOf(state) == CrystalHabit.SCOURED;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder);
        builder.add(HABIT, SOAKED);
    }
}
