package net.voxelindustry.voidheart.common.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * How strongly the void seeps in around tears and wounds. The tears install themselves through {@link VoidSeep#install}.
 */
@FunctionalInterface
public interface VoidSeepProvider
{
    VoidSeepProvider NONE = (level, pos) -> 0;

    /**
     * 0 outside every seep, up to 1 at the heart of one.
     */
    float strengthAt(ServerLevel level, BlockPos pos);
}
