package net.voxelindustry.voidheart.common.energy.creative;

import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.common.energy.EnergyFields;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The creative test blocks loaded in each level. Their tiles join when loaded and leave when broken or unloaded.
 */
public final class CreativeSources
{
    private static final Map<ServerLevel, EnergyFields> WELLS  = new WeakHashMap<>();
    private static final Map<ServerLevel, EnergyFields> STONES = new WeakHashMap<>();

    private CreativeSources()
    {
    }

    public static EnergyFields wells(ServerLevel level)
    {
        return WELLS.computeIfAbsent(level, key -> new EnergyFields());
    }

    public static EnergyFields stones(ServerLevel level)
    {
        return STONES.computeIfAbsent(level, key -> new EnergyFields());
    }

    public static void clear()
    {
        WELLS.clear();
        STONES.clear();
    }
}
