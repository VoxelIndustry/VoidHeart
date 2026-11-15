package net.voxelindustry.voidheart.common.content.secondheart;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a second heart reforms its player: right where they died, unless that spot would kill them again.
 */
public final class SafeSpot
{
    /** How far down a death spot may look for ground before it counts as over the void. */
    public static final int GROUND_SEARCH = 24;

    private SafeSpot()
    {
    }

    /**
     * What makes a spot deadly, worked out by the caller from the world.
     *
     * @param belowWorld below the dimension's floor
     * @param inLava     lava at the feet or the body
     * @param inWall     the body overlaps blocks
     * @param overVoid   no ground within {@link #GROUND_SEARCH} blocks below
     */
    public record Hazards(boolean belowWorld, boolean inLava, boolean inWall, boolean overVoid)
    {
        public static final Hazards NONE = new Hazards(false, false, false, false);

        public boolean safe()
        {
            return !belowWorld && !inLava && !inWall && !overVoid;
        }
    }

    /**
     * @param lastSafe where the player last stood on safe ground, if known
     * @param fallback where to go when neither is safe, such as the top of the world at the death spot
     */
    public static Vec3 choose(Vec3 death, Hazards hazards, @Nullable Vec3 lastSafe, Vec3 fallback)
    {
        if (hazards.safe())
            return death;
        if (lastSafe != null)
            return lastSafe;
        return fallback;
    }

    /**
     * @return whether a spot the player stands on can be remembered as safe
     */
    public static boolean remember(boolean onGround, boolean inFluid, Hazards hazards)
    {
        return onGround && !inFluid && hazards.safe();
    }
}
