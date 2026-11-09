package net.voxelindustry.voidheart.common.ley.taint;

import net.voxelindustry.voidheart.common.ley.TaintStage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Checked against the simulation in docs/plans/energy-numbers.md, for a node converting a fixed share of its flow.
 */
class LeyTaintMathTest
{
    /**
     * The game steps taint once a second.
     */
    private static final double STEP_MINUTES = 1 / 60D;

    @Test
    void stagesStartAt20And50And80()
    {
        assertThat(LeyTaintMath.stage(0)).isEqualTo(TaintStage.CLEAN);
        assertThat(LeyTaintMath.stage(19.9F)).isEqualTo(TaintStage.CLEAN);
        assertThat(LeyTaintMath.stage(20)).isEqualTo(TaintStage.TAINTED);
        assertThat(LeyTaintMath.stage(50)).isEqualTo(TaintStage.FESTERING);
        assertThat(LeyTaintMath.stage(80)).isEqualTo(TaintStage.BREACHED);
        assertThat(LeyTaintMath.stage(100)).isEqualTo(TaintStage.BREACHED);
    }

    @Test
    void lightConversionGainsNothing()
    {
        assertThat(LeyTaintMath.gainPerMinute(0)).isZero();
        assertThat(LeyTaintMath.gainPerMinute(0.25)).isZero();
        assertThat(LeyTaintMath.gainPerMinute(0.5)).isCloseTo(2 / 9D, within(1.0E-9));
        assertThat(LeyTaintMath.gainPerMinute(1)).isEqualTo(2);
        assertThat(LeyTaintMath.gainPerMinute(3)).isEqualTo(2);
    }

    @Test
    void aQuarterOfTheFlowNeverTaints()
    {
        assertThat(convert(0.25, 0, 8 * 60)).isZero();
    }

    @Test
    void halfTheFlowSettlesAtTainted()
    {
        assertThat(convert(0.5, 0, 60)).isCloseTo(8, within(1F));
        float after8Hours = convert(0.5, 0, 8 * 60);
        assertThat(after8Hours).isCloseTo(20, within(1F));
        assertThat(convert(0.5, 0, 24 * 60)).isLessThan(21);
    }

    @Test
    void twoThirdsOfTheFlowSettlesAtFestering()
    {
        float after8Hours = convert(0.65, 0, 8 * 60);
        assertThat(after8Hours).isCloseTo(61, within(2F));
        assertThat(LeyTaintMath.stage(after8Hours)).isEqualTo(TaintStage.FESTERING);
    }

    @Test
    void threeQuartersOfTheFlowBreachesWithinHours()
    {
        assertThat(convert(0.75, 0, 8 * 60)).isCloseTo(98, within(2F));
        assertThat(minutesUntil(0.75, 80)).isBetween(3 * 60, 4 * 60);
    }

    @Test
    void aBurnedNodeHealsBackOverHours()
    {
        float burned = convert(1, 0, 4 * 60);
        assertThat(burned).isEqualTo(100);

        // N0: back under Festering 5.3 h and clean 9.7 h after the burn began
        int underFestering = 0, clean = 0;
        float taint = burned;
        for (int second = 1; clean == 0; second++)
        {
            taint = LeyTaintMath.step(taint, 0, STEP_MINUTES);
            if (underFestering == 0 && taint < 50)
                underFestering = second;
            if (taint <= 0)
                clean = second;
        }
        assertThat(4 + underFestering / 3600D).isCloseTo(5.3, within(0.1));
        assertThat(4 + clean / 3600D).isCloseTo(9.7, within(0.1));
    }

    @Test
    void healingNeverStops()
    {
        assertThat(LeyTaintMath.healPerMinute(0)).isZero();
        assertThat(LeyTaintMath.healPerMinute(0.001F)).isGreaterThan(0.05);
        assertThat(LeyTaintMath.step(0.0001F, 0, 1)).isZero();
    }

    @Test
    void saltHalvesTheGain()
    {
        assertThat(LeyTaintMath.saltFactor(true)).isEqualTo(0.5);
        assertThat(LeyTaintMath.saltFactor(false)).isEqualTo(1);
    }

    @Test
    void spreadPassesFortyPercentHopByHop()
    {
        // 1 -> 2 -> 3 -> 4 -> ...
        var spread = LeyTaintMath.spread(1, 2, node -> new long[] { node + 1 }, node -> false);

        assertThat(spread.get(2)).isCloseTo(0.8, within(1.0E-9));
        assertThat(spread.get(3)).isCloseTo(0.32, within(1.0E-9));
        assertThat(spread.get(4)).isCloseTo(0.128, within(1.0E-9));
        assertThat(spread).doesNotContainKey(1L);
    }

    @Test
    void spreadStopsBelowAHundredthPerMinute()
    {
        var spread = LeyTaintMath.spread(1, 0.1, node -> new long[] { node + 1 }, node -> false);

        // 0.04, 0.016 then 0.0064, which is too little to pass on
        assertThat(spread.keySet()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void aSuddenGainSpreadsAtMostFiveHops()
    {
        var spread = LeyTaintMath.spread(1, 3600, node -> new long[] { node + 1 }, node -> false);

        assertThat(spread.keySet()).containsExactlyInAnyOrder(2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void spreadSplitsToEveryDownstreamLineAndJoinsAgain()
    {
        // 1 flows to 2 and 3, which both flow to 4
        var graph = Map.of(1L, new long[] { 2, 3 }, 2L, new long[] { 4 }, 3L, new long[] { 4 });
        var spread = LeyTaintMath.spread(1, 1, node -> graph.getOrDefault(node, new long[0]), node -> false);

        assertThat(spread.get(2)).isCloseTo(0.4, within(1.0E-9));
        assertThat(spread.get(3)).isCloseTo(0.4, within(1.0E-9));
        assertThat(spread.get(4)).isCloseTo(0.32, within(1.0E-9));
    }

    @Test
    void saltSlowsWhatReachesASaltedNode()
    {
        var spread = LeyTaintMath.spread(1, 1, node -> new long[] { node + 1 }, node -> node == 2);

        assertThat(spread.get(2)).isCloseTo(0.2, within(1.0E-9));
        assertThat(spread.get(3)).isCloseTo(0.08, within(1.0E-9));
    }

    @Test
    void aBreachedNeighbourComesFromAFullBurn()
    {
        // N0: a node held at full conversion drives its direct downstream neighbours to Breached
        double neighbourGain = LeyTaintMath.spread(1, 2, node -> new long[] { node + 1 }, node -> false).get(2);
        float neighbour = 0;
        for (int second = 0; second < 8 * 3600; second++)
            neighbour = LeyTaintMath.step(neighbour, neighbourGain, STEP_MINUTES);

        assertThat(LeyTaintMath.stage(neighbour)).isEqualTo(TaintStage.BREACHED);
    }

    private static float convert(double share, float taint, int minutes)
    {
        for (int second = 0; second < minutes * 60; second++)
            taint = LeyTaintMath.step(taint, LeyTaintMath.gainPerMinute(share), STEP_MINUTES);
        return taint;
    }

    private static int minutesUntil(double share, float target)
    {
        float taint = 0;
        for (int second = 1; second < 24 * 3600; second++)
        {
            taint = LeyTaintMath.step(taint, LeyTaintMath.gainPerMinute(share), STEP_MINUTES);
            if (taint >= target)
                return second / 60;
        }
        return Integer.MAX_VALUE;
    }
}
