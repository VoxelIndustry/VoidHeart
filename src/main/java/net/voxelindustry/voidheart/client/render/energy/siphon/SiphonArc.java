package net.voxelindustry.voidheart.client.render.energy.siphon;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * One small leaping arc from an armillary's rings to a pylon, in the pylon arc's style: re-jagged every 2 to 3 ticks on its own timing, with leaps
 * running outward to the pylon while the siphon delivers. Advanced once per client tick.
 */
final class SiphonArc
{
    static final int   MIN_JOINTS    = 6;
    static final float JOINT_SPACING = 1.5F;
    static final float JAG           = 0.2F;
    static final int   JAG_TICKS_MIN = 2;
    static final int   JAG_TICKS_MAX = 3;

    static final float LEAP_LENGTH    = 1.2F;
    static final int   LEAP_TICKS_MIN = 6;
    static final int   LEAP_TICKS_MAX = 10;
    // Steady while it flows: the siphon delivers the same every tick
    static final float LEAPS_PER_TICK = 2 / 20F;

    static final int FADE_IN_TICKS  = 6;
    static final int FADE_OUT_TICKS = 12;

    static final class Leap
    {
        final int duration;
        int age;

        Leap(int duration)
        {
            this.duration = duration;
        }

        float progress(float partialTicks)
        {
            return Math.min(1, (age + partialTicks) / duration);
        }
    }

    private final RandomSource random;

    float[] offsets = new float[0];
    final List<Leap> leaps = new ArrayList<>();

    private int   joints;
    private int   untilJag;
    private float leapCredit;
    float fade;
    float previousFade;
    long  seenAt;

    SiphonArc(long seed)
    {
        random = RandomSource.create(seed);
        untilJag = 1 + random.nextInt(JAG_TICKS_MAX);
        // A first leap a little later on each arc, so the arcs of one armillary don't leap together
        leapCredit = random.nextFloat();
    }

    /**
     * @return whether a leap started this tick
     */
    boolean tick(double length, boolean flowing)
    {
        previousFade = fade;
        fade = flowing ? Math.min(1, fade + 1F / FADE_IN_TICKS) : Math.max(0, fade - 1F / FADE_OUT_TICKS);

        int wanted = Math.max(MIN_JOINTS, (int) Math.round(length / JOINT_SPACING));
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
        if (!flowing)
            return false;

        leapCredit += LEAPS_PER_TICK;
        if (leapCredit < 1)
            return false;
        // A little jitter, so leaps don't fall on an exact beat
        leapCredit -= 0.75F + random.nextFloat() * 0.5F;
        leaps.add(new Leap(LEAP_TICKS_MIN + random.nextInt(LEAP_TICKS_MAX - LEAP_TICKS_MIN + 1)));
        return true;
    }

    private void rejag()
    {
        for (int i = 0; i < offsets.length; i++)
            offsets[i] = (random.nextFloat() * 2 - 1) * JAG;
    }

    int joints()
    {
        return joints;
    }

    float fade(float partialTicks)
    {
        return previousFade + (fade - previousFade) * partialTicks;
    }
}
