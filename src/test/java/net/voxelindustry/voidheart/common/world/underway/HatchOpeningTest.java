package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.underway.HatchOpening.Change;
import net.voxelindustry.voidheart.common.world.underway.HatchOpening.Part;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class HatchOpeningTest
{
    private static final long SEED = 1234L;
    private static final int RANGE = 6;
    private static final int LEAVES = 6;

    @Test
    void leavesDrawBackFromTheMiddleAndLeaveTheCoaming()
    {
        var fort = UnderwayPlans.fort(SEED, new DistrictPos(0, 0));
        var gone = new HashSet<Change>();
        for (int stage = 0; stage < HatchOpening.STAGES; stage++)
        {
            for (var change : HatchOpening.retreat(fort, stage))
            {
                assertThat(change.part()).isEqualTo(Part.AIR);
                assertThat(Math.abs(change.x() - fort.hatchX() - 3.5)).isEqualTo(stage + 0.5);
                assertThat(change.z() - fort.hatchZ()).isBetween(1, LEAVES);
                assertThat(change.y()).isBetween(0, UnderwayLevels.HATCH_FLOOR);
                assertThat(gone.add(change)).isTrue();
            }
        }
        assertThat(gone).hasSize(LEAVES * LEAVES * 2);
    }

    @Test
    void stairsCarryOnFromTheShaftToTheFloor()
    {
        var canvas = new UnderwayCanvas(StairShaft.SIZE, StairShaft.SIZE);
        for (int z = -RANGE; z <= RANGE; z++)
        {
            for (int x = -RANGE; x <= RANGE; x++)
            {
                var fort = UnderwayPlans.fort(SEED, new DistrictPos(x, z));
                canvas.reset(fort.hatchX(), fort.hatchZ());
                UnderwayPlans.paint(SEED, canvas);
                var changes = HatchOpening.stairs(fort);

                var lower = only(changes, Part.STEP, 0);
                var upper = only(changes, Part.STEP, UnderwayLevels.HATCH_FLOOR);
                var landing = only(changes, Part.LANDING, UnderwayLevels.HATCH_FLOOR);
                int dx = Compass.dx(lower.climb());
                int dz = Compass.dz(lower.climb());
                assertThat(canvas.get(lower.x() - dx, UnderwayLevels.TOP_Y, lower.z() - dz)).isEqualTo(UnderwayMaterial.stairs(lower.climb()));
                assertThat(upper).isEqualTo(new Change(lower.x() + dx, UnderwayLevels.HATCH_FLOOR, lower.z() + dz, Part.STEP, lower.climb()));
                assertThat(landing).isEqualTo(new Change(upper.x() + dx, UnderwayLevels.HATCH_FLOOR, upper.z() + dz, Part.LANDING, 0));

                var caps = changes.stream().filter(change -> change.part() == Part.CAP).toList();
                assertThat(caps).hasSize(4).allSatisfy(cap -> assertThat(canvas.get(cap.x(), cap.y(), cap.z())).isEqualTo(UnderwayMaterial.AIR));
                assertThat(changes).hasSize(7).allSatisfy(change ->
                {
                    assertThat(change.x() - fort.hatchX()).isBetween(1, LEAVES);
                    assertThat(change.z() - fort.hatchZ()).isBetween(1, LEAVES);
                });
            }
        }
    }

    private static Change only(java.util.List<Change> changes, Part part, int y)
    {
        var found = changes.stream().filter(change -> change.part() == part && change.y() == y).toList();
        assertThat(found).hasSize(1);
        return found.get(0);
    }
}
