package net.voxelindustry.voidheart.client.curse;

import net.voxelindustry.voidheart.client.curse.HymnLayout.Edge;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class HymnMeshTest
{
    private static final int     SEEDS   = 60;
    private static final float[][] SCREENS = {{480, 270}, {640, 360}, {427, 240}, {320, 240}, {270, 480}};

    private static long[] seeds()
    {
        var random = new Random(0xF1E5);
        var seeds = new long[SEEDS];
        for (int i = 0; i < SEEDS; i++)
            seeds[i] = random.nextLong();
        return seeds;
    }

    /**
     * Farthest in from the nearest edge any vertex goes, as a fraction of the screen across that edge.
     */
    private static final class Deepest implements HymnMesh.Quads
    {
        final float width;
        final float height;
        float deepest;
        float deepestTentacleTip;

        Deepest(float width, float height)
        {
            this.width = width;
            this.height = height;
        }

        @Override
        public void quad(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3)
        {
            check(x0, y0);
            check(x1, y1);
            check(x2, y2);
            check(x3, y3);
        }

        private void check(float x, float y)
        {
            float fromSide = Math.min(x, width - x) / width;
            float fromTopOrBottom = Math.min(y, height - y) / height;
            deepest = Math.max(deepest, Math.min(fromSide, fromTopOrBottom));
        }
    }

    @Test
    void neverReachesPastATenthOfTheScreen()
    {
        for (var screen : SCREENS)
            for (long seed : seeds())
            {
                var layout = HymnLayout.of(seed, screen[0], screen[1]);
                var deepest = new Deepest(screen[0], screen[1]);
                for (float time = 0; time <= HymnTimeline.TICKS; time += 0.75F)
                    HymnMesh.build(layout, screen[0], screen[1], time, deepest);
                assertThat(deepest.deepest).as("seed %d on %sx%s", seed, screen[0], screen[1]).isLessThanOrEqualTo(HymnLayout.REACH + 1.0E-4F);
            }
    }

    @Test
    void reachesNearlyATenthOfTheScreen()
    {
        for (long seed : seeds())
        {
            var layout = HymnLayout.of(seed, 480, 270);
            var deepest = new Deepest(480, 270);
            for (float time = 40; time <= 80; time += 1)
                HymnMesh.build(layout, 480, 270, time, deepest);
            assertThat(deepest.deepest).as("seed %d", seed).isGreaterThan(HymnLayout.REACH * 0.85F);
        }
    }

    @Test
    void comesFromEveryEdge()
    {
        for (long seed : seeds())
        {
            var layout = HymnLayout.of(seed, 480, 270);
            for (var edge : Edge.values())
            {
                assertThat(layout.tentacles()).as("tentacles on %s", edge).anyMatch(tentacle -> tentacle.edge() == edge);
                assertThat(layout.teeth()).as("teeth on %s", edge).anyMatch(tooth -> tooth.edge() == edge);
                assertThat(layout.eyes()).as("eyes on %s", edge).anyMatch(eye -> eye.edge() == edge);
            }
            assertThat(layout.eyes()).anyMatch(eye -> eye.pupils() > 1);
        }
    }

    @Test
    void sameSeedSameFlesh()
    {
        var first = new StringBuilder();
        var second = new StringBuilder();
        HymnMesh.build(HymnLayout.of(42, 480, 270), 480, 270, 50.5F, (x0, y0, c0, x1, y1, c1, x2, y2, c2, x3, y3, c3) -> first.append(x0).append(y2).append(c3));
        HymnMesh.build(HymnLayout.of(42, 480, 270), 480, 270, 50.5F, (x0, y0, c0, x1, y1, c1, x2, y2, c2, x3, y3, c3) -> second.append(x0).append(y2).append(c3));
        assertThat(first.toString()).isEqualTo(second.toString());
    }

    @Test
    void wriggles()
    {
        var layout = HymnLayout.of(7, 480, 270);
        var before = new StringBuilder();
        var after = new StringBuilder();
        HymnMesh.build(layout, 480, 270, 60, (x0, y0, c0, x1, y1, c1, x2, y2, c2, x3, y3, c3) -> before.append(x0).append(',').append(y0));
        HymnMesh.build(layout, 480, 270, 62, (x0, y0, c0, x1, y1, c1, x2, y2, c2, x3, y3, c3) -> after.append(x0).append(',').append(y0));
        assertThat(before.toString()).isNotEqualTo(after.toString());
    }

    @Test
    void nothingAtFirstAndNothingAtTheEnd()
    {
        var layout = HymnLayout.of(3, 480, 270);
        var first = new Deepest(480, 270);
        HymnMesh.build(layout, 480, 270, 0, first);
        assertThat(first.deepest).isLessThan(0.002F);
        assertThat(HymnMesh.build(layout, 480, 270, HymnTimeline.TICKS, first)).isZero();
    }

    @Test
    void snapsBackQuickly()
    {
        var layout = HymnLayout.of(3, 480, 270);
        var holding = new Deepest(480, 270);
        HymnMesh.build(layout, 480, 270, HymnTimeline.RETRACT_FROM, holding);
        var mostlyBack = new Deepest(480, 270);
        HymnMesh.build(layout, 480, 270, HymnTimeline.TICKS - 1, mostlyBack);
        assertThat(HymnTimeline.RETRACT_TICKS).isLessThanOrEqualTo(10);
        assertThat(mostlyBack.deepest).isLessThan(holding.deepest * 0.5F);
    }

    @Test
    void heartQuickens()
    {
        var beats = HymnTimeline.beatTicks();
        assertThat(beats.length).isGreaterThanOrEqualTo(8);
        assertThat(beats[0]).isLessThan(5);
        assertThat(beats[beats.length - 1]).isLessThan(HymnTimeline.RETRACT_FROM);
        for (int i = 2; i < beats.length; i++)
            assertThat(beats[i] - beats[i - 1]).isLessThanOrEqualTo(beats[i - 1] - beats[i - 2]);
        assertThat(beats[beats.length - 1] - beats[beats.length - 2]).isLessThan((beats[1] - beats[0]) / 3);
        assertThat(HymnTimeline.pitch(beats.length - 1)).isGreaterThan(HymnTimeline.pitch(0));
    }
}
