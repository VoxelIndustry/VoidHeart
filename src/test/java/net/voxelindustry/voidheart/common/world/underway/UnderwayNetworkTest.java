package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnderwayNetworkTest
{
    private static final long[] SEEDS = { 1L, 1234L, -77L, 9_000_000_000L };
    private static final int RANGE = 60;

    @Test
    void linesFallFiveToSevenDistrictsApart()
    {
        for (long seed : SEEDS)
        {
            int lastRow = Integer.MIN_VALUE;
            int lastColumn = Integer.MIN_VALUE;
            for (int at = -RANGE; at <= RANGE; at++)
            {
                if (UnderwayNetwork.isLineRow(seed, at))
                {
                    if (lastRow != Integer.MIN_VALUE)
                        assertThat(at - lastRow).isBetween(5, 7);
                    lastRow = at;
                }
                if (UnderwayNetwork.isLineColumn(seed, at))
                {
                    if (lastColumn != Integer.MIN_VALUE)
                        assertThat(at - lastColumn).isBetween(5, 7);
                    lastColumn = at;
                }
            }
        }
    }

    @Test
    void everyHatchLeadsToAStationAtMostThreeDistrictsAway()
    {
        for (long seed : SEEDS)
        {
            for (int z = -RANGE; z <= RANGE; z++)
            {
                for (int x = -RANGE; x <= RANGE; x++)
                {
                    var pos = new DistrictPos(x, z);
                    var station = UnderwayNetwork.stationOf(seed, pos);
                    assertThat(UnderwayNetwork.nodeAt(seed, station).isOnLine()).isTrue();
                    assertThat(Math.abs(station.x() - x) + Math.abs(station.z() - z)).isLessThanOrEqualTo(3);
                    assertThat(station.equals(pos)).isEqualTo(UnderwayNetwork.nodeAt(seed, pos).isOnLine());
                }
            }
        }
    }

    @Test
    void aTunnelIsTheSameFromBothEnds()
    {
        for (long seed : SEEDS)
        {
            for (int z = -RANGE; z <= RANGE; z++)
            {
                for (int x = -RANGE; x <= RANGE; x++)
                {
                    var pos = new DistrictPos(x, z);
                    for (int direction = 0; direction < Compass.COUNT; direction++)
                    {
                        var next = new DistrictPos(x + Compass.dx(direction), z + Compass.dz(direction));
                        assertThat(UnderwayNetwork.linkFrom(seed, pos, direction)).isEqualTo(UnderwayNetwork.linkFrom(seed, next, Compass.opposite(direction)));
                    }
                }
            }
        }
    }

    @Test
    void trunkTunnelsOnlyJoinDistrictsOfOneLine()
    {
        for (long seed : SEEDS)
        {
            for (int z = -RANGE; z <= RANGE; z++)
            {
                for (int x = -RANGE; x <= RANGE; x++)
                {
                    var pos = new DistrictPos(x, z);
                    assertThat(UnderwayNetwork.linkFrom(seed, pos, Compass.EAST) != UnderwayLink.NONE).isEqualTo(UnderwayNetwork.isLineRow(seed, z));
                    assertThat(UnderwayNetwork.linkFrom(seed, pos, Compass.SOUTH) != UnderwayLink.NONE).isEqualTo(UnderwayNetwork.isLineColumn(seed, x));
                }
            }
        }
    }

    @Test
    void floodedAndSealedRegionsAreGatedOffFromOtherStates()
    {
        for (long seed : SEEDS)
        {
            for (int z = -RANGE; z <= RANGE; z++)
            {
                for (int x = -RANGE; x <= RANGE; x++)
                {
                    var pos = new DistrictPos(x, z);
                    var here = UnderwayNetwork.regionRuin(seed, pos);
                    if (!here.isShutIn())
                        continue;
                    for (int direction = 0; direction < Compass.COUNT; direction++)
                    {
                        var next = new DistrictPos(x + Compass.dx(direction), z + Compass.dz(direction));
                        if (UnderwayNetwork.regionRuin(seed, next) != here)
                            assertThat(UnderwayNetwork.linkFrom(seed, pos, direction).canBeWalked()).isFalse();
                    }
                }
            }
        }
    }

    @Test
    void sectorsSpanAHandfulOfDistricts()
    {
        for (long seed : SEEDS)
        {
            var sizes = new UnderwaySectors(seed, -RANGE, RANGE * 2).sizes().values().stream().mapToInt(Integer::intValue).sorted().toArray();
            assertThat(sizes[sizes.length / 2]).isBetween(2, 12);
            assertThat(sizes[sizes.length - 1]).isLessThan(80);
        }
    }

    @Test
    void galleriesNeverCross()
    {
        for (long seed : SEEDS)
        {
            for (int z = -RANGE; z <= RANGE; z++)
            {
                for (int x = -RANGE; x <= RANGE; x++)
                {
                    var pos = new DistrictPos(x, z);
                    int own = UnderwayNetwork.galleryDirection(seed, pos);
                    if (own == UnderwayNetwork.NO_GALLERY)
                        continue;
                    for (int side : new int[] { (own + 1) & 3, (own + 3) & 3 })
                        assertThat(UnderwayNetwork.galleryBetween(seed, pos, side)).as("a gallery crossing the one of %s, seed %d", pos, seed).isFalse();
                }
            }
        }
    }

    @Test
    void noSectorJoinsTwoHubs()
    {
        for (long seed : SEEDS)
        {
            var sectors = new UnderwaySectors(seed, -RANGE, RANGE * 2);
            var withHub = new java.util.HashSet<Integer>();
            for (int z = -RANGE; z < RANGE; z++)
            {
                for (int x = -RANGE; x < RANGE; x++)
                {
                    var pos = new DistrictPos(x, z);
                    if (UnderwayNetwork.nodeAt(seed, pos) == UnderwayNode.HUB)
                        assertThat(withHub.add(sectors.sectorOf(pos))).as("a second hub at %s, seed %d", pos, seed).isTrue();
                }
            }
        }
    }
}
