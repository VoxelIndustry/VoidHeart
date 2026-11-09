package net.voxelindustry.voidheart.common.energy.spike;

import net.voxelindustry.voidheart.common.ley.TaintStage;
import net.voxelindustry.voidheart.common.ley.flow.LeyFlowMath;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaintMath;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * A spike against the taint curve of docs/plans/energy-numbers.md: a node is stepped once a second, its flow cut by its own taint, and the spike claims
 * its setting's share of that flow every step, as the game does.
 */
class LeySpikeMathTest
{
    private static final double STEP_MINUTES = 1 / 60D;
    private static final int    HOUR         = 60 * 60;
    // A p90 node of the real lattice, and the median one (energy-numbers.md)
    private static final double P90_POWER    = 17.6;
    private static final double MEDIAN_POWER = 7.8;
    // Full moon is phase 0, new moon phase 4
    private static final int    FULL_MOON    = 0;
    private static final int    QUARTER_MOON = 2;
    private static final int    NEW_MOON     = 4;

    private static final int LIGHT  = 0;
    private static final int HALF   = 1;
    private static final int HEAVY  = 3;

    /**
     * Taint after converting for the given seconds, from clean, on a node with no neighbours.
     */
    private static float bleed(double power, int moonPhase, int setting, int seconds, float start)
    {
        float taint = start;
        for (int second = 0; second < seconds; second++)
        {
            double flow = LeyFlowMath.nodeFlow(power, moonPhase, false, taint);
            double ley = LeySpikeMath.targetLey(flow, setting);
            taint = LeyTaintMath.step(taint, LeyTaintMath.gainPerMinute(ley / flow), STEP_MINUTES);
        }
        return taint;
    }

    @Test
    void lightConversionStaysUnderHealing()
    {
        for (int phase = 0; phase < LeyFlowMath.MOON_PHASES; phase++)
        {
            assertThat(bleed(P90_POWER, phase, LIGHT, 8 * HOUR, 0)).as("light on a p90 node, moon phase %d", phase).isZero();
            assertThat(bleed(MEDIAN_POWER, phase, LIGHT, 8 * HOUR, 0)).as("light on a median node, moon phase %d", phase).isZero();
        }
        // A node already bruised keeps healing under light conversion
        assertThat(bleed(P90_POWER, NEW_MOON, LIGHT, HOUR, 30)).isLessThan(30 - 10);
    }

    @Test
    void heavyConversionClimbs()
    {
        // At a quarter moon the tide is even, and the 12 ley cap is about 70% of a p90 node: Tainted within the hour
        float hour = bleed(P90_POWER, QUARTER_MOON, HEAVY, HOUR, 0);
        assertThat(LeyTaintMath.stage(hour)).isEqualTo(TaintStage.TAINTED);
        assertThat(bleed(P90_POWER, QUARTER_MOON, HEAVY, 2 * HOUR, 0)).isGreaterThan(hour);

        // At full moon the cap is only 55% of its flow, which still climbs as the taint cuts the flow under it
        float full = bleed(P90_POWER, FULL_MOON, HEAVY, HOUR, 0);
        float eight = bleed(P90_POWER, FULL_MOON, HEAVY, 8 * HOUR, 0);
        assertThat(full).isGreaterThan(5).isLessThan(hour);
        assertThat(LeyTaintMath.stage(eight)).isEqualTo(TaintStage.BREACHED);

        // On a weak node too: the whole flow is under the cap, so it climbs at the full gain
        assertThat(LeyTaintMath.stage(bleed(MEDIAN_POWER, NEW_MOON, HEAVY, 4 * HOUR, 0))).isEqualTo(TaintStage.BREACHED);
    }

    @Test
    void halfSettlesTainted()
    {
        // energy-numbers.md: 50% settles at Tainted, about 20, and never breaches
        float taint = bleed(P90_POWER, FULL_MOON, HALF, 8 * HOUR, 0);
        assertThat(taint).isCloseTo(20, within(1.5F));
        assertThat(bleed(P90_POWER, FULL_MOON, HALF, 24 * HOUR, 0)).isLessThan(25);
    }

