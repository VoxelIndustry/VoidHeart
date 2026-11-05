package net.voxelindustry.voidheart.common.content.depths;

import net.minecraft.core.BlockPos;
import net.voxelindustry.voidheart.common.content.depths.SmokerCycle.Phase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class SmokerCycleTest
{
    private static final int SAMPLES = 400;

    private static long seedAt(int index)
    {
        return SmokerCycle.seed(new BlockPos(index * 3, 70 + index % 7, -index * 5));
    }

    @Test
    void everyCycle_warnsThenErupts_thenFallsQuiet()
    {
        for (int index = 0; index < SAMPLES; index++)
        {
            long seed = seedAt(index);
            var order = new ArrayList<Phase>();
            var ticks = new EnumMap<Phase, Integer>(Phase.class);
            long start = -SmokerCycle.ticksIntoCycle(seed, 0);
            for (long time = start; time < start + SmokerCycle.period(seed); time++)
            {
                var phase = SmokerCycle.phase(seed, time);
                ticks.merge(phase, 1, Integer::sum);
                if (order.isEmpty() || order.getLast() != phase)
                    order.add(phase);
            }

            assertThat(order).containsExactly(Phase.QUIET, Phase.WARNING, Phase.ERUPTING);
            assertThat(ticks.get(Phase.WARNING)).isEqualTo(SmokerCycle.WARNING_TICKS);
            assertThat(ticks.get(Phase.ERUPTING)).isEqualTo(SmokerCycle.ERUPTING_TICKS);
            assertThat(ticks.get(Phase.QUIET)).isBetween(SmokerCycle.MIN_QUIET_TICKS, SmokerCycle.MIN_QUIET_TICKS + SmokerCycle.QUIET_SPREAD_TICKS - 1);
        }
    }

    @Test
    void cycle_averagesTheAgreedRhythm()
    {
        long total = 0;
        for (int index = 0; index < SAMPLES; index++)
            total += SmokerCycle.period(seedAt(index));

        assertThat(total / (double) SAMPLES).isBetween(240.0, 260.0);
    }

    @Test
    void neighbouringSmokers_keepTheirOwnClocks()
    {
        var erupting = 0;
        var positions = new HashSet<Integer>();
        for (int index = 0; index < SAMPLES; index++)
        {
            long seed = seedAt(index);
            positions.add(SmokerCycle.ticksIntoCycle(seed, 1000));
            if (SmokerCycle.phase(seed, 1000) == Phase.ERUPTING)
                erupting++;
        }

        assertThat(positions.size()).isGreaterThan(100);
        assertThat(erupting).isBetween(SAMPLES / 8, SAMPLES / 2);
    }

    @Test
    void jets_areAboutSixBlocksTall()
    {
        for (int index = 0; index < SAMPLES; index++)
            assertThat(SmokerCycle.jetHeight(seedAt(index))).isBetween(5, 7);
    }
}
