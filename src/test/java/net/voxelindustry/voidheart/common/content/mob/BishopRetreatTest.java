package net.voxelindustry.voidheart.common.content.mob;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BishopRetreatTest
{
    private static final float MAX = 100;

    @Test
    void shouldWithdraw_onlyBelowThirtyPercent()
    {
        assertThat(BishopRetreat.shouldWithdraw(29, MAX, 0)).isTrue();
        assertThat(BishopRetreat.shouldWithdraw(30, MAX, 0)).isFalse();
    }

    @Test
    void shouldWithdraw_atMostTwice()
    {
        assertThat(BishopRetreat.shouldWithdraw(10, MAX, 1)).isTrue();
        assertThat(BishopRetreat.shouldWithdraw(10, MAX, 2)).isFalse();
    }

    @Test
    void isHealed_atSeventyPercent()
    {
        assertThat(BishopRetreat.isHealed(69, MAX)).isFalse();
        assertThat(BishopRetreat.isHealed(70, MAX)).isTrue();
    }

    @Test
    void healing_isFourHealthPerSecond()
    {
        assertThat(BishopRetreat.HEAL_PER_TICK * 20).isEqualTo(4);
    }
}
