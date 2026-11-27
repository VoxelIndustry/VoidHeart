package net.voxelindustry.voidheart.common.world.daedalus;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.content.shroud.ShroudAltarBlock;

import static net.voxelindustry.voidheart.common.setup.VoidHeartBlocks.*;

/**
 * The blocks a shroud gate is built of. Its frame's grain runs up the pillars and across the beams, and the keystone shows its end to the room.
 */
public final class ShroudBlocks
{
    private static final Direction[] DIRECTIONS = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

    private ShroudBlocks()
    {
    }

    public static Direction direction(int compass)
    {
        return DIRECTIONS[compass];
    }

    public static int compass(Direction direction)
    {
        return switch (direction)
        {
            case EAST -> Compass.EAST;
            case SOUTH -> Compass.SOUTH;
            case WEST -> Compass.WEST;
            default -> Compass.NORTH;
        };
    }

    public static BlockState state(ShroudGate gate, int x, int y, int z)
    {
        return switch (gate.partAt(x, y, z))
        {
            case PILLAR -> frame(Direction.Axis.Y);
            case BEAM -> frame(direction(gate.lateral()).getAxis());
            case KEYSTONE -> frame(direction(gate.facing()).getAxis());
            case SEAL -> SHROUD_SEAL.defaultBlockState();
            case ALTAR -> SHROUD_ALTAR.defaultBlockState().setValue(ShroudAltarBlock.FACING, direction(gate.altarFacing()));
            case NONE -> SHROUD_FRAME.defaultBlockState();
        };
    }

    private static BlockState frame(Direction.Axis axis)
    {
        return SHROUD_FRAME.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }
}
