package net.voxelindustry.voidheart.client.render.oculus;

import org.junit.jupiter.api.Test;

import static net.voxelindustry.voidheart.common.content.oculus.OculusSightPayloads.RELIEF_SIDE;
import static net.voxelindustry.voidheart.common.content.oculus.OculusSightPayloads.RELIEF_STEP;
import static org.assertj.core.api.Assertions.assertThat;

class OculusReliefTest
{
    /**
     * Samples rising one block every block east, and two every block south.
     */
    private static int[] slope()
    {
        var heights = new int[RELIEF_SIDE * RELIEF_SIDE];
        for (int z = 0; z < RELIEF_SIDE; z++)
        {
            for (int x = 0; x < RELIEF_SIDE; x++)
                heights[x + z * RELIEF_SIDE] = 64 + x * RELIEF_STEP + z * RELIEF_STEP * 2;
        }
        return heights;
    }

    @Test
    void samplesAreKeptOnTheirColumns()
    {
        var heights = slope();

        assertThat(OculusRelief.height(heights, 0, 0)).isEqualTo(64);
        assertThat(OculusRelief.height(heights, 4, 0)).isEqualTo(68);
        assertThat(OculusRelief.height(heights, 12, 12)).isEqualTo(64 + 12 + 24);
    }

    @Test
    void columnsBetweenSamplesAreEased()
    {
        var heights = slope();

        assertThat(OculusRelief.height(heights, 1, 0)).isEqualTo(65);
        assertThat(OculusRelief.height(heights, 15, 0)).isEqualTo(79);
        assertThat(OculusRelief.height(heights, 15, 15)).isEqualTo(64 + 15 + 30);
    }

    @Test
    void reliefVoxelsAreToldApartByTheirAlpha()
    {
        assertThat(OculusSnapshot.reliefColor(64, 63) >>> 24).isEqualTo(OculusSnapshot.RELIEF_ALPHA);
        assertThat(OculusSnapshot.reliefColor(-40, 63) >>> 24).isEqualTo(OculusSnapshot.RELIEF_ALPHA);
    }

    @Test
    void higherLandIsPaler()
    {
        int low = OculusSnapshot.reliefColor(40, 63) & 0xFF;
        int high = OculusSnapshot.reliefColor(130, 63) & 0xFF;

        assertThat(high).isGreaterThan(low);
    }
}
