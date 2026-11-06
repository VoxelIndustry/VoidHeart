package net.voxelindustry.voidheart.common.content.research;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoidGraftsTest
{
    @Test
    void hunger_untouchedBeforeTheVein()
    {
        assertThat(VoidGrafts.hungerFactor(1, false)).isEqualTo(1);
        assertThat(VoidGrafts.hungerFactor(1, true)).isEqualTo(1);
    }

    @Test
    void hunger_givenVein_thenFasterOutsideAndSlowerInside()
    {
        assertThat(VoidGrafts.hungerFactor(VoidGrafts.VEIN, false)).isGreaterThan(1);
        assertThat(VoidGrafts.hungerFactor(VoidGrafts.VEIN, true)).isLessThan(1);
        assertThat(VoidGrafts.hungerFactor(VoidGrafts.MARROW, false)).isGreaterThan(1);
    }

    @Test
    void heal_givenMarrow_thenSlowerOutsideAndFasterInside()
    {
        assertThat(VoidGrafts.healFactor(VoidGrafts.VEIN, false)).isEqualTo(1);
        assertThat(VoidGrafts.healFactor(VoidGrafts.MARROW, false)).isLessThan(1);
        assertThat(VoidGrafts.healFactor(VoidGrafts.MARROW, true)).isGreaterThan(1);
    }
}
