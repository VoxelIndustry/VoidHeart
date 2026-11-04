package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.world.dungeon.rooms.RoomKind;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonSize.plane;

/**
 * A room on the lattice, up to but excluding cell (i1, j1, k1). Block coordinates are local to the dungeon's corner.
 */
public final class DungeonRoom
{
    // x and z are the middle of the opening, on the wall, and y is the floor it opens on
    public record Door(int x, int y, int z, Direction into)
    {
    }

    public final RoomKind kind;
    public final int i0;
    public final int j0;
    public final int k0;
    public final int i1;
    public final int j1;
    public final int k1;
    public final List<Door> doors = new ArrayList<>();

    public DungeonRoom(RoomKind kind, int i0, int j0, int k0, int i1, int j1, int k1)
    {
        this.kind = kind;
        this.i0 = i0;
        this.j0 = j0;
        this.k0 = k0;
        this.i1 = i1;
        this.j1 = j1;
        this.k1 = k1;
    }

    public int minX()
    {
        return plane(i0);
    }

    public int minY()
    {
        return plane(j0);
    }

    public int minZ()
    {
        return plane(k0);
    }

    public int maxX()
    {
        return plane(i1);
    }

    public int maxY()
    {
        return plane(j1);
    }

    public int maxZ()
    {
        return plane(k1);
    }

    public int width()
    {
        return i1 - i0;
    }

    public int depth()
    {
        return k1 - k0;
    }

    // Climbing counts double, since corridors need stairs for it
    public int distanceTo(DungeonRoom other)
    {
        int di = Math.max(0, Math.max(other.i0 - i1, i0 - other.i1));
        int dk = Math.max(0, Math.max(other.k0 - k1, k0 - other.k1));
        return di + dk + Math.abs(j0 - other.j0) * 2;
    }
}
