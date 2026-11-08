package net.voxelindustry.voidheart.common.content.research;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * Up to four half-width columns sharing a block space, one per quarter. Each one near a repository lets it hold more.
 * The columns float, so they are drawn by {@link KnowledgeColumnTile}'s renderer rather than the chunk.
 */
public class KnowledgeColumnBlock extends Block implements EntityBlock
{
    public static final BooleanProperty NORTH_WEST = BooleanProperty.create("north_west");
    public static final BooleanProperty NORTH_EAST = BooleanProperty.create("north_east");
    public static final BooleanProperty SOUTH_WEST = BooleanProperty.create("south_west");
    public static final BooleanProperty SOUTH_EAST = BooleanProperty.create("south_east");

    /** Indexed by {@link KnowledgeMath#columnQuarter}. */
    private static final BooleanProperty[] QUARTERS = { NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST };

    /** Full quarters of the space, for collision: the columns float and bob, and walking on them stays steady. */
    private static final VoxelShape[] SHAPES = new VoxelShape[1 << QUARTERS.length];

    // How high a column linked to a repository floats at rest, before its wave moves it
    public static final float COLUMN_BOTTOM      = 2 / 16F;
    public static final float COLUMN_WAVE_HEIGHT = 1.5F / 16;
    public static final float COLUMN_HEIGHT_STEP = 2 / 16F;

    private static final int COLUMN_LEVELS = 5;

    /** Selection shapes, each column where it floats at rest, keyed by {@link #outlineKey}. */
    private static final Map<Integer, VoxelShape> OUTLINES = new ConcurrentHashMap<>();

    // Into the face hit, so that a hit on the side between two columns tells which one it was
    private static final double HIT_NUDGE = 1.0E-3;

    static
    {
        for (int mask = 0; mask < SHAPES.length; mask++)
        {
            var shape = Shapes.empty();
            for (int quarter = 0; quarter < QUARTERS.length; quarter++)
            {
                if ((mask & 1 << quarter) == 0)
                    continue;
                double x = (quarter & 1) * 8;
                double z = (quarter >> 1) * 8;
                shape = Shapes.or(shape, box(x, 0, z, x + 8, 16, z + 8));
            }
            SHAPES[mask] = shape;
        }
    }

    // How far past the clicked face the new column goes, to tell which space and quarter that face looks into
    private static final double CLICK_NUDGE = 0.01;

    private static final float MOTE_LOWEST     = 0.5F;
    private static final float MOTE_HEIGHT_ADD = 0.8F;

