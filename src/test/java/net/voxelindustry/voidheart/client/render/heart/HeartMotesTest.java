package net.voxelindustry.voidheart.client.render.heart;

import net.voxelindustry.voidheart.common.content.heart.HeartBeat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeartMotesTest
{
    private static final java.util.List<int[]> ORIGINS = HeartMotes.origins(HeartShape.voxels(false));

    @Test
    void motesLeaveFromTheHeartsTop()
    {
        var flesh = HeartShape.voxels(false);
        assertThat(ORIGINS).isNotEmpty();
        for (var origin : ORIGINS)
        {
            assertThat(flesh[origin[0]][origin[1]][origin[2]]).isFalse();
            assertThat(flesh[origin[0]][origin[1] - 1][origin[2]]).isTrue();
        }
    }

    /** A few motes show at once, each faint to full in flat steps, and they stay near the heart, under the owner's name. */
    @Test
    void aFewMotesDriftNearTheHeart()
    {
        int most = 0;
        for (float time = 1000; time < 1600; time += 0.5F)
        {
            var specks = HeartMotes.at(time, 42, HeartBeat.ROOTED_PERIOD, ORIGINS);
            for (var speck : specks)
            {
                assertThat(speck.alpha()).isBetween(0.01F, 1F);
                assertThat(speck.alpha() * 8).isCloseTo(Math.round(speck.alpha() * 8), org.assertj.core.data.Offset.offset(1e-4F));
                assertThat(speck.x()).isBetween(-0.2F, 1.2F);
                assertThat(speck.z()).isBetween(-0.2F, 1.2F);
                assertThat(speck.y()).isBetween(0.4F, 1.25F);
            }
            most = Math.max(most, specks.size());
        }
        assertThat(most).isBetween(4, 16);
    }

    @Test
    void aMoteRisesAsItAges()
    {
        var origin = ORIGINS.getFirst();
        float last = -1;
        for (float age = 0; age < HeartMotes.LIFE_TICKS; age += 1)
        {
            float y = HeartMotes.position(origin, age, 1.3F)[1];
            assertThat(y).isGreaterThan(last);
            last = y;
        }
    }
}
