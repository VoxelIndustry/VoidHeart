package net.voxelindustry.voidheart.common.content.levitation;

import org.junit.jupiter.api.Test;

import static net.voxelindustry.voidheart.common.content.levitation.LevitationBalance.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BeltLiftTest
{
    private static final double GRAVITY = 0.08;

    /** Blocks fallen from a standstill over the given ticks of a fading lift. */
    private static double fallen(int ticks)
    {
        double y = 0;
        double velocity = 0;
        for (int tick = 0; tick < ticks; tick++)
        {
            y += velocity;
            velocity = BeltLift.step(velocity, tick, GRAVITY);
        }
        return -y;
    }

    @Test
    void startsAsADrift()
    {
        assertThat(BeltLift.gravityScale(0, true)).isEqualTo(FADE_GRAVITY_START);
        assertThat(BeltLift.maxDescent(0)).isEqualTo(FADE_DRIFT);
        assertThat(fallen(20)).as("the first second only drifts down").isLessThan(1.5);
    }

    @Test
    void speedsUpUntilAPlainFall()
    {
        assertThat(BeltLift.maxDescent(FADE_TICKS / 2)).isGreaterThan(BeltLift.maxDescent(FADE_TICKS / 4));
        assertThat(BeltLift.maxDescent(FADE_TICKS - 1)).isGreaterThan(BeltLift.maxDescent(FADE_TICKS / 2));
        assertThat(BeltLift.fading(FADE_TICKS)).isFalse();
        assertThat(BeltLift.gravityScale(FADE_TICKS, true)).isEqualTo(1);
        assertThat(BeltLift.maxDescent(FADE_TICKS)).isInfinite();
        assertThat(BeltLift.step(-1, FADE_TICKS, GRAVITY)).isCloseTo((-1 - GRAVITY) * 0.98, within(1e-9));
    }

    @Test
    void fallsFasterAsItFades()
    {
        double firstHalf = fallen(FADE_TICKS / 2);
        double secondHalf = fallen(FADE_TICKS) - firstHalf;
        assertThat(secondHalf).isGreaterThan(firstHalf * 3);
        assertThat(fallen(FADE_TICKS)).as("leaving a field high up is dangerous").isGreaterThan(30);
    }

    @Test
    void risingFeelsAllOfGravity()
    {
        assertThat(BeltLift.gravityScale(0, false)).isEqualTo(1);
        assertThat(BeltLift.step(0.4, 0, GRAVITY)).isCloseTo((0.4 - GRAVITY) * 0.98, within(1e-9));
    }

    @Test
    void sinkingFasterIsSlowedToTheDrift()
    {
        assertThat(BeltLift.step(-0.5, 0, GRAVITY)).isEqualTo(-FADE_DRIFT);
    }

    @Test
    void cushionsOnlyTheFirstPart()
    {
        assertThat(BeltLift.cushioned(0)).isTrue();
        assertThat(BeltLift.cushioned((long) (FADE_TICKS * FADE_CUSHIONED) - 1)).isTrue();
        assertThat(BeltLift.cushioned((long) (FADE_TICKS * FADE_CUSHIONED))).isFalse();
    }

    @Test
    void aClientTrailingTheServerSeesTheStart()
    {
        assertThat(BeltLift.progress(-3)).isZero();
        assertThat(BeltLift.maxDescent(-3)).isEqualTo(FADE_DRIFT);
    }
}
