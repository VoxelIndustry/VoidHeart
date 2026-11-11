package net.voxelindustry.voidheart.common.content.kleinstar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KleinStarBalanceTest
{
    @Test
    void itAppearsSmallThenGrowsToABlock()
    {
        assertEquals(0, KleinStarBalance.size(0));
        assertEquals(KleinStarBalance.APPEAR_SIZE, KleinStarBalance.size(KleinStarBalance.APPEAR_TICKS), 1e-6);
        assertEquals(KleinStarBalance.FULL_SIZE, KleinStarBalance.size(KleinStarBalance.GROW_TICKS), 1e-6);
        assertEquals(KleinStarBalance.FULL_SIZE, KleinStarBalance.size(KleinStarBalance.GROW_TICKS + 20), 1e-6);
    }

    @Test
    void itNeverShrinksWhileGrowing()
    {
        float last = 0;
        for (float ticks = 0; ticks <= KleinStarBalance.GROW_TICKS; ticks += 0.5F)
        {
            float size = KleinStarBalance.size(ticks);
            assertTrue(size >= last, "shrank at " + ticks);
            last = size;
        }
    }

    @Test
    void itLeavesSlowlyThenHoldsItsSpeed()
    {
        assertEquals(0, KleinStarBalance.flown(0));
        assertEquals(KleinStarBalance.FLIGHT_START + KleinStarBalance.FLIGHT_GAIN / 2, KleinStarBalance.flown(1), 1e-9);
        double first = KleinStarBalance.flown(1) - KleinStarBalance.flown(0);
        double later = KleinStarBalance.flown(31) - KleinStarBalance.flown(30);
        assertTrue(later > first * 2, "first " + first + ", later " + later);
        assertEquals(KleinStarBalance.FLIGHT_SPEED, later, 1e-9);
        // No jump where it stops gaining speed
        for (double ticks = 0; ticks < 20; ticks += 0.25)
        {
            double step = KleinStarBalance.flown(ticks + 0.25) - KleinStarBalance.flown(ticks);
            assertTrue(step > 0 && step <= KleinStarBalance.FLIGHT_SPEED * 0.25 + 1e-9, "step " + step + " at " + ticks);
        }
    }

    @Test
    void theBlastWeakensToItsEdgeAndStopsThere()
    {
        assertEquals(40, KleinStarBalance.damageAt(40, 0, 6), 1e-6);
        assertEquals(40 * KleinStarBalance.EDGE_DAMAGE, KleinStarBalance.damageAt(40, 6, 6), 1e-4);
        assertEquals(0, KleinStarBalance.damageAt(40, 6.01, 6));
        assertTrue(KleinStarBalance.damageAt(40, 3, 6) > KleinStarBalance.damageAt(40, 5, 6));
    }
}
