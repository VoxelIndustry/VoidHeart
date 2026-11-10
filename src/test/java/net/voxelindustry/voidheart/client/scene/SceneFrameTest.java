package net.voxelindustry.voidheart.client.scene;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    private static final SceneTimeline RING_TIMELINE = new SceneTimeline(new int[] { 100, 100, 100, 100 });

    // Rise in step 0, search in step 1, open in step 2, falter through step 3 until it closes 80 ticks in
    private static final Scene.Rings RINGS = new Scene.Rings(BlockPos.ZERO, new Scene.Moment(0, 10),
            List.of(new Scene.Spin(new Scene.Moment(1, 0), 3, 1), new Scene.Spin(new Scene.Moment(2, 0), 1, 1)),
            new Scene.Moment(2, 20), new Scene.Moment(3, 0), new Scene.Moment(3, 80), 0xFFFFFF);

    @Test
    void rings_riseOnceTheirMomentComes()
    {
        assertThat(SceneFrame.rings(RINGS, RING_TIMELINE, 5).formation()).isZero();
        assertThat(SceneFrame.rings(RINGS, RING_TIMELINE, 40).formation()).isBetween(0.3F, 0.7F);
        assertThat(SceneFrame.rings(RINGS, RING_TIMELINE, 90).formation()).isEqualTo(1);
    }

    @Test
    void rings_turnFasterWhileSearching()
    {
        float steady = SceneFrame.rings(RINGS, RING_TIMELINE, 95).angles()[0] - SceneFrame.rings(RINGS, RING_TIMELINE, 90).angles()[0];
        float searching = SceneFrame.rings(RINGS, RING_TIMELINE, 195).angles()[0] - SceneFrame.rings(RINGS, RING_TIMELINE, 190).angles()[0];
        assertThat(searching).isGreaterThan(steady * 2);
    }

    @Test
    void rings_turnSmoothlyBetweenTicks()
    {
        float largest = 0;
        float before = SceneFrame.rings(RINGS, RING_TIMELINE, 0).angles()[2];
        for (float tick = 0.25F; tick < 390; tick += 0.25F)
        {
            float now = SceneFrame.rings(RINGS, RING_TIMELINE, tick).angles()[2];
            largest = Math.max(largest, Math.abs(now - before));
            before = now;
        }
        assertThat(largest).isLessThan(0.05F);
    }

    @Test
    void portal_opensAndClosesWithTheConnection()
    {
        assertThat(SceneFrame.rings(RINGS, RING_TIMELINE, 215).portal()).isZero();
        assertThat(SceneFrame.rings(RINGS, RING_TIMELINE, 280).portal()).isEqualTo(1);
        assertThat(SceneFrame.rings(RINGS, RING_TIMELINE, 380).portal()).isEqualTo(1);
        assertThat(SceneFrame.rings(RINGS, RING_TIMELINE, 395).portal()).isLessThan(1);
    }

    @Test
    void falter_worsensUntilTheConnectionCloses()
    {
        assertThat(SceneFrame.rings(RINGS, RING_TIMELINE, 299).falter()).isZero();
        assertThat(SceneFrame.rings(RINGS, RING_TIMELINE, 340).falter()).isBetween(0.4F, 0.6F);
        assertThat(SceneFrame.rings(RINGS, RING_TIMELINE, 379).falter()).isGreaterThan(0.95F);
        assertThat(SceneFrame.rings(RINGS, RING_TIMELINE, 381).falter()).isZero();
    }
}
