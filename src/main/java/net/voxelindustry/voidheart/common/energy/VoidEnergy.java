package net.voxelindustry.voidheart.common.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.common.energy.creative.CreativeSources;

import java.util.Objects;

/**
 * The one way machines and sources touch void energy. Amounts are void energy, rates are per tick.
 * <p>
 * Creative void wells answer first, ahead of whichever network is installed, so tests can rely on them before and after the pylons land.
 */
public final class VoidEnergy
{
    private static VoidNetworkProvider provider = VoidNetworkProvider.NONE;

    private VoidEnergy()
    {
    }

    /**
     * What could be drawn at pos this tick, summed over every field covering it. A creative well makes it endless.
     */
    public static long available(ServerLevel level, BlockPos pos)
    {
        if (CreativeSources.wells(level).covers(pos))
            return Long.MAX_VALUE;
        return Math.max(0, provider.available(level, pos));
    }

    /**
     * Takes up to amount from the fields covering pos, and returns what was taken.
     */
    public static long draw(ServerLevel level, BlockPos pos, long amount)
    {
        if (amount <= 0)
            return 0;
        if (CreativeSources.wells(level).covers(pos))
            return amount;
        return Math.clamp(provider.draw(level, pos, amount), 0, amount);
    }

    /**
     * A source at pos pushes up to amount into the network it touches, and gets back what was accepted. A creative well takes any offer.
     */
    public static long offer(ServerLevel level, BlockPos pos, long amount)
    {
        if (amount <= 0)
            return 0;
        if (CreativeSources.wells(level).covers(pos))
            return amount;
        return Math.clamp(provider.offer(level, pos, amount), 0, amount);
    }

    public static void install(VoidNetworkProvider provider)
    {
        VoidEnergy.provider = Objects.requireNonNull(provider);
    }

    public static VoidNetworkProvider provider()
    {
        return provider;
    }
}
