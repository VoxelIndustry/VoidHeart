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
    HATCH;

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
