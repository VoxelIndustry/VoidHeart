package net.voxelindustry.voidheart.client.curse;

import net.voxelindustry.voidheart.client.curse.RaptureFieldLayout.Family;
import net.voxelindustry.voidheart.client.curse.sky.StarField;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class RaptureFieldLayoutTest
{
    private static final int SEEDS = 300;

    // From the mesh's point of view, as far as it can round a size up: the discs must keep this much clear on top of it
    private static final double ROUNDING = 1.0E-5;
    private static final float  DOME_RADIUS = 100;

    private static final float GIANT_FROM = 5;
    private static final float[] HISTOGRAM_FROM = {1, 1.2F, 1.6F, 2.2F, 3, 4, 5, 6};
    // Past this between its strongest and weakest color channels, a goat's iris would be as loud as a plain amber one
    private static final int GOAT_CHROMA_MOST = 115;
    // Drawn as the plain pattern of the smallest eyes
    private static final float PLAIN_BELOW = 1.5F;

    private static RaptureFieldLayout layout(long seed)
    {
        return RaptureFieldLayout.of(seed, StarField.vanillaStars());
    }

    private static long[] seeds()
    {
        var random = new Random(0x5EED);
        var seeds = new long[SEEDS];
        for (int i = 0; i < SEEDS; i++)
            seeds[i] = random.nextLong();
        return seeds;
    }

    private static double angle(RaptureFieldLayout layout, int a, int b)
    {
        double dot = (double) layout.directionX(a) * layout.directionX(b) + (double) layout.directionY(a) * layout.directionY(b) + (double) layout.directionZ(a) * layout.directionZ(b);
        return Math.acos(Math.clamp(dot, -1, 1));
    }

    @Test
    void readsEveryVanillaStar()
    {
        var layout = layout(1);
        assertThat(layout.stars()).isBetween(700, 1000);
        for (int star = 0; star < layout.stars(); star++)
        {
            float length = layout.directionX(star) * layout.directionX(star) + layout.directionY(star) * layout.directionY(star) + layout.directionZ(star) * layout.directionZ(star);
            assertThat(length).isBetween(0.999F, 1.001F);
        }
    }

    @Test
    void noTwoEyesEverTouch()
    {
        double margin = Math.atan(RaptureFieldLayout.GAP_LEAST / DOME_RADIUS) - ROUNDING;
        for (long seed : seeds())
        {
            var layout = layout(seed);
            var eyes = layout.eyes();
            for (int i = 0; i < eyes.size(); i++)
            {
                var a = eyes.get(i);
                for (int j = i + 1; j < eyes.size(); j++)
                {
                    var b = eyes.get(j);
                    double clear = angle(layout, a.star(), b.star()) - RaptureFieldLayout.discAngle(a.width()) - RaptureFieldLayout.discAngle(b.width());
                    assertThat(clear).as("seed %d, stars %d and %d of half widths %f and %f", seed, a.star(), b.star(), a.width(), b.width()).isGreaterThanOrEqualTo(margin);
                }
            }
        }
    }

    @Test
    void sizesStayInRangeAndReachBothEnds()
    {
        for (long seed : seeds())
        {
            var eyes = layout(seed).eyes();
            float least = Float.MAX_VALUE;
            float most = 0;
            for (var eye : eyes)
            {
                assertThat(eye.width()).isBetween(RaptureFieldLayout.WIDTH_LEAST, RaptureFieldLayout.WIDTH_MOST);
                least = Math.min(least, eye.width());
                most = Math.max(most, eye.width());
            }
            assertThat(least).as("smallest of seed %d", seed).isLessThan(RaptureFieldLayout.WIDTH_LEAST + 0.1F);
            assertThat(most).as("largest of seed %d", seed).isGreaterThan(RaptureFieldLayout.WIDTH_MOST - 0.8F);
        }
    }

    @Test
    void sizesAreStronglyUneven()
    {
        for (long seed : seeds())
        {
            var eyes = layout(seed).eyes();
            long small = eyes.stream().filter(eye -> eye.width() < 1.6F).count();
            long medium = eyes.stream().filter(eye -> eye.width() >= 1.6F && eye.width() < 3F).count();
            long large = eyes.stream().filter(eye -> eye.width() >= 3F && eye.width() < GIANT_FROM).count();
            long giants = eyes.stream().filter(eye -> eye.width() >= GIANT_FROM).count();

            assertThat(small).as("seed %d", seed).isGreaterThan(medium);
            assertThat(medium).as("seed %d", seed).isGreaterThan(large);
            assertThat(large).as("seed %d", seed).isGreaterThan(giants);
            assertThat(giants).as("seed %d", seed).isBetween(12L, 18L);
        }
    }

    @Test
    void smallEyesOutnumberTheRestOverheadAtMidnight()
    {
        for (long seed : seeds())
        {
            var layout = layout(seed);
            // At midnight the star field stands upside down
            var overhead = layout.eyes().stream().filter(eye -> layout.directionY(eye.star()) < 0).toList();
            long small = overhead.stream().filter(eye -> eye.width() < 1.6F).count();
            long medium = overhead.stream().filter(eye -> eye.width() >= 1.6F && eye.width() < 3F).count();
            long large = overhead.stream().filter(eye -> eye.width() >= 3F).count();
            assertThat(small).as("seed %d", seed).isGreaterThan(medium);
            assertThat(medium).as("seed %d", seed).isGreaterThan(large);
        }
    }

    @Test
    void aReasonableNumberRemainWhereverTheHorizonIs()
    {
        var random = new Random(99);
        for (long seed : seeds())
        {
            var layout = layout(seed);
            assertThat(layout.eyes().size()).as("seed %d", seed).isBetween(260, 620);

            // Any half of the dome, as it turns with the time of day
            for (int half = 0; half < 8; half++)
            {
                double x = random.nextGaussian();
                double y = random.nextGaussian();
                double z = random.nextGaussian();
                int giants = 0;
                int all = 0;
                for (var eye : layout.eyes())
                {
                    if (x * layout.directionX(eye.star()) + y * layout.directionY(eye.star()) + z * layout.directionZ(eye.star()) <= 0)
                        continue;
                    all++;
                    if (eye.width() >= GIANT_FROM)
                        giants++;
                }
                assertThat(all).as("seed %d", seed).isGreaterThan(100);
                assertThat(giants).as("giants in one half of the dome of seed %d", seed).isBetween(2, 13);
            }
        }
    }

    @Test
    void differsPerCurseAndStaysTheSameForOne()
    {
        var a = layout(11).eyes();
        var b = layout(12).eyes();
        assertThat(layout(11).eyes()).isEqualTo(a);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void familiesAreUnevenAndElaborateOnesNeedRoom()
    {
        for (long seed : seeds())
        {
            var eyes = layout(seed).eyes();
            var seen = EnumSet.noneOf(Family.class);
            var giantFamilies = EnumSet.noneOf(Family.class);
            for (var eye : eyes)
            {
                seen.add(eye.family());
                if (eye.width() >= GIANT_FROM + 0.2F)
                    giantFamilies.add(eye.family());
                if (eye.width() < 1.5F)
                    assertThat(eye.family()).isIn(Family.ALMOND, Family.ROUND, Family.NARROW);
                if (eye.family() == Family.WRONG)
                    assertThat(eye.width()).isGreaterThanOrEqualTo(3F);
            }
            assertThat(seen.size()).as("seed %d", seed).isGreaterThanOrEqualTo(6);
            assertThat(giantFamilies.size()).as("seed %d", seed).isGreaterThanOrEqualTo(5);
        }
    }

    @Test
    void goatsStayMuted()
    {
        long amber = 0;
        long goats = 0;
        for (long seed : seeds())
        {
            for (var eye : layout(seed).eyes())
            {
                if (eye.family() != Family.GOAT)
                    continue;

                goats++;
                if (eye.irisSwatch() == RaptureFieldLayout.IRIS_AMBER)
                    amber++;
                int red = eye.iris() >> 16 & 0xFF;
                int green = eye.iris() >> 8 & 0xFF;
                int blue = eye.iris() & 0xFF;
                assertThat(Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue))).as("seed %d", seed).isLessThanOrEqualTo(GOAT_CHROMA_MOST);
            }
        }
        assertThat(amber / (double) goats).isLessThan(0.3);
    }

    @Test
    void theSicklyGreenIsThereWithoutDominating()
    {
        long green = 0;
        long all = 0;
        for (long seed : seeds())
        {
            var eyes = layout(seed).eyes();
            long here = eyes.stream().filter(eye -> eye.irisSwatch() == RaptureFieldLayout.IRIS_GREEN).count();
            assertThat(here / (double) eyes.size()).as("seed %d", seed).isBetween(0.08, 0.27);
            var large = eyes.stream().filter(eye -> eye.width() >= 3F).toList();
            long greenLarge = large.stream().filter(eye -> eye.irisSwatch() == RaptureFieldLayout.IRIS_GREEN).count();
            assertThat(greenLarge / (double) large.size()).as("large eyes of seed %d", seed).isLessThan(0.25);
            green += here;
            all += eyes.size();
        }
        assertThat(green / (double) all).isBetween(0.15, 0.2);
    }

    /**
     * Not a check: what a curse looks like in numbers, printed for whoever tunes this.
     */
    @Test
    void printsHistogram()
    {
        var histogram = new long[HISTOGRAM_FROM.length];
        var families = new EnumMap<Family, Long>(Family.class);
        long eyes = 0;
        long aboveHorizon = 0;
        long giantsAbove = 0;
        long stars = 0;
        long green = 0;
        long blackSclera = 0;
        long plain = 0;
        int leastEyes = Integer.MAX_VALUE;
        int mostEyes = 0;
        for (long seed : seeds())
        {
            var layout = layout(seed);
            stars = layout.stars();
            leastEyes = Math.min(leastEyes, layout.eyes().size());
            mostEyes = Math.max(mostEyes, layout.eyes().size());
            for (var eye : layout.eyes())
            {
                eyes++;
                if (eye.irisSwatch() == RaptureFieldLayout.IRIS_GREEN)
                    green++;
                if (eye.veins() == 0)
                    blackSclera++;
                if (eye.width() < PLAIN_BELOW)
                    plain++;
                families.merge(eye.family(), 1L, Long::sum);
                int bin = HISTOGRAM_FROM.length - 1;
                while (eye.width() < HISTOGRAM_FROM[bin] && bin > 0)
                    bin--;
                histogram[bin]++;
                // At midnight the star field stands upside down
                if (layout.directionY(eye.star()) < 0)
                {
                    aboveHorizon++;
                    if (eye.width() >= GIANT_FROM)
                        giantsAbove++;
                }
            }
        }
        System.out.println("Rapture field over " + SEEDS + " curses, of " + stars + " stars: " + (eyes / SEEDS) + " eyes on the dome (" + leastEyes + " to " + mostEyes + "), "
                + (aboveHorizon / SEEDS) + " above the horizon at midnight, of which " + (giantsAbove / (double) SEEDS) + " of half width " + GIANT_FROM + " or more");
        var line = new StringBuilder();
        for (int bin = 0; bin < HISTOGRAM_FROM.length; bin++)
            line.append(String.format("%.1f+: %.1f  ", HISTOGRAM_FROM[bin], histogram[bin] / (double) SEEDS));
        System.out.println("Half widths, eyes per curse: " + line);
        var shares = new StringBuilder();
        for (var entry : families.entrySet())
            shares.append(String.format("%s %.1f%%  ", entry.getKey(), entry.getValue() * 100D / eyes));
        System.out.println("Families: " + shares);
        System.out.println(String.format("Green irises %.1f%%, black scleras %.1f%%, of half width under %.1f %.1f%%", green * 100D / eyes, blackSclera * 100D / eyes, PLAIN_BELOW, plain * 100D / eyes));
    }
}
