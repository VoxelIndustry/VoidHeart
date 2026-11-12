package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

/**
 * Lets everything under a district be laid out once, along its line and across it, then turned and mirrored into the world. Mirrors turn around the district's middle, so its lattice of cells and the lines through its middle stay where they are.
 */
public record UnderwayFrame(int originX, int originZ, boolean alongX, boolean flipAlong, boolean flipAcross)
{
    public static UnderwayFrame ofLine(DistrictPos pos, boolean alongX)
    {
        return new UnderwayFrame(pos.originX(), pos.originZ(), alongX, false, false);
    }

    public int x(int along, int across)
    {
        return originX + (alongX ? along(along) : across(across));
    }

    public int z(int along, int across)
    {
        return originZ + (alongX ? across(across) : along(along));
    }

    /** @return the world direction of a step along and across */
    public int compass(int stepAlong, int stepAcross)
    {
        int along = flipAlong ? -stepAlong : stepAlong;
        int across = flipAcross ? -stepAcross : stepAcross;
        int stepX = alongX ? along : across;
        int stepZ = alongX ? across : along;
        if (stepX != 0)
            return stepX > 0 ? Compass.EAST : Compass.WEST;
        return stepZ > 0 ? Compass.SOUTH : Compass.NORTH;
    }

    private int along(int along)
    {
        return flipAlong ? DistrictPos.SIZE - 1 - along : along;
    }

    private int across(int across)
    {
        return flipAcross ? DistrictPos.SIZE - 1 - across : across;
    }
}
