package net.voxelindustry.voidheart.common.content.armor;

/**
 * What wearing a frame costs. Standing in it and its running modules cost a steady amount each tick, walking and sprinting cost by the block so standing
 * still with sprint held costs nothing more, and powered jumps, powered hits and falls taken by the legs cost by the event. The socketed apparatus's
 * shield costs by the point refilled.
 */
public final class ArmorDrain
{
    private ArmorDrain()
    {
    }

    /**
     * The steady cost of a tick worn, before any walking. A limp frame has nothing left to spend.
     */
    public static long perTick(ArmorLoad load)
    {
        if (load.limp())
            return 0;
        long drain = ArmorBalance.STANDING_DRAIN;
        drain += load.exoskeletons() * ArmorBalance.EXOSKELETON_DRAIN;
        if (load.nightVision())
            drain += ArmorBalance.NIGHT_VISION_DRAIN;
        if (load.spectral())
            drain += ArmorBalance.SPECTRAL_DRAIN;
        return drain;
    }

    public static double walking(double blocks, boolean sprinting)
    {
        return Math.max(0, blocks) * (sprinting ? ArmorBalance.SPRINT_DRAIN_PER_BLOCK : ArmorBalance.WALK_DRAIN_PER_BLOCK);
    }

    /**
     * How many points of a fall's damage the legs take, as many as the charge pays for in whole points. The rest reaches the wearer.
     */
    public static int fallTaken(int damage, long charge)
    {
        if (damage <= 0 || charge <= 0)
            return 0;
        return (int) Math.min(damage, charge / ArmorBalance.FALL_DRAIN_PER_POINT);
    }

    public static long fallCost(int taken)
    {
        return taken * ArmorBalance.FALL_DRAIN_PER_POINT;
    }

    /**
     * How fast the frame refills the shield of the apparatus in its socket: faster than the apparatus alone while it has the charge to spare, and not at all
     * once it has cut its regen or gone limp, since inside the frame the apparatus draws on nothing else.
     */
    public static float shieldRegen(float regenPerTick, LoadShedding.Stage stage)
    {
        return stage.regen() ? regenPerTick * ArmorBalance.REGEN_BOOST : 0;
    }

    public static double shieldCost(float refilled)
    {
        return Math.max(0, refilled) * ArmorBalance.REGEN_DRAIN_PER_POINT;
    }
}
