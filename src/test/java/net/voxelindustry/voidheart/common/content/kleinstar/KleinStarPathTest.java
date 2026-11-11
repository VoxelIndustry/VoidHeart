package net.voxelindustry.voidheart.common.content.kleinstar;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KleinStarPathTest
{
    private static final KleinStarRoute.Sight OPEN = (from, to) -> true;

    private static final List<Vec3> CORNERS = List.of(new Vec3(0, 2, 0), new Vec3(6, 2, 5), new Vec3(6, 2, 9), new Vec3(0, 1, 12));

    @Test
    void itStartsAndEndsWhereItShould()
    {
        var path = KleinStarPath.through(CORNERS, null, OPEN);
        assertEquals(CORNERS.getFirst(), path.at(0));
        assertEquals(CORNERS.getFirst(), path.at(-3));
        assertEquals(CORNERS.getLast(), path.at(path.length()));
        assertEquals(CORNERS.getLast(), path.at(path.length() + 3));
    }

    @Test
    void itPassesThroughEveryCorner()
    {
        var path = KleinStarPath.through(CORNERS, null, OPEN);
        for (var corner : CORNERS)
        {
            double nearest = Double.MAX_VALUE;
            for (double along = 0; along <= path.length(); along += 0.05)
                nearest = Math.min(nearest, path.at(along).distanceTo(corner));
            assertTrue(nearest < 0.1, "missed " + corner + " by " + nearest);
        }
    }

    @Test
    void itIsWalkedAtAnEvenPace()
    {
        var path = KleinStarPath.through(CORNERS, null, OPEN);
        for (double along = 0; along + 0.5 <= path.length(); along += 0.5)
            assertEquals(0.5, path.at(along).distanceTo(path.at(along + 0.5)), 0.06, "at " + along);
    }

    @Test
    void itIsRoundedWhereNothingIsInTheWay()
    {
        var path = KleinStarPath.through(CORNERS, null, OPEN);
        double straight = 0;
        for (int i = 1; i < CORNERS.size(); i++)
            straight += CORNERS.get(i).distanceTo(CORNERS.get(i - 1));
        assertTrue(path.length() > straight + 0.05, "length " + path.length() + " against " + straight);
    }

    @Test
    void whereRoundingWouldCutAWallItGoesStraight()
    {
        // Nothing but the straight legs themselves is clear
        KleinStarRoute.Sight tight = (from, to) ->
        {
            for (int i = 1; i < CORNERS.size(); i++)
            {
                var start = CORNERS.get(i - 1);
                var leg = CORNERS.get(i).subtract(start);
                if (offLine(from, start, leg) < 1e-6 && offLine(to, start, leg) < 1e-6)
                    return true;
            }
            return false;
        };
        var path = KleinStarPath.through(CORNERS, null, tight);
        double straight = 0;
        for (int i = 1; i < CORNERS.size(); i++)
            straight += CORNERS.get(i).distanceTo(CORNERS.get(i - 1));
        assertEquals(straight, path.length(), 1e-6);
    }

    private static double offLine(Vec3 point, Vec3 start, Vec3 leg)
    {
        double along = Math.clamp(point.subtract(start).dot(leg) / leg.lengthSqr(), 0, 1);
        return point.distanceTo(start.add(leg.scale(along)));
    }

    @Test
    void itLeavesTheWayTheStarWasGoing()
    {
        var heading = new Vec3(-1, 0, 0);
        var path = KleinStarPath.through(List.of(new Vec3(0, 2, 0), new Vec3(0, 2, 10)), heading, OPEN);
        assertTrue(path.at(0.5).x < -0.2, "left toward " + path.at(0.5));
        assertEquals(new Vec3(0, 2, 10), path.at(path.length()));
    }

    @Test
    void theSwingingCurveIsAPathOfOne()
    {
        var flight = KleinStarFlight.plan(new Vec3(0, 66, 0), new Vec3(0, 65, 12), 1, 1, 1);
        var path = KleinStarPath.of(flight);
        assertEquals(flight.length(), path.length(), 0.05);
        assertEquals(flight.to(), path.to());
        assertEquals(4, path.points().size());
    }

    @Test
    void aPathNeedsWholeCurves()
    {
        assertThrows(IllegalArgumentException.class, () -> new KleinStarPath(List.of(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO)));
        assertThrows(IllegalArgumentException.class, () -> new KleinStarPath(List.of(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO)));
    }
}
