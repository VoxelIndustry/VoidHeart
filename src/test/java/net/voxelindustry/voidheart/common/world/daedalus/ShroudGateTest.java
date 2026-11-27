package net.voxelindustry.voidheart.common.world.daedalus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShroudGateTest
{
    private static final int PILLARS = 2 * (ShroudGate.LINTEL + 1);
    private static final int BEAMS = 3 + 3 + 5;
    private static final int SEALS = 3 * ShroudGate.SEAL_HEIGHT;

    @Test
    void aGateIsAFrameAroundItsSealWithAnAltarBeforeIt()
    {
        for (int facing = 0; facing < Compass.COUNT; facing++)
        {
            var gate = new ShroudGate(100, 20, -50, facing);
            var counts = new EnumMap<ShroudGate.Part, Integer>(ShroudGate.Part.class);
            var seen = new HashSet<Long>();
            gate.forEach((x, y, z, part) ->
            {
                counts.merge(part, 1, Integer::sum);
                assertTrue(seen.add(((long) x << 40) ^ ((long) y << 20) ^ (z & 0xFFFFF)), "Two parts at " + x + " " + y + " " + z);
                assertEquals(part, gate.partAt(x, y, z));
            });
            assertEquals(PILLARS, counts.get(ShroudGate.Part.PILLAR));
            assertEquals(BEAMS, counts.get(ShroudGate.Part.BEAM));
            assertEquals(1, counts.get(ShroudGate.Part.KEYSTONE));
            assertEquals(SEALS, counts.get(ShroudGate.Part.SEAL));
            assertEquals(1, counts.get(ShroudGate.Part.ALTAR));
            assertEquals(ShroudGate.Part.ALTAR, gate.partAt(gate.altarX(), gate.altarY(), gate.altarZ()));
        }
    }

    @Test
    void theSealIsSetBackInTheWallBehindAnEmptyNiche()
    {
        for (int facing = 0; facing < Compass.COUNT; facing++)
        {
            var gate = new ShroudGate(0, 0, 0, facing);
            for (int up = ShroudGate.SEAL_BOTTOM; up < ShroudGate.LINTEL; up++)
            {
                for (int across = -ShroudGate.SEAL_HALF_WIDTH; across <= ShroudGate.SEAL_HALF_WIDTH; across++)
                {
                    assertEquals(ShroudGate.Part.SEAL, gate.partAt(gate.worldX(across, 0), up, gate.worldZ(across, 0)));
                    for (int depth = 1; depth < ShroudGate.ALTAR_DEPTH; depth++)
                        assertEquals(ShroudGate.Part.NONE, gate.partAt(gate.worldX(across, depth), up, gate.worldZ(across, depth)), "Something stands before the seal");
                }
            }
        }
    }

    @Test
    void theAltarLeadsBackToItsGate()
    {
        for (int facing = 0; facing < Compass.COUNT; facing++)
        {
            var gate = new ShroudGate(-7, 33, 12, facing);
            assertEquals(gate, ShroudGate.fromAltar(gate.altarX(), gate.altarY(), gate.altarZ(), gate.altarFacing()));
            assertEquals(Compass.opposite(facing), gate.altarFacing());
        }
    }

    /** Column 0 is on the left of someone standing in the room facing the gate, whichever way it faces. */
    @Test
    void sealColumnsRunLeftToRightSeenFromTheRoom()
    {
        for (int facing = 0; facing < Compass.COUNT; facing++)
        {
            var gate = new ShroudGate(0, 0, 0, facing);
            int look = Compass.opposite(facing);
            // Facing a direction, right is a quarter turn clockwise
            int right = (look + 1) & 3;
            List<int[]> bottom = new ArrayList<>();
            gate.forEachSealBlock((x, y, z, column, row) ->
            {
                if (row == 0)
                    bottom.add(new int[] { column, x * Compass.dx(right) + z * Compass.dz(right) });
            });
            bottom.sort((a, b) -> Integer.compare(a[0], b[0]));
            for (int index = 1; index < bottom.size(); index++)
                assertTrue(bottom.get(index)[1] > bottom.get(index - 1)[1], "Columns run the wrong way facing " + facing);
        }
    }
}