    public KnowledgeColumnBlock(Properties properties)
    {
        super(properties
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(2F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE)
                .noOcclusion());
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH_WEST, true)
                .setValue(NORTH_EAST, false)
                .setValue(SOUTH_WEST, false)
                .setValue(SOUTH_EAST, false));
    }

    public static int maskOf(BlockState state)
    {
        int mask = 0;
        for (int quarter = 0; quarter < QUARTERS.length; quarter++)
            if (state.getValue(QUARTERS[quarter]))
                mask |= 1 << quarter;
        return mask;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(QUARTERS);
    }

    private static int targetQuarter(BlockPlaceContext context)
    {
        var click = context.getClickLocation();
        var pos = context.getClickedPos();
        var normal = context.getClickedFace().getUnitVec3();
        return KnowledgeMath.columnQuarter(click.x + normal.x * CLICK_NUDGE - pos.getX(), click.z + normal.z * CLICK_NUDGE - pos.getZ());
    }

    /**
     * Only true when the face clicked looks into this very space, so that clicking the top of a column stacks one above it instead of filling a neighboring quarter.
     */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context)
    {
        if (!context.getItemInHand().is(asItem()))
            return false;

        var click = context.getClickLocation();
        var normal = context.getClickedFace().getUnitVec3();
        var target = BlockPos.containing(click.x + normal.x * CLICK_NUDGE, click.y + normal.y * CLICK_NUDGE, click.z + normal.z * CLICK_NUDGE);
        return target.equals(context.getClickedPos()) && !state.getValue(QUARTERS[targetQuarter(context)]);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var existing = context.getLevel().getBlockState(context.getClickedPos());
        var base = existing.is(this) ? existing : defaultBlockState().setValue(NORTH_WEST, false);
        return base.setValue(QUARTERS[targetQuarter(context)], true);
    }

    /**
     * Each column where it floats at rest, reaching into the space above, so that it is picked where it is seen. The
     * outline then shows only the column hovered, following its wave.
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        var mask = maskOf(state);
        var active = world.getBlockEntity(pos) instanceof KnowledgeColumnTile tile && tile.isActive();
        return OUTLINES.computeIfAbsent(outlineKey(mask, active, pos), key ->
        {
            var shape = Shapes.empty();
            for (int quarter = 0; quarter < QUARTERS.length; quarter++)
            {
                if ((mask & 1 << quarter) != 0)
                    shape = Shapes.or(shape, columnShape(quarter, active ? restingRise(pos, quarter) : 0));
            }
            return shape;
        });
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return SHAPES[maskOf(state)];
    }

    private static int outlineKey(int mask, boolean active, BlockPos pos)
    {
        int levels = 0;
        for (int quarter = QUARTERS.length - 1; quarter >= 0; quarter--)
            levels = levels * COLUMN_LEVELS + heightLevel(pos, quarter);
        return mask | (active ? 1 << QUARTERS.length : 0) | levels << QUARTERS.length + 1;
    }

    private static int heightLevel(BlockPos pos, int quarter)
    {
        return KnowledgeMath.columnHeightLevel(pos.getX() * 2 + (quarter & 1), pos.getZ() * 2 + (quarter >> 1));
    }

    /** How high above its space a linked column floats when its wave is at rest. */
    public static float restingRise(BlockPos pos, int quarter)
    {
        return COLUMN_BOTTOM + COLUMN_WAVE_HEIGHT + heightLevel(pos, quarter) * COLUMN_HEIGHT_STEP;
    }

    /** A column one block tall, raised by rise blocks. */
    public static VoxelShape columnShape(int quarter, double rise)
    {
        double x = (quarter & 1) * 8;
        double z = (quarter >> 1) * 8;
        return box(x, rise * 16, z, x + 8, rise * 16 + 16, z + 8);
    }

    /**
     * The column of a space a hit landed on, or the nearest one there when the hit falls between them.
     */
    public static int quarterHit(BlockState state, BlockPos pos, Vec3 location, Direction face)
    {
        var inside = location.subtract(face.getUnitVec3().scale(HIT_NUDGE));
        int quarter = KnowledgeMath.columnQuarter(inside.x - pos.getX(), inside.z - pos.getZ());
        int mask = maskOf(state);
        if ((mask & 1 << quarter) != 0)
            return quarter;

        int nearest = quarter;
        double best = Double.MAX_VALUE;
        for (int candidate = 0; candidate < QUARTERS.length; candidate++)
        {
            if ((mask & 1 << candidate) == 0)
                continue;
            double dx = pos.getX() + KnowledgeMath.columnCenterX(candidate) - inside.x;
            double dz = pos.getZ() + KnowledgeMath.columnCenterZ(candidate) - inside.z;
            if (dx * dx + dz * dz < best)
            {
                best = dx * dx + dz * dz;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /**
     * The column a player is breaking: the one they look at, found again on each side since the server is only told the space.
     */
    public static int quarterBroken(BlockState state, BlockPos pos, Player player)
    {
        if (player.pick(player.blockInteractionRange(), 1, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(pos))
            return quarterHit(state, pos, hit.getLocation(), hit.getDirection());
        return quarterHit(state, pos, Vec3.atCenterOf(pos), Direction.UP);
    }

    /**
     * What is left of a space once a player breaks the column they look at, or null when it was the last one and the
     * space goes with it.
     */
    public static @Nullable BlockState remainingAfterBreak(BlockState state, BlockPos pos, Player player)
    {
        if (!(state.getBlock() instanceof KnowledgeColumnBlock) || Integer.bitCount(maskOf(state)) <= 1)
            return null;
        return state.setValue(QUARTERS[quarterBroken(state, pos, player)], false);
    }

    /**
     * Only the broken column drops, and its particles fill its own quarter.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        var broken = state;
        if (Integer.bitCount(maskOf(state)) > 1)
        {
            int quarter = quarterBroken(state, pos, player);
            broken = defaultBlockState().setValue(NORTH_WEST, false).setValue(QUARTERS[quarter], true);
        }
        super.playerWillDestroy(level, pos, broken, player);
        return broken;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    /**
     * What a column holds flows to its repository, so a larger cluster shows a denser flow.
     */
    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random)
    {
        int quarter = random.nextInt(QUARTERS.length);
        if ((maskOf(state) & 1 << quarter) == 0 || !(world.getBlockEntity(pos) instanceof KnowledgeColumnTile column) || !column.isActive() || column.getLink().isEmpty())
            return;

        if (world.getBlockEntity(column.getLink().get().repository()) instanceof KnowledgeRepositoryTile repository)
            repository.drawMote(new Vec3(pos.getX() + KnowledgeMath.columnCenterX(quarter), pos.getY() + MOTE_LOWEST + random.nextFloat() * MOTE_HEIGHT_ADD, pos.getZ() + KnowledgeMath.columnCenterZ(quarter)));
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type)
    {
        return world.isClientSide() ? null : createTickerHelper(type, VoidHeartTiles.KNOWLEDGE_COLUMN, RepositoryBoundTile::tick);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new KnowledgeColumnTile(pos, state);
    }
}
