package net.voxelindustry.voidheart.client.render.heart;

import org.junit.jupiter.api.Test;

import static net.voxelindustry.voidheart.client.render.heart.HeartShape.SIZE;
import static org.assertj.core.api.Assertions.assertThat;

class HeartCageTest
{
    private static final int[] SEEDS = { 0, 1, 7, 42, -1, 123456789, "Ourten".hashCode(), 0x7FFFFFFF };

    /** Bone never lies where the heart swells to or where a vein runs, and keeps off the block's sides. */
    @Test
    void boneKeepsClearOfTheHeartAndItsVeins()
    {
        var swelled = HeartShape.voxels(true);
        for (int seed = 0; seed < 500; seed++)
        {
            int owner = seed * 0x9E3779B9;
            var veins = HeartVeins.grow(HeartShape.voxels(false), owner);
            var cage = HeartCage.grow(swelled, veins, owner);
            eachBone(cage, (x, y, z, along) ->
            {
                assertThat(swelled[x][y][z]).as("seed %d bone in the flesh at %d %d %d", owner, x, y, z).isFalse();
                assertThat(veins[x][y][z]).as("seed %d bone on a vein at %d %d %d", owner, x, y, z).isEqualTo(HeartVeins.NONE);
                assertThat(x).isBetween(1, SIZE - 2);
                assertThat(z).isBetween(1, SIZE - 2);
            });
        }
    }

    /** The cage is one piece of bone, joined face to face or across a corner on a layer, grown up the spine from the floor and out along each rib from it. */
    @Test
    void theCageGrowsOutFromTheSpineInOnePiece()
    {
        for (int seed : SEEDS)
        {
            var cage = grow(seed);
            eachBone(cage, (x, y, z, along) ->
            {
                if (y == 0 && along == 0)
                    return;
                boolean joined = false;
                for (int[] side : new int[][] { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 1, 0, 1 }, { 1, 0, -1 }, { -1, 0, 1 }, { -1, 0, -1 } })
                {
                    int nx = x + side[0], ny = y + side[1], nz = z + side[2];
                    if (nx >= 0 && ny >= 0 && nz >= 0 && nx < SIZE && ny < SIZE && nz < SIZE && cage[nx][ny][nz] != HeartCage.NONE && cage[nx][ny][nz] < along)
                        joined = true;
                }
                assertThat(joined).as("seed %d bone at %d %d %d, %d along, joins nothing grown before it", seed, x, y, z, along).isTrue();
            });
        }
    }

    /** Ribs reach around both sides of the heart toward its front, and stop short of meeting there. */
    @Test
    void ribsCurveAroundBothSidesTowardTheFront()
    {
        for (int seed : SEEDS)
        {
            var cage = grow(seed);
            int[] counts = new int[3];
            eachBone(cage, (x, y, z, along) ->
            {
                if (x <= 3)
                    counts[0]++;
                if (x >= 12)
                    counts[1]++;
                if (z >= 11)
                    counts[2]++;
            });
            assertThat(counts[0]).as("seed %d left", seed).isGreaterThanOrEqualTo(8);
            assertThat(counts[1]).as("seed %d right", seed).isGreaterThanOrEqualTo(8);
            assertThat(counts[2]).as("seed %d front", seed).isGreaterThanOrEqualTo(6);
            for (int y = 0; y < SIZE; y++)
            {
                assertThat(cage[7][y][SIZE - 2]).as("seed %d sternum", seed).isEqualTo(HeartCage.NONE);
                assertThat(cage[8][y][SIZE - 2]).as("seed %d sternum", seed).isEqualTo(HeartCage.NONE);
            }
        }
    }

    @Test
    void ownersGrowDifferentCages()
    {
        boolean differ = false;
        var first = grow(1);
        for (int seed : SEEDS)
            differ |= !java.util.Arrays.deepEquals(first, grow(seed));
        assertThat(differ).isTrue();
    }

    /** Prints one owner's cage as layers seen from above, to judge its look. */
    @Test
    void printCage()
    {
        var flesh = HeartShape.voxels(false);
        int seed = "Ourten".hashCode();
        var veins = HeartVeins.grow(flesh, seed);
        var cage = HeartCage.grow(HeartShape.voxels(true), veins, seed);
        var out = new StringBuilder();
        for (int y = SIZE - 1; y >= 0; y--)
        {
            out.append("y=").append(y).append('\n');
            for (int z = 0; z < SIZE; z++)
            {
                for (int x = 0; x < SIZE; x++)
                    out.append(cage[x][y][z] != HeartCage.NONE ? 'B' : veins[x][y][z] != HeartVeins.NONE ? 'v' : flesh[x][y][z] ? '#' : '.');
                out.append('\n');
            }
        }
        System.out.println(out);
    }

    private static int[][][] grow(int seed)
    {
        return HeartCage.grow(HeartShape.voxels(true), HeartVeins.grow(HeartShape.voxels(false), seed), seed);
    }

    private interface BoneVisitor
    {
        void visit(int x, int y, int z, int along);
    }

    private static void eachBone(int[][][] cage, BoneVisitor visitor)
    {
        for (int x = 0; x < SIZE; x++)
        {
            for (int y = 0; y < SIZE; y++)
            {
                for (int z = 0; z < SIZE; z++)
                {
                    if (cage[x][y][z] != HeartCage.NONE)
                        visitor.visit(x, y, z, cage[x][y][z]);
                }
            }
        }
    }
}
