package net.voxelindustry.voidheart.common.content.sin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SinTemperamentTest
{
    @Test
    void prideStacks_growEveryTwoSecondsUpToTheCap()
    {
        assertThat(SinTemperament.prideStacks(0, 0)).isZero();
        assertThat(SinTemperament.prideStacks(SinBalance.PRIDE_STACK_TICKS - 1, 0)).isZero();
        assertThat(SinTemperament.prideStacks(SinBalance.PRIDE_STACK_TICKS * 3, 0)).isEqualTo(3);
        assertThat(SinTemperament.prideStacks(100_000, 0)).isEqualTo(SinBalance.PRIDE_MAX_STACKS_NOVICE);
        assertThat(SinTemperament.prideStacks(100_000, 1)).isEqualTo(SinBalance.PRIDE_MAX_STACKS_DEVOTED);
    }

    @Test
    void prideMultiplier_givenFullStacks_thenBriefValues()
    {
        assertThat(SinTemperament.prideMultiplier(5, 0)).isCloseTo(1.2F, within(1e-5F));
        assertThat(SinTemperament.prideMultiplier(8, 1)).isCloseTo(1.64F, within(1e-5F));
    }

    @Test
    void prideWeakness_growsWithDevotion()
    {
        assertThat(SinTemperament.prideWeaknessLevel(0)).isZero();
        assertThat(SinTemperament.prideWeaknessLevel(1)).isEqualTo(1);
        assertThat(SinTemperament.prideWeaknessTicks(1)).isGreaterThan(SinTemperament.prideWeaknessTicks(0));
    }

    @Test
    void wrathMultiplier_givenHealth_thenScalesWithMissingHealth()
    {
        assertThat(SinTemperament.wrathMultiplier(20, 20, 1)).isEqualTo(1);
        assertThat(SinTemperament.wrathMultiplier(2, 20, 0)).isCloseTo(1.3F, within(1e-5F));
        assertThat(SinTemperament.wrathMultiplier(1, 20, 1)).isCloseTo(2, within(1e-5F));
        assertThat(SinTemperament.wrathMultiplier(11, 20, 1)).isCloseTo(1.5F, within(1e-5F));
    }

    @Test
    void wrathHealing_givenAboveThreeQuarters_thenKept()
    {
        assertThat(SinTemperament.wrathHealingKept(16, 20, 1)).isEqualTo(1);
        assertThat(SinTemperament.wrathHealingKept(14, 20, 0)).isCloseTo(0.8F, within(1e-5F));
        assertThat(SinTemperament.wrathHealingKept(14, 20, 1)).isCloseTo(0.5F, within(1e-5F));
    }

    @Test
    void gluttonyEat_takesSaturationThenHungerThenHealth()
    {
        var fromSaturation = SinTemperament.gluttonyEat(2, 20, 1.5F);
        assertThat(fromSaturation.saturation()).isCloseTo(0.5F, within(1e-5F));
        assertThat(fromSaturation.food()).isEqualTo(20);
        assertThat(fromSaturation.healthCost()).isZero();

        var intoHunger = SinTemperament.gluttonyEat(0.5F, 20, 1.5F);
        assertThat(intoHunger.saturation()).isZero();
        assertThat(intoHunger.food()).isEqualTo(19);
        assertThat(intoHunger.healthCost()).isZero();

        var starved = SinTemperament.gluttonyEat(0, 0, 1.5F);
        assertThat(starved.food()).isZero();
        assertThat(starved.healthCost()).isCloseTo(1.5F, within(1e-5F));
    }

    @Test
    void gluttonyHeal_givenDamage_thenShareOfIt()
    {
        assertThat(SinTemperament.gluttonyHeal(10, 0)).isCloseTo(0.5F, within(1e-5F));
        assertThat(SinTemperament.gluttonyHeal(10, 1)).isCloseTo(1.5F, within(1e-5F));
        assertThat(SinTemperament.gluttonyHeal(-3, 1)).isZero();
    }

    @Test
    void slothCharge_fillsStandingStillAndDrainsMoving()
    {
        int charge = 0;
        for (int tick = 0; tick < SinBalance.SLOTH_CHARGE_TICKS_NOVICE; tick++)
            charge = SinTemperament.slothCharge(charge, true, 0);
        assertThat(SinTemperament.slothCharged(charge, 0)).isTrue();

        charge = SinTemperament.slothCharge(charge, false, 0);
        assertThat(SinTemperament.slothCharged(charge, 0)).isFalse();
        assertThat(charge).isEqualTo(SinBalance.SLOTH_CHARGE_TICKS_NOVICE - SinBalance.SLOTH_DRAIN_PER_TICK);
    }

    @Test
    void slothCharge_devotedChargesFaster()
    {
        assertThat(SinTemperament.slothChargeTicks(1)).isLessThan(SinTemperament.slothChargeTicks(0));
        assertThat(SinTemperament.slothCharged(SinBalance.SLOTH_CHARGE_TICKS_DEVOTED, 1)).isTrue();
    }

    @Test
    void greed_noviceStealsLowestLevelDevotedFull()
    {
        assertThat(SinTemperament.greedStolenLevel(2, 0)).isZero();
        assertThat(SinTemperament.greedStolenLevel(2, 1)).isEqualTo(2);
        assertThat(SinTemperament.greedCooldown(1)).isLessThan(SinTemperament.greedCooldown(0));
    }

    @Test
    void greedStolenTicks_givenInfinite_thenCapped()
    {
        assertThat(SinTemperament.greedStolenTicks(-1)).isEqualTo(SinBalance.GREED_MAX_STOLEN_TICKS);
        assertThat(SinTemperament.greedStolenTicks(100)).isEqualTo(100);
    }

    @Test
    void greedDebt_growsWithDevotion()
    {
        assertThat(SinTemperament.greedDebtTicks(200, 0)).isEqualTo(100);
        assertThat(SinTemperament.greedDebtTicks(200, 1)).isEqualTo(300);
    }

    @Test
    void teachingWeight_givenStrongerFoeAndPlace_thenMore()
    {
        assertThat(SinTemperament.teachingWeight(20, 1)).isEqualTo(1);
        assertThat(SinTemperament.teachingWeight(4, 1)).isEqualTo(SinBalance.MIN_WEIGHT);
        assertThat(SinTemperament.teachingWeight(1000, 1)).isEqualTo(SinBalance.MAX_WEIGHT);
        assertThat(SinTemperament.teachingWeight(20, SinBalance.DAEDALUS_WEIGHT)).isEqualTo(SinBalance.DAEDALUS_WEIGHT);
    }
}
