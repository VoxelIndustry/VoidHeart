package net.voxelindustry.voidheart.common.world.underway;

/**
 * A working piece of a fort's security, in its frame. Its place in the fort's list of units names it for good.
 *
 * @param faceAlong  the way it looks, as a step along the frame
 * @param faceAcross the same, as a step across the frame
 * @param emergency  whether it runs on the emergency circuit, so an alarm wakes it in a section without power
 */
public record UnderwayUnit(UnderwayUnitKind kind, int along, int y, int across, int faceAlong, int faceAcross, boolean emergency)
{
    public UnderwaySection section()
    {
        return UnderwaySection.at(y);
    }
}
