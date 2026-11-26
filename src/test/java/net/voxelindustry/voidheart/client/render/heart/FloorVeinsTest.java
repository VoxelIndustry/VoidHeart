package net.voxelindustry.voidheart.client.render.heart;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.client.render.heart.FloorVeins.CELLS;
import static net.voxelindustry.voidheart.client.render.heart.FloorVeins.NONE;
import static net.voxelindustry.voidheart.client.render.heart.FloorVeins.PIXELS;
import static net.voxelindustry.voidheart.client.render.heart.FloorVeins.RADIUS;
import static org.assertj.core.api.Assertions.assertThat;

class FloorVeinsTest
{
    private static final int[] SEEDS = { 0, 1, 7, 42, -1, 123456789, "Ourten".hashCode(), 0x7FFFFFFF };

    @Test
    void aQuarterTurnFourTimesOverIsNoTurn()
    {
        for (int x = 0; x < 16; x++)
        {
            for (int z = 0; z < 16; z++)
            {
                assertThat(FloorVeins.turn(x, z, 4)).containsExactly(x, z);
                var once = FloorVeins.turn(x, z, 1);
                assertThat(FloorVeins.turn(once[0], once[1], 3)).containsExactly(x, z);
            }
        }
        assertThat(FloorVeins.turn(0, 0, 1)).containsExactly(15, 0);
    }

    /** Lines one pixel wide: no two by two square of vein anywhere, as vines never thicken into a blot. */
    @Test
    void veinsNeverThicken()
    {
        for (int seed : SEEDS)
        {
            var along = grid(grow(fullFloor(), seed));
            for (int x = 0; x + 1 < PIXELS; x++)
            {
                for (int z = 0; z + 1 < PIXELS; z++)
                {
                    boolean square = along[x][z] != NONE && along[x + 1][z] != NONE && along[x][z + 1] != NONE && along[x + 1][z + 1] != NONE;
                    assertThat(square).as("seed %d at %d %d", seed, x, z).isFalse();
                }
            }
        }
    }

    /** Each pixel follows one nearer the heart, so the veins creep out from the strands without gaps. */
    @Test
    void veinsCreepOutFromTheFeetWithoutGaps()
    {
        for (int seed : SEEDS)
        {
            var growth = grow(fullFloor(), seed);
            var along = grid(growth);
            for (var pixel : growth.pixels())
            {
                if (pixel.along() == growth.first())
                    continue;
                boolean joined = false;
                for (int[] side : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
                {
                    int x = pixel.x() + side[0], z = pixel.z() + side[1];
                    if (x >= 0 && z >= 0 && x < PIXELS && z < PIXELS && along[x][z] != NONE && along[x][z] < pixel.along())
                        joined = true;
                }
                boolean foot = pixel.x() / 16 == RADIUS && pixel.z() / 16 == RADIUS;
                assertThat(joined || foot).as("seed %d at %d %d", seed, pixel.x(), pixel.z()).isTrue();
            }
        }
    }

    @Test
    void veinsReachWellPastTheHeartsBlock()
    {
        for (int seed : SEEDS)
        {
            var growth = grow(fullFloor(), seed);
            int furthest = 0;
            for (var pixel : growth.pixels())
                furthest = Math.max(furthest, Math.max(Math.abs(pixel.x() / 16 - RADIUS), Math.abs(pixel.z() / 16 - RADIUS)));
            assertThat(furthest).as("seed %d", seed).isGreaterThanOrEqualTo(3);
            assertThat(growth.pixels().size()).as("seed %d", seed).isGreaterThan(100);
        }
    }

    @Test
    void veinsStopWhereTheFloorDoes()
    {
        var floor = fullFloor();
        for (int z = 0; z < CELLS; z++)
            floor[RADIUS + 1][z] = false;
        for (int seed : SEEDS)
        {
            for (var pixel : grow(floor, seed).pixels())
                assertThat(floor[pixel.x() / 16][pixel.z() / 16]).isTrue();
        }
    }

    /** Prints one owner's floor veins from above, to judge their look. */
    @Test
    void printFloor()
    {
        var along = grid(grow(fullFloor(), "Ourten".hashCode()));
        var out = new StringBuilder();
        for (int z = 0; z < PIXELS; z++)
        {
            for (int x = 0; x < PIXELS; x++)
                out.append(along[x][z] != NONE ? '#' : (x % 16 == 0 || z % 16 == 0) ? ',' : '.');
            out.append('\n');
        }
        System.out.println(out);
    }

    private static FloorVeins.Growth grow(boolean[][] floor, int seed)
    {
        var veins = HeartVeins.grow(HeartShape.voxels(false), seed);
        List<int[]> feet = new ArrayList<>(HeartVeins.feet(veins));
        return FloorVeins.grow(floor, feet, seed);
    }

    private static int[][] grid(FloorVeins.Growth growth)
    {
        var along = new int[PIXELS][PIXELS];
        for (var row : along)
            java.util.Arrays.fill(row, NONE);
        for (var pixel : growth.pixels())
            along[pixel.x()][pixel.z()] = pixel.along();
        return along;
    }

    private static boolean[][] fullFloor()
    {
        var floor = new boolean[CELLS][CELLS];
        for (var row : floor)
            java.util.Arrays.fill(row, true);
        return floor;
    }
}
