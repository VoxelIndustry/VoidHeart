package net.voxelindustry.voidheart.common.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import java.util.Objects;

/**
 * The seep around Void Fissures and void wounds. Wards weaken by it, and corruption rises faster inside it.
 */
public final class VoidSeep
{
    private static VoidSeepProvider provider = VoidSeepProvider.NONE;

    private VoidSeep()
    {
    }

    /**
     * 0 outside every seep, up to 1 at the heart of one.
     */
    public static float strengthAt(ServerLevel level, BlockPos pos)
    {
        float strength = provider.strengthAt(level, pos);
        return Float.isNaN(strength) ? 0 : Mth.clamp(strength, 0, 1);
    }

    public static void install(VoidSeepProvider provider)
    {
        VoidSeep.provider = Objects.requireNonNull(provider);
    }

    public static VoidSeepProvider provider()
    {
        return provider;
    }
}
