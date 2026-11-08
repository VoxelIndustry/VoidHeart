package net.voxelindustry.voidheart.common.content.essence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageFillTest
{
    @Test
    void emptyStorageKeepsPillarsDown()
    {
        assertThat(StorageFill.fillOf(0, 32_000)).isZero();
    }

    @Test
    void anyEssenceRaisesThePillarsOneStep()
    {
        assertThat(StorageFill.fillOf(1, 32_000)).isEqualTo(1);
    }

    @Test
    void onlyAFullStorageReachesTheTop()
    {
        assertThat(StorageFill.fillOf(31_999, 32_000)).isEqualTo(StorageFill.MAX_FILL);
        assertThat(StorageFill.fillOf(32_000, 32_000)).isEqualTo(StorageFill.MAX_FILL);
        assertThat(StorageFill.fillOf(16_000, 32_000)).isEqualTo(5);
    }

    @Test
    void fillNeverLeavesItsRange()
    {
        assertThat(StorageFill.fillOf(64_000, 32_000)).isEqualTo(StorageFill.MAX_FILL);
        assertThat(StorageFill.fillOf(-5, 32_000)).isZero();
    }

    @Test
    void pillarsStayInsideTheTwoBlocks()
    {
        assertThat(StorageFill.pillarTop(0)).isGreaterThan(12);
        assertThat(StorageFill.pillarTop(StorageFill.MAX_FILL)).isEqualTo(32);
    }
}
