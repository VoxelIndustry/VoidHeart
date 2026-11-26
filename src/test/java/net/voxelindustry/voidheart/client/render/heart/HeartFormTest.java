package net.voxelindustry.voidheart.client.render.heart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.voxelindustry.voidheart.client.render.heart.HeartShape.SIZE;
import static org.assertj.core.api.Assertions.assertThat;

class HeartFormTest
{
    private static final int[] SEEDS = { 0, 1, 7, 42, -1, 123456789, "Ourten".hashCode(), 0x7FFFFFFF };

    @Test
    void veinsLieAgainstTheHeartOrHangBelowIt()
    {
        var flesh = HeartShape.voxels(false);
        for (int seed : SEEDS)
        {
            var veins = HeartVeins.grow(flesh, seed);
            eachVein(veins, (x, y, z, along) ->
            {
                assertThat(flesh[x][y][z]).as("seed %d vein inside the flesh at %d %d %d", seed, x, y, z).isFalse();
                assertThat(x).isBetween(1, SIZE - 2);
                assertThat(z).isBetween(1, SIZE - 2);
                assertThat(y).isLessThan(SIZE - 1);
            });
        }
    }

    /** Each vein pixel follows one nearer the heart, and the first touch it, so veins grow out from the heart without gaps. */
    @Test
    void veinsGrowOutFromTheHeartWithoutGaps()
    {
        var flesh = HeartShape.voxels(false);
        for (int seed : SEEDS)
        {
            var veins = HeartVeins.grow(flesh, seed);
            eachVein(veins, (x, y, z, along) ->
            {
                boolean joined = false;
                for (var side : sides())
                {
                    int nx = x + side[0], ny = y + side[1], nz = z + side[2];
                    if (nx < 0 || ny < 0 || nz < 0 || nx >= SIZE || ny >= SIZE || nz >= SIZE)
                        continue;
                    if (along == 0 ? flesh[nx][ny][nz] : veins[nx][ny][nz] != HeartVeins.NONE && veins[nx][ny][nz] < along)
                        joined = true;
                }
                assertThat(joined).as("seed %d vein at %d %d %d, %d along, joins nothing nearer the heart", seed, x, y, z, along).isTrue();
            });
        }
    }

    @Test
    void severalVeinsReachTheFloor()
    {
        var flesh = HeartShape.voxels(false);
        for (int seed : SEEDS)
            assertThat(HeartVeins.feet(HeartVeins.grow(flesh, seed))).as("seed %d", seed).hasSizeGreaterThanOrEqualTo(2);
    }

    /** Every owner's heart roots: whatever its seed, a vein reaches the floor to carry on over it. */
    @Test
    void everyHeartReachesTheFloor()
    {
        var flesh = HeartShape.voxels(false);
        int[] counts = new int[8];
        for (int seed = 0; seed < 2000; seed++)
            counts[Math.min(7, HeartVeins.feet(HeartVeins.grow(flesh, seed * 0x9E3779B9)).size())]++;
        System.out.println("feet per heart: " + Arrays.toString(counts));
        assertThat(counts[0]).isZero();
    }

    @Test
    void ownersGrowDifferentVeins()
    {
        var flesh = HeartShape.voxels(false);
        assertThat(Arrays.deepEquals(HeartVeins.grow(flesh, 1), HeartVeins.grow(flesh, 2))).isFalse();
        assertThat(Arrays.deepEquals(HeartVeins.grow(flesh, 1), HeartVeins.grow(flesh, 1))).isTrue();
    }

