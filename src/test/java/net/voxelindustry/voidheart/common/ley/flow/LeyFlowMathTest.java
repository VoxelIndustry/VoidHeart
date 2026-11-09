package net.voxelindustry.voidheart.common.ley.flow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LeyFlowMathTest
{
    private static final double POWER = 17;

    @Test
    void tideSwellsAtFullMoonAndEbbsAtNewMoon()
    {
        assertThat(LeyFlowMath.tide(0)).isCloseTo(1.25, within(1.0E-9));
        assertThat(LeyFlowMath.tide(4)).isCloseTo(0.75, within(1.0E-9));
        assertThat(LeyFlowMath.tide(2)).isCloseTo(1, within(1.0E-9));
        assertThat(LeyFlowMath.tide(6)).isCloseTo(1, within(1.0E-9));
        assertThat(LeyFlowMath.tide(8)).isEqualTo(LeyFlowMath.tide(0));
    }

    @Test
    void tideAveragesOutOverAMonth()
    {
        double sum = 0;
        for (int phase = 0; phase < LeyFlowMath.MOON_PHASES; phase++)
            sum += LeyFlowMath.tide(phase);

        assertThat(sum / LeyFlowMath.MOON_PHASES).isCloseTo(1, within(1.0E-9));
    }

    @Test
    void awakeningRaisesFlowByHalf()
    {
        assertThat(LeyFlowMath.nodeFlow(POWER, 2, true, 0)).isCloseTo(POWER * 1.5, within(1.0E-9));
        assertThat(LeyFlowMath.nodeFlow(POWER, 2, false, 0)).isCloseTo(POWER, within(1.0E-9));
    }

    @Test
    void fullTaintHalvesFlow()
    {
        assertThat(LeyFlowMath.nodeFlow(POWER, 2, false, 100)).isCloseTo(POWER / 2, within(1.0E-9));
        assertThat(LeyFlowMath.nodeFlow(POWER, 2, false, 50)).isCloseTo(POWER * 0.75, within(1.0E-9));
        assertThat(LeyFlowMath.taintFactor(250)).isEqualTo(LeyFlowMath.taintFactor(100));
    }

    @Test
    void factorsMultiply()
    {
        assertThat(LeyFlowMath.nodeFlow(POWER, 0, true, 100)).isCloseTo(POWER * 1.25 * 1.5 * 0.5, within(1.0E-9));
    }

    @Test
    void lineCarriesAnEvenShareOfWhatTheNodeKeeps()
    {
        assertThat(LeyFlowMath.lineFlow(12, 0, 2)).isEqualTo(6);
        assertThat(LeyFlowMath.lineFlow(12, 4, 2)).isEqualTo(4);
        assertThat(LeyFlowMath.lineFlow(12, 20, 2)).isZero();
        assertThat(LeyFlowMath.lineFlow(12, 0, 0)).isZero();
    }
}
