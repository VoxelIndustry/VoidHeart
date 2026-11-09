package net.voxelindustry.voidheart.common.energy.fissure;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client side motion of a fissure or wound, ticked by its tile and read by its renderer: its edges crawling a few joints at a time, a wound's rim
 * twitching, and the leaps along a fissure's output arc.
 */
public class FissureAnimation
{
    /**
     * Joints along the crack's length, each with an offset for both edges.
     */
    public static final int JOINTS = 14;

    private static final int   CRAWL_MIN_TICKS   = 10;
    private static final int   CRAWL_MAX_TICKS   = 20;
    private static final int   CRAWL_JOINTS_MIN  = 2;
    private static final int   CRAWL_JOINTS_MAX  = 3;
    private static final float CRAWL_EASE        = 0.2F;
    private static final float TWITCH_CHANCE     = 0.08F;
    private static final int   TWITCH_TICKS      = 3;

    private static final float LEAPS_PER_TICK_AT_FULL = 3 / 20F;
    private static final int   LEAP_MIN_TICKS         = 6;
    private static final int   LEAP_MAX_TICKS         = 10;
    private static final int   JAG_MIN_TICKS          = 2;
    private static final int   JAG_MAX_TICKS          = 3;

    /**
     * Where each edge's joints are heading and where they stand, from -1 to 1 of the jag allowed. Index 2 × joint for one edge, + 1 for the other.
     */
    private final float[] target  = new float[JOINTS * 2];
    private final float[] current = new float[JOINTS * 2];
    private final float[] previous = new float[JOINTS * 2];
    private boolean seeded;
    private long    nextCrawl;

    private long time;
    private long twitchUntil;
    private long twitchSeed;

    @Nullable
    private BlockPos displayedTarget;
    private float    activity;
    private long     arcSeed;
    private long     nextArcJag;
    private final List<Leap> leaps = new ArrayList<>();

    public record Leap(long start, int duration)
    {
    }

    public void tick(Level level, VoidFissureTile tile)
    {
        var random = level.getRandom();
        time = level.getGameTime();
        if (!seeded)
            seed(tile.seed());

        System.arraycopy(current, 0, previous, 0, current.length);
        if (time >= nextCrawl)
        {
            int count = CRAWL_JOINTS_MIN + random.nextInt(CRAWL_JOINTS_MAX - CRAWL_JOINTS_MIN + 1);
            for (int i = 0; i < count; i++)
                target[random.nextInt(target.length)] = random.nextFloat() * 2 - 1;
            nextCrawl = time + CRAWL_MIN_TICKS + random.nextInt(CRAWL_MAX_TICKS - CRAWL_MIN_TICKS + 1);
        }
        for (int i = 0; i < current.length; i++)
            current[i] += (target[i] - current[i]) * CRAWL_EASE;

        if (tile.isWound() && time >= twitchUntil && random.nextFloat() < TWITCH_CHANCE)
        {
            twitchUntil = time + TWITCH_TICKS;
            twitchSeed = random.nextLong();
        }

        var shownTarget = tile.outputTarget();
        activity = shownTarget != null ? tile.activity() / (float) VoidFissureTile.ACTIVITY_STEPS : 0;
        if (shownTarget != null)
            displayedTarget = shownTarget;
        else if (leaps.isEmpty())
            displayedTarget = null;

        if (time >= nextArcJag)
        {
            arcSeed = random.nextLong();
            nextArcJag = time + JAG_MIN_TICKS + random.nextInt(JAG_MAX_TICKS - JAG_MIN_TICKS + 1);
        }
        leaps.removeIf(leap -> time - leap.start() > leap.duration());
        if (shownTarget != null && random.nextFloat() < activity * LEAPS_PER_TICK_AT_FULL)
            leaps.add(new Leap(time, LEAP_MIN_TICKS + random.nextInt(LEAP_MAX_TICKS - LEAP_MIN_TICKS + 1)));
    }

    private void seed(long seed)
    {
        var random = RandomSource.create(seed);
        for (int i = 0; i < target.length; i++)
            target[i] = current[i] = previous[i] = random.nextFloat() * 2 - 1;
        seeded = true;
    }

    /**
     * An edge joint's offset between ticks, from -1 to 1 of the jag allowed.
     */
    public float jag(int joint, int edge, float partialTicks)
    {
        int index = joint * 2 + edge;
        return Mth.lerp(partialTicks, previous[index], current[index]);
    }

    /**
     * Non zero during one of a wound's rim twitches: a seed for the twitch's shape.
     */
    public long twitch()
    {
        return time < twitchUntil ? twitchSeed : 0;
    }

    public boolean isSeeded()
    {
        return seeded;
    }

    public long time()
    {
        return time;
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

    public long arcSeed()
    {
        return arcSeed;
    }

    public List<Leap> leaps()
    {
        return leaps;
    }
}
