package net.voxelindustry.voidheart.common.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class StateProperties
{
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");

    public static final BooleanProperty MODEL = BooleanProperty.create("model");

    public static boolean isSideConnected(BlockState state, Direction direction)
    {
        return state.getValue(getSideFromDirection(direction));
    }

    public static BooleanProperty getSideFromDirection(Direction direction)
    {
        return switch (direction)
        {
            case DOWN -> DOWN;
            case UP -> UP;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
        };
    }
}
