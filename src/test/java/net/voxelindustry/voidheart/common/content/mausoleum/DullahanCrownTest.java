package net.voxelindustry.voidheart.common.content.mausoleum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DullahanCrownTest
{
    @Test
    void eachFlameHoldsANinthOfItsHealth()
    {
        float last = 1;
        for (int released = 0; released < DullahanCrown.FLAMES; released++)
        {
            float threshold = DullahanCrown.threshold(released);
            assertEquals(1F / (DullahanCrown.FLAMES + 1), last - threshold, 1e-5);
            last = threshold;
        }
        assertEquals(1F / (DullahanCrown.FLAMES + 1), last, 1e-5, "The last share is left once every flame has gone");
        assertEquals(0, DullahanCrown.threshold(DullahanCrown.FLAMES));
    }

    @Test
    void aFlameLeavesOnlyOnceItsShareIsSpent()
    {
        assertFalse(DullahanCrown.releases(0, 0.95F));
        assertTrue(DullahanCrown.releases(0, DullahanCrown.threshold(0)));
        assertFalse(DullahanCrown.releases(1, DullahanCrown.threshold(0)));
        assertTrue(DullahanCrown.releases(7, 0.05F));
        assertFalse(DullahanCrown.releases(DullahanCrown.FLAMES, 0), "No ninth flame to lose");
    }

    @Test
    void nothingHarmsItWhileAWispBurns()
    {
        assertFalse(DullahanCrown.vulnerable(true));
        assertTrue(DullahanCrown.vulnerable(false));
    }

    @Test
    void wavesGrowAsItWeakens()
    {
        int total = 0;
        int previous = 0;
        for (int released = 1; released <= DullahanCrown.FLAMES; released++)
        {
            int wave = DullahanCrown.wave(released);
            assertTrue(wave >= previous, "A later wave is smaller");
            previous = wave;
            total += wave;
        }
        assertEquals(1, DullahanCrown.wave(1));
        assertEquals(5, DullahanCrown.wave(DullahanCrown.FLAMES));
        assertEquals(24, total);
    }
}
