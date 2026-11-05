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
    static final byte VOID = 5;
    static final byte CATWALK = 6;
    static final byte FLOODED = 7;

    /** @param direction the side of its cell open onto the bridge */
    public record Landing(int i, int k, int j, int direction)
    {
    }

    public final long worldSeed;
    public final DistrictPos pos;
    public final DaedalusTheme theme;
    private final byte[] kinds;
    private final byte[] faces;
    private final byte[] windows;
    private final short[] rooms;
    private final Room[] roomList;
    private final Room[] voids;
    private final List<Landing> landings;

    DistrictPlan(long worldSeed, DistrictPos pos, DaedalusTheme theme, byte[] kinds, byte[] faces, byte[] windows, short[] rooms, Room[] roomList, Room[] voids, List<Landing> landings)
    {
        this.worldSeed = worldSeed;
        this.pos = pos;
        this.theme = theme;
        this.kinds = kinds;
        this.faces = faces;
        this.windows = windows;
        this.rooms = rooms;
        this.roomList = roomList;
        this.voids = voids;
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

    /** @return for corridors, rooms, catwalks and balconies one bit per open {@link Compass} side, for stairs the direction they climb */
    int faces(int i, int k, int j)
    {
        return faces[index(i, k, j)];
    }

    boolean isOpen(int i, int k, int j, int direction)
    {
        return (faces[index(i, k, j)] & (1 << direction)) != 0;
    }

    public boolean hasWindow(int i, int k, int j, int direction)
    {
        return isInside(i, k, j) && (windows[index(i, k, j)] & (1 << direction)) != 0;
    }

    /** @return whether this cell is part of a pit or vault, open from wall to wall, whether crossed by a catwalk or full of water */
    public boolean isOpenVolume(int i, int k, int j)
    {
        byte kind = kind(i, k, j);
        return kind == VOID || kind == CATWALK || kind == FLOODED;
    }

    public boolean isCatwalk(int i, int k, int j)
    {
        return kind(i, k, j) == CATWALK;
    }

    public boolean isCatwalkOpen(int i, int k, int j, int direction)
    {
        return isCatwalk(i, k, j) && isOpen(i, k, j, direction);
    }

    /** @return whether a balcony stands on this side of a void cell */
    public boolean hasBalcony(int i, int k, int j, int direction)
    {
        return kind(i, k, j) == VOID && isOpen(i, k, j, direction);
    }

    public boolean isCorridorOpenTowards(int i, int k, int j, int direction)
    {
        return kind(i, k, j) == CORRIDOR && isOpen(i, k, j, direction);
    }

    Room room(int i, int k, int j)
    {
        if (!isInside(i, k, j))
            return null;
        int id = rooms[index(i, k, j)];
        return id == 0 ? null : roomList[id - 1];
    }

    public Room[] rooms()
    {
        return roomList;
    }

    /** @return every pit and vault, a vault's lowest tier being the room of its floor */
    public Room[] voids()
    {
        return voids;
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
