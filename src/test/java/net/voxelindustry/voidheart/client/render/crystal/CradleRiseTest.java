package net.voxelindustry.voidheart.client.render.crystal;

import net.voxelindustry.voidheart.common.content.crystal.BundleSize;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CradleRiseTest
{
    @Test
    void nothing_showsBeforeAnythingIsHeld()
    {
        assertThat(CradleRise.of(0, null)).isNull();
    }

    @Test
    void first_bundleRisesFromTheBed()
    {
        var start = CradleRise.of(0, BundleSize.LARGE);
        assertThat(start.size()).isEqualTo(BundleSize.SMALL);
        assertThat(start.scale()).isEqualTo(CradleRise.FIRST_START);
        assertThat(start.sunk()).isEqualTo(1);

        var grown = CradleRise.of(0.999, BundleSize.LARGE);
        assertThat(grown.sunk()).isCloseTo(0, within(0.01F));
    }

    @Test
    void next_sizeStartsWhereTheLastEnded()
    {
        var small = CradleRise.of(0.9999, BundleSize.LARGE);
        var medium = CradleRise.of(1, BundleSize.LARGE);
        assertThat(medium.size()).isEqualTo(BundleSize.MEDIUM);
        float smallHeight = CradleRise.height(BundleSize.SMALL) * small.scale();
        float mediumHeight = CradleRise.height(BundleSize.MEDIUM) * medium.scale();
        assertThat(mediumHeight).isCloseTo(smallHeight, within(0.05F));
    }

    @Test
    void grown_restsAtFullSize()
    {
        var pose = CradleRise.of(2, BundleSize.MEDIUM);
        assertThat(pose.size()).isEqualTo(BundleSize.MEDIUM);
        assertThat(pose.scale()).isEqualTo(1);
        assertThat(pose.sunk()).isZero();
    }

    @Test
    void neverShowsMoreThanTheTarget()
    {
        var pose = CradleRise.of(1.5, BundleSize.SMALL);
        assertThat(pose.size()).isEqualTo(BundleSize.SMALL);
        assertThat(pose.scale()).isEqualTo(1);
    }
}
