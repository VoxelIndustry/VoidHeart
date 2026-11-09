package net.voxelindustry.voidheart.common.energy;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Spherical fields around block positions, each with its own radius. Pure, so it can be tested without a level.
 */
public class EnergyFields
{
    private final Map<BlockPos, Integer> radiusByCenter = new HashMap<>();

    public void add(BlockPos center, int radius)
    {
        radiusByCenter.put(center.immutable(), Math.max(0, radius));
    }

    public void remove(BlockPos center)
    {
        radiusByCenter.remove(center);
    }

    public boolean covers(BlockPos pos)
    {
        for (var field : radiusByCenter.entrySet())
        {
            int radius = field.getValue();
            if (field.getKey().distSqr(pos) <= (double) radius * radius)
                return true;
        }
        return false;
    }

    public Set<BlockPos> centers()
    {
        return Collections.unmodifiableSet(radiusByCenter.keySet());
    }

    public boolean isEmpty()
    {
        return radiusByCenter.isEmpty();
    }

    public void clear()
    {
        radiusByCenter.clear();
    }
}
