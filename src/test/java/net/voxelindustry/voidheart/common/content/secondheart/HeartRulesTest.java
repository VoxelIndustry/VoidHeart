package net.voxelindustry.voidheart.common.content.secondheart;

import org.junit.jupiter.api.Test;

import static net.voxelindustry.voidheart.common.content.secondheart.HeartRules.Verdict.DIE;
import static net.voxelindustry.voidheart.common.content.secondheart.HeartRules.Verdict.HEART;
import static net.voxelindustry.voidheart.common.content.secondheart.HeartRules.Verdict.HOLD;
import static net.voxelindustry.voidheart.common.content.secondheart.HeartRules.Verdict.TOTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HeartRulesTest
{
    @Test
    void onLethal_givenReadyHeart_thenHeartFires()
    {
        assertThat(HeartRules.onLethal(true, 0, false, false, false)).isEqualTo(HEART);
    }

    @Test
    void onLethal_givenRecharging_thenDies()
    {
        assertThat(HeartRules.onLethal(true, 1, false, false, false)).isEqualTo(DIE);
    }

    @Test
    void onLethal_givenNoHeart_thenDies()
    {
        assertThat(HeartRules.onLethal(false, 0, false, false, false)).isEqualTo(DIE);
    }

    @Test
    void onLethal_givenTotem_thenTotemGoesFirst()
    {
        assertThat(HeartRules.onLethal(true, 0, false, true, false)).isEqualTo(TOTEM);
    }

    @Test
    void onLethal_givenGenericKill_thenDiesWhateverElse()
    {
        assertThat(HeartRules.onLethal(true, 0, false, true, true)).isEqualTo(DIE);
        assertThat(HeartRules.onLethal(true, 0, true, false, true)).isEqualTo(DIE);
    }

    @Test
    void onLethal_givenAlreadyReforming_thenHolds()
    {
        assertThat(HeartRules.onLethal(true, HeartRules.RECHARGE_TICKS, true, false, false)).isEqualTo(HOLD);
    }

    @Test
    void recharge_countsDownToZeroAndStays()
    {
        assertThat(HeartRules.recharge(5)).isEqualTo(4);
        assertThat(HeartRules.recharge(1)).isZero();
        assertThat(HeartRules.recharge(0)).isZero();
    }

    @Test
    void recharge_givenTwentyMinutes_thenReadyAfterExactlyThatManyTicks()
    {
        int left = HeartRules.RECHARGE_TICKS, ticks = 0, readyCues = 0;
        while (left > 0)
        {
            if (HeartRules.comesBack(left))
                readyCues++;
            left = HeartRules.recharge(left);
            ticks++;
        }
        assertThat(ticks).isEqualTo(24_000);
        assertThat(readyCues).isOne();
    }

    @Test
    void phase_followsTheTimeline()
    {
        assertThat(HeartRules.phase(0)).isEqualTo(HeartRules.Phase.DISSOLVE);
        assertThat(HeartRules.phase(HeartRules.DISSOLVE_TICKS)).isEqualTo(HeartRules.Phase.REFORM);
        assertThat(HeartRules.phase(HeartRules.BURST_TICK)).isEqualTo(HeartRules.Phase.GRACE);
        assertThat(HeartRules.phase(HeartRules.SEQUENCE_TICKS - 1)).isEqualTo(HeartRules.Phase.GRACE);
        assertThat(HeartRules.phase(HeartRules.SEQUENCE_TICKS)).isEqualTo(HeartRules.Phase.DONE);
    }

    @Test
    void grace_isThreeSeconds()
    {
        assertThat(HeartRules.GRACE_TICKS).isEqualTo(60);
    }

    @Test
    void gone_risesThroughDissolveAndFallsThroughReform()
    {
        assertThat(HeartRules.gone(0)).isZero();
        assertThat(HeartRules.gone(HeartRules.DISSOLVE_TICKS / 2F)).isCloseTo(0.5F, within(1.0E-4F));
        assertThat(HeartRules.gone(HeartRules.DISSOLVE_TICKS)).isCloseTo(1, within(1.0E-4F));
        assertThat(HeartRules.gone(HeartRules.DISSOLVE_TICKS + HeartRules.REFORM_TICKS / 2F)).isCloseTo(0.5F, within(1.0E-4F));
        assertThat(HeartRules.gone(HeartRules.BURST_TICK)).isZero();
    }

    @Test
    void burstStrength_isStrongestCloseAndNothingOutside()
    {
        assertThat(HeartRules.burstStrength(0)).isEqualTo(HeartRules.BURST_STRENGTH);
        assertThat(HeartRules.burstStrength(3)).isBetween(HeartRules.BURST_MIN, HeartRules.BURST_STRENGTH);
        assertThat(HeartRules.burstStrength(2)).isGreaterThan(HeartRules.burstStrength(4));
        assertThat(HeartRules.burstStrength(HeartRules.BURST_RADIUS)).isZero();
        assertThat(HeartRules.burstStrength(10)).isZero();
    }
}
