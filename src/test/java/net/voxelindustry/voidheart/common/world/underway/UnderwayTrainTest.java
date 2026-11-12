package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class UnderwayTrainTest
{
    private static final long SEED = 1234L;
    private static final int RANGE = 12;
    private static final int TRACK_Y = UnderwayLevels.BALLAST + 1;
    private static final int CAR_FLOOR = UnderwayLevels.PLATFORM_FLOOR;

    @Test
    void everyCarIsBoardedFromTheTrackBed()
    {
        var kinds = EnumSet.noneOf(UnderwayTrain.Kind.class);
        int stations = 0;
        int tunnels = 0;
        for (int z = -RANGE; z <= RANGE; z++)
        {
            for (int x = -RANGE; x <= RANGE; x++)
            {
                var pos = new DistrictPos(x, z);
                for (boolean alongX : new boolean[] { true, false })
                {
                    if (!(alongX ? UnderwayNetwork.isLineRow(SEED, z) : UnderwayNetwork.isLineColumn(SEED, x)))
                        continue;
                    for (var train : UnderwayTrain.at(SEED, pos, alongX))
                    {
                        boolean tunnel = train.cars().getFirst().along() >= DistrictPos.SIZE;
                        if (tunnel)
                            tunnels++;
                        else
                            stations++;
                        var line = UnderwayFrame.ofLine(pos, alongX);
                        var canvas = new UnderwayCanvas(DistrictPos.PITCH, DistrictPos.PITCH);
                        canvas.reset(pos.originX(), pos.originZ());
                        UnderwayPlans.paint(SEED, canvas);
                        int free = train.innerWall() + train.inner() * 3;
                        int start = tunnel ? DistrictPos.SIZE - 2 : 2;
                        var walker = new UnderwayWalker(canvas, line.x(start, free), TRACK_Y, line.z(start, free));
                        for (var car : train.cars())
                        {
                            kinds.add(car.kind());
                            int along = car.along() + UnderwayTrain.CAR_LENGTH / 2;
                            assertThat(walker.reaches(line.x(along, train.aisle()), CAR_FLOOR + 1, line.z(along, train.aisle())))
                                    .as("the %s car at %d of the train under %s, along x %s", car.kind(), car.along(), pos, alongX)
                                    .isTrue();
                            assertThat(car.along() + UnderwayTrain.CAR_LENGTH).isLessThanOrEqualTo(DistrictPos.PITCH);
                        }
                        assertThat(train.cars().stream().filter(car -> car.kind() == UnderwayTrain.Kind.LOCOMOTIVE)).hasSize(1);
                    }
                }
            }
        }
        assertThat(stations).isGreaterThan(5);
        assertThat(tunnels).isGreaterThan(5);
        assertThat(kinds).containsExactlyInAnyOrder(UnderwayTrain.Kind.values());
    }
}
