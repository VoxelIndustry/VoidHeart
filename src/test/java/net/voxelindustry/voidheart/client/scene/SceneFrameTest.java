package net.voxelindustry.voidheart.client.scene;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SceneFrameTest
{
    @Test
    void flash_risesAndFallsOnce()
    {
        int peaks = 0;
        float before = 0;
        boolean rising = true;
        for (float tick = 0; tick < 400; tick += 0.5F)
        {
            float now = SceneFrame.flash(tick);
            if (rising && now < before)
            {
                peaks++;
                rising = false;
            }
            else if (!rising && now > before)
                rising = true;
            before = now;
        }

        assertThat(peaks).isEqualTo(1);
    }

    @Test
    void flash_isOverLongBeforeAStepEnds()
    {
        assertThat(SceneFrame.flash(-1)).isZero();
        assertThat(SceneFrame.flash(12)).isGreaterThan(0.9F);
        assertThat(SceneFrame.flash(60)).isZero();
    }
}
