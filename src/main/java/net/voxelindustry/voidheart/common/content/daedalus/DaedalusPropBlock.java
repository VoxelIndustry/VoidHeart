package net.voxelindustry.voidheart.common.content.daedalus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusProp;

import java.util.Map;

/** Furniture of the Daedalus, turned to face the room or hung on a wall facing away from it. No block entity, since districts hold hundreds of them. */
public class DaedalusPropBlock extends Block
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final DaedalusProp prop;
    private final Map<Direction, VoxelShape> shapes;
    private final boolean onWall;

    /**
     * @param northShape the shape when facing north, with a wall prop's wall on the south side
     */
    public DaedalusPropBlock(Properties properties, DaedalusProp prop, VoxelShape northShape)
    {
        super(properties);
        this.prop = prop;
        shapes = Shapes.rotateHorizontal(northShape);
        onWall = prop.hangsOnWall();
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        DaedalusFlames.burn(prop, state.getValue(FACING), level, pos, random);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var face = context.getClickedFace();
        if (onWall && face.getAxis().isHorizontal())
            return defaultBlockState().setValue(FACING, face);
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }
}
