package net.voxelindustry.voidheart.client.render.heart;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayDeque;

import static net.voxelindustry.voidheart.client.render.heart.HeartShape.SIZE;
import static org.assertj.core.api.Assertions.assertThat;

class HeartShapeTest
{
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void facesCoverEachExposedPixelExactlyOnce(boolean swelled)
    {
        var filled = HeartShape.voxels(swelled);
        var faces = HeartShape.faces(filled);

        int exposed = 0;
        for (int axis = 0; axis < 3; axis++)
        {
            for (int layer = 0; layer < SIZE; layer++)
            {
                for (int a = 0; a < SIZE; a++)
                {
                    for (int b = 0; b < SIZE; b++)
                    {
                        if (!HeartShape.at(filled, axis, layer, a, b))
                            continue;
                        if (!HeartShape.at(filled, axis, layer - 1, a, b))
                            exposed++;
                        if (!HeartShape.at(filled, axis, layer + 1, a, b))
                            exposed++;
                    }
                }
            }
        }
        assertThat(faces.stream().mapToInt(HeartShape.Face::area).sum()).isEqualTo(exposed);
    }

    /** Two faces over each other on one plane fight for depth, whichever way each faces, since the glow layer draws on the same faces. */
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void noTwoFacesShareAPlaneWhereTheyOverlap(boolean swelled)
    {
        var faces = HeartShape.faces(HeartShape.voxels(swelled));

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

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void heartIsOneMass(boolean swelled)
    {
        var filled = HeartShape.voxels(swelled);
        int total = 0;
        int[] start = null;
        for (int x = 0; x < SIZE; x++)
            for (int y = 0; y < SIZE; y++)
                for (int z = 0; z < SIZE; z++)
                    if (filled[x][y][z])
                    {
                        total++;
                        if (start == null)
                            start = new int[] { x, y, z };
                    }

        var seen = new boolean[SIZE][SIZE][SIZE];
        var frontier = new ArrayDeque<int[]>();
        frontier.add(start);
        seen[start[0]][start[1]][start[2]] = true;
        int reached = 0;
        int[][] steps = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        while (!frontier.isEmpty())
        {
            var at = frontier.poll();
            reached++;
            for (var step : steps)
            {
                int x = at[0] + step[0], y = at[1] + step[1], z = at[2] + step[2];
                if (x < 0 || y < 0 || z < 0 || x >= SIZE || y >= SIZE || z >= SIZE || seen[x][y][z] || !filled[x][y][z])
                    continue;
                seen[x][y][z] = true;
                frontier.add(new int[] { x, y, z });
            }
        }
        assertThat(reached).isEqualTo(total);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void heartStaysInsideItsBlockWithRoomAround(boolean swelled)
    {
        var filled = HeartShape.voxels(swelled);
        for (int a = 0; a < SIZE; a++)
        {
            for (int b = 0; b < SIZE; b++)
            {
                for (int axis = 0; axis < 3; axis++)
                {
                    assertThat(HeartShape.at(filled, axis, 0, a, b)).isFalse();
                    assertThat(HeartShape.at(filled, axis, SIZE - 1, a, b)).isFalse();
                }
            }
        }
    }

    @org.junit.jupiter.api.Test
    void aBeatOnlyAddsPixels()
    {
        var rest = HeartShape.voxels(false);
        var swelled = HeartShape.voxels(true);
        int added = 0;
        for (int x = 0; x < SIZE; x++)
            for (int y = 0; y < SIZE; y++)
                for (int z = 0; z < SIZE; z++)
                {
                    if (rest[x][y][z])
                        assertThat(swelled[x][y][z]).isTrue();
                    else if (swelled[x][y][z])
                        added++;
                }
        assertThat(added).isGreaterThan(0);
    }
}
