package net.voxelindustry.voidheart.common.world.daedalus;

/**
 * Keeps the Daedalus' mobs in their district: none of them sets foot on a bridge, or into the chasm, and none goes under the districts' floor into the Underway, whatever it can pass through. Players are never held back.
 * A mob already past the edge, as one knocked out before this held, can still move back in.
 */
public final class DaedalusFence
{
    /** The top of the layer under every district and chasm, below which the Underway lies. */
    public static final int FLOOR = 1;

    private DaedalusFence()
    {
    }

    /**
     * @return how far down a box whose underside is at {@code minY} may move. One already under the floor may still rise.
     */
    public static double clipY(double minY, double dy)
    {
        return dy < 0 ? Math.max(dy, Math.min(0, FLOOR - minY)) : dy;
    }

    public static boolean isUnderFloor(double y)
    {
        return y < FLOOR;
    }

    /**
     * @return how far the box may move along one axis, given its bounds on that axis and the district's
     */
    public static double clip(double min, double max, double delta, double districtMin, double districtMax)
    {
        if (delta > 0)
            return Math.min(delta, Math.max(0, districtMax - max));
        if (delta < 0)
            return Math.max(delta, Math.min(0, districtMin - min));
        return delta;
    }

    public static double clipX(double centreX, double centreZ, double minX, double maxX, double dx)
    {
        if (!DistrictPos.isInsideDistrict((int) Math.floor(centreX), (int) Math.floor(centreZ)))
            return dx;
        int origin = DistrictPos.at((int) Math.floor(centreX), (int) Math.floor(centreZ)).originX();
        return clip(minX, maxX, dx, origin, origin + DistrictPos.SIZE);
    }

    public static double clipZ(double centreX, double centreZ, double minZ, double maxZ, double dz)
    {
        if (!DistrictPos.isInsideDistrict((int) Math.floor(centreX), (int) Math.floor(centreZ)))
            return dz;
        int origin = DistrictPos.at((int) Math.floor(centreX), (int) Math.floor(centreZ)).originZ();
        return clip(minZ, maxZ, dz, origin, origin + DistrictPos.SIZE);
    }

    /**
     * @return whether a mob at {@code fromX, fromZ} may put itself at the other place: never under the floor, and never out of its district
     */
    public static boolean allows(double fromX, double fromZ, double toX, double toY, double toZ)
    {
        return !isUnderFloor(toY) && allows(fromX, fromZ, toX, toZ);
    }

    /**
     * @return whether a mob in the district holding {@code fromX, fromZ} may be at {@code toX, toZ}
     */
    public static boolean allows(double fromX, double fromZ, double toX, double toZ)
    {
        int x = (int) Math.floor(fromX);
        int z = (int) Math.floor(fromZ);
        if (!DistrictPos.isInsideDistrict(x, z))
            return true;
        int tx = (int) Math.floor(toX);
        int tz = (int) Math.floor(toZ);
        return DistrictPos.isInsideDistrict(tx, tz) && DistrictPos.at(x, z).equals(DistrictPos.at(tx, tz));
    }
}
