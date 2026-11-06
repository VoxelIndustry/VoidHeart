package net.voxelindustry.voidheart.common.world.daedalus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DaedalusMobPlanTest
{
    private static final long[] SEEDS = { 0L, 1234L, -987654321L };
    private static final long[] DRAGOON_SEEDS = { 0L, 1234L, -987654321L, 7L, 42L, 0x5EEDL, -1L, 99999L, 31337L, 271828L };
    private static final int PER_THEME = 6;
    private static final int SEARCH_RADIUS = 30;
    private static final int LANE_CENTRE = 3;
    private static final double MIN_FOUND = 0.9;
    private static final double MIN_REACHED = 0.95;

    private static List<DistrictPos> districtsOf(DaedalusTheme theme, int wanted)
    {
        var found = new ArrayList<DistrictPos>();
        for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS && found.size() < wanted; z++)
        {
            for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS && found.size() < wanted; x++)
            {
                var pos = new DistrictPos(x, z);
                if (DaedalusTheme.at(pos) == theme)
                    found.add(pos);
            }
        }
        return found;
    }

    private static DaedalusCanvas painted(DistrictPlan plan)
    {
        var canvas = new DaedalusCanvas(DistrictPos.SIZE, DistrictPos.SIZE);
        canvas.reset(plan.pos.originX(), plan.pos.originZ());
        DistrictPainter.paint(plan, canvas);
        return canvas;
    }

    /**
     * Spots as the spawner finds them: nests first in each cell, then the mobs in plan order.
     */
    private static Map<DaedalusMobPlan.Placement, DaedalusMobSpots.Spot> spots(DistrictPlan plan, DaedalusCanvas canvas)
    {
        var taken = new HashMap<Integer, Set<Long>>();
        var spots = new HashMap<DaedalusMobPlan.Placement, DaedalusMobSpots.Spot>();
        for (boolean nests : new boolean[] { true, false })
        {
            for (var placement : plan.mobs().placements())
            {
                if (placement.mob().isMob() == nests)
                    continue;
                var spot = DaedalusMobSpots.find(canvas, plan.pos, placement, taken.computeIfAbsent(DistrictPlan.index(placement.i(), placement.k(), placement.j()), key -> new HashSet<>()));
                if (spot != null)
                    spots.put(placement, spot);
            }
        }
        return spots;
    }

    private static int[] landingPosition(DistrictPlan plan, DistrictPlan.Landing landing)
    {
        int along = landing.direction() == Compass.NORTH || landing.direction() == Compass.WEST ? 0 : DistrictPos.CELL - 1;
        int localX = Compass.isAlongX(landing.direction()) ? along : LANE_CENTRE;
        int localZ = Compass.isAlongX(landing.direction()) ? LANE_CENTRE : along;
        return new int[] {
                plan.pos.originX() + landing.i() * DistrictPos.CELL + localX,
                DistrictPos.floorY(landing.k()) + 1,
                plan.pos.originZ() + landing.j() * DistrictPos.CELL + localZ };
    }

    private static double distanceToLanding(DistrictPlan plan, DaedalusMobPlan.Placement placement)
    {
        double nearest = Double.MAX_VALUE;
        for (var landing : plan.landings())
        {
            int di = landing.i() - placement.i();
            int dk = landing.k() - placement.k();
            int dj = landing.j() - placement.j();
            nearest = Math.min(nearest, Math.sqrt(di * di + dk * dk + dj * dj));
        }
        return nearest;
    }

    @Test
    void everyDistrictHasOneDragoonOnGuardFarFromTheBridges()
    {
        for (long seed : DRAGOON_SEEDS)
        {
            for (var theme : DaedalusTheme.values())
            {
                for (var pos : districtsOf(theme, PER_THEME))
                {
                    var plan = DistrictPlanner.generate(seed, pos);
                    var dragoons = plan.mobs().placements().stream().filter(placement -> placement.mob() == DaedalusMob.KERNACIAN_DRAGOON).toList();
                    String where = theme + " " + pos + " seed " + seed;
                    assertEquals(1, dragoons.size(), "Dragoons in " + where);

                    var dragoon = dragoons.getFirst();
                    assertTrue(distanceToLanding(plan, dragoon) >= DaedalusMobPlan.DRAGOON_CLEARANCE - 1, "Dragoon too close to a bridge in " + where);
                    assertNotEquals(DaedalusMobPlan.ANYWHERE, dragoon.facing(), "Dragoon facing no door in " + where);

                    var canvas = painted(plan);
                    var spot = spots(plan, canvas).get(dragoon);
                    assertNotNull(spot, "No room to stand for the dragoon in " + where);
                    var walker = new DaedalusWalker(canvas);
                    int[] start = landingPosition(plan, plan.landings().getFirst());
                    walker.walkFrom(start[0], start[1], start[2]);
                    assertTrue(walker.hasReached(spot.x(), spot.y(), spot.z()), "Dragoon out of reach at " + spot + " in " + where);
                }
            }
        }
    }

    @Test
    void mobsStandOnReachableGroundAwayFromTheBridges()
    {
        for (var theme : DaedalusTheme.values())
        {
            int planned = 0;
            int found = 0;
            int walkers = 0;
            int reached = 0;
            for (var pos : districtsOf(theme, PER_THEME))
            {
                var plan = DistrictPlanner.generate(SEEDS[1], pos);
                var canvas = painted(plan);
                var spots = spots(plan, canvas);
                var walker = new DaedalusWalker(canvas);
                int[] start = landingPosition(plan, plan.landings().getFirst());
                walker.walkFrom(start[0], start[1], start[2]);

                for (var placement : plan.mobs().placements())
                {
                    assertTrue(distanceToLanding(plan, placement) >= DaedalusMobPlan.LANDING_CLEARANCE, placement + " too close to a bridge in " + theme + " " + pos);
                    planned++;
                    var spot = spots.get(placement);
                    if (spot == null)
                        continue;
                    found++;
                    if (placement.mob().placing != DaedalusMob.Placing.FLOOR)
                        continue;
                    walkers++;
                    if (walker.hasReached(spot.x(), spot.y(), spot.z()))
                        reached++;
                }
            }
            System.out.printf("%s: %d planned, %.0f%% found a spot, %.0f%% of walkers reachable%n", theme, planned, 100F * found / planned, 100F * reached / walkers);
            assertTrue(found >= planned * MIN_FOUND, theme + ": only " + found + " of " + planned + " found a spot");
            assertTrue(reached >= walkers * MIN_REACHED, theme + ": only " + reached + " of " + walkers + " walkers can be reached");
        }
    }

    @Test
    void aboutAThirdOfEachDistrictComesFromItsThemesFamily()
    {
        for (var theme : DaedalusTheme.values())
        {
            var roster = DaedalusRoster.of(theme);
            var counts = new EnumMap<DaedalusMob, Integer>(DaedalusMob.class);
            int mobs = 0;
            int family = 0;
            for (var pos : districtsOf(theme, PER_THEME))
            {
                var plan = DistrictPlanner.generate(SEEDS[0], pos);
                int inDistrict = 0;
                for (var placement : plan.mobs().placements())
                {
                    var mob = placement.mob();
                    if (!mob.isMob())
                    {
                        assertTrue(roster.hasNests(), "Nest in " + theme);
                        continue;
                    }
                    if (mob != DaedalusMob.KERNACIAN_DRAGOON)
                        assertTrue(roster.holds(mob), mob + " does not live in " + theme);
                    counts.merge(mob, 1, Integer::sum);
                    inDistrict++;
                    if (roster.isFamily(mob))
                        family++;
                }
                mobs += inDistrict;
                assertTrue(inDistrict >= DaedalusMobPlan.BUDGET * 0.8 && inDistrict <= DaedalusMobPlan.BUDGET + 8, inDistrict + " mobs in " + theme + " " + pos);
            }
            System.out.println(theme + " " + counts);
            double share = family / (double) mobs;
            assertEquals(DaedalusRoster.FAMILY_SHARE, share, 0.05, theme + " family share");
        }
    }

    @Test
    void nestsGrowOnlyWhereBarnaclesLive()
    {
        for (var theme : DaedalusTheme.values())
        {
            for (var pos : districtsOf(theme, 2))
            {
                var plan = DistrictPlanner.generate(SEEDS[2], pos);
                long nests = plan.mobs().placements().stream().filter(placement -> placement.mob() == DaedalusMob.BARNACLE_NEST).count();
                assertEquals(DaedalusRoster.of(theme).hasNests() ? DaedalusMobPlan.NESTS : 0, nests, "Nests in " + theme + " " + pos);
            }
        }
    }

    /**
     * Chunks are painted one at a time when they spawn their mobs, so each must find the very spots the whole district would.
     */
    @Test
    void aChunkAloneFindsTheSameSpotsAsTheWholeDistrict()
    {
        for (var theme : DaedalusTheme.values())
        {
            var plan = DistrictPlanner.generate(SEEDS[1], districtsOf(theme, 1).getFirst());
            var whole = spots(plan, painted(plan));
            var chunk = new DaedalusCanvas(16, 16);
            for (var placement : plan.mobs().placements())
            {
                chunk.reset(placement.chunkX(plan.pos) * 16, placement.chunkZ(plan.pos) * 16);
                DistrictPlans.paint(plan.worldSeed, chunk);
                var alone = new HashMap<DaedalusMobPlan.Placement, DaedalusMobSpots.Spot>();
                var taken = new HashMap<Integer, Set<Long>>();
                for (boolean nests : new boolean[] { true, false })
                {
                    for (var other : plan.mobs().inChunk(placement.chunkX(plan.pos), placement.chunkZ(plan.pos)))
                    {
                        if (other.mob().isMob() == nests)
                            continue;
                        var spot = DaedalusMobSpots.find(chunk, plan.pos, other, taken.computeIfAbsent(DistrictPlan.index(other.i(), other.k(), other.j()), key -> new HashSet<>()));
                        if (spot != null)
                            alone.put(other, spot);
                    }
                }
                assertEquals(whole.get(placement), alone.get(placement), placement + " in " + theme);
            }
        }
    }

    @Test
    void plansAreTheSameEveryTime()
    {
        var pos = new DistrictPos(3, -7);
        assertEquals(DistrictPlanner.generate(42, pos).mobs().placements(), DistrictPlanner.generate(42, pos).mobs().placements());
    }

    @Test
    void theFenceStopsMobsAtTheDistrictEdge()
    {
        double edge = DistrictPos.SIZE;
        // Walking east into the chasm stops at the edge
        assertEquals(0.2, DaedalusFence.clipX(edge - 1, 10, edge - 1.5, edge - 0.2, 0.5), 1e-9);
        assertEquals(0, DaedalusFence.clipX(edge - 0.5, 10, edge - 0.8, edge, 0.5), 1e-9);
        // Walking back in is free
        assertEquals(-0.5, DaedalusFence.clipX(edge - 0.5, 10, edge - 0.8, edge, -0.5), 1e-9);
        // West of the origin, the previous district's chasm
        assertEquals(-0.3, DaedalusFence.clipX(0.5, 10, 0.3, 0.9, -1), 1e-9);
        // Already out in the chasm, nothing holds it
        assertEquals(1, DaedalusFence.clipX(edge + 4, 10, edge + 3.7, edge + 4.3, 1), 1e-9);

        assertTrue(DaedalusFence.allows(10, 10, 20, 20));
        assertFalse(DaedalusFence.allows(10, 10, edge + 2, 20));
        assertFalse(DaedalusFence.allows(10, 10, DistrictPos.PITCH + 10, 20));
        assertTrue(DaedalusFence.allows(edge + 2, 10, 10, 10));
    }

    @Test
    void compassYawsMatchMinecraft()
    {
        assertEquals(180, Compass.yaw(Compass.NORTH));
        assertEquals(270, Compass.yaw(Compass.EAST));
        assertEquals(0, Compass.yaw(Compass.SOUTH));
        assertEquals(90, Compass.yaw(Compass.WEST));
    }
}
