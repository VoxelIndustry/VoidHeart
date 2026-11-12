package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class UnderwayFortTest
{
    private static final long[] SEEDS = { 1L, 1234L };
    private static final int RANGE = 7;
    private static final int SEARCH = 80;
    private static final int TRACK_Y = UnderwayLevels.BALLAST + 1;

    @Test
    void everyRoomIsReachedFromTheHatch()
    {
        var nodes = EnumSet.noneOf(UnderwayNode.class);
        for (long seed : SEEDS)
        {
            for (int z = -RANGE; z <= RANGE; z++)
            {
                for (int x = -RANGE; x <= RANGE; x++)
                {
                    nodes.add(UnderwayNetwork.nodeAt(seed, new DistrictPos(x, z)));
                    assertWalkable(seed, new DistrictPos(x, z));
                }
            }
        }
        assertThat(nodes).containsExactlyInAnyOrder(UnderwayNode.values());
    }

    @Test
    void rareRuinsAreWalkableToo()
    {
        for (var ruin : new UnderwayRuin[] { UnderwayRuin.SEALED, UnderwayRuin.BREACHED, UnderwayRuin.FLOODED, UnderwayRuin.COLLAPSED })
        {
            for (var node : UnderwayNode.values())
            {
                if (ruin == UnderwayRuin.BREACHED && node != UnderwayNode.STATION)
                    continue;
                assertWalkable(SEEDS[0], find(SEEDS[0], node, ruin));
            }
        }
    }

    private static DistrictPos find(long seed, UnderwayNode node, UnderwayRuin ruin)
    {
        for (int z = 0; z < SEARCH; z++)
        {
            for (int x = 0; x < SEARCH; x++)
            {
                var pos = new DistrictPos(x, z);
                if (UnderwayNetwork.nodeAt(seed, pos) == node && UnderwayNetwork.ruinAt(seed, pos) == ruin)
                    return pos;
            }
        }
        throw new IllegalStateException("No " + ruin + " " + node + " within " + SEARCH + " districts");
    }

    private static void assertWalkable(long seed, DistrictPos pos)
    {
        var fort = UnderwayPlans.fort(seed, pos);
        var frame = fort.frame();
        var canvas = paint(seed, pos, 1, 1);
        var walker = new UnderwayWalker(canvas, frame.x(fort.footAlong(), fort.footAcross()), UnderwayLevels.ENTRY_FLOOR + 1, frame.z(fort.footAlong(), fort.footAcross()));
        for (var room : fort.rooms())
        {
            if (room.kind().isCutOff())
                continue;
            int floor = 0;
            int walked = 0;
            for (int along = room.a0(); along <= room.a1(); along++)
            {
                for (int across = room.c0(); across <= room.c1(); across++)
                {
                    int x = frame.x(along, across);
                    int z = frame.z(along, across);
                    if (canvas.propAt(x, room.floorY() + 1, z) != null || canvas.propAt(x, room.floorY() + 2, z) != null || canvas.get(x, room.floorY(), z).isOpen())
                        continue;
                    floor++;
                    if (walker.reaches(x, room.floorY() + 1, z) || walker.reaches(x, room.floorY() + 2, z))
                        walked++;
                }
            }
            assertThat(walked * 10)
                    .as("%s at %d %d of the %s %s under %s, seed %d: %d of %d free blocks walked", room.kind(), room.centreAlong(), room.centreAcross(), fort.ruin(), fort.node(), pos, seed, walked, floor)
                    .isGreaterThanOrEqualTo(floor * 8);
        }
        for (boolean alongX : new boolean[] { true, false })
        {
            if (!(alongX ? UnderwayNetwork.isLineRow(seed, pos.z()) : UnderwayNetwork.isLineColumn(seed, pos.x())))
                continue;
            var line = UnderwayFrame.ofLine(pos, alongX);
            for (int along : new int[] { 1, DistrictPos.SIZE - 2 })
                assertThat(walker.reaches(line.x(along, TrunkPainter.CENTRE), TRACK_Y, line.z(along, TrunkPainter.CENTRE))).as("trunk at %d under %s, seed %d", along, pos, seed).isTrue();
        }
    }

    @Test
    void hatchesStandOnTheLatticeAndVary()
    {
        var places = new HashSet<Integer>();
        for (int z = -RANGE; z <= RANGE; z++)
        {
            for (int x = -RANGE; x <= RANGE; x++)
            {
                var pos = new DistrictPos(x, z);
                var fort = UnderwayPlans.fort(SEEDS[0], pos);
                int localX = fort.hatchX() - pos.originX();
                int localZ = fort.hatchZ() - pos.originZ();
                assertThat(localX % DistrictPos.CELL).isZero();
                assertThat(localZ % DistrictPos.CELL).isZero();
                assertThat(localX).isBetween(DistrictPos.CELL, DistrictPos.SIZE - 2 * DistrictPos.CELL);
                assertThat(localZ).isBetween(DistrictPos.CELL, DistrictPos.SIZE - 2 * DistrictPos.CELL);
                places.add(localX * DistrictPos.SIZE + localZ);
            }
        }
        assertThat(places).hasSizeGreaterThan(40);
    }

    @Test
    void aCaveInIsWalkedAroundAndAGateIsNot()
    {
        var met = EnumSet.noneOf(UnderwayLink.class);
        for (long seed : SEEDS)
        {
            for (int z = -RANGE * 2; z <= RANGE * 2; z++)
            {
                for (int x = -RANGE * 2; x <= RANGE * 2; x++)
                {
                    var pos = new DistrictPos(x, z);
                    for (int direction : new int[] { Compass.EAST, Compass.SOUTH })
                    {
                        var link = UnderwayNetwork.linkFrom(seed, pos, direction);
                        if (link == UnderwayLink.NONE)
                            continue;
                        met.add(link);
                        boolean alongX = direction == Compass.EAST;
                        var line = UnderwayFrame.ofLine(pos, alongX);
                        var canvas = paint(seed, pos, alongX ? 2 : 1, alongX ? 1 : 2);
                        var walker = new UnderwayWalker(canvas, line.x(110, TrunkPainter.CENTRE), TRACK_Y, line.z(110, TrunkPainter.CENTRE));
                        int beyond = DistrictPos.PITCH + 10;
                        assertThat(walker.reaches(line.x(beyond, TrunkPainter.CENTRE), TRACK_Y, line.z(beyond, TrunkPainter.CENTRE)))
                                .as("%s link %d of %s, seed %d", link, direction, pos, seed)
                                .isEqualTo(link.canBeWalked());
                    }
                }
            }
        }
        assertThat(met).contains(UnderwayLink.OPEN, UnderwayLink.COLLAPSED, UnderwayLink.GATED);
    }

    @Test
    void everyPostWalksToItsStation()
    {
        int walked = 0;
        for (int z = -RANGE; z <= RANGE; z++)
        {
            for (int x = -RANGE; x <= RANGE; x += 2)
            {
                var pos = new DistrictPos(x, z);
                if (UnderwayNetwork.nodeAt(SEEDS[0], pos).isOnLine())
                    continue;
                var station = UnderwayNetwork.stationOf(SEEDS[0], pos);
                var from = new DistrictPos(Math.min(pos.x(), station.x()), Math.min(pos.z(), station.z()));
                var canvas = paint(SEEDS[0], from, Math.abs(pos.x() - station.x()) + 1, Math.abs(pos.z() - station.z()) + 1);
                var post = UnderwayPlans.fort(SEEDS[0], pos);
                var walker = new UnderwayWalker(canvas, post.frame().x(post.footAlong(), post.footAcross()), UnderwayLevels.ENTRY_FLOOR + 1, post.frame().z(post.footAlong(), post.footAcross()));

                var fort = UnderwayPlans.fort(SEEDS[0], station);
                var gallery = fort.rooms().stream().filter(room -> room.kind() == UnderwayRoomKind.GALLERY).findFirst().orElseThrow();
                boolean arrived = false;
                for (int along = gallery.a0(); along <= gallery.a1() && !arrived; along++)
                {
                    int targetX = fort.frame().x(along, gallery.c0() + 1);
                    int targetZ = fort.frame().z(along, gallery.c0() + 1);
                    arrived = walker.reaches(targetX, UnderwayLevels.LIVING_FLOOR + 1, targetZ) || walker.reaches(targetX, UnderwayLevels.LIVING_FLOOR + 2, targetZ);
                }
                assertThat(arrived).as("the post under %s to the station under %s", pos, station).isTrue();
                walked++;
            }
        }
        assertThat(walked).isGreaterThan(20);
    }

    private static UnderwayCanvas paint(long seed, DistrictPos pos, int districtsX, int districtsZ)
    {
        var canvas = new UnderwayCanvas(DistrictPos.PITCH * districtsX, DistrictPos.PITCH * districtsZ);
        canvas.reset(pos.originX(), pos.originZ());
        UnderwayPlans.paint(seed, canvas);
        return canvas;
    }
}
