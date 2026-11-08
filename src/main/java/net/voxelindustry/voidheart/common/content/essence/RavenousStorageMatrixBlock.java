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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A core holding six aspects, one per face, each with its own column reaching into the block beside it as it fills.
 * The core takes the middle of a cross of seven blocks, so it is placed one block out from the face clicked.
 */
public class RavenousStorageMatrixBlock extends Block implements EntityBlock
{
    public static final EnumProperty<RavenousStorageMatrixPart> PART = EnumProperty.create("part", RavenousStorageMatrixPart.class);
    public static final IntegerProperty                         FILL = IntegerProperty.create("fill", 0, StorageFill.MATRIX_MAX_FILL);

    /** The face each slot of the storage shows on, in slot order. */
    public static final Direction[] FACES = {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    // In pixels, across a column: four pillars around the essence
    private static final int[]  PILLAR_CORNERS = {3, 11};
    private static final int    PILLAR_WIDTH   = 2;
    private static final double CAP_OVERHANG   = 0.5;
    // Columns start on the core body, a pixel inside the core's space
    private static final int    COLUMN_FROM    = -1;

    private static final VoxelShape CORE = Block.box(0, 0, 0, 16, 16, 16);

    /** Each column in its own block, by face and fill, for collision. */
    private static final VoxelShape[][] COLUMNS = new VoxelShape[FACES.length][StorageFill.MATRIX_MAX_FILL + 1];
    /** Each column whole, as seen from the core, by face and fill. */
    private static final VoxelShape[][] COLUMNS_FROM_CORE = new VoxelShape[FACES.length][StorageFill.MATRIX_MAX_FILL + 1];

    private static final int                      ABSENT   = StorageFill.MATRIX_MAX_FILL + 1;
    private static final Map<Integer, VoxelShape> OUTLINES = new ConcurrentHashMap<>();

    static
    {
        for (var face = 0; face < FACES.length; face++)
        {
            for (var fill = 0; fill <= StorageFill.MATRIX_MAX_FILL; fill++)
            {
                var length = StorageFill.matrixColumnLength(fill);
                COLUMNS[face][fill] = column(FACES[face], 0, length);
                var step = FACES[face].getUnitVec3i();
                COLUMNS_FROM_CORE[face][fill] = column(FACES[face], COLUMN_FROM, length).move(step.getX(), step.getY(), step.getZ());
            }
        }
    }

    /**
     * Four pillars and their caps, running from {@code from} to {@code length} pixels out from the core into the block
     * on this face.
     */
    private static VoxelShape column(Direction face, int from, int length)
    {
        var shape = Shapes.empty();
        var capFrom = length - 1;
        for (var u : PILLAR_CORNERS)
        {
            for (var v : PILLAR_CORNERS)
            {
                shape = Shapes.or(shape, oriented(face, u, u + PILLAR_WIDTH, from, capFrom, v, v + PILLAR_WIDTH));
                shape = Shapes.or(shape, oriented(face, u - CAP_OVERHANG, u + PILLAR_WIDTH + CAP_OVERHANG, capFrom, length,
                        v - CAP_OVERHANG, v + PILLAR_WIDTH + CAP_OVERHANG));
            }
        }
        return shape;
    }

    /**
     * A box given across the column and along it, out from the core, placed in the block on that face. The pillars
     * are laid out the same on both axes across, so which one is which does not matter.
     */
    private static VoxelShape oriented(Direction face, double u0, double u1, double out0, double out1, double v0, double v1)
    {
        if (out1 <= out0)
            return Shapes.empty();
        double near = 16 - out1, far = 16 - out0;
        return switch (face)
        {
            case UP -> Block.box(u0, out0, v0, u1, out1, v1);
            case DOWN -> Block.box(u0, near, v0, u1, far, v1);
            case SOUTH -> Block.box(u0, v0, out0, u1, v1, out1);
            case NORTH -> Block.box(u0, v0, near, u1, v1, far);
            case EAST -> Block.box(out0, u0, v0, out1, u1, v1);
            case WEST -> Block.box(near, u0, v0, far, u1, v1);
        };
    }

    public RavenousStorageMatrixBlock(Properties settings)
    {
        super(settings);

        this.registerDefaultState(getStateDefinition().any()
                .setValue(PART, RavenousStorageMatrixPart.BASE)
                .setValue(FILL, 0)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(PART, FILL);
    }

    /**
     * Only the core is placed here, and only with all six blocks around it free for its columns.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var level = context.getLevel();
        var core = context.getClickedPos();
        for (var face : FACES)
        {
            var beside = core.relative(face);
            if (level.isOutsideBuildHeight(beside) || !level.getBlockState(beside).canBeReplaced(context))
                return null;
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(world, pos, state, placer, stack);

        for (var face : FACES)
            world.setBlock(pos.relative(face), defaultBlockState().setValue(PART, RavenousStorageMatrixPart.of(face)), Block.UPDATE_ALL);

        var storage = stack.get(VoidHeartDataComponents.MULTI_ESSENCE_STORAGE);
        if (world.getBlockEntity(pos) instanceof RavenousStorageMatrixTile tile)
        {
            if (storage != null)
                tile.restoreStorage(storage);
            else
                tile.refreshColumns();
        }
    }

    /**
     * A column falls with its core. A core left without its columns, from before they existed, keeps its essence.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random)
    {
        var part = state.getValue(PART);
        if (part != RavenousStorageMatrixPart.BASE && direction == part.face().getOpposite() && !isCore(neighbourState))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    /**
     * Breaking a column breaks the core, which drops the matrix with its essence.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        var part = state.getValue(PART);
        if (!level.isClientSide() && part != RavenousStorageMatrixPart.BASE)
        {
            var core = pos.relative(part.face().getOpposite());
            var coreState = level.getBlockState(core);
            if (isCore(coreState))
                level.destroyBlock(core, !player.preventsBlockDrops() && player.hasCorrectToolForDrops(coreState), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Keeps the stored essence on the dropped item, an empty matrix drops a plain item so it stacks.
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        if (state.getValue(PART) != RavenousStorageMatrixPart.BASE)
            return List.of();

        var stack = new ItemStack(this);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RavenousStorageMatrixTile matrix && !matrix.storage().isEmpty())
            stack.set(VoidHeartDataComponents.MULTI_ESSENCE_STORAGE, matrix.storage().copy());
        return List.of(stack);
    }

    /**
     * Any of its seven blocks outlines the whole matrix, each column as far as it reaches.
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        var part = state.getValue(PART);
        if (part == RavenousStorageMatrixPart.BASE)
            return outline(world, pos);

        var core = pos.relative(part.face().getOpposite());
        if (!isCore(world.getBlockState(core)))
            return COLUMNS[part.ordinal() - 1][state.getValue(FILL)];
        var step = part.face().getUnitVec3i();
        return outline(world, core).move(-step.getX(), -step.getY(), -step.getZ());
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        var part = state.getValue(PART);
        return part == RavenousStorageMatrixPart.BASE ? CORE : COLUMNS[part.ordinal() - 1][state.getValue(FILL)];
    }

    private VoxelShape outline(BlockGetter world, BlockPos core)
    {
        int key = 0;
        for (var face = FACES.length - 1; face >= 0; face--)
        {
            var beside = world.getBlockState(core.relative(FACES[face]));
            int fill = isColumn(beside, FACES[face]) ? beside.getValue(FILL) : ABSENT;
            key = key * (ABSENT + 1) + fill;
        }
        return OUTLINES.computeIfAbsent(key, fills ->
        {
            var shape = CORE;
            for (var face = 0; face < FACES.length; face++)
            {
                int fill = fills % (ABSENT + 1);
                fills /= ABSENT + 1;
                if (fill != ABSENT)
                    shape = Shapes.or(shape, COLUMNS_FROM_CORE[face][fill]);
            }
            return shape;
        });
    }

    private boolean isCore(BlockState state)
    {
        return state.is(this) && state.getValue(PART) == RavenousStorageMatrixPart.BASE;
    }

    private boolean isColumn(BlockState state, Direction face)
    {
        return state.is(this) && state.getValue(PART) == RavenousStorageMatrixPart.of(face);
    }

    /**
     * Whether a block is one of the columns around this core, which relays see through to reach it.
     */
    public static boolean isColumnOf(BlockState state, BlockPos pos, BlockPos core)
    {
        if (!(state.getBlock() instanceof RavenousStorageMatrixBlock) || state.getValue(PART) == RavenousStorageMatrixPart.BASE)
            return false;
        return pos.relative(state.getValue(PART).face().getOpposite()).equals(core);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return state.getValue(PART) == RavenousStorageMatrixPart.BASE ? new RavenousStorageMatrixTile(pos, state) : null;
    }

    /**
     * The core, then one column per face in the order of {@link #FACES}.
     */
    public enum RavenousStorageMatrixPart implements StringRepresentable
    {
        BASE("base", null),
        PILLAR_TOP("pillar_top", Direction.UP),
        PILLAR_BOTTOM("pillar_bottom", Direction.DOWN),
        PILLAR_NORTH("pillar_north", Direction.NORTH),
        PILLAR_SOUTH("pillar_south", Direction.SOUTH),
        PILLAR_EAST("pillar_east", Direction.EAST),
        PILLAR_WEST("pillar_west", Direction.WEST);

        private final String    name;
        private final Direction face;

        RavenousStorageMatrixPart(String name, Direction face)
        {
            this.name = name;
            this.face = face;
        }

        /**
         * Which side of the core this column stands on.
         */
        public Direction face()
        {
            return face;
        }

        public static RavenousStorageMatrixPart of(Direction face)
        {
            for (var part : values())
            {
                if (part.face == face)
                    return part;
            }
            throw new IllegalArgumentException("No column on " + face);
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
