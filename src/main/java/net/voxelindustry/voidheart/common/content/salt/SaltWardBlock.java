package net.voxelindustry.voidheart.common.content.salt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.content.mob.CorruptedCrab;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;

/**
 * Salt laid over the floor. Each side not joined to more salt shows the ward's line. Nothing collides with it: what it keeps out is decided by each mob,
 * except for the Fallen, which it burns, and the crabs, which it drags at.
 */
public class SaltWardBlock extends Block
{
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST  = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST  = BlockStateProperties.WEST;

    public static final float BURN_DAMAGE = 4;
    // Applied every tick it is in the salt, which also takes its momentum away, so this comes to a little under half its pace
    private static final Vec3 CRAB_DRAG = new Vec3(0.85, 1, 0.85);
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 1, 16);

    public SaltWardBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(NORTH, true).setValue(EAST, true).setValue(SOUTH, true).setValue(WEST, true));
    }

    /**
     * @return the salt for this spot, its edges facing whatever around it isn't salt
     */
    public BlockState stateAt(LevelReader level, BlockPos pos)
    {
        return defaultBlockState()
                .setValue(NORTH, !isSalt(level, pos.north()))
                .setValue(EAST, !isSalt(level, pos.east()))
                .setValue(SOUTH, !isSalt(level, pos.south()))
                .setValue(WEST, !isSalt(level, pos.west()));
    }

    private boolean isSalt(LevelReader level, BlockPos pos)
    {
        return level.getBlockState(pos).is(this);
    }

    public static boolean canLayOn(LevelReader level, BlockPos pos)
    {
        var below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        return canLayOn(level, pos);
    }

    @Override
    protected BlockState updateShape(BlockState state,
                                     LevelReader level,
                                     ScheduledTickAccess ticks,
                                     BlockPos pos,
                                     Direction direction,
                                     BlockPos neighbourPos,
                                     BlockState neighbour,
                                     RandomSource random)
    {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos))
            return Blocks.AIR.defaultBlockState();
        if (direction.getAxis().isHorizontal())
            return state.setValue(propertyOf(direction), !neighbour.is(this));
        return state;
    }

    /**
     * Building over salt sweeps it away, but nothing else may take its place.
     */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context)
    {
        return context.getPlayer() != null;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise)
    {
        if (entity instanceof CorruptedCrab)
            entity.makeStuckInBlock(state, CRAB_DRAG);
        if (level instanceof ServerLevel server && entity.getType().builtInRegistryHolder().is(VoidHeartTags.SALT_BURNED) && entity.isAlive())
        {
            if (entity.hurtServer(server, server.damageSources().source(SaltWard.DAMAGE), BURN_DAMAGE))
            {
                // The burn wears the salt it happened on away
                SaltWard.crackle(server, pos);
                server.removeBlock(pos, false);
            }
        }
    }

    private static BooleanProperty propertyOf(Direction direction)
    {
        return switch (direction)
        {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }
}
