package net.voxelindustry.voidheart.common.world.pocket;

import java.util.ArrayList;
import java.util.List;

/**
 * Every cell of the pocket cube, addressed in blocks relative to the heart. Same size for every pocket: a pocket wall 9 blocks from the
 * center, one layer of rock inside it, and 15 blocks across within. Only its height relative to the heart varies. Anything outside reads as pocket wall.
 */
public final class PocketGrid
{
    public static final int WALL_REACH = 9;
    public static final int INNER_REACH = 7;
    private static final int CENTER_ABOVE_HEART = 6;
    private static final int SIZE = WALL_REACH * 2 + 1;

    public record Niche(int x, int y, int z, int radius) {}

    private final int centerY;
    private final byte[] cells = new byte[SIZE * SIZE * SIZE];
    private final List<Niche> niches = new ArrayList<>();

    public PocketGrid(int heartRise)
    {
        centerY = CENTER_ABOVE_HEART - heartRise;
    }

    public PocketSlot slot(int x, int y, int z)
    {
        if (!contains(x, y, z))
            return PocketSlot.WALL;
        return PocketSlot.of(cells[index(x, y, z)]);
    }

    void set(int x, int y, int z, PocketSlot slot)
    {
        cells[index(x, y, z)] = (byte) slot.ordinal();
    }

    public boolean contains(int x, int y, int z)
    {
        return Math.abs(x) <= WALL_REACH && Math.abs(z) <= WALL_REACH && Math.abs(y - centerY) <= WALL_REACH;
    }

    public boolean isBoundary(int x, int y, int z)
    {
        return Math.abs(x) == WALL_REACH || Math.abs(z) == WALL_REACH || Math.abs(y - centerY) == WALL_REACH;
    }

    public boolean isInner(int x, int y, int z)
    {
        return Math.abs(x) <= INNER_REACH && Math.abs(z) <= INNER_REACH && Math.abs(y - centerY) <= INNER_REACH;
    }

    public int bottom()
    {
        return centerY - WALL_REACH;
    }

    public int top()
    {
        return centerY + WALL_REACH;
    }

    public List<Niche> niches()
    {
        return niches;
    }

    private int index(int x, int y, int z)
    {
        return (x + WALL_REACH) + (z + WALL_REACH) * SIZE + (y - bottom()) * SIZE * SIZE;
    }
}
