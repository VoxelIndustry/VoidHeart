package net.voxelindustry.voidheart.client.render.oculus;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OculusCavingTest
{
    private static final int MIXED_TICKS = 80;

    @Test
    void withNoOneFollowedTheTableChoiceDecides()
    {
        var caving = new OculusCaving();
        caving.tick(0, true, false, false, MIXED_TICKS);
        assertThat(caving.goal(0)).isEqualTo(1);

        caving.tick(1, false, false, true, MIXED_TICKS);
        assertThat(caving.goal(1)).as("cover only counts while following").isZero();
    }

    @Test
    void followedPlayerGoingUnderCoverShowsBothThenTheHollows()
    {
        var caving = new OculusCaving();
        long tick = 0;
        caving.tick(tick, false, true, false, MIXED_TICKS);
        assertThat(caving.goal(tick)).isZero();

        // Briefly under a ledge: nothing changes
        for (int i = 0; i < OculusCaving.SETTLE_TICKS - 1; i++)
            caving.tick(++tick, false, true, true, MIXED_TICKS);
        caving.tick(++tick, false, true, false, MIXED_TICKS);
        assertThat(caving.goal(tick)).isZero();

        for (int i = 0; i < OculusCaving.SETTLE_TICKS; i++)
            caving.tick(++tick, false, true, true, MIXED_TICKS);
        assertThat(caving.goal(tick)).isEqualTo(OculusCaving.MIXED);
        assertThat(caving.goal(tick + MIXED_TICKS - 1)).isEqualTo(OculusCaving.MIXED);
        assertThat(caving.goal(tick + MIXED_TICKS)).isEqualTo(1);

        // Coming back out mixes again before settling on the surface
        tick += MIXED_TICKS;
        for (int i = 0; i < OculusCaving.SETTLE_TICKS; i++)
            caving.tick(++tick, false, true, false, MIXED_TICKS);
        assertThat(caving.goal(tick)).isEqualTo(OculusCaving.MIXED);
        assertThat(caving.goal(tick + MIXED_TICKS)).isZero();
    }

    @Test
    void startingToFollowSettlesAtOnce()
    {
        var caving = new OculusCaving();
        caving.tick(0, false, false, false, MIXED_TICKS);
        caving.tick(1, false, true, true, MIXED_TICKS);
        assertThat(caving.goal(1)).isEqualTo(1);
    }

    @Test
    void blendGlidesTowardItsGoal()
    {
        var caving = new OculusCaving();
        caving.tick(0, false, false, false, MIXED_TICKS);
        assertThat(caving.advance(0, 0.1F)).as("the first frame starts at the goal").isZero();

        caving.tick(1, true, false, false, MIXED_TICKS);
        float halfway = caving.advance(1, 0.2F);
        assertThat(halfway).isBetween(0.01F, 0.99F);
        assertThat(caving.advance(1, 10)).isEqualTo(1);
    }
}
