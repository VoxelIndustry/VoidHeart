package net.voxelindustry.voidheart.common.content.oil;

import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeaponOilTest
{
    @Test
    void freshCoat_hasTheOilsCharges()
    {
        assertThat(WeaponOil.fresh(OilType.SEARING).charges()).isEqualTo(OilType.SEARING.charges()).isEqualTo(20);
    }

    @Test
    void spend_countsDownThenWearsOff()
    {
        var coating = WeaponOil.fresh(OilType.SEARING);
        for (int hit = 1; hit < OilType.SEARING.charges(); hit++)
        {
            coating = coating.spend();
            assertThat(coating).isNotNull();
            assertThat(coating.charges()).isEqualTo(OilType.SEARING.charges() - hit);
        }
        assertThat(coating.spend()).isNull();
    }

    @Test
    void searing_countsAsFire()
    {
        assertThat(OilType.SEARING.kind()).isEqualTo(DragoonAdaptation.Kind.FIRE);
    }
}
