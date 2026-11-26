package net.voxelindustry.voidheart.client.render.heart;

import org.junit.jupiter.api.Test;

import static net.voxelindustry.voidheart.client.render.heart.HeartShape.SIZE;
import static org.assertj.core.api.Assertions.assertThat;

class HeartTwinTest
{
    /** Every owner's twin is joined to their heart on either side, clear of the cage and veins, by a bridge one pixel thin that never breaks. */
    @Test
    void theBridgeJoinsBothHeartsClearOfBone()
    {
        var rest = HeartShape.voxels(false);
        var swelled = HeartShape.voxels(true);
        for (int seed = 0; seed < 500; seed++)
        {
            int owner = seed * 0x9E3779B9;
            var veins = HeartVeins.grow(rest, owner);
            var cage = HeartCage.grow(swelled, veins, owner);
            for (int side = -1; side <= 1; side += 2)
            {
                var bridge = HeartTwin.bridge(rest, swelled, veins, cage, side);
                assertThat(bridge).as("seed %d side %d bridge", owner, side).isNotEmpty();

                var first = bridge.getFirst();
                assertThat(touches(rest, first, 0)).as("seed %d side %d leaves the heart", owner, side).isTrue();
                var twin = HeartTwin.voxels(1, false, side);
                assertThat(touches(twin, bridge.getLast(), HeartTwin.offset(side))).as("seed %d side %d meets the twin", owner, side).isTrue();

                for (int i = 0; i < bridge.size(); i++)
                {
                    int[] pixel = bridge.get(i);
                    if (pixel[0] >= 0 && pixel[0] < SIZE)
                    {
                        assertThat(rest[pixel[0]][pixel[1]][pixel[2]]).isFalse();
                        assertThat(cage[pixel[0]][pixel[1]][pixel[2]]).as("seed %d side %d on bone", owner, side).isEqualTo(HeartCage.NONE);
                        assertThat(veins[pixel[0]][pixel[1]][pixel[2]]).isEqualTo(HeartVeins.NONE);
                    }
                    if (i > 0)
                    {
                        int[] before = bridge.get(i - 1);
                        int dx = Math.abs(before[0] - pixel[0]), dy = Math.abs(before[1] - pixel[1]), dz = Math.abs(before[2] - pixel[2]);
                        assertThat(dx + dy + dz).as("seed %d side %d broken", owner, side).isBetween(1, 2);
                        assertThat(Math.max(dx, Math.max(dy, dz))).isEqualTo(1);
                    }
                }
            }
        }
    }

    /** The twin stands on the floor apart from the first heart, smaller than it, and buds from its join as it grows. */
    @Test
    void theTwinStandsOnTheFloorAndBudsFromItsJoin()
    {
        for (int side = -1; side <= 1; side += 2)
        {
            var full = HeartTwin.voxels(1, false, side);
            int count = 0, lowest = SIZE;
            for (int x = 0; x < SIZE; x++)
            {
                for (int y = 0; y < SIZE; y++)
                {
                    for (int z = 0; z < SIZE; z++)
                    {
                        if (!full[x][y][z])
                            continue;
                        count++;
                        lowest = Math.min(lowest, y);
                        int main = x + HeartTwin.offset(side);
                        assertThat(main < 0 || main >= SIZE).as("twin inside the first heart's block at %d", main).isTrue();
                    }
                }
            }
            assertThat(lowest).isZero();
            assertThat(count).isBetween(60, 260);

            int smaller = 0;
            for (float size = 0.1F; size <= 1; size += 0.1F)
            {
                int now = count(HeartTwin.voxels(size, false, side));
                assertThat(now).isGreaterThanOrEqualTo(smaller);
                smaller = now;
            }
        }
    }

    @Test
    void sidesFollowTheFloor()
    {
        var open = new boolean[9][9];
        for (var row : open)
            java.util.Arrays.fill(row, true);
        assertThat(HeartTwin.side(open, 0)).isEqualTo(1);
        open[5][4] = false;
        assertThat(HeartTwin.side(open, 0)).isEqualTo(-1);
        // A quarter turn carries the heart's X onto the world's Z
        assertThat(HeartTwin.side(open, 1)).isEqualTo(1);
        open[4][5] = false;
        assertThat(HeartTwin.side(open, 1)).isEqualTo(-1);
        assertThat(HeartTwin.side(null, 0)).isEqualTo(1);
    }

    /** Prints one owner's heart, cage, bridge and twin from above and the front, to judge the look. */
    @Test
    void printTwin()
    {
        var rest = HeartShape.voxels(false);
        int seed = "Ourten".hashCode();
        var veins = HeartVeins.grow(rest, seed);
        var cage = HeartCage.grow(HeartShape.voxels(true), veins, seed);
        var bridge = HeartTwin.bridge(rest, HeartShape.voxels(true), veins, cage, 1);
        var twin = HeartTwin.voxels(1, false, 1);
        var out = new StringBuilder("front, X across and Y up\n");
        for (int y = SIZE - 1; y >= 0; y--)
        {
            for (int x = 0; x < SIZE + HeartTwin.OFFSET + 1; x++)
            {
                char c = '.';
                for (int z = SIZE - 1; z >= 0 && c == '.'; z--)
                    c = at(rest, cage, bridge, twin, x, y, z);
                out.append(c);
            }
            out.append('\n');
        }
        System.out.println(out);
    }

    private static char at(boolean[][][] rest, int[][][] cage, java.util.List<int[]> bridge, boolean[][][] twin, int x, int y, int z)
    {
        for (var pixel : bridge)
        {
            if (pixel[0] == x && pixel[1] == y && pixel[2] == z)
                return 'v';
        }
        if (x < SIZE)
            return cage[x][y][z] != HeartCage.NONE ? 'B' : rest[x][y][z] ? '#' : '.';
        int tx = x - HeartTwin.OFFSET;
        return tx < SIZE && twin[tx][y][z] ? 'o' : '.';
    }

    private static int count(boolean[][][] filled)
    {
        int count = 0;
        for (var plane : filled)
        {
            for (var row : plane)
            {
                for (boolean pixel : row)
                    count += pixel ? 1 : 0;
            }
        }
        return count;
    }

    private static boolean touches(boolean[][][] flesh, int[] pixel, int offset)
    {
        for (int[] step : new int[][] { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } })
        {
            int x = pixel[0] + step[0] - offset, y = pixel[1] + step[1], z = pixel[2] + step[2];
            if (x >= 0 && y >= 0 && z >= 0 && x < SIZE && y < SIZE && z < SIZE && flesh[x][y][z])
                return true;
        }
        return false;
    }
}
