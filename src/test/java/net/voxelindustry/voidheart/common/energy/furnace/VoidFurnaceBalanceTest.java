package net.voxelindustry.voidheart.common.energy.furnace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VoidFurnaceBalanceTest
{
    @Test
    void pace_givenHeat_thenClimbsFromColdToHot()
    {
        assertThat(VoidFurnaceBalance.pace(0, false)).isEqualTo(4);
        assertThat(VoidFurnaceBalance.pace(0.5, false)).isEqualTo(6);
        assertThat(VoidFurnaceBalance.pace(1, false)).isEqualTo(8);
    }

    @Test
    void pace_givenFiery_thenDoublesAtAnyHeat()
    {
        assertThat(VoidFurnaceBalance.pace(0, true)).isEqualTo(8);
        assertThat(VoidFurnaceBalance.pace(1, true)).isEqualTo(16);
    }

    @Test
    void warm_givenAMinuteOfSmelting_thenReachesFullHeat()
    {
        double heat = 0;
        for (int tick = 0; tick < VoidFurnaceBalance.HEAT_UP_TICKS; tick++)
            heat = VoidFurnaceBalance.warm(heat);
        assertThat(heat).isCloseTo(1, within(1e-9));
        assertThat(VoidFurnaceBalance.warm(1)).isEqualTo(1);
    }

    @Test
    void cool_givenTwoMinutesIdle_thenIsColdAgain()
    {
        double heat = 1;
        for (int tick = 0; tick < VoidFurnaceBalance.COOL_TICKS / 2; tick++)
            heat = VoidFurnaceBalance.cool(heat);
        assertThat(heat).isCloseTo(0.5, within(1e-9));
        for (int tick = 0; tick < VoidFurnaceBalance.COOL_TICKS; tick++)
            heat = VoidFurnaceBalance.cool(heat);
        assertThat(heat).isZero();
    }

    @Test
    void run_givenAColdFurnace_thenTheFirstItemTakesAQuarterOfAFurnacesTime()
    {
        assertThat(VoidFurnaceBalance.run(1, 200, 0, false).ticks()).isBetween(49, 50);
    }

    @Test
    void run_givenAHotFurnaceOnFiery_thenAStackTakesASixteenthOfAFurnacesTime()
    {
        var run = VoidFurnaceBalance.run(64, 200, 1, true);

        assertThat(run.ticks()).isBetween(64 * 200 / 16, 64 * 200 / 16 + 64);
    }

    @Test
    void run_givenAColdStack_thenWarmsAsItGoes()
    {
        var cold = VoidFurnaceBalance.run(64, 200, 0, false);
        var hot = VoidFurnaceBalance.run(64, 200, 1, false);

        // A stack warms the furnace most of the way, so it beats the cold pace and trails the hot one
        assertThat(cold.ticks()).isLessThan(64 * 50).isGreaterThan(hot.ticks());
        assertThat(cold.heat()).isGreaterThan(0.5);
    }

    @Test
    void draw_givenAnyPace_thenAnItemCostsTheSame()
    {
        // 600 VE for a 200-tick item on void energy, 800 on Fiery
        assertThat(VoidFurnaceBalance.draw(4, false) * 50).isEqualTo(600);
        assertThat(VoidFurnaceBalance.draw(8, false) * 25).isEqualTo(600);
        assertThat(VoidFurnaceBalance.draw(8, true) * 25).isEqualTo(800);
        assertThat(VoidFurnaceBalance.draw(16, true) * 12.5).isEqualTo(800);
    }

    @Test
    void canBurnFiery_givenLessThanAnItemsWorth_thenRefuses()
    {
        assertThat(VoidFurnaceBalance.canBurnFiery(VoidFurnaceBalance.FIERY_PER_ITEM - 1)).isFalse();
        assertThat(VoidFurnaceBalance.canBurnFiery(VoidFurnaceBalance.FIERY_PER_ITEM)).isTrue();
    }

    @Test
    void heatStage_givenHeat_thenReadsFromColdToFull()
    {
        assertThat(VoidFurnaceBalance.heatStage(0)).isZero();
        assertThat(VoidFurnaceBalance.heatStage(0.2)).isEqualTo(1);
        assertThat(VoidFurnaceBalance.heatStage(0.7)).isEqualTo(2);
        assertThat(VoidFurnaceBalance.heatStage(1)).isEqualTo(3);
    }

    @Test
    void paceText_givenAPace_thenShowsItWholeOrToATenth()
    {
        assertThat(VoidFurnaceBalance.paceText(4)).isEqualTo("4");
        assertThat(VoidFurnaceBalance.paceText(5.333)).isEqualTo("5.3");
        assertThat(VoidFurnaceBalance.paceText(15.97)).isEqualTo("16");
    }

    @Test
    void fieryLine_givenTheTank_thenReadsFromEmptyToWellFed()
    {
        assertThat(VoidFurnaceJade.fieryLine(0)).isZero();
        assertThat(VoidFurnaceJade.fieryLine(VoidFurnaceBalance.FIERY_PER_ITEM)).isEqualTo(1);
        assertThat(VoidFurnaceJade.fieryLine(VoidFurnaceBalance.FIERY_TANK / 4)).isEqualTo(2);
        assertThat(VoidFurnaceJade.fieryLine(VoidFurnaceBalance.FIERY_TANK)).isEqualTo(3);
    }
}
