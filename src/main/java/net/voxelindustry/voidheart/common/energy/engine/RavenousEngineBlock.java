package net.voxelindustry.voidheart.common.energy.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * Two blocks high: a maw holding the essence and burning it, and an open chamber above where the burning essence is pulled down into it.
 */
public class RavenousEngineBlock extends Block implements EntityBlock
{
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private static final int[] POST_CORNERS = {1, 12};
    private static final int   POST_WIDTH   = 3;

    private static final VoxelShape MAW_SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 3, 16),
            Block.box(1, 3, 1, 15, 13, 15),
            Block.box(0, 13, 0, 16, 15, 16),
            posts(15, 16));
    private static final VoxelShape CHAMBER_SHAPE = Shapes.or(posts(0, 15), caps(15, 16));
    private static final VoxelShape MAW_OUTLINE     = Shapes.or(MAW_SHAPE, CHAMBER_SHAPE.move(0, 1, 0));
    private static final VoxelShape CHAMBER_OUTLINE = Shapes.or(MAW_SHAPE.move(0, -1, 0), CHAMBER_SHAPE);

    private static VoxelShape posts(double from, double to)
    {
        var shape = Shapes.empty();
        for (var x : POST_CORNERS)
        {
            for (var z : POST_CORNERS)
                shape = Shapes.or(shape, Block.box(x, from, z, x + POST_WIDTH, to, z + POST_WIDTH));
        }
        return shape;
    }

    // A pixel tall, overhanging their post by half a pixel, as in the model
    private static VoxelShape caps(double from, double to)
    {
        var shape = Shapes.empty();
        for (var x : POST_CORNERS)
        {
            for (var z : POST_CORNERS)
                shape = Shapes.or(shape, Block.box(x - 0.5, from, z - 0.5, x + POST_WIDTH + 0.5, to, z + POST_WIDTH + 0.5));
        }
        return shape;
    }

    public RavenousEngineBlock(Properties properties)
    {
        super(properties);

        registerDefaultState(getStateDefinition().any().setValue(PART, Part.MAW));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(PART);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var above = context.getClickedPos().above();
        var level = context.getLevel();
        if (above.getY() > level.getMaxY() || !level.getBlockState(above).canBeReplaced(context))
            return null;
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);

        level.setBlock(pos.above(), state.setValue(PART, Part.CHAMBER), Block.UPDATE_ALL);

        var tank = stack.get(VoidHeartDataComponents.MULTI_ESSENCE_STORAGE);
        if (tank != null && level.getBlockEntity(pos) instanceof RavenousEngineTile engine)
            engine.restoreTank(tank);
    }

    /**
     * The chamber falls with the maw. A maw left without its chamber still burns.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random)
    {
        if (state.getValue(PART) == Part.CHAMBER && direction == Direction.DOWN && !isMaw(neighbourState))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    /**
     * Breaking the chamber breaks the maw, which drops the engine with the essence it held.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        var below = pos.below();
        var mawState = level.getBlockState(below);
        if (!level.isClientSide() && state.getValue(PART) == Part.CHAMBER && isMaw(mawState))
            level.destroyBlock(below, !player.preventsBlockDrops() && player.hasCorrectToolForDrops(mawState), player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Keeps the essence on the dropped item. An empty engine drops a plain item so it stacks.
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        if (state.getValue(PART) == Part.CHAMBER)
            return List.of();

        var stack = new ItemStack(this);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RavenousEngineTile engine && !engine.tank().isEmpty())
            stack.set(VoidHeartDataComponents.MULTI_ESSENCE_STORAGE, engine.tank().copy());
        return List.of(stack);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        if (state.getValue(PART) == Part.CHAMBER)
            return CHAMBER_OUTLINE;
        return isChamber(level.getBlockState(pos.above())) ? MAW_OUTLINE : MAW_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return state.getValue(PART) == Part.CHAMBER ? CHAMBER_SHAPE : MAW_SHAPE;
    }

    private boolean isMaw(BlockState state)
    {
        return state.is(this) && state.getValue(PART) == Part.MAW;
    }

    private boolean isChamber(BlockState state)
    {
        return state.is(this) && state.getValue(PART) == Part.CHAMBER;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return state.getValue(PART) == Part.MAW ? new RavenousEngineTile(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return state.getValue(PART) == Part.MAW ? createTickerHelper(type, RavenousEngineContent.RAVENOUS_ENGINE_TILE, RavenousEngineTile::tick) : null;
    }

    public enum Part implements StringRepresentable
    {
        MAW("maw"),
        CHAMBER("chamber");

        private final String name;

        Part(String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }

        @Override
        public String getSerializedName()
        {
            return name;
        }
    }
}
