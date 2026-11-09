package net.voxelindustry.voidheart.common.energy.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client side rhythm of a Ravenous Engine, ticked by its tile and read by its renderer: how open the maw is, the flares and gulps of the burning aspect, and the
 * leaps along the output arc. Fiery flares often and briefly, Stillness has no pulses, and Suffering gulps rarely and heavily.
 */
public class EngineAnimation
{
    public static final int FLARE_MIN_TICKS = 6;
    public static final int FLARE_MAX_TICKS = 10;
    public static final int GULP_MIN_TICKS  = 30;
    public static final int GULP_MAX_TICKS  = 40;
    public static final int FLARE_TICKS     = 4;
    public static final int GULP_TICKS      = 12;

    private static final int HUM_PERIOD_TICKS = 80;

    // About 3 leaps a second at a full burn, none while idle
    private static final float LEAPS_PER_TICK_AT_FULL = 3 / 20F;
    private static final int   LEAP_MIN_TICKS         = 6;
    private static final int   LEAP_MAX_TICKS         = 10;
    private static final int   JAG_MIN_TICKS          = 2;
    private static final int   JAG_MAX_TICKS          = 3;

    private float open;
    private float prevOpen;
    @Nullable
    private EngineFuel displayedFuel;
    @Nullable
    private BlockPos   displayedTarget;
    private float      activity;

    private long time;
    private long lastPulse = Long.MIN_VALUE / 2;
    private long nextPulse;
    private int  pulses;

    private long jagSeed;
    private long nextJag;
    private final List<Leap> leaps = new ArrayList<>();

    /**
     * A brighter stretch running from the engine to what takes its energy.
     */
    public record Leap(long start, int duration)
    {
    }

    /**
     * @return whether a flare or gulp started on this tick
     */
    public boolean tick(Level level, @Nullable EngineFuel shown, float activity, @Nullable BlockPos target)
    {
        var random = level.getRandom();
        time = level.getGameTime();
        prevOpen = open;
        open = Mth.clamp(open + (shown != null ? 1F : -1F) / RavenousEngineTile.OPEN_TICKS, 0, 1);
        this.activity = shown != null ? activity : 0;

        if (shown != null)
        {
            if (displayedFuel != shown)
                nextPulse = time + interval(shown, random);
            displayedFuel = shown;
            if (target != null)
                displayedTarget = target;
        }
        else if (open <= 0 && prevOpen <= 0)
        {
            displayedFuel = null;
            displayedTarget = null;
        }

        if (time >= nextJag)
        {
            jagSeed = random.nextLong();
            nextJag = time + JAG_MIN_TICKS + random.nextInt(JAG_MAX_TICKS - JAG_MIN_TICKS + 1);
        }

        leaps.removeIf(leap -> time - leap.start() > leap.duration());
        if (shown != null && target != null && random.nextFloat() < this.activity * LEAPS_PER_TICK_AT_FULL)
            leaps.add(new Leap(time, LEAP_MIN_TICKS + random.nextInt(LEAP_MAX_TICKS - LEAP_MIN_TICKS + 1)));

        if (shown == null || shown == EngineFuel.STILLNESS || time < nextPulse)
            return false;
        lastPulse = time;
        pulses++;
        nextPulse = time + interval(shown, random);
        return true;
    }

    private static int interval(EngineFuel fuel, RandomSource random)
    {
        return switch (fuel)
        {
            case FIERY -> FLARE_MIN_TICKS + random.nextInt(FLARE_MAX_TICKS - FLARE_MIN_TICKS + 1);
            case SUFFERING -> GULP_MIN_TICKS + random.nextInt(GULP_MAX_TICKS - GULP_MIN_TICKS + 1);
            case STILLNESS -> Integer.MAX_VALUE / 2;
        };
    }

    public boolean humDue(long gameTime, BlockPos pos)
    {
        return Math.floorMod(gameTime + pos.hashCode(), HUM_PERIOD_TICKS) == 0;
    }

    /**
     * 0 with the plates shut over the opening, 1 wide open.
     */
    public float open(float partialTicks)
    {
        var eased = Mth.lerp(partialTicks, prevOpen, open);
        return eased * eased * (3 - 2 * eased);
    }

    /**
     * Progress through the current flare or gulp, from 0 as it starts to 1 once over.
     */
    public float pulse(float partialTicks)
    {
        if (displayedFuel == null || displayedFuel == EngineFuel.STILLNESS)
            return 1;
        var length = displayedFuel == EngineFuel.FIERY ? FLARE_TICKS : GULP_TICKS;
        return Mth.clamp((time - lastPulse + partialTicks) / length, 0, 1);
    }

    public int pulses()
    {
        return pulses;
    }

    @Nullable
    public EngineFuel displayedFuel()
    {
        return displayedFuel;
    }

    @Nullable
    public BlockPos displayedTarget()
    {
        return displayedTarget;
    }

    public float activity()
    {
        return activity;
    }

    public long time()
    {
        return time;
    }

    public long jagSeed()
    {
        return jagSeed;
    }

    public List<Leap> leaps()
    {
        return leaps;
    }
}
