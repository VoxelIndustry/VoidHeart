package net.voxelindustry.voidheart.common.content.mob;

/**
 * How an Arcane Eye moves on its prey. It freezes under a gaze, comes closer the moment it is left unwatched, and can only ever fire from up close.
 */
public final class ArcaneStalk
{
    public static final double FIRE_RANGE = 5;
    public static final double FOLLOW_NEAR = 8;
    public static final double FOLLOW_FAR = 12;

    private static final double CLOSE_MARGIN = 0.5;

    public enum Move
    {
        CLOSE_IN,
        HOLD,
        DRIFT_BACK,
        BACK_OFF
    }

    private ArcaneStalk()
    {
    }

    public static Move decide(double distance, boolean watched, boolean backingOff)
    {
        if (backingOff)
            return distance < FOLLOW_NEAR ? Move.BACK_OFF : Move.HOLD;
        if (watched)
            return distance < FOLLOW_NEAR ? Move.DRIFT_BACK : Move.HOLD;
        return distance > FIRE_RANGE - CLOSE_MARGIN ? Move.CLOSE_IN : Move.HOLD;
    }

    public static boolean canFire(double distance, boolean seesTarget)
    {
        return seesTarget && distance <= FIRE_RANGE;
    }
}
