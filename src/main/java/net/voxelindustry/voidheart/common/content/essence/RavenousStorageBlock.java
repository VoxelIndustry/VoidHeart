package net.voxelindustry.voidheart.common.content.essence;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Two blocks high: a base holding the essence, and four pillars rising out of it into the block above as it fills.
 */
public class RavenousStorageBlock extends Block implements EntityBlock
{
    public static final EnumProperty<RavenousStoragePart> PART = EnumProperty.create("part", RavenousStoragePart.class);
    public static final int                               MAX_FILL = StorageFill.MAX_FILL;
    public static final IntegerProperty                   FILL = IntegerProperty.create("fill", 0, MAX_FILL);

    private static final int[]        PILLAR_CORNERS = {1, 12};
    private static final int          PILLAR_WIDTH   = 3;
    // Caps are a pixel tall and overhang their pillar by half a pixel, as in the block models
    private static final double       CAP_OVERHANG   = 0.5;
    private static final int          PILLARS_FROM   = 12;
    private static final VoxelShape   PLINTH         = Block.box(0, 0, 0, 16, PILLARS_FROM, 16);
    private static final VoxelShape[] BASE_SHAPES    = new VoxelShape[MAX_FILL + 1];
    private static final VoxelShape[] PILLAR_SHAPES  = new VoxelShape[MAX_FILL + 1];
    private static final VoxelShape[] BASE_OUTLINES   = new VoxelShape[MAX_FILL + 1];
    private static final VoxelShape[] PILLAR_OUTLINES = new VoxelShape[MAX_FILL + 1];

    /**
     * Each half collides as its share of the pillars, caps included, so it follows them as they rise. Either half
     * selects the whole storage, the outline reaching into the other block.
     */
    static
    {
        for (var fill = 0; fill <= MAX_FILL; fill++)
        {
            var top = StorageFill.pillarTop(fill);
            BASE_SHAPES[fill] = Shapes.or(PLINTH, pillars(PILLARS_FROM, top, 0));
            PILLAR_SHAPES[fill] = pillars(PILLARS_FROM, top, 16);
            BASE_OUTLINES[fill] = Shapes.or(BASE_SHAPES[fill], PILLAR_SHAPES[fill].move(0, 1, 0));
            PILLAR_OUTLINES[fill] = Shapes.or(BASE_SHAPES[fill].move(0, -1, 0), PILLAR_SHAPES[fill]);
        }
    }

    /**
     * Pillars rising from {@code from} to {@code top} pixels above the base bottom, cut to the block starting
     * {@code offset} pixels up.
     */
    private static VoxelShape pillars(int from, int top, int offset)
    {
        var shape = Shapes.empty();
        var capFrom = top - 1;
        for (var x : PILLAR_CORNERS)
        {
            for (var z : PILLAR_CORNERS)
            {
                shape = Shapes.or(shape, slice(x, x + PILLAR_WIDTH, from, capFrom, z, z + PILLAR_WIDTH, offset));
                shape = Shapes.or(shape, slice(x - CAP_OVERHANG, x + PILLAR_WIDTH + CAP_OVERHANG, capFrom, top, z - CAP_OVERHANG, z + PILLAR_WIDTH + CAP_OVERHANG, offset));
            }
        }
        return shape;
    }

    private static VoxelShape slice(double x0, double x1, double y0, double y1, double z0, double z1, int offset)
    {
        var from = Math.max(y0 - offset, 0);
        var to = Math.min(y1 - offset, 16);
        return to > from ? Block.box(x0, from, z0, x1, to, z1) : Shapes.empty();
    }

    public RavenousStorageBlock(Properties settings)
    {
        super(settings);

        this.registerDefaultState(getStateDefinition().any()
                .setValue(BlockStateProperties.FACING, Direction.UP)
                .setValue(PART, RavenousStoragePart.BASE)
                .setValue(FILL, 0)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.FACING, PART, FILL);
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
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(world, pos, state, placer, stack);

        world.setBlock(pos.above(), state.setValue(PART, RavenousStoragePart.PILLAR), Block.UPDATE_ALL);

        var storage = stack.get(VoidHeartDataComponents.ESSENCE_STORAGE);
        if (storage != null && world.getBlockEntity(pos) instanceof RavenousStorageTile tile)
            tile.restoreStorage(storage);
    }

    /**
     * The pillars fall with the base. A base left without them, from before storages stood two blocks high, keeps its
     * essence.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random)
    {
        if (state.getValue(PART) == RavenousStoragePart.PILLAR && direction == Direction.DOWN
                && !(neighbourState.is(this) && neighbourState.getValue(PART) == RavenousStoragePart.BASE))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    /**
     * Breaking the pillars breaks the base, which drops the storage with its essence.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        var below = pos.below();
        var baseState = level.getBlockState(below);
        if (!level.isClientSide() && state.getValue(PART) == RavenousStoragePart.PILLAR
                && baseState.is(this) && baseState.getValue(PART) == RavenousStoragePart.BASE)
            level.destroyBlock(below, !player.preventsBlockDrops() && player.hasCorrectToolForDrops(baseState), player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Keeps the stored essence on the dropped item, an empty storage drops a plain item so it stacks.
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        if (state.getValue(PART) == RavenousStoragePart.PILLAR)
            return List.of();

        var stack = new ItemStack(this);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RavenousStorageTile storage
                && !storage.storage().isUnattuned() && storage.storage().amount() > 0)
            stack.set(VoidHeartDataComponents.ESSENCE_STORAGE, storage.storage().copy());
        return List.of(stack);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        var fill = state.getValue(FILL);
        if (state.getValue(PART) == RavenousStoragePart.PILLAR)
            return PILLAR_OUTLINES[fill];
        // A base from before storages stood two blocks high has no pillars above to outline
        return isPillarOf(world.getBlockState(pos.above())) ? BASE_OUTLINES[fill] : BASE_SHAPES[fill];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        var fill = state.getValue(FILL);
        return state.getValue(PART) == RavenousStoragePart.PILLAR ? PILLAR_SHAPES[fill] : BASE_SHAPES[fill];
    }

    private boolean isPillarOf(BlockState state)
    {
        return state.is(this) && state.getValue(PART) == RavenousStoragePart.PILLAR;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return state.getValue(PART) == RavenousStoragePart.BASE ? new RavenousStorageTile(pos, state) : null;
    }

    public enum RavenousStoragePart implements StringRepresentable
    {
        BASE("base"),
        PILLAR("pillar");

        private final String name;

        RavenousStoragePart(String name)
        {
            this.name = name;
        }

        public String toString()
        {
            return this.name;
        }

        public String getSerializedName()
        {
            return this.name;
        }
    }
}
