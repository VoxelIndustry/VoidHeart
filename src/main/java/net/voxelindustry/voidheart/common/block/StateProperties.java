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

    public static final BooleanProperty BROKEN = BooleanProperty.of("broken");

    public static final BooleanProperty MODEL = BooleanProperty.of("model");

    public static boolean isSideConnected(BlockState state, Direction direction)
    {
        return switch (direction)
                {
                    case DOWN -> state.get(DOWN);
                    case UP -> state.get(UP);
                    case NORTH -> state.get(NORTH);
                    case SOUTH -> state.get(SOUTH);
                    case WEST -> state.get(WEST);
                    case EAST -> state.get(EAST);
                };
    }
}
