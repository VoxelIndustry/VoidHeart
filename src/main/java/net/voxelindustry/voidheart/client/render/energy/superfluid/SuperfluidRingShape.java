package net.voxelindustry.voidheart.client.render.energy.superfluid;

import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidBalance;
import net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidRings;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Where a pearl's rings of superfluid lie at a moment, worked out from the pearl's seed and the time alone, so the pearl drawing its rings and a
 * cradle drawing the stream into the forming one agree on where its head is.
 * <p>
 * The rings lie near one plane tipped off the horizontal, like a planet's, each tipped a little further its own way and slowly precessing, so they read
 * as rings rather than an armillary's crossed hoops. Each turns about the pearl at its own speed, and wavers out of true, more while the pearl
 * shudders.
 */
public final class SuperfluidRingShape
{
    /** Ring radii, innermost first, clear of a mature pearl's buds. */
    static final float[] RADII = {7.5F / 16, 9.5F / 16, 11.5F / 16, 13.5F / 16};

    /** Degrees the common plane is tipped off the horizontal, and how far each ring tips further on its own. */
    private static final float TILT_MIN  = 10;
    private static final float TILT_MAX  = 22;
    private static final float RING_TILT = 7;

    /** Radians a tick each ring turns about the pearl, from one turn in 16 seconds to one in 9. */
    private static final double SPIN_MIN = Mth.TWO_PI / 320;
    private static final double SPIN_MAX = Mth.TWO_PI / 180;
    /** Radians a tick the planes precess, one turn in a minute and a half. */
    private static final double PRECESS  = Mth.TWO_PI / 1800;

    /** How far a ring wavers out of true at rest, and how much more at the height of the pearl's tremor, in and out and up and down. */
    private static final float  WAVER        = 0.25F / 16;
    private static final float  RIPPLE       = 1.1F / 16;
    private static final float  LIFT         = 0.2F / 16;
    private static final float  TREMOR_LIFT  = 0.7F / 16;
    /** Radians a tick the waver drifts round the ring. */
    private static final double WAVE_DRIFT   = 0.02;

    /**
     * One ring at one moment.
     *
     * @param start angle its tail starts at, in radians round e1 toward e2
     * @param thick its thickness against the others'
     */
    public record Frame(Vector3f center, Vector3f e1, Vector3f e2, Vector3f normal, float radius, double start, int waves, double phase, float thick)
    {
    }

    /**
     * Where the forming ring ends, and which way it runs on from there.
     */
    public record Head(Vector3f point, Vector3f tangent, int ring, float closed)
    {
    }

    private SuperfluidRingShape()
    {
    }

    public static Frame frame(long seed, int ring, double time, Vector3f center)
    {
        var baseAzimuth = Mth.TWO_PI * hash(seed, 0, 0) + time * PRECESS;
        var baseTilt = Mth.lerp(hash(seed, 0, 1), TILT_MIN, TILT_MAX) * Mth.DEG_TO_RAD;
        var ringAzimuth = Mth.TWO_PI * hash(seed, ring + 1, 2) + time * PRECESS * (0.5 + hash(seed, ring + 1, 3));
        var ringTilt = Mth.lerp(hash(seed, ring + 1, 4), -RING_TILT, RING_TILT) * Mth.DEG_TO_RAD;

        var turn = new Quaternionf().rotateAxis((float) ringTilt, (float) Math.cos(ringAzimuth), 0, (float) Math.sin(ringAzimuth))
                .mul(new Quaternionf().rotateAxis((float) baseTilt, (float) Math.cos(baseAzimuth), 0, (float) Math.sin(baseAzimuth)));
        var e1 = turn.transform(new Vector3f(1, 0, 0));
        var e2 = turn.transform(new Vector3f(0, 0, 1));
        var normal = turn.transform(new Vector3f(0, 1, 0));

        var spin = Mth.lerp(hash(seed, ring + 1, 6), SPIN_MIN, SPIN_MAX);
        var start = Mth.TWO_PI * hash(seed, ring + 1, 5) + time * spin;
        var waves = 3 + (int) (hash(seed, ring + 1, 7) * 3);
        var phase = Mth.TWO_PI * hash(seed, ring + 1, 8) + time * WAVE_DRIFT * (0.5 + hash(seed, ring + 1, 9));
        var thick = (float) Mth.lerp(hash(seed, ring + 1, 10), 0.8, 1.2);
        return new Frame(new Vector3f(center), e1, e2, normal, RADII[Math.clamp(ring, 0, RADII.length - 1)], start, waves, phase, thick);
    }

    public static Vector3f radial(Frame frame, double angle)
    {
        return new Vector3f(frame.e1()).mul((float) Math.cos(angle)).add(new Vector3f(frame.e2()).mul((float) Math.sin(angle)));
    }

    public static Vector3f point(Frame frame, double angle, float tremor)
    {
        var out = (float) ((WAVER + RIPPLE * tremor) * Math.sin(frame.waves() * angle + frame.phase()));
        var lift = (float) ((LIFT + TREMOR_LIFT * tremor) * Math.sin((frame.waves() + 1) * angle - frame.phase() * 1.3));
        return radial(frame, angle).mul(frame.radius() + out).add(new Vector3f(frame.normal()).mul(lift)).add(frame.center());
    }

    /**
     * Which way the ring runs at that angle, toward its head.
     */
    public static Vector3f tangent(Frame frame, double angle, float tremor)
    {
        var step = 1E-3;
        var ahead = point(frame, angle + step, tremor);
        return ahead.sub(point(frame, angle - step, tremor)).normalize();
    }

    /**
     * The head of the ring now forming: the end of the last ring begun, or the start of the first while there are none.
     */
    public static Head head(long seed, double units, double time, float tremor, Vector3f center)
    {
        return end(seed, units, time, tremor, center, true);
    }

    /**
     * The end of the last ring begun, even when it has just closed: where a draw-off unwinds from.
     */
    public static Head tail(long seed, double units, double time, float tremor, Vector3f center)
    {
        return end(seed, units, time, tremor, center, false);
    }

    private static Head end(long seed, double units, double time, float tremor, Vector3f center, boolean advance)
    {
        var ring = Math.max(0, SuperfluidRings.begun(units) - 1);
        var closed = SuperfluidRings.closed(units, ring);
        if (advance && closed >= 1 && ring < SuperfluidBalance.RINGS - 1)
        {
            ring++;
            closed = 0;
        }
        var frame = frame(seed, ring, time, center);
        var angle = frame.start() + Mth.TWO_PI * closed;
        return new Head(point(frame, angle, tremor), tangent(frame, angle, tremor), ring, closed);
    }

    /**
     * A number from 0 to 1 for this seed and these two indices, the same every frame.
     */
    static double hash(long seed, int a, int b)
    {
        var h = seed * 0x9E3779B97F4A7C15L + a * 0xBF58476D1CE4E5B9L + b * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        h *= 0x7FB5D329728EA185L;
        h ^= h >>> 27;
        h *= 0x81DADEF4BC2DD44DL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }
}
