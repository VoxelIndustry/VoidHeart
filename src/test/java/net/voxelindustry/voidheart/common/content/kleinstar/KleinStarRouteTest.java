package net.voxelindustry.voidheart.common.content.kleinstar;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        // One block stands beside the diagonal from the start to the end, a block apart corner to corner
        Walls post = (x, y, z) -> x == 1 && y == 2 && z == 6;
        var space = space(post);
        assertFalse(KleinStarRoute.passable(space, 1, 2, 5, 1, 0, 1, false), "stepped past the corner of a block");
        assertTrue(KleinStarRoute.passable(space, 1, 2, 5, 1, 0, 0, false));

        // With no straight line allowed to skip a block, the way is every block crossed: it has to go by the open side
        var corners = KleinStarRoute.find(space, (from, to) -> false, new Vec3(1.5, 2.5, 5.5), new Vec3(2.5, 2.5, 6.5));
        assertEquals(List.of(new Vec3(1.5, 2.5, 5.5), new Vec3(2.5, 2.5, 5.5), new Vec3(2.5, 2.5, 6.5)), corners);
    }

    @Test
    void theEndMayBeInABlockButNothingBrushedPastMay()
    {
        Walls post = (x, y, z) -> x == 2 && y == 2 && z == 6;
        var space = space(post);
        assertTrue(KleinStarRoute.passable(space, 2, 2, 5, 0, 0, 1, true));
        assertFalse(KleinStarRoute.passable(space, 2, 2, 5, 0, 0, 1, false));
        assertFalse(KleinStarRoute.passable(space, 1, 2, 5, 1, 0, 1, false));
    }

    @Test
    void withBothEndsInOneBlockItIsStraightOrNothing()
    {
        var from = new Vec3(0.1, 2.5, 0.5);
        var to = new Vec3(0.9, 2.5, 0.5);
        KleinStarRoute.Space open = (x, y, z) -> true;
        assertEquals(List.of(from, to), KleinStarRoute.find(open, (a, b) -> true, from, to));
        // Something thinner than a block stands between them
        assertNull(KleinStarRoute.find(open, (a, b) -> false, from, to));
    }

    @Test
    void endsFarApartCostNoMoreThanEndsClose()
    {
        // A wall across the way and an end thousands of blocks past it: the search gives up after as many blocks as ever, and holds no more
        Walls wall = (x, y, z) -> z == 6;
        var space = space(wall);
        assertNull(KleinStarRoute.find(space, sight(space), FROM, new Vec3(0.5, 1.5, 1_000_000.5)));
        assertNull(KleinStarRoute.find(space, sight(space), FROM, new Vec3(20_000_000.5, 1.5, 20_000_000.5)));
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
