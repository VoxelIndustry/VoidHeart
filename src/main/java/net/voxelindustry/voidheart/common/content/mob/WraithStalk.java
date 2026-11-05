package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.world.phys.Vec3;

/**
 * Where a Stalking Wraith wants to stand, and how long a stare it takes to set it off.
 */
public final class WraithStalk
{
    public static final double HOLD_NEAR = 5;
    public static final double HOLD_FAR = 6;
    public static final int CONTACT_TICKS = 30;
    public static final int UNNOTICED_TICKS = 20 * 20;

    private static final double HOLD_SPOT = (HOLD_NEAR + HOLD_FAR) / 2;

    private WraithStalk()
    {
    }

    /**
     * @return the spot behind someone looking along the given direction, at the height of their feet
     */
    public static Vec3 spotBehind(Vec3 feet, Vec3 look)
    {
        var flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-6)
            return feet;
        return feet.subtract(flat.normalize().scale(HOLD_SPOT));
    }

    public static boolean isBehind(Vec3 feet, Vec3 look, Vec3 position)
    {
        return new Vec3(look.x, 0, look.z).dot(position.subtract(feet)) < 0;
    }

    public static boolean isHolding(double distance, boolean behind)
    {
        return behind && distance >= HOLD_NEAR && distance <= HOLD_FAR;
    }

    /**
     * The stare builds as long as it lasts and drains at the same rate, so glancing back and forth cannot be used to keep the wraith waiting forever.
     */
    public static int nextContact(int contact, boolean eyeContact)
    {
        return eyeContact ? Math.min(contact + 1, CONTACT_TICKS) : Math.max(contact - 1, 0);
    }

    public static boolean rushes(int contact)
    {
        return contact >= CONTACT_TICKS;
    }

    public static boolean creeps(int unnoticedTicks)
    {
        return unnoticedTicks >= UNNOTICED_TICKS;
    }
}
