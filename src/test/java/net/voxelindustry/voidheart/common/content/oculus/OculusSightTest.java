package net.voxelindustry.voidheart.common.content.oculus;

import net.minecraft.core.BlockPos;
import net.voxelindustry.voidheart.common.content.oculus.OculusSight.Resolution;
import net.voxelindustry.voidheart.common.content.oculus.OculusSight.Source;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OculusSightTest
{
    private static final Source TABLE = new Source(new BlockPos(0, 64, 0), 16, 0, false);
    private static final Source BEHOLDER = new Source(new BlockPos(100, 70, 0), 64, 32, true);

    @Test
    void tableAloneSeesDimlyAroundItself()
    {
        var sight = new OculusSight(List.of(TABLE));

        assertThat(sight.resolutionAt(0.5, 0.5)).isEqualTo(Resolution.LOW);
        assertThat(sight.resolutionAt(16.4, 0.5)).isEqualTo(Resolution.LOW);
        assertThat(sight.resolutionAt(17, 0.5)).isEqualTo(Resolution.UNSEEN);
        assertThat(sight.resolutionAt(12.5, 12.5)).isEqualTo(Resolution.UNSEEN);
    }

    @Test
    void beholderSeesClearlyNearAndDimlyFar()
    {
        var sight = new OculusSight(List.of(TABLE, BEHOLDER));

        assertThat(sight.resolutionAt(100.5, 31.5)).isEqualTo(Resolution.HIGH);
        assertThat(sight.resolutionAt(100.5, 33)).isEqualTo(Resolution.LOW);
        assertThat(sight.resolutionAt(100.5 - 64, 0.5)).isEqualTo(Resolution.LOW);
        assertThat(sight.resolutionAt(100.5 - 65, 0.5)).isEqualTo(Resolution.UNSEEN);
        assertThat(sight.resolutionAt(0.5, 0.5)).isEqualTo(Resolution.LOW);
    }

    @Test
    void clearSightWinsOverlappingDimSight()
    {
        var near = new Source(new BlockPos(10, 64, 0), 64, 8, true);
        var sight = new OculusSight(List.of(TABLE, near));

        assertThat(sight.resolutionAt(10.5, 0.5)).isEqualTo(Resolution.HIGH);
        assertThat(sight.resolutionAt(0.5, 0.5)).isEqualTo(Resolution.LOW);
    }

    @Test
    void columnsAreSeenByTheirCenter()
    {
        var sight = new OculusSight(List.of(TABLE));

        assertThat(sight.seesColumn(15, 0)).isTrue();
        assertThat(sight.seesColumn(16, 0)).isTrue();
        assertThat(sight.seesColumn(17, 0)).isFalse();
        assertThat(sight.seesColumn(-16, 0)).isTrue();
        assertThat(sight.seesColumn(-17, 0)).isFalse();
    }

    @Test
    void chunkIsTouchedWhenAnyOfItsColumnsIsSeen()
    {
        var sight = new OculusSight(List.of(TABLE));

        assertThat(sight.touchesChunk(0, 0)).isTrue();
        assertThat(sight.touchesChunk(1, 0)).isTrue();
        assertThat(sight.touchesChunk(-2, 0)).isFalse();
        assertThat(sight.touchesChunk(1, 1)).isFalse();
        assertThat(sight.touchesChunk(2, 0)).isFalse();
        assertThat(new OculusSight(List.of()).touchesChunk(0, 0)).isFalse();
    }
}
