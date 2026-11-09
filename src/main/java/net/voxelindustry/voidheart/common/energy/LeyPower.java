package net.voxelindustry.voidheart.common.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.common.energy.creative.CreativeSources;

import java.util.Objects;

/**
 * Ley for machines. It can't be stored, so machines use it as it flows and ask again every tick.
 * <p>
 * A creative ley stone answers first, ahead of whichever provider is installed.
 */
public final class LeyPower
{
    private static LeyPowerProvider provider = LeyPowerProvider.NONE;

    private LeyPower()
    {
    }

    /**
     * The fraction of need met at pos this tick, from 0 to 1. Needing nothing is always fully met.
     */
    public static float supply(ServerLevel level, BlockPos pos, double need)
    {
        if (need <= 0 || CreativeSources.stones(level).covers(pos))
            return 1;
        float supplied = provider.supply(level, pos, need);
        return Float.isNaN(supplied) ? 0 : Mth.clamp(supplied, 0, 1);
    }

    public static void install(LeyPowerProvider provider)
    {
        LeyPower.provider = Objects.requireNonNull(provider);
    }

    public static LeyPowerProvider provider()
    {
        return provider;
    }
}
