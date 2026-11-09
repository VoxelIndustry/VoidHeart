package net.voxelindustry.voidheart.common.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Where machines get their ley. Standing stones and their spurs install themselves through {@link LeyPower#install}.
 */
@FunctionalInterface
public interface LeyPowerProvider
{
    /**
     * Nothing supplies ley, so only the creative ley stone powers anything.
     */
    LeyPowerProvider NONE = (level, pos, need) -> 0;

    /**
     * The fraction of need met at pos this tick, from 0 to 1.
     */
    float supply(ServerLevel level, BlockPos pos, double need);
}
