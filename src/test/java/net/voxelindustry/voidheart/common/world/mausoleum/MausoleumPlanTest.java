package net.voxelindustry.voidheart.common.world.mausoleum;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.ShroudGate;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import net.voxelindustry.voidheart.common.world.sanctum.SanctumAddress;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static net.voxelindustry.voidheart.common.world.mausoleum.MausoleumMaterial.AIR;
import static org.junit.jupiter.api.Assertions.*;

class MausoleumPlanTest
{
    private static final long SEED = 7;

    @Test
    void sameSeedSamePlan()
    {
        var first = MausoleumPlan.generate(SEED);
        var second = MausoleumPlan.generate(SEED);
        for (int y = 0; y < MausoleumPlan.HEIGHT; y++)
            for (int z = 0; z < MausoleumPlan.LENGTH; z++)
                for (int x = 0; x < MausoleumPlan.WIDTH; x++)
                    assertEquals(first.material(x, y, z), second.material(x, y, z));
        assertEquals(first.orbs(), second.orbs());
    }

    @Test
    void addressesKeepToTheirOwnRow()
    {
        for (int index : new int[] { 0, 1, 57, MausoleumAddress.MAX_INDEX })
        {
            var origin = MausoleumAddress.origin(index);
            for (int dx : new int[] { 0, MausoleumPlan.WIDTH - 1 })
            {
                for (int dz : new int[] { 0, MausoleumPlan.LENGTH - 1 })
                {
                    int x = origin.getX() + dx, z = origin.getZ() + dz;
                    assertEquals(index, MausoleumAddress.at(x, z));
                    assertEquals(-1, SanctumAddress.at(x, z), "A mausoleum overlaps a sanctum");
                    assertNull(DungeonAddress.at(x, z), "A mausoleum overlaps a dungeon");
                }
            }
        }
        assertEquals(-1, MausoleumAddress.at(SanctumAddress.origin(3).getX(), SanctumAddress.origin(3).getZ()));
    }

    @Test
    void theWayFromTheGateReachesTheRiderAndTheDais()
    {
        var plan = MausoleumPlan.generate(SEED);
        var reached = walk(plan, MausoleumPlan.ARRIVAL_X, MausoleumPlan.FLOOR_Y, MausoleumPlan.ARRIVAL_Z);
        assertTrue(reached.contains(pack(MausoleumPlan.RIDER_X, MausoleumPlan.RIDER_Y, MausoleumPlan.RIDER_Z)), "The rider's post can't be walked to");
        assertTrue(reached.contains(pack(MausoleumPlan.SARCOPHAGUS_X + 2, MausoleumPlan.DAIS_Y, MausoleumPlan.SARCOPHAGUS_Z)), "The dais can't be walked to");
    }

    @Test
    void burialChambersAreSealedOffAndWithinAWraithsCrossing()
    {
        var plan = MausoleumPlan.generate(SEED);
        var reached = walk(plan, MausoleumPlan.ARRIVAL_X, MausoleumPlan.FLOOR_Y, MausoleumPlan.ARRIVAL_Z);
        assertEquals(8, plan.chambers().size());
        for (var chamber : plan.chambers())
        {
            assertEquals(AIR, plan.material(chamber.x(), chamber.y(), chamber.z()));
            assertEquals(AIR, plan.material(chamber.x(), chamber.y() + 1, chamber.z()), "A wraith has no room to stand");
            assertFalse(reached.contains(pack(chamber.x(), chamber.y(), chamber.z())), "A burial chamber is open to the hall");

            // Toward the hall, only stone that can be crossed, no more than a few blocks of it
            int solid = 0;
            int x = chamber.x();
            while (!MausoleumPlan.inHall(x, chamber.z()))
            {
                x -= Compass.dx(chamber.side());
                if (plan.material(x, chamber.y() + 1, chamber.z()) != AIR)
                    solid++;
            }
            assertTrue(solid > 0 && solid <= 2, "The wall between a chamber and the hall is " + solid + " thick");
        }
    }

