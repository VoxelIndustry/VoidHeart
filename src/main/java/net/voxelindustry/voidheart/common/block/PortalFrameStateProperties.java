package net.voxelindustry.voidheart.common.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class PortalFrameStateProperties
{
    public static final EnumProperty<FrameConnection> NORTH = EnumProperty.create("north", FrameConnection.class);
    public static final EnumProperty<FrameConnection> SOUTH = EnumProperty.create("south", FrameConnection.class);
    public static final EnumProperty<FrameConnection> EAST  = EnumProperty.create("east", FrameConnection.class);
    public static final EnumProperty<FrameConnection> WEST  = EnumProperty.create("west", FrameConnection.class);
    public static final EnumProperty<FrameConnection> UP    = EnumProperty.create("up", FrameConnection.class);
    public static final EnumProperty<FrameConnection> DOWN  = EnumProperty.create("down", FrameConnection.class);

    public static FrameConnection getSideConnection(BlockState state, Direction direction)
    {
        return switch (direction)
                {
                    case DOWN -> state.getValue(DOWN);
                    case UP -> state.getValue(UP);
                    case NORTH -> state.getValue(NORTH);
                    case SOUTH -> state.getValue(SOUTH);
                    case WEST -> state.getValue(WEST);
                    case EAST -> state.getValue(EAST);
                };
    }

    public enum FrameConnection implements StringRepresentable
    {
        INTERIOR,
        FRAME,
        NONE;

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase();
        }

        public boolean isConnected()
        {
            return this != NONE;
        }
    }
}
