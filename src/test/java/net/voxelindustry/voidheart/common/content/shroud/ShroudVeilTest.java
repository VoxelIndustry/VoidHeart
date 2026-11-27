package net.voxelindustry.voidheart.common.content.shroud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShroudVeilTest
{
    private static final long SALT = 0x1234_5678_9ABCL;
    private static final int STEPS = 200;
    /** Neighbouring columns of the edge differ by little, so it reads as a torn line rather than noise. */
    private static final int MAX_STEP = 4;

    @Test
    void theSealLiftsWhileOpenAndSettlesOnceClosed()
    {
        assertEquals(0, ShroudVeil.progress(true, 0, 0));
        assertEquals(0.5F, ShroudVeil.progress(true, 0, ShroudVeil.LIFT_TICKS / 2F), 1e-6);
        assertEquals(1, ShroudVeil.progress(true, 0, ShroudVeil.LIFT_TICKS * 3));
        assertEquals(1, ShroudVeil.progress(false, 1, 0));
        assertEquals(0, ShroudVeil.progress(false, 1, ShroudVeil.SETTLE_TICKS));
        // A change seen before its time on a clock running behind holds still rather than going back
        assertEquals(0.3F, ShroudVeil.progress(true, 0.3F, -5), 1e-6);
    }

    @Test
    void turningBackHalfwayGoesOnFromWhereItWas()
    {
        float halfway = ShroudVeil.progress(true, 0, ShroudVeil.LIFT_TICKS * 0.4F);
        float justAfter = ShroudVeil.progress(false, halfway, 1);
        assertTrue(justAfter < halfway && halfway - justAfter < 0.05F);
    }

    @Test
    void theVoidFillsNothingWhenSealedAndEverythingWhenOpen()
    {
        for (int column = 0; column < ShroudVeil.COLUMNS; column++)
        {
            assertEquals(0, ShroudVeil.columnTop(0, column, SALT));
            assertEquals(ShroudVeil.OPENING_PIXELS, ShroudVeil.columnTop(1, column, SALT));
        }
    }

    @Test
    void everyColumnRisesSteadilyAndTheEdgeStaysTorn()
    {
        int[] previous = new int[ShroudVeil.COLUMNS];
        boolean ragged = false;
        for (int step = 0; step <= STEPS; step++)
        {
            float risen = ShroudVeil.risen(step / (float) STEPS);
            int[] tops = new int[ShroudVeil.COLUMNS];
            for (int column = 0; column < ShroudVeil.COLUMNS; column++)
            {
                tops[column] = ShroudVeil.columnTop(risen, column, SALT);
                assertTrue(tops[column] >= previous[column], "Column " + column + " fell back at step " + step);
                if (column > 0)
                {
                    assertTrue(Math.abs(tops[column] - tops[column - 1]) <= MAX_STEP, "The edge breaks between columns " + (column - 1) + " and " + column);
                    ragged |= tops[column] != tops[column - 1];
                }
            }
            previous = tops;
        }
        assertTrue(ragged, "The edge is a straight line");
    }

    @Test
    void theRiseIsSlowAtBothEnds()
    {
        assertTrue(ShroudVeil.risen(0.05F) < 0.05F);
        assertTrue(ShroudVeil.risen(0.95F) > 0.95F);
        assertEquals(0.5F, ShroudVeil.risen(0.5F), 1e-6);
    }
}
