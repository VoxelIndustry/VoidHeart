package net.voxelindustry.voidheart.common.content.underway;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.world.underway.UnderwayProp;

import java.util.Map;

/** What was left in the Underway, turned to face the room or hung on a wall facing away from it. No block entity, since a fort holds hundreds of them. */
public class UnderwayPropBlock extends Block
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Map<Direction, VoxelShape> shapes;
    private final boolean onWall;

    public UnderwayPropBlock(Properties properties, UnderwayProp prop)
    {
        super(properties);
        int[] bounds = prop.bounds();
        shapes = Shapes.rotateHorizontal(Block.box(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]));
        onWall = prop.mount() == UnderwayProp.Mount.WALL;
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
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
