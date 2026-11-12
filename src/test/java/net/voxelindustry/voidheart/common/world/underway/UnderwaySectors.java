package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Groups the districts of a square window by the stretch of Underway a player can walk without passing a blast gate. */
final class UnderwaySectors
{
    private final Map<DistrictPos, Integer> sectorOf = new HashMap<>();
    private final Map<Integer, Integer> sizes = new HashMap<>();

    /** Tunnels leaving the window count as shut, so sectors at its edge come out smaller than they are. */
    UnderwaySectors(long seed, int min, int size)
    {
        int max = min + size - 1;
        int next = 0;
        for (int z = min; z <= max; z++)
        {
            for (int x = min; x <= max; x++)
            {
                var start = new DistrictPos(x, z);
                if (!UnderwayNetwork.nodeAt(seed, start).isOnLine() || sectorOf.containsKey(start))
                    continue;
                flood(seed, start, next++, min, max);
            }
        }
        for (int z = min; z <= max; z++)
        {
            for (int x = min; x <= max; x++)
            {
                var pos = new DistrictPos(x, z);
                var sector = sectorOf.get(UnderwayNetwork.stationOf(seed, pos));
                if (sector == null)
                    continue;
                sectorOf.put(pos, sector);
                sizes.merge(sector, 1, Integer::sum);
            }
        }
    }

    private void flood(long seed, DistrictPos start, int sector, int min, int max)
    {
        var open = new ArrayDeque<DistrictPos>();
        open.add(start);
        sectorOf.put(start, sector);
        while (!open.isEmpty())
        {
            var pos = open.poll();
            for (int direction = 0; direction < Compass.COUNT; direction++)
            {
                var next = new DistrictPos(pos.x() + Compass.dx(direction), pos.z() + Compass.dz(direction));
                if (next.x() < min || next.x() > max || next.z() < min || next.z() > max)
                    continue;
                if (UnderwayNetwork.linkFrom(seed, pos, direction).canBeWalked() && sectorOf.putIfAbsent(next, sector) == null)
                    open.add(next);
            }
        }
    }

    /** @return the sector of a district, or null when its station lies outside the window */
    Integer sectorOf(DistrictPos pos)
    {
        return sectorOf.get(pos);
    }

    /** @return how many districts each sector holds, posts included */
    Map<Integer, Integer> sizes()
    {
        return sizes;
    }
}
