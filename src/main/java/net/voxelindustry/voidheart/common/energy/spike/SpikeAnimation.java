package net.voxelindustry.voidheart.common.energy.spike;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client side rhythm of an Azurium Spike, ticked by its tile and read by its renderer: the bands of light climbing the stake, more often the more it
 * converts, and the leaps along its output arc.
 */
public class SpikeAnimation
{
    public static final int FADE_TICKS = 10;

    // Bands: one every 30 ticks at the lightest conversion, about 3 a second at the most, each taking about a second to climb the stake
    private static final float BANDS_PER_TICK_MIN = 1 / 30F;
    private static final float BANDS_PER_TICK_MAX = 3 / 20F;
    private static final int   CLIMB_TICKS_SLOW   = 28;
    private static final int   CLIMB_TICKS_FAST   = 16;

    // About 3 leaps a second at the most, none while idle, as on the pylons' arcs
    private static final float LEAPS_PER_TICK_AT_FULL = 3 / 20F;
    private static final int   LEAP_MIN_TICKS         = 6;
    private static final int   LEAP_MAX_TICKS         = 10;
    private static final int   JAG_MIN_TICKS          = 2;
    private static final int   JAG_MAX_TICKS          = 3;

    /**
     * A band of light climbing the stake, or a brighter stretch running along the arc to what takes the energy.
     */
    public record Pulse(long start, int duration)
    {
        public float progress(float time)
        {
            return Mth.clamp((time - start) / duration, 0, 1);
        }
    }

    private float fade;
    private float prevFade;
    private float activity;
    @Nullable
    private BlockPos displayedTarget;

    private long  time;
    private float bandCredit;
    private long  jagSeed;
    private long  nextJag;
    private final List<Pulse> bands = new ArrayList<>();
    private final List<Pulse> leaps = new ArrayList<>();

    public void tick(Level level, float activity, @Nullable BlockPos target)
    {
        var random = level.getRandom();
        time = level.getGameTime();
        prevFade = fade;
        var active = activity > 0;
        fade = Mth.clamp(fade + (active ? 1F : -1F) / FADE_TICKS, 0, 1);
        if (active)
            this.activity = activity;
        if (active && target != null)
            displayedTarget = target;
        else if (fade <= 0 && prevFade <= 0)
            displayedTarget = null;

        if (time >= nextJag)
        {
            jagSeed = random.nextLong();
            nextJag = time + JAG_MIN_TICKS + random.nextInt(JAG_MAX_TICKS - JAG_MIN_TICKS + 1);
        }

        bands.removeIf(band -> time - band.start() > band.duration());
        leaps.removeIf(leap -> time - leap.start() > leap.duration());
        if (!active)
        {
            bandCredit = 0;
            return;
        }

        // Evenly paced rather than random, so a steady conversion reads as a steady climb
        bandCredit += Mth.lerp(activity, BANDS_PER_TICK_MIN, BANDS_PER_TICK_MAX);
        if (bandCredit >= 1)
        {
            bandCredit -= 1;
            bands.add(new Pulse(time, Math.round(Mth.lerp(activity, CLIMB_TICKS_SLOW, CLIMB_TICKS_FAST))));
        }
        if (target != null && random.nextFloat() < activity * LEAPS_PER_TICK_AT_FULL)
            leaps.add(new Pulse(time, LEAP_MIN_TICKS + random.nextInt(LEAP_MAX_TICKS - LEAP_MIN_TICKS + 1)));
    }

    /**
     * 0 plain crystal, 1 fully lit.
     */
    public float fade(float partialTicks)
    {
        return Mth.lerp(partialTicks, prevFade, fade);
    }

    public float activity()
    {
        return activity;
    }

    @Nullable
    public BlockPos displayedTarget()
    {
        return displayedTarget;
    }

    public long time()
    {
        return time;
    }

    public long jagSeed()
    {
        return jagSeed;
    }

    public List<Pulse> bands()
    {
        return bands;
    }

    public List<Pulse> leaps()
    {
        return leaps;
    }
}
