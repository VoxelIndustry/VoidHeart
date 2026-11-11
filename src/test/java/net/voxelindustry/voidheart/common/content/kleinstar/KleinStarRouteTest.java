package net.voxelindustry.voidheart.common.content.kleinstar;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KleinStarRouteTest
{
    private static final Vec3 FROM = new Vec3(0.5, 2.5, 0.5);
    private static final Vec3 TO   = new Vec3(0.5, 1.5, 12.5);

    /** A floor under everything, and whatever else the test stands on it. */
    private interface Walls
    {
        boolean solid(int x, int y, int z);
    }

    private static KleinStarRoute.Space space(Walls walls)
    {
        return (x, y, z) -> y >= 1 && !walls.solid(x, y, z);
    }

    /** Straight lines, walked in small steps through the same blocks. */
    private static KleinStarRoute.Sight sight(KleinStarRoute.Space space)
    {
        return (from, to) ->
        {
            int steps = (int) Math.ceil(from.distanceTo(to) / 0.1);
            for (int step = 0; step <= steps; step++)
            {
                var point = from.lerp(to, steps == 0 ? 0 : step / (double) steps);
                if (!space.open((int) Math.floor(point.x), (int) Math.floor(point.y), (int) Math.floor(point.z)))
                    return false;
            }
            return true;
        };
    }

    private static List<Vec3> find(Walls walls)
    {
        var space = space(walls);
        return KleinStarRoute.find(space, sight(space), FROM, TO);
    }

    private static void assertClear(List<Vec3> corners, Walls walls)
    {
        var sight = sight(space(walls));
        for (int i = 1; i < corners.size(); i++)
            assertTrue(sight.clear(corners.get(i - 1), corners.get(i)), "leg " + i + " of " + corners + " goes through a wall");
    }

    @Test
    void inTheOpenItGoesStraight()
    {
        var corners = find((x, y, z) -> false);
        assertEquals(List.of(FROM, TO), corners);
    }

    @Test
    void itGoesOverAWallItCannotGoAround()
    {
        // Across the whole width it may search, four blocks tall
        Walls wall = (x, y, z) -> z == 6 && y <= 4;
        var corners = find(wall);
        assertNotNull(corners);
        assertEquals(FROM, corners.getFirst());
        assertEquals(TO, corners.getLast());
        assertTrue(corners.stream().anyMatch(corner -> corner.y > 5), "never rose above the wall: " + corners);
        assertClear(corners, wall);
    }

    @Test
    void itGoesAroundAWallItCannotGoOver()
    {
        // As tall as it may search, but ending three blocks to one side
        Walls wall = (x, y, z) -> z == 6 && x >= -20 && x <= 3;
        var corners = find(wall);
        assertNotNull(corners);
        assertTrue(corners.stream().anyMatch(corner -> corner.x > 4), "never went around the end of the wall: " + corners);
        assertClear(corners, wall);
    }

    @Test
    void itTakesTheGapInAWall()
    {
        Walls wall = (x, y, z) -> z == 6 && !(x == 2 && y == 2);
        var corners = find(wall);
        assertNotNull(corners);
        assertClear(corners, wall);
        // Whichever leg crosses the wall does so inside the gap
        for (int i = 1; i < corners.size(); i++)
        {
            var before = corners.get(i - 1);
            var after = corners.get(i);
            if (before.z < 6.5 && after.z >= 6.5)
            {
                var crossing = before.lerp(after, (6.5 - before.z) / (after.z - before.z));
                assertTrue(crossing.x > 2 && crossing.x < 3 && crossing.y > 2 && crossing.y < 3, "crossed the wall at " + crossing);
            }
        }
    }

    @Test
    void itCutsNoCorner()
    {
        // Two walls meeting at a corner leave no way between them diagonally
        Walls walls = (x, y, z) -> z == 6 && x <= 0 || x == 1 && z >= 6 && z <= 9;
        var corners = find(walls);
        assertNotNull(corners);
        assertClear(corners, walls);
    }

    @Test
    void walledInThereIsNoWay()
    {
        Walls box = (x, y, z) -> Math.max(Math.abs(x), Math.abs(z - 12)) == 2 && y <= 4 || y == 4 && Math.max(Math.abs(x), Math.abs(z - 12)) <= 2;
        assertNull(find(box));
    }

    @Test
    void whatItTurnsAroundIsAllThatIsLeft()
    {
        Walls wall = (x, y, z) -> z == 6 && x >= -20 && x <= 3;
        // Out to the end of the wall, around it and back: a start, an end and a corner or two, however many blocks were crossed
        assertTrue(find(wall).size() <= 5, "kept " + find(wall));
    }
}
