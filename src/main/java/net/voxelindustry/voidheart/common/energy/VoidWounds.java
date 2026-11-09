package net.voxelindustry.voidheart.common.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.VoidHeart;

import java.util.Objects;

/**
 * Wounds left where something failed, such as a reactor. Until the tears install theirs, opening one only leaves a line in the log.
 */
public final class VoidWounds
{
    private static VoidWoundsProvider provider = VoidWoundsProvider.NONE;

    private VoidWounds()
    {
    }

    public static void open(ServerLevel level, BlockPos pos, float size)
    {
        if (provider == VoidWoundsProvider.NONE)
            VoidHeart.LOGGER.info("A void wound of size " + size + " would open at " + pos.toShortString() + " in " + level.dimension().identifier());
        provider.open(level, pos, size);
    }

    public static void install(VoidWoundsProvider provider)
    {
        VoidWounds.provider = Objects.requireNonNull(provider);
    }

    public static VoidWoundsProvider provider()
    {
        return provider;
    }
}
