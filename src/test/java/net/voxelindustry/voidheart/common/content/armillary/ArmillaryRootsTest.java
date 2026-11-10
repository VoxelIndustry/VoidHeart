package net.voxelindustry.voidheart.common.content.armillary;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ArmillaryRootsTest
{
    private static final float SOURCE_X = 0.5F;
    private static final float TARGET_X = 0.8F;
    private static final float TARGET_Y = 0.7F;
    private static final float RADIUS   = 0.055F;
    private static final float MOUNT_X  = 0.06F;
    private static final float MOUNT_Y  = 0.09F;

    private static List<ArmillaryRoots.Branch> grow(long seed)
    {
        return ArmillaryRoots.grow(seed, SOURCE_X, MOUNT_X, MOUNT_Y, TARGET_X, TARGET_Y, RADIUS);
    }

    private static ArmillaryRoots.Branch main(List<ArmillaryRoots.Branch> branches)
    {
        var main = branches.stream().filter(ArmillaryRoots.Branch::main).toList();
        assertThat(main).hasSize(1);
        return main.getFirst();
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 7, 123456789, -987654321 })
    void oneRoot_goesFromTheMountToTheBubble(long seed)
    {
        var main = main(grow(seed));
        int last = main.segments();

        assertThat(mountDistance(main.xs()[0], main.ys()[0])).as("on the mount's rim").isCloseTo(1, within(1.0E-3F));
        float startAngle = (float) Math.atan2(main.ys()[0], (main.xs()[0] - SOURCE_X) * ArmillaryRoots.ASPECT);
        float targetAngle = (float) Math.atan2(TARGET_Y - RADIUS, (TARGET_X - SOURCE_X) * ArmillaryRoots.ASPECT);
        assertThat(startAngle).as("leaving the way to the bubble").isCloseTo(targetAngle, within(1.0E-3F));
        assertThat(main.xs()[last]).isCloseTo(TARGET_X, within(1.0E-4F));
        assertThat(main.ys()[last]).isCloseTo(TARGET_Y - RADIUS, within(1.0E-4F));
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 7, 123456789, -987654321 })
    void everyOtherRoot_diesBeforeTheSearchEnds(long seed)
    {
        var branches = grow(seed);
        assertThat(branches).hasSizeGreaterThan(10);
        for (var branch : branches)
        {
            if (branch.main())
                continue;
            assertThat(branch.witherEnd()).isLessThanOrEqualTo(1);
            assertThat(branch.withered(1)).isEqualTo(1);
            assertThat(branch.growStart()).isLessThan(branch.growEnd());
            assertThat(branch.growEnd()).isLessThanOrEqualTo(branch.witherStart());
        }
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 7, 123456789, -987654321 })
    void deadEnds_neverReachTheBubble(long seed)
    {
        for (var branch : grow(seed))
        {
            if (branch.main())
                continue;
            for (int point = 1; point < branch.xs().length; point++)
            {
                float dx = (branch.xs()[point] - TARGET_X) * ArmillaryRoots.ASPECT;
                float dy = branch.ys()[point] - TARGET_Y;
                assertThat(Mth.sqrt(dx * dx + dy * dy)).isGreaterThanOrEqualTo(RADIUS);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 7, 123456789, -987654321 })
    void roots_stayOnTheChart(long seed)
    {
        for (var branch : grow(seed))
        {
            for (int point = 0; point < branch.xs().length; point++)
            {
                assertThat(branch.xs()[point]).isBetween(0F, 1F);
                assertThat(branch.ys()[point]).as("above the strata").isBetween(0F, ArmillaryChart.STRATA_TOP);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 7, 123456789, -987654321 })
    void rootToTheStrata_endsOnItsLip_andNoneDigsIn(long seed)
    {
        var branches = ArmillaryRoots.grow(seed, SOURCE_X, MOUNT_X, MOUNT_Y, 0.2F, ArmillaryChart.STRATA_TOP, 0);
        var main = main(branches);
        assertThat(main.xs()[main.segments()]).isCloseTo(0.2F, within(1.0E-4F));
        assertThat(main.ys()[main.segments()]).isCloseTo(ArmillaryChart.STRATA_TOP, within(1.0E-4F));

        for (var branch : branches)
        {
            for (float y : branch.ys())
                assertThat(y).isLessThanOrEqualTo(ArmillaryChart.STRATA_TOP);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 7, 123456789, -987654321 })
    void everyRoot_growsFromTheMountOrFromAnotherRoot(long seed)
    {
        var branches = grow(seed);
        for (var branch : branches)
        {
            if (Math.abs(mountDistance(branch.xs()[0], branch.ys()[0]) - 1) < 1.0E-3F)
                continue;

            boolean attached = branches.stream().filter(other -> other != branch).anyMatch(other -> passesThrough(other, branch.xs()[0], branch.ys()[0]));
            assertThat(attached).as("root starting at %s, %s", branch.xs()[0], branch.ys()[0]).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 7, 123456789, -987654321 })
    void noRoot_passesUnderTheMount(long seed)
    {
        for (var branch : grow(seed))
        {
            for (int point = 0; point < branch.xs().length; point++)
                assertThat(mountDistance(branch.xs()[point], branch.ys()[point])).isGreaterThanOrEqualTo(1 - 1.0E-3F);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 7, 123456789, -987654321 })
    void mainRoot_keepsHeadingForTheBubble(long seed)
    {
        var main = main(grow(seed));
        for (int point = 1; point < main.xs().length; point++)
        {
            float stepX = (main.xs()[point] - main.xs()[point - 1]) * ArmillaryRoots.ASPECT;
            float stepY = main.ys()[point] - main.ys()[point - 1];
            float toX = (TARGET_X - main.xs()[point - 1]) * ArmillaryRoots.ASPECT;
            float toY = TARGET_Y - RADIUS - main.ys()[point - 1];
            float cos = (stepX * toX + stepY * toY) / (Mth.sqrt(stepX * stepX + stepY * stepY) * Mth.sqrt(toX * toX + toY * toY) + 1.0E-6F);
            assertThat(cos).as("step %s heads within 90 degrees of the bubble", point).isGreaterThan(0);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 7, 123456789, -987654321 })
    void deadEnds_mostlyEndCloserToTheBubbleThanTheyStart(long seed)
    {
        int closer = 0, total = 0;
        for (var branch : grow(seed))
        {
            if (branch.main())
                continue;
            int last = branch.segments();
            total++;
            if (targetDistance(branch.xs()[last], branch.ys()[last]) < targetDistance(branch.xs()[0], branch.ys()[0]))
                closer++;
        }
        assertThat(closer).isGreaterThanOrEqualTo(total * 3 / 4);
    }

    private static float targetDistance(float x, float y)
    {
        float dx = (x - TARGET_X) * ArmillaryRoots.ASPECT;
        float dy = y - TARGET_Y;
        return Mth.sqrt(dx * dx + dy * dy);
    }

    /** 1 on the mount's rim, less under it. */
    private static float mountDistance(float x, float y)
    {
        float dx = (x - SOURCE_X) / MOUNT_X;
        float dy = y / MOUNT_Y;
        return Mth.sqrt(dx * dx + dy * dy);
    }

    private static boolean passesThrough(ArmillaryRoots.Branch branch, float x, float y)
    {
        for (int point = 0; point < branch.xs().length; point++)
        {
            if (Math.abs(branch.xs()[point] - x) < 1.0E-4F && Math.abs(branch.ys()[point] - y) < 1.0E-4F)
                return true;
        }
        return false;
    }

    @Test
    void mainRoot_neverMovesBack_andArrivesExactlyWhenTheSearchEnds()
    {
        var main = main(grow(3));
        float previous = 0;
        for (int step = 0; step <= 1000; step++)
        {
            float grown = main.grown(step / 1000F);
            assertThat(grown).isGreaterThanOrEqualTo(previous - 1.0E-5F);
            previous = grown;
        }
        assertThat(main.grown(0)).isCloseTo(0, within(1.0E-5F));
        assertThat(main.grown(1)).isCloseTo(1, within(1.0E-4F));
    }

    @Test
    void sameBubble_growsTheSameRoots()
    {
        var first = grow(99);
        var second = grow(99);
        assertThat(first).hasSameSizeAs(second);
        for (int index = 0; index < first.size(); index++)
        {
            assertThat(Arrays.equals(first.get(index).xs(), second.get(index).xs())).isTrue();
            assertThat(first.get(index).witherEnd()).isEqualTo(second.get(index).witherEnd());
        }
    }
}
