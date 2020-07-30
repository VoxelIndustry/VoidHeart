package net.voxelindustry.voidheart.common.block;

import net.minecraft.block.BlockState;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.Direction;

public class StateProperties
{
    public static final BooleanProperty NORTH = BooleanProperty.of("north");
    public static final BooleanProperty SOUTH = BooleanProperty.of("south");
    public static final BooleanProperty EAST  = BooleanProperty.of("east");
    public static final BooleanProperty WEST  = BooleanProperty.of("west");
    public static final BooleanProperty UP    = BooleanProperty.of("up");
    public static final BooleanProperty DOWN  = BooleanProperty.of("down");

    public static boolean isConnectedToSide(BlockState state, Direction direction)
    {
        switch (direction)
        {
            case DOWN:
                return state.get(DOWN);
            case UP:
                return state.get(UP);
            case NORTH:
                return state.get(NORTH);
            case SOUTH:
                return state.get(SOUTH);
            case WEST:
                return state.get(WEST);
            case EAST:
                return state.get(EAST);
        }
        return false;
    }
}
