package net.voxelindustry.voidheart.common.curse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorruptionRiseTest
{
    @Test
    void noModifiersLeaveRisesUnchanged()
    {
        assertThat(CorruptionRise.multiplier(null, List.of())).isEqualTo(1);
        assertThat(CorruptionRise.of(7, 1, 0)).isEqualTo(new CorruptionRise(7, 0));
    }

    @Test
    void modifiersMultiplyTogether()
    {
        List<CorruptionModifier> ward = List.of(player -> 0.5F);
        List<CorruptionModifier> wardInSeep = List.of(player -> 0.5F, player -> 3F);

        assertThat(CorruptionRise.multiplier(null, ward)).isEqualTo(0.5F);
        assertThat(CorruptionRise.multiplier(null, wardInSeep)).isEqualTo(1.5F);
    }

    @Test
    void brokenModifiersNeverLowerCorruptionOrPoisonTheRise()
    {
        assertThat(CorruptionRise.multiplier(null, List.of(player -> -2F))).isZero();
        assertThat(CorruptionRise.multiplier(null, List.of(player -> Float.NaN, player -> 2F))).isEqualTo(2);
    }

    @Test
    void fractionsCarryOverSoSlowedRisesStillAddUp()
    {
        float carry = 0;
        int total = 0;
        for (int i = 0; i < 10; i++)
        {
            var rise = CorruptionRise.of(1, 0.5F, carry);
            total += rise.points();
            carry = rise.carry();
        }

        assertThat(total).isEqualTo(5);
        assertThat(carry).isZero();
    }

    @Test
    void fullWardStopsTheRise()
    {
        assertThat(CorruptionRise.of(10, 0, 0.25F)).isEqualTo(new CorruptionRise(0, 0.25F));
    }

    @Test
    void hugeOrStaleValuesAreBounded()
    {
        assertThat(CorruptionRise.of(1_000, 10, 0).points()).isEqualTo(Corruption.MAX);
        assertThat(CorruptionRise.of(1, Float.POSITIVE_INFINITY, 0).points()).isEqualTo(Corruption.MAX);
        assertThat(CorruptionRise.of(2, 1, Float.NaN)).isEqualTo(new CorruptionRise(2, 0));
        assertThat(CorruptionRise.of(2, 1, 40)).isEqualTo(new CorruptionRise(2, 0));
    }
}
