package net.voxelindustry.voidheart.common.energy.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Cause;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Phase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side timing of a reactor's looks, advanced once per client tick from what the server synced: the point and streams easing in and out, the
 * warning rising over the countdown and easing off after a recovery, and the leaping arcs to its pylons. The renderer only reads it.
 */
public final class ReactorAnimation
{
    // energy-looks.md: shutdown and recovery take about 40 ticks, the collapse 10
    public static final int   DRAIN_TICKS    = 40;
    public static final int   EASE_TICKS     = 40;
    public static final int   STREAM_TICKS   = 12;
    public static final int   FLARE_TICKS    = 18;
    public static final float PULSE_HZ_START = 0.5F;
    public static final float PULSE_HZ_END   = 4F;

    static final int   JAG_TICKS_MIN         = 2;
    static final int   JAG_TICKS_MAX         = 3;
    static final int   LEAP_TICKS_MIN        = 6;
    static final int   LEAP_TICKS_MAX        = 10;
    static final float LEAPS_PER_TICK_AT_CAP = 3 / 20F;

    /**
     * One leaping arc to a pylon's bundle: its jag seed, and the leaps running along it.
     */
    public static final class Arc
    {
        private final RandomSource random;
        private long  seed;
        private int   untilJag;
        private float leapCredit;
        private final List<float[]> leaps = new ArrayList<>();
        private int   seenAt;

        Arc(long seed)
        {
            random = RandomSource.create(seed);
            this.seed = random.nextLong();
            untilJag = 1 + random.nextInt(JAG_TICKS_MAX);
        }

        void tick(float load)
        {
            if (--untilJag <= 0)
            {
                seed = random.nextLong();
                untilJag = JAG_TICKS_MIN + random.nextInt(JAG_TICKS_MAX - JAG_TICKS_MIN + 1);
            }
            for (var leap : leaps)
                leap[0]++;
            leaps.removeIf(leap -> leap[0] >= leap[1]);

            if (load <= 0)
            {
                leapCredit = 0;
                return;
            }
            leapCredit += LEAPS_PER_TICK_AT_CAP * Math.min(1, load);
            if (leapCredit >= 1)
            {
                leapCredit -= 0.75F + random.nextFloat() * 0.5F;
                leaps.add(new float[]{0, LEAP_TICKS_MIN + random.nextInt(LEAP_TICKS_MAX - LEAP_TICKS_MIN + 1)});
            }
        }

        public long seed()
        {
            return seed;
        }

        /**
         * How far along each leap is, from 0 at the giving end to 1 at the receiving one.
         */
        public List<Float> leapHeads(float partialTicks)
        {
            var heads = new ArrayList<Float>(leaps.size());
            for (var leap : leaps)
                heads.add(Math.min(1, (leap[0] + partialTicks) / leap[1]));
            return heads;
        }
    }

    private int   time;
    private Phase phase = Phase.COLD;

    private float light, previousLight;
    private float streams, previousStreams;
    private float flare, previousFlare;
    private float warning, previousWarning;
    private float warnStrength, previousWarnStrength;
    private float dryContravoid, dryEssence, breach;
    private float collapse, previousCollapse;
    private float activity, previousActivity;
    private float charge, previousCharge;
    private float pulsePhase, previousPulsePhase;
    private int   countdown;
    private int   lastSyncedCountdown = -1;
    private boolean draining;

    private final Map<BlockPos, Arc> arcs = new HashMap<>();

