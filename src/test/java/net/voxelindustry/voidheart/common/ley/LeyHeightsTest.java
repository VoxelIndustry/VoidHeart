package net.voxelindustry.voidheart.common.ley;

import org.junit.jupiter.api.Test;

import static net.voxelindustry.voidheart.common.ley.LeyHeights.HOVER;
import static org.assertj.core.api.Assertions.assertThat;

class LeyHeightsTest
{
    @Test
    void flatTerrainHoversAtConstantHeight()
    {
        var heights = new LeyHeights((x, z) -> 70);
        var profile = heights.profile(new LeyEdge(1, 2, 0, 0, 200, 0));

        assertThat(profile).hasSize(LeyHeights.sampleCount(new LeyEdge(1, 2, 0, 0, 200, 0)));
        for (float height : profile)
            assertThat(height).isEqualTo(70 + HOVER);
    }

    @Test
    void endsMatchNodeHeightsSoLinesJoin()
    {
        var heights = new LeyHeights((x, z) -> 60 + x / 10);
        var profile = heights.profile(new LeyEdge(1, 2, 13, 4, 357, 90));

        assertThat(profile[0]).isEqualTo(heights.nodeHeight(13, 4));
        assertThat(profile[profile.length - 1]).isEqualTo(heights.nodeHeight(357, 90));
    }

    @Test
    void smoothingNeverSinksIntoTerrain()
    {
        // A sharp 30 block spike in the middle of flat land
        var heights = new LeyHeights((x, z) -> x >= 96 && x < 104 ? 100 : 70);
        var edge = new LeyEdge(1, 2, 0, 0, 200, 0);
        var profile = heights.profile(edge);

        for (int i = 0; i < profile.length; i++)
        {
            int x = Math.round(200F * i / (profile.length - 1));
            int ground = x >= 96 && x < 104 ? 100 : 70;
            assertThat(profile[i]).isGreaterThan(ground);
        }
    }
}
