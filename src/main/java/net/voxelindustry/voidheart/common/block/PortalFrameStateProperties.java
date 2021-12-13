package net.voxelindustry.voidheart.common.block;

import net.minecraft.block.BlockState;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.Direction;

public class PortalFrameStateProperties
{
    public static final EnumProperty<FrameConnection> NORTH = EnumProperty.of("north", FrameConnection.class);
    public static final EnumProperty<FrameConnection> SOUTH = EnumProperty.of("south", FrameConnection.class);
    public static final EnumProperty<FrameConnection> EAST  = EnumProperty.of("east", FrameConnection.class);
    public static final EnumProperty<FrameConnection> WEST  = EnumProperty.of("west", FrameConnection.class);
    public static final EnumProperty<FrameConnection> UP    = EnumProperty.of("up", FrameConnection.class);
    public static final EnumProperty<FrameConnection> DOWN  = EnumProperty.of("down", FrameConnection.class);

    public static FrameConnection getSideConnection(BlockState state, Direction direction)
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

    public enum FrameConnection implements StringIdentifiable
    {
        INTERIOR,
        FRAME,
        NONE;

        @Override
        public String asString()
        {
            return name().toLowerCase();
        }

        public boolean isConnected()
        {
            return this != NONE;
        }
    }
}
