package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnderwayTrunkTest
{
    private static final long SEED = 1234L;
    private static final int RANGE = 14;
    private static final int TRACK_Y = UnderwayLevels.BALLAST + 1;
    private static final int CABLES_Y = UnderwayLevels.TRUNK_TOP - 3;

    @Test
    void aBlastGateIsMetByBufferStopsOnBothTracksWithAWayBetweenThem()
    {
        int gates = 0;
        for (int z = -RANGE; z <= RANGE; z++)
        {
            for (int x = -RANGE; x <= RANGE; x++)
            {
                var pos = new DistrictPos(x, z);
                if (UnderwayNetwork.linkFrom(SEED, pos, Compass.EAST) != UnderwayLink.GATED || UnderwayNetwork.regionRuin(SEED, pos) == UnderwayRuin.FLOODED)
                    continue;
                gates++;
                var line = UnderwayFrame.ofLine(pos, true);
                var canvas = paint(pos);
                int buffers = TrunkPainter.GATE_START - 4;
                for (int across : new int[] { 59, 60, 61, 66, 67, 68 })
                    assertThat(canvas.propAt(line.x(buffers, across), TRACK_Y, line.z(buffers, across))).as("buffer stop at %d before the gate east of %s", across, pos).isEqualTo(UnderwayProp.BUFFER_STOP);
                assertThat(canvas.propAt(line.x(buffers + 1, TrunkPainter.HIGH), TRACK_Y + 3, line.z(buffers + 1, TrunkPainter.HIGH))).isEqualTo(UnderwayProp.KLAXON);

                var walker = new UnderwayWalker(canvas, line.x(DistrictPos.SIZE - 2, 63), TRACK_Y, line.z(DistrictPos.SIZE - 2, 63));
                assertThat(walker.reaches(line.x(TrunkPainter.GATE_START - 2, 63), TRACK_Y, line.z(TrunkPainter.GATE_START - 2, 63))).as("the walkway up to the gate east of %s", pos).isTrue();
                assertThat(walker.reaches(line.x(TrunkPainter.GATE_END + 2, 63), TRACK_Y, line.z(TrunkPainter.GATE_END + 2, 63))).as("past the gate east of %s", pos).isFalse();
            }
        }
        assertThat(gates).isGreaterThan(10);
    }

    @Test
    void tunnelsCarryACableRunAndStationsHaveSignalsOnTheirApproaches()
    {
        int stations = 0;
        for (int z = -RANGE; z <= RANGE; z++)
        {
            for (int x = -RANGE; x <= RANGE; x++)
            {
                var pos = new DistrictPos(x, z);
                if (UnderwayNetwork.nodeAt(SEED, pos) != UnderwayNode.STATION || !UnderwayNetwork.isLineRow(SEED, z) || UnderwayNetwork.regionRuin(SEED, pos) == UnderwayRuin.FLOODED)
                    continue;
                stations++;
                var line = UnderwayFrame.ofLine(pos, true);
                var canvas = paint(pos);
                int cables = 0;
                for (int along = 0; along < StationPlanner.HALL_START; along++)
                {
                    if (canvas.propAt(line.x(along, TrunkPainter.LOW), CABLES_Y, line.z(along, TrunkPainter.LOW)) == UnderwayProp.CABLE_TRAY)
                        cables++;
                }
                assertThat(cables).as("cable trays on the way into the station under %s", pos).isGreaterThan(StationPlanner.HALL_START * 3 / 4);
                for (int along : new int[] { StationPlanner.HALL_START - 4, StationPlanner.HALL_END + 4 })
                {
                    assertThat(canvas.propAt(line.x(along, TrunkPainter.LOW), TRACK_Y, line.z(along, TrunkPainter.LOW))).as("signal at %d under %s", along, pos).isEqualTo(UnderwayProp.SIGNAL);
                    assertThat(canvas.propAt(line.x(along, TrunkPainter.HIGH), TRACK_Y, line.z(along, TrunkPainter.HIGH))).as("signal at %d under %s", along, pos).isEqualTo(UnderwayProp.SIGNAL);
                }
            }
        }
        assertThat(stations).isGreaterThan(10);
    }

    private static UnderwayCanvas paint(DistrictPos pos)
    {
        var canvas = new UnderwayCanvas(DistrictPos.PITCH, DistrictPos.PITCH);
        canvas.reset(pos.originX(), pos.originZ());
        UnderwayPlans.paint(SEED, canvas);
        return canvas;
    }
}
