package net.voxelindustry.voidheart.common.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Opens void wounds, which seep like a Void Fissure but give no power. The fissures install the real ones through {@link VoidWounds#install}.
 */
@FunctionalInterface
public interface VoidWoundsProvider
{
    VoidWoundsProvider NONE = (level, pos, size) ->
    {
    };

    void open(ServerLevel level, BlockPos pos, float size);
}
