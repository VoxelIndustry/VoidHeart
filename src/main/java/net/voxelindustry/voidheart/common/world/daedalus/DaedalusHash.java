package net.voxelindustry.voidheart.common.world.daedalus;

final class DaedalusHash
{
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;
    private static final long MIX_A = 0xBF58476D1CE4E5B9L;
    private static final long MIX_B = 0x94D049BB133111EBL;

    private DaedalusHash()
    {
    }

    /** SplitMix64 finalizer. Worlds depend on its exact results, so it must never change. */
    static long mix(long value)
    {
        long mixed = value + GOLDEN;
        mixed = (mixed ^ (mixed >>> 30)) * MIX_A;
        mixed = (mixed ^ (mixed >>> 27)) * MIX_B;
        return mixed ^ (mixed >>> 31);
    }

    static long mix(long seed, long a, long b, long salt)
    {
        return mix(mix(mix(seed ^ salt) + a) + b);
    }

    static int pick(long hash, int count)
    {
        return (int) Long.remainderUnsigned(hash, count);
    }
}
