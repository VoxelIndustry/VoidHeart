package net.voxelindustry.voidheart.common.world.daedalus;

public enum RoomShape
{
    PLAIN,
    STACKS,
    CELL,
    COLONNADE,
    FLOOD,
    ATRIUM,
    CHAMBER,
    VAULT,
    PIT,
    /** The bare room on the lowest tier whose floor holds the hatch of the Underway. */
    HATCH,
    /** A tall room with a single door, facing the shroud gate on its far wall. */
    SHROUD;

    /** @return the side of a shroud room its door is on, which its gate faces */
    static int shroudDoor(Room room)
    {
        return room.variant() & (Compass.COUNT - 1);
    }

    /** Only a shroud room keeps to a single side, so its far wall stays whole for the gate. */
    boolean opensOn(int side, Room room)
    {
        return this != SHROUD || side == shroudDoor(room);
    }

    /** Rooms taller than one tier can only be reached where they have a floor to arrive on. */
    boolean hasDoorsAt(int tier, Room room)
    {
        return switch (this)
        {
            case ATRIUM -> true;
            case FLOOD -> tier == room.maxK();
            case COLONNADE -> tier == room.minK() || tier == room.maxK();
            default -> tier == room.minK();
        };
    }
}
