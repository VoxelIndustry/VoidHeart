package net.voxelindustry.voidheart.common.content.crystal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScouredSoakTest
{
    @Test
    void capacity_isAboutTwoThirdsOfWhatScouringCost()
    {
        for (var size : BundleSize.values())
        {
            double cost = size.worth() * CradleBalance.TAINT_PER_WORTH;
            assertThat((double) ScouredSoak.capacity(size)).isCloseTo(cost * 2 / 3, org.assertj.core.data.Offset.offset(0.5));
            assertThat(ScouredSoak.capacity(size)).isLessThan((int) cost);
        }
    }

    @Test
    void step_drawsASoakFromATaintedNode()
    {
        var step = ScouredSoak.step(BundleSize.LARGE, 4, 30);
        assertThat(step.soaked()).isEqualTo(5);
        assertThat(step.taken()).isEqualTo(ScouredSoak.PER_SOAK);
        assertThat(step.fouled()).isFalse();
    }

    @Test
    void step_takesNothingFromANearlyCleanNode()
    {
        var step = ScouredSoak.step(BundleSize.MEDIUM, 2, 0.6F);
        assertThat(step.soaked()).isEqualTo(2);
        assertThat(step.taken()).isZero();
        assertThat(step.fouled()).isFalse();
    }

    @Test
    void step_turnsFouledOnceFull()
    {
        int soaked = 0;
        int taken = 0;
        int steps = 0;
        ScouredSoak.Step step;
        do
        {
            step = ScouredSoak.step(BundleSize.SMALL, soaked, 50);
            soaked = step.soaked();
            taken += step.taken();
            steps++;
        }
        while (!step.fouled());

        assertThat(taken).isEqualTo(ScouredSoak.capacity(BundleSize.SMALL));
        assertThat(steps).isEqualTo(ScouredSoak.capacity(BundleSize.SMALL));
        assertThat(step.soaked()).isZero();
    }

    @Test
    void step_mendsSoakPastWhatItHolds()
    {
        var step = ScouredSoak.step(BundleSize.SMALL, 99, 50);
        assertThat(step.fouled()).isTrue();
        assertThat(ScouredSoak.step(BundleSize.SMALL, -3, 50).soaked()).isEqualTo(1);
    }
}