    /**
     * @return whether it just lit, for the flare's sound
     */
    boolean tick(Phase newPhase, int causes, int syncedCountdown, float targetActivity, float targetCharge, boolean drawing, List<BlockPos> targets,
                 long seedBase)
    {
        time++;
        previousLight = light;
        previousStreams = streams;
        previousFlare = flare;
        previousWarning = warning;
        previousWarnStrength = warnStrength;
        previousCollapse = collapse;
        previousActivity = activity;
        previousCharge = charge;
        previousPulsePhase = pulsePhase;

        var justLit = newPhase.isRunning() && !phase.isRunning() && phase != Phase.COLLAPSING;
        if (justLit)
            flare = 1;
        if (!newPhase.isRunning() && phase.isRunning() && newPhase != Phase.COLLAPSING)
            draining = true;
        // Counted down here between syncs, and set again whenever the server sends a new value
        if (syncedCountdown != lastSyncedCountdown || newPhase == Phase.FAILING && phase != Phase.FAILING)
            countdown = syncedCountdown;
        lastSyncedCountdown = syncedCountdown;
        phase = newPhase;

        var running = phase.isRunning();
        if (running)
        {
            draining = false;
            streams = Math.min(1, streams + 1F / STREAM_TICKS);
            light = Math.min(1, light + 1F / STREAM_TICKS);
        }
        else if (phase == Phase.COLLAPSING)
            collapse = Math.min(1, collapse + 1F / ReactorBalance.COLLAPSE);
        else
        {
            // A safe shutdown: the streams stop at the intakes and drain into the point, which dims
            streams = Math.max(0, streams - 1F / DRAIN_TICKS);
            light = Math.max(0, light - 1F / DRAIN_TICKS);
            if (light == 0)
                draining = false;
        }
        flare = Math.max(0, flare - 1F / FLARE_TICKS);
        activity += (targetActivity - activity) * 0.1F;
        charge += (targetCharge - charge) * 0.2F;

        if (phase == Phase.FAILING)
        {
            countdown = Math.max(0, countdown - 1);
            warning = 1 - countdown / (float) ReactorBalance.COUNTDOWN;
            warnStrength = 1;
        }
        else if (phase == Phase.COLLAPSING)
            warning = 1;
        else
        {
            // Recovered or shut down: the pulses and the wobble ease off
            warnStrength = Math.max(0, warnStrength - 1F / EASE_TICKS);
            if (warnStrength == 0)
                warning = 0;
        }
        // A shutdown or collapse keeps a dry side dry while the rest drains away; only a recovery brings it back
        if (phase == Phase.FAILING || running)
        {
            dryContravoid = ease(dryContravoid, phase == Phase.FAILING && Cause.CONTRAVOID_DRY.in(causes));
            dryEssence = ease(dryEssence, phase == Phase.FAILING && Cause.ESSENCE_DRY.in(causes));
        }
        else if (light == 0)
        {
            dryContravoid = 0;
            dryEssence = 0;
        }
        breach = ease(breach, phase == Phase.FAILING && Cause.BREACH.in(causes));

        // The bands pulse from once every 2 seconds to four times a second over the countdown
        pulsePhase += Mth.lerp(warning, PULSE_HZ_START, PULSE_HZ_END) / 20F;

        tickArcs(targets, running ? activity : phase == Phase.IGNITING && drawing ? 1 : 0, seedBase);
        return justLit;
    }

    private static float ease(float value, boolean toward)
    {
        return toward ? Math.min(1, value + 1F / 10) : Math.max(0, value - 1F / EASE_TICKS);
    }

    private void tickArcs(List<BlockPos> targets, float load, long seedBase)
    {
        if (load <= 0 && light <= 0)
        {
            arcs.clear();
            return;
        }
        for (var target : targets)
            arcs.computeIfAbsent(target, key -> new Arc(seedBase ^ key.asLong())).seenAt = time;
        arcs.values().removeIf(arc -> arc.seenAt != time);
        for (var arc : arcs.values())
            arc.tick(load);
    }

    public Phase phase()
    {
        return phase;
    }

    public float time(float partialTicks)
    {
        return time + partialTicks;
    }

    public float light(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousLight, light);
    }

    public float streams(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousStreams, streams);
    }

    public boolean draining()
    {
        return draining;
    }

    public float flare(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousFlare, flare);
    }

    public float warning(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousWarning, warning);
    }

    public float warnStrength(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousWarnStrength, warnStrength);
    }

    public float dryContravoid()
    {
        return dryContravoid;
    }

    public float dryEssence()
    {
        return dryEssence;
    }

    public float breach()
    {
        return breach;
    }

    public float collapse(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousCollapse, collapse);
    }

    public float activity(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousActivity, activity);
    }

    public float charge(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousCharge, charge);
    }

    public float pulsePhase(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousPulsePhase, pulsePhase);
    }

    public int countdown()
    {
        return countdown;
    }

    public Map<BlockPos, Arc> arcs()
    {
        return arcs;
    }
}
