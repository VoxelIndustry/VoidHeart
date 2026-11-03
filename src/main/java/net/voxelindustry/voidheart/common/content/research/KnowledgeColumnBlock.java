package net.voxelindustry.voidheart.common.content.research;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

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

    private static final VoxelShape[] SHAPES = new VoxelShape[1 << QUARTERS.length];

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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return SHAPES[maskOf(state)];
    }

    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type)
    {
        return world.isClientSide() ? null : createTickerHelper(type, VoidHeartTiles.KNOWLEDGE_COLUMN, KnowledgeColumnTile::tick);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new KnowledgeColumnTile(pos, state);
    }
}
