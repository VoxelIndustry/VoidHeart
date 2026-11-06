package net.voxelindustry.voidheart.common.world.daedalus;

public final class Compass
{
    public static final int NORTH = 0;
    public static final int EAST = 1;
    public static final int SOUTH = 2;
    public static final int WEST = 3;
    public static final int COUNT = 4;

    private static final int[] DX = { 0, 1, 0, -1 };
    private static final int[] DZ = { -1, 0, 1, 0 };

    private Compass()
    {
    }

    public static int dx(int direction)
    {
        return DX[direction];
    }

    public static int dz(int direction)
    {
        return DZ[direction];
    }

    public static int opposite(int direction)
    {
        return (direction + 2) & 3;
    }

    static int clockwise(int direction)
    {
        return (direction + 1) & 3;
    }

    static int counterClockwise(int direction)
    {
        return (direction + 3) & 3;
    }

    /**
     * @return the direction as a Minecraft yaw, where south is 0 and west 90
     */
    public static float yaw(int direction)
    {
        return Math.floorMod(direction * 90 + 180, 360);
    }

    static boolean isAlongX(int direction)
    {
        return direction == EAST || direction == WEST;
    }
}
