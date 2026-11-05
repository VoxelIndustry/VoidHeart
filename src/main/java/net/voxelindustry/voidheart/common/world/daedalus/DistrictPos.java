package net.voxelindustry.voidheart.common.world.daedalus;

/** A district's place on the infinite grid: 16 x 8 x 16 cubes of 8 blocks, then a chasm east and south, so every chunk is wholly one or the other. */
public record DistrictPos(int x, int z)
{
    public static final int CELL = 8;
    public static final int CELLS = 16;
    public static final int TIERS = 8;
    public static final int SIZE = CELL * CELLS;
    public static final int CHASM = 32;
    public static final int PITCH = SIZE + CHASM;
    public static final int HEIGHT = 64;
    public static final int TOP_Y = HEIGHT - 1;

    public static DistrictPos at(int blockX, int blockZ)
    {
        return new DistrictPos(Math.floorDiv(blockX, PITCH), Math.floorDiv(blockZ, PITCH));
    }

    public static boolean isInsideDistrict(int blockX, int blockZ)
    {
        return Math.floorMod(blockX, PITCH) < SIZE && Math.floorMod(blockZ, PITCH) < SIZE;
    }

    /** @return the height of a tier's floor block, whose walkable surface is one block higher */
    public static int floorY(int tier)
    {
        return tier * CELL + 1;
    }

    public int originX()
    {
        return x * PITCH;
    }

    public int originZ()
    {
        return z * PITCH;
    }
}
