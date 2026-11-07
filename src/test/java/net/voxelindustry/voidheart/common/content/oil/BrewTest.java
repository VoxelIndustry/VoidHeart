package net.voxelindustry.voidheart.common.content.oil;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BrewTest
{
    private static Brew in(SinVessel vessel)
    {
        return new Brew(false, false, Optional.of(vessel));
    }

    @Test
    void plain_changesNothing()
    {
        assertThat(Brew.PLAIN.isPlain()).isTrue();
        assertThat(Brew.PLAIN.duration(3600)).isEqualTo(3600);
        assertThat(Brew.PLAIN.amplifier(0)).isZero();
        assertThat(Brew.PLAIN.charges(20)).isEqualTo(20);
        assertThat(Brew.PLAIN.yield()).isEqualTo(1);
    }

    @Test
    void azurium_raisesTheLevel_rubrumLengthens()
    {
        var both = new Brew(true, true, Optional.empty());
        assertThat(both.amplifier(0)).isEqualTo(1);
        assertThat(both.duration(3600)).isEqualTo(5400);
        assertThat(both.charges(20)).isEqualTo(30);
    }

    @Test
    void vessels_lengthenOrStrengthen()
    {
        assertThat(in(SinVessel.RAVENOUS_GOLD).duration(3600)).isEqualTo(7200);
        assertThat(in(SinVessel.LISTLESS_LEAD).duration(3600)).isEqualTo(10800);
        assertThat(in(SinVessel.ARROGANT_IRON).amplifier(0)).isEqualTo(1);
        assertThat(in(SinVessel.SEETHING_STEEL).charges(20)).isEqualTo(30);
    }

    @Test
    void copper_givesTwoThinnerBrews()
    {
        var copper = in(SinVessel.COVETOUS_COPPER);
        assertThat(copper.yield()).isEqualTo(2);
        assertThat(copper.duration(3600)).isEqualTo(2160);
        assertThat(copper.charges(20)).isEqualTo(12);
    }

    @Test
    void shardsAndVessel_stack()
    {
        var brew = new Brew(true, true, Optional.of(SinVessel.ARROGANT_IRON));
        assertThat(brew.amplifier(0)).isEqualTo(2);
        assertThat(new Brew(false, true, Optional.of(SinVessel.LISTLESS_LEAD)).duration(1000)).isEqualTo(4500);
    }

    @Test
    void vessels_holdTheirOwnFamily()
    {
        assertThat(SinVessel.SEETHING_STEEL.holdsOils()).isTrue();
        assertThat(SinVessel.SEETHING_STEEL.holdsElixirs()).isFalse();
        assertThat(SinVessel.COVETOUS_COPPER.holdsOils()).isTrue();
        assertThat(SinVessel.COVETOUS_COPPER.holdsElixirs()).isTrue();
        for (var vessel : new SinVessel[]{ SinVessel.ARROGANT_IRON, SinVessel.RAVENOUS_GOLD, SinVessel.LISTLESS_LEAD })
            assertThat(vessel.holdsOils()).as("%s", vessel).isFalse();
    }

    @Test
    void freshCoat_keepsWhatItWasLaidFor()
    {
        var coat = WeaponOil.fresh(OilType.ARCANE, new Brew(true, true, Optional.of(SinVessel.SEETHING_STEEL)));
        assertThat(coat.charges()).isEqualTo(45).isEqualTo(coat.capacity());
        assertThat(coat.strong()).isTrue();
        assertThat(coat.wearing()).isTrue();
        var spent = coat.spend();
        assertThat(spent.capacity()).isEqualTo(45);
        assertThat(spent.strong()).isTrue();
        assertThat(spent.wearing()).isTrue();
    }

    @Test
    void olderCoat_fillsItsBarFromTheOil()
    {
        assertThat(new WeaponOil(OilType.SEARING, 5).capacity()).isEqualTo(OilType.SEARING.charges());
    }
}
