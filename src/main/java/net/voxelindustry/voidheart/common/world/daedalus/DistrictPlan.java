package net.voxelindustry.voidheart.common.world.daedalus;

import java.util.List;

/** What a district is made of, kept compact: a few kilobytes describing the lattice, drawn into blocks only when a chunk asks for it. */
public final class DistrictPlan
{
    static final int CELL_COUNT = DistrictPos.CELLS * DistrictPos.CELLS * DistrictPos.TIERS;
    static final byte EMPTY = 0;
    static final byte CORRIDOR = 1;
    static final byte ROOM = 2;
    static final byte STAIR_LOW = 3;
    static final byte STAIR_HIGH = 4;

    /** @param direction the side of its cell open onto the bridge */
    public record Landing(int i, int k, int j, int direction)
    {
    }

    public final long worldSeed;
    public final DistrictPos pos;
    public final DaedalusTheme theme;
    private final byte[] kinds;
    private final byte[] faces;
    private final byte[] rooms;
    private final Room[] roomList;
    private final List<Landing> landings;

    DistrictPlan(long worldSeed, DistrictPos pos, DaedalusTheme theme, byte[] kinds, byte[] faces, byte[] rooms, Room[] roomList, List<Landing> landings)
    {
        this.worldSeed = worldSeed;
        this.pos = pos;
        this.theme = theme;
        this.kinds = kinds;
        this.faces = faces;
        this.rooms = rooms;
        this.roomList = roomList;
        this.landings = landings;
    }

    static int index(int i, int k, int j)
    {
        return (k * DistrictPos.CELLS + j) * DistrictPos.CELLS + i;
    }

    static boolean isInside(int i, int k, int j)
    {
        return i >= 0 && j >= 0 && k >= 0 && i < DistrictPos.CELLS && j < DistrictPos.CELLS && k < DistrictPos.TIERS;
    }

    byte kind(int i, int k, int j)
    {
        return isInside(i, k, j) ? kinds[index(i, k, j)] : EMPTY;
    }

    /** @return for corridors and rooms one bit per open {@link Compass} side, for stairs the direction they climb */
    int faces(int i, int k, int j)
    {
        return faces[index(i, k, j)];
    }

    boolean isOpen(int i, int k, int j, int direction)
    {
        return (faces[index(i, k, j)] & (1 << direction)) != 0;
    }

    /**
     * @return whether this cell is a corridor with a passage open on that side
     */
    public boolean isCorridorOpenTowards(int i, int k, int j, int direction)
    {
        return kind(i, k, j) == CORRIDOR && isOpen(i, k, j, direction);
    }

    Room room(int i, int k, int j)
    {
        if (!isInside(i, k, j))
            return null;
        int id = rooms[index(i, k, j)] & 0xFF;
        return id == 0 ? null : roomList[id - 1];
    }

    public Room[] rooms()
    {
        return roomList;
    }

    public List<Landing> landings()
    {
        return landings;
    }

    long cellHash(int i, int k, int j)
    {
        return DaedalusHash.mix(worldSeed, pos.x(), pos.z(), index(i, k, j));
    }
}
