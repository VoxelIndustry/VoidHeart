package net.voxelindustry.voidheart.common.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * Whatever carries void energy between sources, stores and machines. The pylon network installs itself through {@link VoidEnergy#install}.
 * Amounts are void energy, rates are per tick.
 */
public interface VoidNetworkProvider
{
    /**
     * No network at all, so only the creative void well gives or takes energy.
     */
    VoidNetworkProvider NONE = new VoidNetworkProvider()
    {
        @Override
        public long available(ServerLevel level, BlockPos pos)
        {
            return 0;
        }

        @Override
        public long draw(ServerLevel level, BlockPos pos, long amount)
        {
            return 0;
        }

        @Override
        public long offer(ServerLevel level, BlockPos pos, long amount)
        {
            return 0;
        }
    };

    /**
     * What could be drawn at pos this tick, summed over every field covering it.
     */
    long available(ServerLevel level, BlockPos pos);

    /**
     * Takes up to amount from the fields covering pos, and returns what was taken.
     */
    long draw(ServerLevel level, BlockPos pos, long amount);

    /**
     * A source at pos pushes up to amount into the network it touches, and gets back what was accepted.
     */
    long offer(ServerLevel level, BlockPos pos, long amount);

    /**
     * Names what a draw at pos takes from besides what waits there for it alone. Draws at two positions whose names don't meet never take from each
     * other. By default every draw in a level may.
     */
    default Set<Object> drawsFrom(ServerLevel level, BlockPos pos)
    {
        return Set.of(level);
    }

    /**
     * What of {@link #available} only a draw at pos can take, such as energy already delivered ahead for it. None by default.
     */
    default long exclusive(ServerLevel level, BlockPos pos)
    {
        return 0;
    }
}
