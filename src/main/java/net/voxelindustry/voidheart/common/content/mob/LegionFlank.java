package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.world.phys.Vec3;

/**
 * Keeps a pair of Legionaries on opposite sides of what they fight: one goes for its left and the other for its right, which also keeps one of them out of view.
 */
public final class LegionFlank
{
    public static final double WIDTH = 3;
    public static final double ENGAGE = 3.5;
    // Gives up on reaching its flank after this long, for when walls are in the way
    public static final int FLANK_PATIENCE_TICKS = 100;
    public static final double DISENGAGE = ENGAGE + 1.5;
    public static final double SPOT_REACHED = 1.5;

    private LegionFlank()
    {
    }

    /**
     * @param look where the target faces
     * @param side 1 for its right and -1 for its left
     */
    public static Vec3 spot(Vec3 target, Vec3 look, int side)
    {
        var flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-6)
            flat = new Vec3(0, 0, 1);
        var right = new Vec3(-flat.z, 0, flat.x).normalize();
        return target.add(right.scale(WIDTH * side));
    }

    /**
     * Once it reaches its flank, so that it comes in from the side rather than from wherever it happened to approach. It stays engaged until pushed well away, so
     * that the target turning on the spot does not send it back and forth.
     */
    public static boolean shouldEngage(Vec3 target, Vec3 self, Vec3 spot, boolean engaged)
    {
        if (engaged)
            return new Vec3(self.x - target.x, 0, self.z - target.z).length() <= DISENGAGE;
        return new Vec3(self.x - spot.x, 0, self.z - spot.z).length() <= SPOT_REACHED;
    }
}
