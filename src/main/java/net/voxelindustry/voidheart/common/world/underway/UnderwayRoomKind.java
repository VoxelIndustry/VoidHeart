package net.voxelindustry.voidheart.common.world.underway;

/** What a room of the Underway was for, which decides how it is furnished. */
public enum UnderwayRoomKind
{
    HATCH_LOBBY,
    ENTRY_GALLERY,
    /** A trap across the entry gallery, crossed on a strip of grating. */
    PIT,
    GUARD_ROOM,
    VESTIBULE,
    ARMOURY,
    QUARTERS,
    DECONTAMINATION,
    GALLERY,
    RUNG,
    CROSS_GALLERY,
    ACCESS_GALLERY,
    BARRACKS(11),
    SHELTER(13),
    MESS(13),
    KITCHEN(9),
    INFIRMARY(9),
    POWER_PLANT(17),
    FILTERS(9),
    CISTERN(11),
    COMMAND_POST(9),
    EXCHANGE(7),
    STORES(7),
    WORKSHOP(11),
    WASHROOM(7),
    LABORATORY(9),
    BRIG(9),
    ARCHIVE(9),
    MORGUE(7),
    /** A bay whose vault came down, left full of rubble. */
    CAVED_IN,
    /** A bay the void tore open. */
    BREACH,
    PLATFORM,
    /** The track bed where two trunk lines cross. */
    CROSSING,
    LOBBY,
    MAGAZINE_GALLERY,
    MAGAZINE,
    DEPOT,
    PUMP_ROOM,
    FREIGHT_HALL,
    SIGNAL_CABIN,
    CONTROL_ROOM;

    private final int bayWidth;

    UnderwayRoomKind()
    {
        this(0);
    }

    UnderwayRoomKind(int bayWidth)
    {
        this.bayWidth = bayWidth;
    }

    /** @return how far a bay of this kind runs along its gallery, or 0 for rooms that are not bays */
    public int bayWidth()
    {
        return bayWidth;
    }

    /** @return whether a walk from the hatch is not expected to end up inside it */
    public boolean isCutOff()
    {
        return this == CAVED_IN || this == BREACH || this == PIT;
    }
}
