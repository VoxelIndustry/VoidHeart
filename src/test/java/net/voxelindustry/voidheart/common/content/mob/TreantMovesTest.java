package net.voxelindustry.voidheart.common.content.mob;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TreantMovesTest
{
    // Facing south, toward +z
    private static final float SOUTH = 0;

    @Test
    void sweep_catchesTheFrontAndBothFlanks()
    {
        assertThat(TreantMoves.inSweep(0, 0, SOUTH, 0, 4)).as("ahead").isTrue();
        assertThat(TreantMoves.inSweep(0, 0, SOUTH, 4, 0)).as("left flank").isTrue();
        assertThat(TreantMoves.inSweep(0, 0, SOUTH, -4, 0)).as("right flank").isTrue();
        assertThat(TreantMoves.inSweep(0, 0, SOUTH, 3, -1)).as("just behind the flank").isTrue();
    }

    @Test
    void sweep_missesWhatIsBehindOrOutOfReach()
    {
        assertThat(TreantMoves.inSweep(0, 0, SOUTH, 0, -3)).as("behind").isFalse();
        assertThat(TreantMoves.inSweep(0, 0, SOUTH, 1, -3)).as("behind, off centre").isFalse();
        assertThat(TreantMoves.inSweep(0, 0, SOUTH, 0, TreantMoves.SWEEP_RANGE + 0.1)).as("past its reach").isFalse();
    }

    @Test
    void sweep_reachesFartherThanAPlayerSword()
    {
        assertThat(TreantMoves.SWEEP_RANGE).isGreaterThan(4);
        assertThat(TreantMoves.inSweep(10, 10, SOUTH, 10, 14.4)).isTrue();
    }

    @Test
    void sweep_turnsWithItsHeading()
    {
        // Facing west, toward -x
        assertThat(TreantMoves.inSweep(0, 0, 90, -4, 0)).isTrue();
        assertThat(TreantMoves.inSweep(0, 0, 90, 4, 0)).isFalse();
    }

    @Test
    void sweep_catchesWhatStandsUnderItsArms()
    {
        assertThat(TreantMoves.inSweep(0, 0, SOUTH, 0.1, -0.2)).isTrue();
    }

    @Test
    void emerge_waitsInTheBarkThenComesOutSteadily()
    {
        assertThat(TreantMoves.emergeProgress(0)).isZero();
        assertThat(TreantMoves.emergeProgress(TreantMoves.EMERGE_STIR_TICKS)).isZero();
        assertThat(TreantMoves.emergeProgress(TreantMoves.EMERGE_TICKS)).isEqualTo(1);
        assertThat(TreantMoves.emergeProgress(TreantMoves.EMERGE_TICKS + 10)).isEqualTo(1);

        float last = 0;
        for (int tick = TreantMoves.EMERGE_STIR_TICKS + 1; tick <= TreantMoves.EMERGE_TICKS; tick++)
        {
            float progress = TreantMoves.emergeProgress(tick);
            assertThat(progress).as("tick %d", tick).isGreaterThan(last);
            last = progress;
        }
    }

    @Test
    void emerge_takesSeconds()
    {
        assertThat(TreantMoves.EMERGE_TICKS).isGreaterThanOrEqualTo(60);
    }
}
