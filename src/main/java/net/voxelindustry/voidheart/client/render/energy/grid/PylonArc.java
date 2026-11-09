package net.voxelindustry.voidheart.client.render.energy.grid;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Animation of one arc between two pylons, advanced once per client tick: its jag, the leaps running along it, and the forks it throws at cap.
 * Each arc rolls its own timings, so a chain never twitches in step. The Deep Siphon's arcs from an armillary use it too.
 */
public final class PylonArc
{
    static final int   MIN_JOINTS    = 6;
    static final float JOINT_SPACING = 1.5F;
    static final float JAG           = 0.25F;
    static final int   JAG_TICKS_MIN = 2;
    static final int   JAG_TICKS_MAX = 3;

    static final float LEAP_LENGTH           = 1.5F;
    static final int   LEAP_TICKS_MIN        = 6;
    static final int   LEAP_TICKS_MAX        = 10;
    static final float LEAPS_PER_TICK_AT_CAP = 3 / 20F;

    static final float AT_CAP           = 0.95F;
    static final int   FORK_EVERY_MIN   = 10;
    static final int   FORK_EVERY_MAX   = 20;
    static final float FORK_LENGTH_MIN  = 1;
    static final float FORK_LENGTH_MAX  = 2;
    static final int   FORK_TICKS_MIN   = 2;
    static final int   FORK_TICKS_MAX   = 3;
    static final int   FORK_JOINTS      = 3;
    static final float FORK_JAG         = 0.18F;

    /**
     * A bright stretch running from the giving pylon to the receiving one.
     */
    public static final class Leap
    {
        final int duration;
        int age;

        Leap(int duration)
        {
            this.duration = duration;
        }

        public float progress(float partialTicks)
        {
            return Math.min(1, (age + partialTicks) / duration);
        }
    }

    /**
     * A short branch leaving a joint, in the arc's own frame: along the arc, and on its two sides.
     */
    record Fork(int joint, float along, float side, float up, float length, float[] jag, int[] life)
    {
    }

    private final RandomSource random;
    private final float        jag;

    float[] offsets = new float[0];
    final List<Leap> leaps = new ArrayList<>();
    final List<Fork> forks = new ArrayList<>();

    private int   joints;
    private int   untilJag;
    private float leapCredit;
    private int   untilFork;
    long seenAt;

    PylonArc(long seed)
    {
        this(seed, JAG, false);
    }

    /**
     * @param jag       how far each joint strays from the straight line
     * @param staggered starts partway to its first leap, so the arcs one source throws together don't leap in step
     */
    public PylonArc(long seed, float jag, boolean staggered)
    {
        random = RandomSource.create(seed);
        this.jag = jag;
        untilJag = 1 + random.nextInt(JAG_TICKS_MAX);
        untilFork = FORK_EVERY_MIN + random.nextInt(FORK_EVERY_MAX - FORK_EVERY_MIN + 1);
        if (staggered)
            leapCredit = random.nextFloat();
    }

    static int jointsFor(double length)
    {
        return Math.max(MIN_JOINTS, (int) Math.round(length / JOINT_SPACING));
    }

    /**
     * @param load    flow over the smaller cap of the two ends, 0 when idle. Leaps come faster with it, and forks only near 1.
     * @return whether a leap started this tick
     */
    public boolean tick(double length, float load)
    {
        int wanted = jointsFor(length);
        if (wanted != joints)
        {
            joints = wanted;
            offsets = new float[joints * 2];
            rejag();
        }
        if (--untilJag <= 0)
        {
            rejag();
            untilJag = JAG_TICKS_MIN + random.nextInt(JAG_TICKS_MAX - JAG_TICKS_MIN + 1);
        }

        leaps.removeIf(leap -> ++leap.age >= leap.duration);
        forks.removeIf(fork -> --fork.life()[0] <= 0);

        boolean leapt = false;
        if (load <= 0)
            leapCredit = 0;
        else
        {
            leapCredit += LEAPS_PER_TICK_AT_CAP * Math.min(1, load);
            if (leapCredit >= 1)
            {
                // A little jitter, so leaps don't fall on an exact beat
                leapCredit -= 0.75F + random.nextFloat() * 0.5F;
                leaps.add(new Leap(LEAP_TICKS_MIN + random.nextInt(LEAP_TICKS_MAX - LEAP_TICKS_MIN + 1)));
                leapt = true;
            }
        }

        if (load >= AT_CAP && --untilFork <= 0)
        {
            int count = 1 + random.nextInt(2);
            for (int i = 0; i < count; i++)
                forks.add(fork());
            untilFork = FORK_EVERY_MIN + random.nextInt(FORK_EVERY_MAX - FORK_EVERY_MIN + 1);
        }
        return leapt;
    }

    private void rejag()
    {
        for (int i = 0; i < offsets.length; i++)
            offsets[i] = (random.nextFloat() * 2 - 1) * jag;
    }

    private Fork fork()
    {
        float angle = random.nextFloat() * (float) (Math.PI * 2);
        var jag = new float[FORK_JOINTS * 2];
        for (int i = 0; i < jag.length; i++)
            jag[i] = (random.nextFloat() * 2 - 1) * FORK_JAG;
        int life = FORK_TICKS_MIN + random.nextInt(FORK_TICKS_MAX - FORK_TICKS_MIN + 1);
        return new Fork(1 + random.nextInt(Math.max(1, joints - 1)), (random.nextFloat() - 0.5F) * 0.8F, (float) Math.cos(angle), (float) Math.sin(angle),
                FORK_LENGTH_MIN + random.nextFloat() * (FORK_LENGTH_MAX - FORK_LENGTH_MIN), jag, new int[] { life });
    }

    public int joints()
    {
        return joints;
    }

    /**
     * Each joint's stray on the arc's two sides, two floats a joint.
     */
    public float[] offsets()
    {
        return offsets;
    }

    /**
     * The leaps running along it now.
     */
    public List<Leap> leaps()
    {
        return leaps;
    }
}
