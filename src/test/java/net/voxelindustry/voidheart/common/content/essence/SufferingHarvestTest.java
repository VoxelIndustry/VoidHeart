package net.voxelindustry.voidheart.common.content.essence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SufferingHarvestTest
{
    @Test
    void yieldFollowsMaxHealth()
    {
        assertThat(SufferingHarvest.yieldOf(20)).isEqualTo(200);
        assertThat(SufferingHarvest.yieldOf(40)).isEqualTo(400);
        assertThat(SufferingHarvest.yieldOf(300)).isEqualTo(3000);
    }

    @Test
    void evenTheFrailestMobGivesSome()
    {
        assertThat(SufferingHarvest.yieldOf(0.01F)).isEqualTo(1);
    }
}
