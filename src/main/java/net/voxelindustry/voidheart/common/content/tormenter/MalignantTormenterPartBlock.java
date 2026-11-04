package net.voxelindustry.voidheart.common.content.tormenter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jetbrains.annotations.Nullable;

/**
 * An invisible piece of the droplet of a sated tormenter, solid where the flesh is drawn. It knows where its tormenter stands
 * from its own state, and breaking it breaks the tormenter with everything else.
 */
public class MalignantTormenterPartBlock extends Block
{
    private static final int SIDE_OFFSET = 1;

    public static final IntegerProperty X = IntegerProperty.create("x", 0, 2);
    public static final IntegerProperty Y = IntegerProperty.create("y", 1, 3);
    public static final IntegerProperty Z = IntegerProperty.create("z", 0, 2);

    private static final VoxelShape[][][] SHAPES = new VoxelShape[3][4][3];

    public MalignantTormenterPartBlock(Properties settings)
    {
        super(settings);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(X, Y, Z);
    }

    public BlockState stateFor(TormenterDropletShape.Cell cell)
    {
        return defaultBlockState()
                .setValue(X, cell.x() + SIDE_OFFSET)
                .setValue(Y, cell.y())
                .setValue(Z, cell.z() + SIDE_OFFSET);
    }

    /**
     * @return where the tormenter this part belongs to should stand
     */
    public static BlockPos tormenterOf(BlockPos pos, BlockState state)
    {
        return pos.offset(SIDE_OFFSET - state.getValue(X), -state.getValue(Y), SIDE_OFFSET - state.getValue(Z));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        var x = state.getValue(X);
        var y = state.getValue(Y);
        var z = state.getValue(Z);
        if (SHAPES[x][y][z] == null)
            SHAPES[x][y][z] = shape(x - SIDE_OFFSET, y, z - SIDE_OFFSET);
        return SHAPES[x][y][z];
    }

    private static VoxelShape shape(int x, int y, int z)
    {
        var shape = Shapes.empty();
        for (var box : TormenterDropletShape.boxes(x, y, z))
            shape = Shapes.or(shape, Shapes.box(box.minX(), 0, box.minZ(), box.maxX(), 1, box.maxZ()));
        return shape;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    /**
     * Someone in creative mode gets no drop, wherever they hit the droplet.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        var tormenter = tormenterOf(pos, state);
        if (!level.isClientSide() && player.getAbilities().instabuild && level.getBlockState(tormenter).is(VoidHeartBlocks.MALIGNANT_TORMENTER))
            level.destroyBlock(tormenter, false);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved)
    {
        var tormenter = tormenterOf(pos, state);
        if (level.getBlockState(tormenter).is(VoidHeartBlocks.MALIGNANT_TORMENTER))
            level.destroyBlock(tormenter, true);

        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData)
    {
        return MalignantTormenterBlock.itemOf(level.getBlockEntity(tormenterOf(pos, state)));
    }
}
