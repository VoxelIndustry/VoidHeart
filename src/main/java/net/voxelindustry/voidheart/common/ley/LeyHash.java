package net.voxelindustry.voidheart.common.ley;

public final class LeyHash
{
    private LeyHash()
    {
    }

    public static long hash(long seed, long a, long b, long salt)
    {
        long h = mix(seed ^ 0x9E3779B97F4A7C15L * (salt + 1));
        h = mix(h ^ a * 0xC2B2AE3D27D4EB4FL);
        return mix(h ^ b * 0x165667B19E3779F9L);
    }

    public static float unit(long hash)
    {
        return (hash >>> 40) / (float) (1L << 24);
    }

    /**
     * Smooth value noise in [0, 1], summed over two octaves.
     */
    public static float valueNoise(long seed, double x, double z, double scale)
    {
        float base = valueOctave(seed, x / scale, z / scale);
        float detail = valueOctave(seed + 1, x / (scale / 3), z / (scale / 3));
        return (base + detail * 0.35F) / 1.35F;
    }

    private static float valueOctave(long seed, double x, double z)
    {
        long x0 = (long) Math.floor(x);
        long z0 = (long) Math.floor(z);
        float tx = smooth((float) (x - x0));
        float tz = smooth((float) (z - z0));

        float v00 = unit(hash(seed, x0, z0, 7));
        float v10 = unit(hash(seed, x0 + 1, z0, 7));
        float v01 = unit(hash(seed, x0, z0 + 1, 7));
        float v11 = unit(hash(seed, x0 + 1, z0 + 1, 7));

        float top = v00 + (v10 - v00) * tx;
        float bottom = v01 + (v11 - v01) * tx;
        return top + (bottom - top) * tz;
    }

    private static float smooth(float t)
    {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static long mix(long z)
    {
        z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
        z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
        return z ^ z >>> 31;
    }
}
