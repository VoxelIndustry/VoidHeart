package net.voxelindustry.voidheart.common.content.kleinstar;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KleinStarFlightTest
{
    private static final Vec3 FROM = new Vec3(0, 66, 0);
    private static final Vec3 TO   = new Vec3(0, 65, 12);

    private static double offLine(Vec3 point)
    {
        var span = TO.subtract(FROM);
        var along = point.subtract(FROM).dot(span) / span.lengthSqr();
        return point.distanceTo(FROM.add(span.scale(along)));
    }

    @Test
    void itStartsWhereItGrewAndEndsOnItsTarget()
    {
        var flight = KleinStarFlight.plan(FROM, TO, 1, 1, 1);
        assertEquals(FROM, flight.at(0));
        assertEquals(TO, flight.at(1));
    }

    @Test
    void itDoesNotTakeTheStraightPath()
    {
        var flight = KleinStarFlight.plan(FROM, TO, 1, 1, 1);
        assertTrue(offLine(flight.at(0.5)) > 2, "off the line by " + offLine(flight.at(0.5)));
        assertTrue(flight.length() > FROM.distanceTo(TO) + 1);
    }

    @Test
    void itSwingsToTheSideItIsGiven()
    {
        // Heading south, a positive side is to the right, which is west
        assertTrue(KleinStarFlight.plan(FROM, TO, 1, 1, 1).at(0.4).x < -1);
        assertTrue(KleinStarFlight.plan(FROM, TO, -1, 1, 1).at(0.4).x > 1);
    }

    @Test
    void twistedItCrossesOver()
    {
        var flight = KleinStarFlight.plan(FROM, TO, 1, -1, 1);
        assertTrue(flight.at(0.25).x < 0 && flight.at(0.9).x > 0, "from " + flight.at(0.25) + " to " + flight.at(0.9));
    }

    @Test
    void itLiftsOnTheWay()
    {
        assertTrue(KleinStarFlight.plan(FROM, TO, 0, 0, 1).at(0.4).y > FROM.y + 1);
    }

    @Test
    void withoutSwingItIsTheStraightLine()
    {
        var flight = KleinStarFlight.plan(FROM, TO, 1, 1, 0);
        for (double along = 0; along <= 1; along += 0.1)
            assertEquals(0, offLine(flight.at(along)), 1e-9);
    }

    @Test
    void aTargetStraightAboveStillCurves()
    {
        var flight = KleinStarFlight.plan(FROM, FROM.add(0, 10, 0), 1, 1, 1);
        assertTrue(Double.isFinite(flight.at(0.5).x) && Math.abs(flight.at(0.5).x) > 1);
    }
}
