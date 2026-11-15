package net.voxelindustry.voidheart.common.content.sanctum;

import net.minecraft.world.phys.Vec3;

import static net.voxelindustry.voidheart.common.content.sanctum.SanctumBalance.BILE_SPEED;

/**
 * How a glob of bile flies, and how to throw one so it lands on a spot. Each tick it moves by its velocity, then its speed along the ground keeps
 * {@link #DRAG} of itself and gravity takes {@link #GRAVITY} from its vertical speed.
 */
public final class BileArc
{
    public static final double GRAVITY = 0.04;
    public static final double DRAG = 0.99;
    /** Fewest ticks a throw spends in the air, so a near target still gets an arc. */
    private static final int MIN_TICKS = 8;

    private BileArc()
    {
    }

    /** @return a velocity that lands on a spot, at about the glob's speed along the ground */
    public static Vec3 launch(Vec3 from, Vec3 to)
    {
        double dx = to.x - from.x, dz = to.z - from.z, dy = to.y - from.y;
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1e-3)
            return new Vec3(0, BILE_SPEED, 0);
        int ticks = (int) Math.max(MIN_TICKS, Math.ceil(flat / (BILE_SPEED * 0.7)));
        // Over n ticks from a start speed v it covers v (1 - DRAG^n) / (1 - DRAG) along the ground, and climbs vy n - g n (n - 1) / 2
        double reach = (1 - Math.pow(DRAG, ticks)) / (1 - DRAG);
        double vy = (dy + GRAVITY * ticks * (ticks - 1) / 2) / ticks;
        return new Vec3(dx / reach, vy, dz / reach);
    }

    /** @return the velocity one tick later */
    public static Vec3 step(Vec3 velocity)
    {
        return new Vec3(velocity.x * DRAG, velocity.y - GRAVITY, velocity.z * DRAG);
    }
}
