package net.voxelindustry.voidheart.client.armor;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * The shape a wing's filaments want to hold, in the frame's model space: two fans opening from the vents between its shoulder blades, the upper filaments
 * rising longest, the lower reaching out. The beat sweeps the fan down hard and draws it back up slowly, as a wing with weight does. Gravity, the air and
 * the wearer's movement are left to {@link WingSim}, which pulls each filament toward this shape. Pure, so it is unit tested.
 */
public final class WingShape
{
    public static final int FILAMENTS = 7;
    public static final int NODES     = 12;

    // Where the wings root, in model pixels: either side of the vents between the shoulder blades
    private static final float ROOT_X        = 2.0F / 16;
    private static final float ROOT_Y        = 2.4F / 16;
    private static final float ROOT_Z        = 4.3F / 16;
    private static final float ROOT_SPREAD   = 0.9F / 16;

    // The fan, in radians from straight up toward the wing's side: top filaments rise, the bottom ones reach out and a little down
    private static final float FAN_TOP       = 0.45F;
    private static final float FAN_BOTTOM    = 2.1F;
    private static final float FAN_JITTER    = 0.09F;
    /** How far the wings lean back from the frame, as a share of their reach. */
    private static final float BACK_LEAN     = 0.45F;

    // Lengths in model units, about 2 to 4 blocks at the frame's scale; the upper filaments are the longest
    private static final float LENGTH_MIN    = 1.4F;
    private static final float LENGTH_MAX    = 3.0F;
    private static final float LENGTH_JITTER = 0.3F;
    /** How far the shape itself arcs over along its length; gravity sags it further. */
    private static final float ARC           = 0.14F;
    private static final float ARC_BACK      = 0.6F;

    // The beat: a downstroke over the first share of each cycle and a slow recovery, sweeping the fan by this much at the top filaments
    private static final float BEAT_DOWN_SHARE = 0.32F;
    private static final float BEAT_SWEEP      = 0.42F;
    /** On the downstroke the wing also comes forward a little, as it pushes the air under it. */
    private static final float BEAT_FORWARD    = 0.35F;

    // Flying forward, the wings fold their fan and sweep back
    private static final float TRAIL_BACK    = 0.9F;
    private static final float TRAIL_FOLD    = 0.3F;

    // Unfurling: each filament waits its turn, top first
    private static final float STAGGER       = 0.35F;

    private WingShape()
    {
    }

    /**
     * Where the beat holds the wing, from -1 raised to 1 swept down, a share of the way through its cycle: down fast, back up slowly, easing at both ends.
     */
    public static float beat(float cycle)
    {
        float t = cycle - Mth.floor(cycle);
        if (t < BEAT_DOWN_SHARE)
            return -Mth.cos(Mth.PI * t / BEAT_DOWN_SHARE);
        return Mth.cos(Mth.PI * (t - BEAT_DOWN_SHARE) / (1 - BEAT_DOWN_SHARE));
    }

    /**
     * How far a filament has grown out, from how open the wing is: each waits its turn, top first, and eases in.
     */
    public static float growth(int index, float open)
    {
        float delay = place(index) * STAGGER;
        float growth = Mth.clamp((open - delay) / (1 - delay), 0, 1);
        return growth * growth * (3 - 2 * growth);
    }

    public static float place(int index)
    {
        return index / (float) (FILAMENTS - 1);
    }

    /**
     * Writes the points a filament wants to hold into {@code out}, in model space.
     *
     * @param side   -1 or 1
     * @param beat   from {@link #beat}
     * @param trail  0 hovering to 1 flying forward fast
     * @param growth from {@link #growth}
     * @return the filament's full length, in model units
     */
    public static float rest(int side, int index, int seed, float growth, float beat, float trail, Vector3f[] out)
    {
        float place = place(index);
        // The beat moves the upper filaments most, as a wing's leading edge
        float sweep = BEAT_SWEEP * beat * (1 - place * 0.5F);
        float angle = Mth.lerp(place, FAN_TOP, FAN_BOTTOM) * (1 - TRAIL_FOLD * trail) + (noise(seed, 3) - 0.5F) * 2 * FAN_JITTER + sweep;
        float length = Mth.lerp(place, LENGTH_MAX, LENGTH_MIN) + (noise(seed, 4) - 0.5F) * 2 * LENGTH_JITTER;
        float lean = BACK_LEAN + TRAIL_BACK * trail - BEAT_FORWARD * Math.max(0, beat);

        float rootX = side * (ROOT_X + place * ROOT_SPREAD);
        float rootY = ROOT_Y + place * ROOT_SPREAD;
        var reach = new Vector3f(side * Mth.sin(angle), -Mth.cos(angle), lean).normalize();
        for (int j = 0; j < out.length; j++)
        {
            float u = growth * j / (out.length - 1);
            float along = length * u;
            out[j].set(rootX + reach.x * along, rootY + reach.y * along + ARC * length * u * u, ROOT_Z + reach.z * along + ARC * ARC_BACK * length * u * u);
        }
        return length;
    }

    /**
     * Across the wing, out of its plane: the way a filament's ribbon faces, in model space.
     */
    public static Vector3f facing(int side, int index, int seed, Vector3f out)
    {
        float angle = Mth.lerp(place(index), FAN_TOP, FAN_BOTTOM);
        var reach = new Vector3f(side * Mth.sin(angle), -Mth.cos(angle), BACK_LEAN).normalize();
        var across = new Vector3f(reach).cross(0, 0, 1).normalize();
        return out.set(across).cross(reach).normalize();
    }

    /**
     * A steady pseudo-random value in [0, 1) for a filament and a purpose.
     */
    public static float noise(int seed, int salt)
    {
        int h = seed * 0x27D4EB2D + salt * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h >>> 8) / (float) (1 << 24);
    }

    public static int seed(int wearer, int side, int index)
    {
        return wearer + side * 7919 + index * 104_729;
    }
}
