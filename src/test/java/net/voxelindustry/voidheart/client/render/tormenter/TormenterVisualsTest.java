package net.voxelindustry.voidheart.client.render.tormenter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TormenterVisualsTest
{
    private static final int STEPS = 200;

    @Test
    void dropletIsSlenderAndStaysInThreeBlocksAcross()
    {
        var widest = 0F;
        var widestAt = 0F;
        for (var step = 0; step <= STEPS; step++)
        {
            var rise = (float) step / STEPS;
            var half = TormenterDroplet.halfWidth(rise);
            if (half > widest)
            {
                widest = half;
                widestAt = rise;
            }
        }

        assertThat(widest + TormenterDroplet.MOST_STRAY).isLessThanOrEqualTo(1.5F);
        assertThat(widest).isGreaterThan(0.9F);
        assertThat(TormenterDroplet.HEIGHT).isGreaterThan(widest * 2);
        // A droplet flowing upward trails its tip below a belly that sits high
        assertThat(widestAt).isGreaterThan(0.55F);
    }

    @Test
    void dropletEndsInATipAndADome()
    {
        assertThat(TormenterDroplet.halfWidth(0)).isLessThan(0.01F);
        assertThat(TormenterDroplet.halfWidth(1)).isLessThan(0.01F);
    }

    @Test
    void headsStartAndEndUnderTheSkin()
    {
        assertThat(TormenterHeadFlow.size(0)).isZero();
        assertThat(TormenterHeadFlow.size(1)).isLessThan(0.01F);
    }

    @Test
    void headsAreLargeButMostlyDrownedInTheFlesh()
    {
        for (var step = 1; step < STEPS; step++)
        {
            var rise = (float) step / STEPS;
            var size = TormenterHeadFlow.size(rise);
            var face = TormenterHeadFlow.depth(rise) + size / 2;
            assertThat(face - TormenterDroplet.halfWidth(rise)).isLessThanOrEqualTo(Math.max(size * TormenterHeadFlow.STANDING_OUT, size / 2) + 1E-4F);
        }
        assertThat(TormenterHeadFlow.size(0.6F)).isGreaterThan(0.7F);
        assertThat(TormenterHeadFlow.STANDING_OUT).isLessThan(0.5F);
    }

    @Test
    void headsStayInTheThreeBlockArea()
    {
        for (var step = 0; step <= STEPS; step++)
        {
            var rise = (float) step / STEPS;
            var size = TormenterHeadFlow.size(rise);
            assertThat(TormenterHeadFlow.depth(rise) + size / 2).isLessThanOrEqualTo(1.6F);
            assertThat(TormenterHeadFlow.lateral(rise) + size / 2).isLessThanOrEqualTo(1.5F);
        }
    }

    @Test
    void facesFollowTheSlopeOfTheSurface()
    {
        assertThat(TormenterHeadFlow.tilt(0.25F)).isLessThan(0);
        assertThat(TormenterHeadFlow.tilt(0.9F)).isGreaterThan(0);
    }
}