    /**
     * Veins on the heart are painted into faces the flesh would show anyway, and those faces left out of the flesh, so together they cover each exposed
     * face exactly once and none lies over another, at rest, swelled and while growing.
     */
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void paintTakesTheFleshsOwnFaces(boolean swelled)
    {
        var flesh = HeartShape.voxels(swelled);
        int exposed = HeartShape.faces(flesh).stream().mapToInt(HeartShape.Face::area).sum();
        for (int seed : SEEDS)
        {
            var veins = HeartVeins.grow(HeartShape.voxels(false), seed);
            int length = HeartVeins.length(veins);
            for (int reach : new int[] { 0, length / 3, length / 2, length })
            {
                var form = HeartForm.build(swelled, veins, reach);
                var faces = new ArrayList<>(form.flesh());
                form.paint().forEach(paint -> faces.add(paint.face()));
                assertNoOverlap(faces);
                assertThat(faces.stream().mapToInt(HeartShape.Face::area).sum()).as("seed %d reach %d", seed, reach).isEqualTo(exposed);
                for (var strand : form.strands())
                    assertThat(flesh[strand.x()][strand.y()][strand.z()]).isFalse();
            }
        }
    }

    @Test
    void someVeinsHangFreeAndSomeLieOnTheHeart()
    {
        for (int seed : SEEDS)
        {
            var veins = HeartVeins.grow(HeartShape.voxels(false), seed);
            var form = HeartForm.build(false, veins, HeartVeins.length(veins));
            assertThat(form.paint()).as("seed %d", seed).hasSizeGreaterThan(10);
            assertThat(form.strands()).as("seed %d", seed).hasSizeGreaterThanOrEqualTo(6);
        }
    }

    @Test
    void growingVeinsOnlyAddVeinPixels()
    {
        var veins = HeartVeins.grow(HeartShape.voxels(false), 42);
        int length = HeartVeins.length(veins);
        int shown = -1;
        for (int reach = 0; reach <= length; reach++)
        {
            var form = HeartForm.build(false, veins, reach);
            int count = form.paint().size() + form.strands().size();
            assertThat(count).isGreaterThanOrEqualTo(shown);
            shown = count;
        }
        var bare = HeartForm.build(false, veins, 0);
        assertThat(bare.paint()).isEmpty();
        assertThat(bare.strands()).isEmpty();
        assertThat(bare.flesh()).isEqualTo(HeartShape.faces(HeartShape.voxels(false)));
    }

    /** Prints one owner's veins as layers seen from above, to judge their layout. */
    @Test
    void printVeins()
    {
        var flesh = HeartShape.voxels(false);
        var veins = HeartVeins.grow(flesh, "Ourten".hashCode());
        var out = new StringBuilder();
        for (int y = SIZE - 1; y >= 0; y--)
        {
            out.append("y=").append(y).append('\n');
            for (int z = 0; z < SIZE; z++)
            {
                for (int x = 0; x < SIZE; x++)
                    out.append(veins[x][y][z] != HeartVeins.NONE ? 'V' : flesh[x][y][z] ? '#' : '.');
                out.append('\n');
            }
        }
        System.out.println(out);
    }

    private static void assertNoOverlap(List<HeartShape.Face> faces)
    {
        for (int i = 0; i < faces.size(); i++)
        {
            for (int j = i + 1; j < faces.size(); j++)
            {
                var first = faces.get(i);
                var second = faces.get(j);
                if (first.axis() != second.axis() || first.plane() != second.plane())
                    continue;
                boolean overlap = first.a0() < second.a1() && second.a0() < first.a1() && first.b0() < second.b1() && second.b0() < first.b1();
                assertThat(overlap).as("%s and %s", first, second).isFalse();
            }
        }
    }

    private interface VeinVisitor
    {
        void visit(int x, int y, int z, int along);
    }

    private static void eachVein(int[][][] veins, VeinVisitor visitor)
    {
        for (int x = 0; x < SIZE; x++)
        {
            for (int y = 0; y < SIZE; y++)
            {
                for (int z = 0; z < SIZE; z++)
                {
                    if (veins[x][y][z] != HeartVeins.NONE)
                        visitor.visit(x, y, z, veins[x][y][z]);
                }
            }
        }
    }

    private static int[][] sides()
    {
        return new int[][] { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
    }
}
