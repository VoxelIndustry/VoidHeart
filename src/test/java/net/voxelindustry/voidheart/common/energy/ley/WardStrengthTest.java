package net.voxelindustry.voidheart.common.energy.ley;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WardStrengthTest
{
    @Test
    void fullySuppliedCleanWardHalvesTheRise()
    {
        assertThat(WardStrength.of(1, 0, 0)).isEqualTo(1);
        assertThat(WardStrength.multiplier(1)).isEqualTo(0.5F);
    }

    @Test
    void strengthFollowsSupply()
    {
        assertThat(WardStrength.of(0.74F, 0, 0)).isCloseTo(0.74F, within(1e-6F));
        assertThat(WardStrength.of(0, 0, 0)).isZero();
    }

    @Test
    void lineTaintWeakensTheWard()
    {
        assertThat(WardStrength.of(1, 50, 0)).isCloseTo(0.6F, within(1e-6F));
        assertThat(WardStrength.of(1, 100, 0)).isCloseTo(0.2F, within(1e-6F));
    }

    @Test
    void seepWeakensTheWard()
    {
        assertThat(WardStrength.of(1, 0, 0.5F)).isCloseTo(0.5F, within(1e-6F));
        assertThat(WardStrength.of(1, 0, 1)).isZero();
        assertThat(WardStrength.of(1, 50, 0.5F)).isCloseTo(0.3F, within(1e-6F));
    }

    @Test
    void wardNeverRaisesNorStopsCorruption()
    {
        assertThat(WardStrength.multiplier(0)).isEqualTo(1);
        assertThat(WardStrength.multiplier(5)).isEqualTo(0.5F);
        assertThat(WardStrength.multiplier(-1)).isEqualTo(1);
        assertThat(WardStrength.multiplier(Float.NaN)).isEqualTo(1);
    }

    @Test
    void outOfRangeInputsAreClamped()
    {
        assertThat(WardStrength.of(2, -10, -1)).isEqualTo(1);
        assertThat(WardStrength.of(Float.NaN, Float.NaN, Float.NaN)).isZero();
    }
}
