package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnderwayTempleTest
{
    private static final long[] SEEDS = { 1L, 1234L, 99L };
    private static final int RANGE = 8;

    private record Found(long seed, DistrictPos pos, UnderwayFort fort)
    {
    }

    private static List<Found> forts()
    {
        var forts = new ArrayList<Found>();
        for (long seed : SEEDS)
        {
            for (int z = -RANGE; z <= RANGE; z++)
            {
                for (int x = -RANGE; x <= RANGE; x++)
                    forts.add(new Found(seed, new DistrictPos(x, z), UnderwayPlans.fort(seed, new DistrictPos(x, z))));
            }
        }
        return forts;
    }

    @Test
    void aboutAFortInThreeHoldsATempleAndNoneHoldsTwo()
    {
        int temples = 0;
        var forts = forts();
        for (var found : forts)
        {
            long rooms = found.fort().rooms().stream().filter(room -> room.kind() == UnderwayRoomKind.TEMPLE).count();
            assertThat(rooms).as("temples under %s, seed %d", found.pos(), found.seed()).isLessThanOrEqualTo(1);
            assertThat(found.fort().temple() != null).as("the temple of %s, seed %d, is the one in its rooms", found.pos(), found.seed()).isEqualTo(rooms == 1);
            if (found.fort().temple() != null)
                temples++;
        }
        assertThat(temples * 100 / forts.size()).as("%d temples in %d forts", temples, forts.size()).isBetween(20, 40);
    }

    @Test
    void aTempleIsLaidOutAroundItsAisle()
    {
        for (var found : forts())
        {
            var temple = found.fort().temple();
            if (temple == null)
                continue;
            var room = temple.room();
            int alongSpan = room.a1() - room.a0() + 1;
            int acrossSpan = room.c1() - room.c0() + 1;
            assertThat(temple.inAlong() == 0 ? new int[] { alongSpan, acrossSpan } : new int[] { acrossSpan, alongSpan })
                    .as("temple under %s", found.pos()).containsExactly(UnderwayTemple.WIDTH, UnderwayTemple.DEPTH);
            assertThat(room.height()).isEqualTo(UnderwayTemple.HEIGHT);
            // The doorway is three wide, in line with the aisle, in the wall at the temple's low d
            int wall = temple.inAlong() == 0 ? temple.across(0, -1) : temple.along(0, -1);
            var door = found.fort().openings().stream()
                    .filter(opening -> opening.floorY() == room.floorY())
                    .filter(opening -> temple.inAlong() == 0 ? opening.c0() == wall && opening.c1() == wall && opening.a0() >= room.a0() && opening.a1() <= room.a1()
                            : opening.a0() == wall && opening.a1() == wall && opening.c0() >= room.c0() && opening.c1() <= room.c1())
                    .toList();
            assertThat(door).as("doorway of the temple under %s", found.pos()).hasSize(1);
            int low = temple.inAlong() == 0 ? door.getFirst().a0() : door.getFirst().c0();
            int high = temple.inAlong() == 0 ? door.getFirst().a1() : door.getFirst().c1();
            int aisle = temple.inAlong() == 0 ? temple.along(UnderwayTemple.AISLE, 0) : temple.across(UnderwayTemple.AISLE, 0);
            assertThat(new int[] { low, high }).containsExactly(aisle - 1, aisle + 1);

            var props = found.fort().props();
            assertThat(props).as("altar of the temple under %s", found.pos()).anyMatch(spot -> spot.prop() == UnderwayProp.TEMPLE_ALTAR
                    && spot.along() == temple.along(UnderwayTemple.AISLE, FortBuilder.DAIS_FROM + 1) && spot.across() == temple.across(UnderwayTemple.AISLE, FortBuilder.DAIS_FROM + 1));
            assertThat(props).anyMatch(spot -> spot.prop() == UnderwayProp.TEETH_IDOL).anyMatch(spot -> spot.prop() == UnderwayProp.BROOD_IDOL);
            boolean sealed = found.fort().ruin() == UnderwayRuin.SEALED;
            assertThat(props).as("flames of the %s temple under %s", found.fort().ruin(), found.pos())
                    .noneMatch(spot -> !sealed && (spot.prop() == UnderwayProp.BRAZIER_LIT || spot.prop() == UnderwayProp.VOTIVE_CANDLES_LIT))
                    .noneMatch(spot -> sealed && (spot.prop() == UnderwayProp.BRAZIER || spot.prop() == UnderwayProp.VOTIVE_CANDLES));
        }
    }

    @Test
    void aSealedFortKeepsItsMosaicWholeAndOthersLoseSome()
    {
        int fallen = 0;
        int tiles = 0;
        for (var found : forts())
        {
            var temple = found.fort().temple();
            if (temple == null)
                continue;
            assertThat(temple.wear()).hasSize(UnderwayTemple.COLUMNS * UnderwayTemple.ROWS);
            if (found.fort().ruin() == UnderwayRuin.SEALED)
                assertThat(temple.wear()).containsOnly(UnderwayTemple.Wear.WHOLE);
            else
            {
                fallen += (int) temple.wear().stream().filter(wear -> wear == UnderwayTemple.Wear.FALLEN).count();
                tiles += temple.wear().size();
                assertThat(temple.wear()).as("wear of the temple under %s", found.pos()).contains(UnderwayTemple.Wear.CHIPPED);
            }
        }
        // Enough fell to show its age, not so much the picture is lost
        assertThat(fallen * 100 / tiles).isBetween(1, 12);
    }

    @Test
    void theMosaicFacesTheDoorwayTheRightWayRound()
    {
        int checked = 0;
        for (var found : forts())
        {
            var temple = found.fort().temple();
            if (temple == null)
                continue;
            var fort = found.fort();
            var frame = fort.frame();
            var canvas = new UnderwayCanvas(DistrictPos.PITCH, DistrictPos.PITCH);
            canvas.reset(found.pos().originX(), found.pos().originZ());
            UnderwayPlans.paint(found.seed(), canvas);

            int in = frame.compass(temple.inAlong(), temple.inAcross());
            // Facing the mosaic, the right hand points this way
            int rightX = -Compass.dz(in);
            int rightZ = Compass.dx(in);
            var seen = new HashSet<Integer>();
            for (int row = 0; row < UnderwayTemple.ROWS; row++)
            {
                int y = temple.rowY(row);
                int lastColumn = -1;
                int lastRight = Integer.MIN_VALUE;
                for (int w = UnderwayTemple.FIRST_COLUMN; w < UnderwayTemple.FIRST_COLUMN + UnderwayTemple.COLUMNS; w++)
                {
                    int along = temple.along(w, UnderwayTemple.DEPTH);
                    int across = temple.across(w, UnderwayTemple.DEPTH);
                    int x = frame.x(along, across);
                    int z = frame.z(along, across);
                    // The block in front of it is the temple's air, so it is seen from inside
                    int frontX = frame.x(temple.along(w, UnderwayTemple.DEPTH - 1), temple.across(w, UnderwayTemple.DEPTH - 1));
                    int frontZ = frame.z(temple.along(w, UnderwayTemple.DEPTH - 1), temple.across(w, UnderwayTemple.DEPTH - 1));
                    assertThat(frontX + Compass.dx(in) == x && frontZ + Compass.dz(in) == z).isTrue();

                    var wear = temple.wear(w, row);
                    var material = canvas.get(x, y, z);
                    if (wear == UnderwayTemple.Wear.FALLEN)
                    {
                        assertThat(material).isEqualTo(UnderwayMaterial.MOSAIC_BED);
                        continue;
                    }
                    assertThat(material).as("mosaic of the temple under %s at row %d, w %d", found.pos(), row, w).isEqualTo(UnderwayMaterial.MOSAIC);
                    assertThat(canvas.isMosaicChipped(x, y, z)).isEqualTo(wear == UnderwayTemple.Wear.CHIPPED);
                    int tile = canvas.mosaicTile(x, y, z);
                    assertThat(tile / UnderwayTemple.COLUMNS).isEqualTo(row);
                    assertThat(seen.add(tile)).as("tile %d set twice", tile).isTrue();
                    // Further to the right, further along the picture
                    int right = x * rightX + z * rightZ;
                    if (lastColumn >= 0)
                        assertThat(Integer.compare(tile % UnderwayTemple.COLUMNS, lastColumn)).isEqualTo(Integer.compare(right, lastRight));
                    lastColumn = tile % UnderwayTemple.COLUMNS;
                    lastRight = right;
                }
            }
            checked++;
        }
        assertThat(checked).isPositive();
    }
}