    @Test
    void claimIsAShareCappedAt12Ley()
    {
        assertThat(LeySpikeMath.targetLey(7.8, LIGHT)).isCloseTo(1.95, within(1e-9));
        assertThat(LeySpikeMath.targetLey(17.6, LIGHT)).isCloseTo(4.4, within(1e-9));
        assertThat(LeySpikeMath.targetLey(17.6, HEAVY)).isEqualTo(LeySpikeBalance.MAX_LEY);
        assertThat(LeySpikeMath.targetLey(0, HEAVY)).isZero();
        assertThat(LeySpikeMath.targetLey(-1, HEAVY)).isZero();
        assertThat(LeySpikeMath.share(-3)).isEqualTo(0.25);
        assertThat(LeySpikeMath.share(9)).isEqualTo(1);
    }

    @Test
    void convertsEverythingTakenAndCarriesTheFraction()
    {
        // Light on the median node: 1.95 ley is 15.6 VE/t, given as 15 and 16 so that it averages 15.6
        double credit = 0;
        long energy = 0;
        double ley = 0;
        for (int tick = 0; tick < 100; tick++)
        {
            var step = LeySpikeMath.step(1.95, credit, amount -> amount);
            assertThat(step.ley()).isEqualTo(1.95);
            assertThat(step.credit()).isBetween(0D, 1D);
            credit = step.credit();
            energy += step.energy();
            ley += step.ley();
        }
        assertThat(energy).isEqualTo(1560);
        assertThat(ley).isCloseTo(195, within(1e-6));
    }

    @Test
    void convertsOnlyWhatIsTaken()
    {
        var none = LeySpikeMath.step(12, 0, amount -> 0);
        assertThat(none.ley()).isZero();
        assertThat(none.energy()).isZero();
        assertThat(none.credit()).isZero();

        var part = LeySpikeMath.step(12, 0, amount -> 40);
        assertThat(part.energy()).isEqualTo(40);
        assertThat(part.ley()).isCloseTo(5, within(1e-9));
        assertThat(part.credit()).isZero();

        // A credit is offered first, and only what it doesn't cover is converted
        var credited = LeySpikeMath.step(12, 0.75, amount -> 8);
        assertThat(credited.ley()).isCloseTo((8 - 0.75) / 8, within(1e-9));
        assertThat(credited.credit()).isZero();

        // Nothing granted, but a whole VE of credit is still offered
        var fromCredit = LeySpikeMath.step(0, 1.5, amount -> amount);
        assertThat(fromCredit.energy()).isEqualTo(1);
        assertThat(fromCredit.ley()).isZero();
        assertThat(fromCredit.credit()).isCloseTo(0.5, within(1e-9));

        // An offer answering more than it was asked is held to what was offered
        var greedy = LeySpikeMath.step(1, 0, amount -> amount * 10);
        assertThat(greedy.energy()).isEqualTo(8);
        assertThat(greedy.ley()).isEqualTo(1);
    }

    @Test
    void contravoidFollowsTaint()
    {
        assertThat(LeySpikeMath.contravoidPerTick(12, 0)).isZero();
        assertThat(LeySpikeMath.contravoidPerTick(0, 100)).isZero();
        assertThat(LeySpikeMath.contravoidPerTick(10, 100)).isCloseTo(0.85, within(1e-9));
        assertThat(LeySpikeMath.contravoidPerTick(10, 50)).isCloseTo(0.425, within(1e-9));
        assertThat(LeySpikeMath.contravoidPerTick(10, 150)).isCloseTo(0.85, within(1e-9));
    }

    @Test
    void heavyBleedingYieldsContravoidOnceBruised()
    {
        // Bleed a p90 node heavily, then light: Contravoid follows the node's bruise in and out
        var perHour = new ArrayList<Double>();
        float taint = 0;
        for (int hour = 0; hour < 6; hour++)
        {
            double contravoid = 0;
            for (int second = 0; second < HOUR; second++)
            {
                double flow = LeyFlowMath.nodeFlow(P90_POWER, FULL_MOON, false, taint);
                double ley = LeySpikeMath.targetLey(flow, HEAVY);
                contravoid += LeySpikeMath.contravoidPerTick(ley, taint) * 20;
                taint = LeyTaintMath.step(taint, LeyTaintMath.gainPerMinute(ley / flow), STEP_MINUTES);
            }
            perHour.add(contravoid);
        }
        for (int hour = 1; hour < perHour.size(); hour++)
            assertThat(perHour.get(hour)).as("hour %d", hour).isGreaterThanOrEqualTo(perHour.get(hour - 1) * 0.99);
        assertThat(perHour.getFirst()).isGreaterThan(0);
    }
}
