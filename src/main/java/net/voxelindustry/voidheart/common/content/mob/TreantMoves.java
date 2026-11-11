package net.voxelindustry.voidheart.common.content.mob;

/**
 * The timing and reach of a Treant's two moves, coming out of its trunk and sweeping its claws, kept apart from the entity so they can be checked on their own.
 * Yaw follows the game's: 0 faces south, toward +z, and 90 faces west.
 */
public final class TreantMoves
{
    public static final int EMERGE_TICKS = 70;
    // Shudders inside the bark this long before it starts to come out
    public static final int EMERGE_STIR_TICKS = 12;
    // From the trunk's centre to standing clear of it
    public static final double EMERGE_DISTANCE = 1.15;

    public static final int SWEEP_WINDUP_TICKS = 22;
    public static final int FOLLOW_THROUGH_TICKS = 8;
    public static final int RECOVER_TICKS = 30;
    public static final double SWEEP_RANGE = 4.6;
    // About two thirds of a full turn, centred on where it faces
    public static final double SWEEP_ARC_DOT = -0.45;
    // Close enough to be under its arms, where any bearing is caught
    public static final double SWEEP_POINT_BLANK = 0.5;

    private TreantMoves()
    {
    }

    /**
     * How far along the way out it is, eased so that it tears free slowly and steps clear at the end.
     */
    public static float emergeProgress(float ticks)
    {
        float moving = Math.clamp((ticks - EMERGE_STIR_TICKS) / (EMERGE_TICKS - EMERGE_STIR_TICKS), 0F, 1F);
        return moving * moving * (3 - 2 * moving);
    }

    /**
     * Whether a spot is caught by a sweep from a Treant standing at a place and facing a heading, on the flat.
     */
    public static boolean inSweep(double treantX, double treantZ, float yaw, double spotX, double spotZ)
    {
        double radians = Math.toRadians(yaw);
        double facingX = -Math.sin(radians);
        double facingZ = Math.cos(radians);
        double towardX = spotX - treantX;
        double towardZ = spotZ - treantZ;
        double distance = Math.sqrt(towardX * towardX + towardZ * towardZ);
        if (distance > SWEEP_RANGE)
            return false;
        return distance < SWEEP_POINT_BLANK || (towardX * facingX + towardZ * facingZ) / distance >= SWEEP_ARC_DOT;
    }
}
