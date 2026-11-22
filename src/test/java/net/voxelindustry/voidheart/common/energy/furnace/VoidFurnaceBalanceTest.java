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

    /**
     * Charges items as the furnace does, whole points a tick with what is below a point carried on.
     */
    private static long charge(int items, int vanillaTicks, double heat, boolean fiery)
    {
        double owed = 0;
        long total = 0;
        for (int item = 0; item < items; item++)
        {
            double progress = 0;
            while (progress < 1 - 1e-9)
            {
                var pace = VoidFurnaceBalance.pace(heat, fiery);
                var cost = owed + VoidFurnaceBalance.tickEnergy(progress, vanillaTicks, pace, fiery);
                var need = (long) Math.floor(cost + 1e-9);
                total += need;
                owed = cost - need;
                progress += VoidFurnaceBalance.progressPerTick(vanillaTicks, pace);
                heat = VoidFurnaceBalance.warm(heat);
            }
        }
        return total;
    }

    @Test
    void charge_givenAnyHeat_thenAnItemCostsWhatItsCookingIsWorth()
    {
        // 600 VE for a 200-tick item on void energy, 800 on Fiery, even when its last tick only needs part of the work
        assertThat(charge(1, 200, 0, false)).isBetween(599L, 600L);
        assertThat(charge(1, 200, 0.37, false)).isBetween(599L, 600L);
        assertThat(charge(1, 200, 1, true)).isBetween(799L, 800L);
        assertThat(charge(1, 100, 0, true)).isBetween(399L, 400L);
        assertThat(charge(64, 200, 0, false)).isBetween(64 * 600L - 1, 64 * 600L);
        assertThat(VoidFurnaceBalance.itemEnergy(200, true)).isEqualTo(800);
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
