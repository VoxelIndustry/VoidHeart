package net.voxelindustry.voidheart.common.content.avengingreach;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The rules of the Avenging Reach, shared by the server that strikes and the client that draws the lash, so the claw lands when the blow does.
 * <p>
 * A strike runs in four parts: the tendril curls out of the middle of the player's back, lashes at the aggressor, holds there with its claw shut,
 * then draws back and thins away.
 */
public final class ReachArms
{
    public static final int    MAX_ARMS = 4;
    /** How far from the player's chest the claw can land. */
    public static final double REACH    = 3;

    public static final int EMERGE_TICKS  = 3;
    public static final int LASH_TICKS    = 3;
    public static final int IMPACT_TICK   = EMERGE_TICKS + LASH_TICKS;
    public static final int HOLD_TICKS    = 2;
    public static final int RETRACT_TICKS = 6;
    public static final int TOTAL_TICKS   = IMPACT_TICK + HOLD_TICKS + RETRACT_TICKS;

    /** How much of the curve shows once the tendril has curled out, before it lashes. */
    private static final float COILED_EXTENT     = 0.35F;
    private static final float CLAW_SHUT_TICKS   = 1.5F;
    private static final float COILED_CLAW       = 0.25F;
    private static final float EMERGE_THICKNESS  = 0.4F;
    private static final float RETRACT_THICKNESS = 0.15F;

    /** Where every arm leaves the body, the middle of the back: x to the right, y up from the feet of a standing player, z behind. */
    private static final float[] ANCHOR = {0, 1.15F, 0.14F};
    /** Which side each arm curls out to, 1 for the right, and how high it coils before lashing, so four arms fan out apart. */
    private static final int[]   SIDES  = {1, -1, 1, -1};
    private static final float[] RISES  = {1, 1, -0.2F, -0.2F};

    private ReachArms()
    {
    }

    /**
     * The first arm ready to answer, or -1 if every arm is still recharging.
     *
     * @param readyAt the tick each arm can strike again, at least as long as {@code arms}
     */
    public static int nextReady(long[] readyAt, int arms, long now)
    {
        return nextReady(readyAt, arms, now, 0);
    }

    /**
     * The first ready arm on the aggressor's side of the body, so arms don't cross the back, or else the first ready arm.
     *
     * @param side 1 when the aggressor is to the right, -1 to the left, 0 for no preference
     */
    public static int nextReady(long[] readyAt, int arms, long now, int side)
    {
        int count = Math.min(arms, readyAt.length);
        for (int arm = 0; arm < count && side != 0; arm++)
        {
            if (readyAt[arm] <= now && side(arm) == side)
                return arm;
        }
        for (int arm = 0; arm < count; arm++)
        {
            if (readyAt[arm] <= now)
                return arm;
        }
        return -1;
    }

    /**
     * Which side of a body turned to {@code bodyYaw} degrees a point lies on, by its offset from the body.
     *
     * @return 1 to the right, -1 to the left
     */
    public static int sideOf(float bodyYaw, double dx, double dz)
    {
        double yaw = Math.toRadians(bodyYaw);
        // The body's right, as it faces (-sin, cos)
        double right = -Math.cos(yaw) * dx - Math.sin(yaw) * dz;
        return right >= 0 ? 1 : -1;
    }

    /**
     * Whether a graft adding the arm numbered {@code number}, from 1, can be taken by a body that already has {@code owned} arms.
     */
    public static boolean canTake(int owned, int number)
    {
        return owned == number - 1;
    }

    /** The point of the box nearest to the chest, where the claw lands. */
    public static Vec3 nearestPoint(Vec3 chest, AABB box)
    {
        return new Vec3(Mth.clamp(chest.x, box.minX, box.maxX), Mth.clamp(chest.y, box.minY, box.maxY), Mth.clamp(chest.z, box.minZ, box.maxZ));
    }

    public static boolean inReach(Vec3 chest, AABB box)
    {
        return nearestPoint(chest, box).distanceToSqr(chest) <= REACH * REACH;
    }

    /** The chest of a body this tall, where reach is measured from. */
    public static double chestHeight(double height)
    {
        return height * 0.72;
    }

    /**
     * Where an arm leaves the body, the same for every arm, scaled to the body's height so a crouching player keeps it on their back.
     *
     * @return x to the right, y up from the feet, z behind
     */
    public static Vec3 anchor(int arm, double height)
    {
        return new Vec3(ANCHOR[0], ANCHOR[1] * height / 1.8, ANCHOR[2]);
    }

    /** Which side the arm curls out to: 1 for the right, -1 for the left. */
    public static int side(int arm)
    {
        return SIDES[Mth.clamp(arm, 0, SIDES.length - 1)];
    }

    /** How high the arm coils before lashing, against the highest arms. */
    public static float coilRise(int arm)
    {
        return RISES[Mth.clamp(arm, 0, RISES.length - 1)];
    }

    /** How much of the curve from the anchor to the claw is drawn, from 0 to 1. */
    public static float extent(float ticks)
    {
        if (ticks <= 0)
            return 0;
        if (ticks < EMERGE_TICKS)
            return COILED_EXTENT * easeOut(ticks / EMERGE_TICKS);
        if (ticks < IMPACT_TICK)
        {
            // Speeding up as it goes, like a whip
            float lash = (ticks - EMERGE_TICKS) / LASH_TICKS;
            return COILED_EXTENT + (1 - COILED_EXTENT) * lash * lash;
        }
        if (ticks < IMPACT_TICK + HOLD_TICKS)
            return 1;
        float back = (ticks - IMPACT_TICK - HOLD_TICKS) / RETRACT_TICKS;
        return Math.max(0, 1 - easeIn(back));
    }

    /** How far the curve has unbent from its coil behind the player toward the straight line to the target, from 0 to 1. */
    public static float straighten(float ticks)
    {
        if (ticks <= EMERGE_TICKS)
            return 0;
        if (ticks >= IMPACT_TICK)
            return 1;
        return easeOut((ticks - EMERGE_TICKS) / LASH_TICKS);
    }

    /** How open the claw is: half shut as it curls out, spreading wide as it lashes, then 0 shut on the target. */
    public static float clawOpen(float ticks)
    {
        if (ticks < EMERGE_TICKS)
            return COILED_CLAW * easeOut(Math.max(0, ticks) / EMERGE_TICKS);
        if (ticks < IMPACT_TICK)
            return COILED_CLAW + (1 - COILED_CLAW) * easeOut((ticks - EMERGE_TICKS) / LASH_TICKS);
        return Math.max(0, 1 - (ticks - IMPACT_TICK) / CLAW_SHUT_TICKS);
    }

    /** How thick the tendril is against its full size: it swells as it curls out and thins as it draws back. */
    public static float thickness(float ticks)
    {
        if (ticks < EMERGE_TICKS)
            return EMERGE_THICKNESS + (1 - EMERGE_THICKNESS) * easeOut(Math.max(0, ticks) / EMERGE_TICKS);
        if (ticks < IMPACT_TICK + HOLD_TICKS)
            return 1;
        float back = Math.min(1, (ticks - IMPACT_TICK - HOLD_TICKS) / RETRACT_TICKS);
        return 1 - (1 - RETRACT_THICKNESS) * back;
    }

    private static float easeOut(float t)
    {
        float clamped = Mth.clamp(t, 0, 1);
        return 1 - (1 - clamped) * (1 - clamped);
    }

    private static float easeIn(float t)
    {
        float clamped = Mth.clamp(t, 0, 1);
        return clamped * clamped;
    }
}