    @Test
    void theRiderHasRoomAtItsPost()
    {
        var plan = MausoleumPlan.generate(SEED);
        for (int dz = -2; dz <= 2; dz++)
            for (int dx = -2; dx <= 2; dx++)
                for (int y = MausoleumPlan.RIDER_Y; y < MausoleumPlan.RIDER_Y + 4; y++)
                    assertEquals(AIR, plan.material(MausoleumPlan.RIDER_X + dx, y, MausoleumPlan.RIDER_Z + dz), "No room at " + dx + ", " + y + ", " + dz);
    }

    @Test
    void theGateStandsAgainstTheWallWithRoomBeforeIt()
    {
        var plan = MausoleumPlan.generate(SEED);
        MausoleumPlan.GATE.forEach((x, y, z, part) ->
        {
            if (part == ShroudGate.Part.SEAL)
                assertNotEquals(AIR, plan.material(x, y, z), "The seal hangs in the air");
            else
                assertEquals(AIR, plan.material(x, y, z), "Part " + part + " of the gate is set in stone");
        });
        assertEquals(AIR, plan.material(MausoleumPlan.ARRIVAL_X, MausoleumPlan.FLOOR_Y, MausoleumPlan.ARRIVAL_Z));
        assertEquals(AIR, plan.material(MausoleumPlan.ARRIVAL_X, MausoleumPlan.FLOOR_Y + 1, MausoleumPlan.ARRIVAL_Z));
    }

    @Test
    void orbsAndTheSarcophagusHaveTheirPlace()
    {
        var plan = MausoleumPlan.generate(SEED);
        assertTrue(plan.orbs().size() >= 20);
        for (var orb : plan.orbs())
        {
            assertEquals(AIR, plan.material(orb.x(), orb.y(), orb.z()), "An orb is set in stone");
            assertTrue(orb.y() >= MausoleumPlan.FLOOR_Y + 2, "An orb hangs where heads go");
        }
        for (int offset : MausoleumPlan.sarcophagusParts())
        {
            int z = MausoleumPlan.SARCOPHAGUS_Z + offset * Compass.dz(MausoleumPlan.SARCOPHAGUS_HEAD);
            assertEquals(AIR, plan.material(MausoleumPlan.SARCOPHAGUS_X, MausoleumPlan.SARCOPHAGUS_Y, z));
            assertNotEquals(AIR, plan.material(MausoleumPlan.SARCOPHAGUS_X, MausoleumPlan.SARCOPHAGUS_Y - 1, z), "The sarcophagus lies on nothing");
        }
    }

    /** Every place a body two tall can stand and walk to, stepping up or down a block at most. */
    private static Set<Long> walk(MausoleumPlan plan, int x0, int y0, int z0)
    {
        var reached = new HashSet<Long>();
        var queue = new ArrayDeque<int[]>();
        queue.add(new int[] { x0, y0, z0 });
        reached.add(pack(x0, y0, z0));
        while (!queue.isEmpty())
        {
            var at = queue.poll();
            for (int side = 0; side < Compass.COUNT; side++)
            {
                for (int dy = -1; dy <= 1; dy++)
                {
                    int x = at[0] + Compass.dx(side), y = at[1] + dy, z = at[2] + Compass.dz(side);
                    if (!standable(plan, x, y, z) || dy > 0 && plan.material(at[0], at[1] + 2, at[2]) != AIR)
                        continue;
                    if (reached.add(pack(x, y, z)))
                        queue.add(new int[] { x, y, z });
                }
            }
        }
        return reached;
    }

    private static boolean standable(MausoleumPlan plan, int x, int y, int z)
    {
        return MausoleumPlan.inside(x, y, z) && plan.material(x, y, z) == AIR && plan.material(x, y + 1, z) == AIR && plan.material(x, y - 1, z) != AIR;
    }

    private static long pack(int x, int y, int z)
    {
        return ((long) x << 40) | ((long) y << 20) | z;
    }
}
