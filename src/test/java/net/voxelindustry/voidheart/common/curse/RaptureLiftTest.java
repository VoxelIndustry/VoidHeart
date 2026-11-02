package net.voxelindustry.voidheart.common.curse;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class RaptureLiftTest
{
    private static final int SEEDS = 10_000;

    // What LivingEntity.travelInAir settles on at amplifier 0: about a block a second
    private static final double TERMINAL_SPEED = 0.0098 / (1 - 0.784);

    @Test
    void heightStaysInRangeAndReachesBothEnds()
    {
        var random = new Random(42);
        var heights = new HashSet<Integer>();
        for (int i = 0; i < SEEDS; i++)
        {
            int height = RaptureLift.height(random.nextLong());
            assertThat(height).isBetween(6, 12);
            heights.add(height);
        }
        assertThat(heights).containsExactlyInAnyOrder(6, 7, 8, 9, 10, 11, 12);
    }

    @Test
    void heightIsDerivedFromTheSeedAlone()
    {
        var random = new Random(7);
        for (int i = 0; i < 100; i++)
        {
            long seed = random.nextLong();
            assertThat(RaptureLift.height(seed)).isEqualTo(RaptureLift.height(seed));
            assertThat(RaptureLift.totalTicks(seed)).isEqualTo(RaptureLift.totalTicks(seed));
        }
    }

    @Test
    void totalDurationCoversTheWholeLift()
    {
        var random = new Random(1234);
        for (int i = 0; i < SEEDS; i++)
        {
            long seed = random.nextLong();
            int lift = RaptureLift.liftTicks(RaptureLift.height(seed));
            assertThat(RaptureLift.totalTicks(seed)).isGreaterThan(RaptureLift.liftStartTick() + lift);
            assertThat(RaptureLift.endTick(seed)).isEqualTo(RaptureLift.liftStartTick() + lift);
        }
    }

    @Test
    void liftLastsAsLongAsLevitationTakesToRiseThatHigh()
    {
        for (int height = RaptureLift.MIN_HEIGHT; height <= RaptureLift.MAX_HEIGHT; height++)
        {
            double atTerminalSpeed = height / TERMINAL_SPEED;
            assertThat((double) RaptureLift.liftTicks(height)).isBetween(atTerminalSpeed, atTerminalSpeed + 10);
        }
    }

    @Test
    void timelineRunsInTheIntendedOrder()
    {
        assertThat(RaptureLift.EYES_OPEN_TICKS).isLessThanOrEqualTo(RaptureLift.STARE_START_TICK);
        assertThat(RaptureLift.STARE_START_TICK).isLessThan(RaptureLift.STARE_END_TICK);
        assertThat(RaptureLift.STARE_END_TICK).isLessThanOrEqualTo(RaptureLift.RUMBLE_TICK);
        assertThat(RaptureLift.RUMBLE_TICK).isLessThan(RaptureLift.MOUTH_TICK);
        assertThat(RaptureLift.MOUTH_TICK).isLessThan(RaptureLift.LIFT_TICK);
        assertThat(RaptureLift.RELEASE_TICKS).isGreaterThanOrEqualTo(RaptureLift.EYES_CLOSE_TICKS);
        assertThat(RaptureLift.RELEASE_TICKS).isGreaterThanOrEqualTo(RaptureLift.MOUTH_SNAP_TICKS);
    }
}
