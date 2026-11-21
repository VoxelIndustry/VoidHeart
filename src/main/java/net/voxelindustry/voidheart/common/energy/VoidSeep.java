package net.voxelindustry.voidheart.common.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The seep around Void Fissures and void wounds. Wards weaken by it, and corruption rises faster inside it.
 */
public final class VoidSeep
{
    private static VoidSeepProvider provider = VoidSeepProvider.NONE;

    /**
     * Something other than a fissure that seeps a little: it weakens wards and quickens corruption like any seep, but brings nothing through and taints no
     * line.
     */
    public interface Leak
    {
        /**
         * Strength at that point, 0 outside it.
         */
        float leakAt(double x, double y, double z);

        boolean isRemoved();
    }

    private static final Map<ServerLevel, Set<Leak>> LEAKS = new WeakHashMap<>();

    private VoidSeep()
    {
    }

    public static void addLeak(ServerLevel level, Leak leak)
    {
        LEAKS.computeIfAbsent(level, key -> Collections.newSetFromMap(new WeakHashMap<>())).add(leak);
    }

    public static void removeLeak(ServerLevel level, Leak leak)
    {
        var leaks = LEAKS.get(level);
        if (leaks != null)
            leaks.remove(leak);
    }

    /**
     * Every leak at that point, combined as overlapping seeps are. For the seep provider to fold in with the fissures'.
     */
    public static float leaksAt(ServerLevel level, double x, double y, double z, java.util.function.BinaryOperator<Float> combine)
    {
        var leaks = LEAKS.get(level);
        if (leaks == null || leaks.isEmpty())
            return 0;
        float strength = 0;
        for (var leak : leaks)
        {
            if (!leak.isRemoved())
                strength = combine.apply(strength, leak.leakAt(x, y, z));
        }
        return strength;
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
