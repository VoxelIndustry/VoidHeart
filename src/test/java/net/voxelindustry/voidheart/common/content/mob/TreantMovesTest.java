package net.voxelindustry.voidheart.common.content.mob;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.within;

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

    @Test
    void sweep_includesTheReachBoundaryButNotTheNextRepresentableDistance()
    {
        assertThat(TreantMoves.inSweep(0, 0, SOUTH, 0, TreantMoves.SWEEP_RANGE)).isTrue();
        assertThat(TreantMoves.inSweep(0, 0, SOUTH, 0, Math.nextUp(TreantMoves.SWEEP_RANGE))).isFalse();
        // Each component is in range, but the diagonal distance is not.
        assertThat(TreantMoves.inSweep(0, 0, SOUTH, 4, 4)).isFalse();
    }

    @Test
    void sweep_pointBlankExceptionEndsExactlyAtItsBoundary()
    {
        assertThat(TreantMoves.inSweep(0, 0, SOUTH, 0, 0)).as("coincident positions").isTrue();
        assertThat(TreantMoves.inSweep(0, 0, SOUTH, 0, -Math.nextDown(TreantMoves.SWEEP_POINT_BLANK))).isTrue();
        assertThat(TreantMoves.inSweep(0, 0, SOUTH, 0, -TreantMoves.SWEEP_POINT_BLANK)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(doubles = { -1, 1 })
    void sweep_checksBothAngularEdges(double side)
    {
        double edge = Math.acos(TreantMoves.SWEEP_ARC_DOT);
        double inside = side * (edge - 0.000001);
        double outside = side * (edge + 0.000001);
        assertThat(TreantMoves.inSweep(0, 0, SOUTH, 3 * Math.sin(inside), 3 * Math.cos(inside))).isTrue();
        assertThat(TreantMoves.inSweep(0, 0, SOUTH, 3 * Math.sin(outside), 3 * Math.cos(outside))).isFalse();
    }

    @ParameterizedTest
    @CsvSource({ "0, 0, 1", "90, -1, 0", "180, 0, -1", "270, 1, 0",
            "-90, 1, 0", "450, -1, 0", "720, 0, 1" })
    void sweep_respectsCardinalHeadingsAndFullTurnsAtANegativeOrigin(float yaw, int dx, int dz)
    {
        assertThat(TreantMoves.inSweep(-20, -30, yaw, -20 + 4 * dx, -30 + 4 * dz)).isTrue();
        assertThat(TreantMoves.inSweep(-20, -30, yaw, -20 - 4 * dx, -30 - 4 * dz)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(floats = { -100, -0.5F, 11.5F, 12 })
    void emerge_clampsTicksBeforeMovement(float ticks)
    {
        assertThat(TreantMoves.emergeProgress(ticks)).isZero();
    }

    @ParameterizedTest
    @ValueSource(floats = { 70, 70.5F, 1000 })
    void emerge_clampsTicksAfterCompletion(float ticks)
    {
        assertThat(TreantMoves.emergeProgress(ticks)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({ "26.5, 0.15625", "41, 0.5", "55.5, 0.84375" })
    void emerge_easesFractionalTicksRatherThanMovingLinearly(float ticks, float expected)
    {
        assertThat(TreantMoves.emergeProgress(ticks)).isCloseTo(expected, within(0.000001F));
    }

}
