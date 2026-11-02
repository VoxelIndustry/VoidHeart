package net.voxelindustry.voidheart.client.curse;

import net.minecraft.util.Mth;

import java.util.Random;

/**
 * Where the sky tears for one curse, worked out from its seed. Everything is in the plane y = 1 above the player: a point (x, z) of it is the direction
 * (x, 1, z), and its distance from the zenith is the tangent of the angle from straight up.
 */
final class RaptureMassShape
{
    private static final int LATTICE = 4096;

    // The ragged inner edge of the flesh, as a tangent: 20 to 35 degrees
    private static final float RIM_MEAN     = 0.52F;
    private static final float RIM_LEAST    = 0.36F;
    private static final float RIM_MOST     = 0.7F;
    private static final float RIM_STRETCH  = 0.13F;
    private static final float RIM_LOBES    = 0.5F;
    private static final float RIM_LOBES_AT = 1.5F;
    private static final float RIM_RAGS     = 0.2F;
    private static final float RIM_RAGS_AT  = 5.5F;

    private static final float OUTER_LEAST = 0.12F;
    private static final float OUTER_REACH = 0.36F;
    private static final float OUTER_AT    = 1.9F;

    private static final float CRACK_REACH     = 1.36F;
    private static final int   CRACK_LEVELS    = 6;
    private static final float CRACK_JAG       = 0.24F;
    private static final float CRACK_JAG_DECAY = 0.74F;
    // A share of the opening, which runs from 0 to 1
    private static final float CRACK_RUN       = 0.12F;
    // Under the flesh as well, so that the gaps between cubes show the dark and not the sky
    private static final float TEAR_UNDER_FLESH = 1.5F;
    private static final float TEAR_LAST        = 0.93F;
    private static final float TEAR_POWER       = 0.82F;
    private static final float TEAR_UNEVEN      = 0.7F;
    private static final float TEAR_UNEVEN_AT   = 5.5F;
    private static final float TEAR_NICKS       = 0.42F;
    private static final float TEAR_NICKS_AT    = 64;

    private static final int   HAIRLINES        = 24;
    private static final int   HAIRLINE_STEPS   = 18;
    private static final float HAIRLINE_STEP    = 0.026F;
    private static final float HAIRLINE_WANDER  = 0.3F;
    private static final float HAIRLINE_ZIG     = 0.85F;
    private static final float HAIRLINE_UNDER   = 0.9F;
    private static final float HAIRLINE_FROM    = 0.42F;
    private static final float HAIRLINE_SPREAD  = 0.42F;
    private static final float HAIRLINE_ALONG   = 0.12F;

    /**
     * One texel to a cell of the sky's pixel grid, reaching this far from the zenith: as far as the tangent of 55 degrees.
     */
    static final int   TEAR_SIZE   = 640;
    static final float CELL        = 0.0045F;
    static final float TEAR_EXTENT = TEAR_SIZE / 2F * CELL;

    private static final int LIP = 1;
    private static final int CORE = 2;

    private final float[] lattice = new float[LATTICE];
    private final Random  random;

    private final float   crackAngle;
    private final float[] crackX;
    private final float[] crackZ;

    RaptureMassShape(long seed)
    {
        random = new Random(seed ^ 0x5ACE5CA9L);
        for (int i = 0; i < LATTICE; i++)
            lattice[i] = random.nextFloat();

        crackAngle = random.nextFloat() * Mth.TWO_PI;
        int points = (1 << CRACK_LEVELS) + 1;
        crackX = new float[points];
        crackZ = new float[points];
        float dirX = Mth.cos(crackAngle);
        float dirZ = Mth.sin(crackAngle);
        crackX[0] = -dirX * CRACK_REACH;
        crackZ[0] = -dirZ * CRACK_REACH;
        crackX[points - 1] = dirX * CRACK_REACH;
        crackZ[points - 1] = dirZ * CRACK_REACH;

        float jag = CRACK_REACH * CRACK_JAG;
        for (int step = points - 1; step > 1; step /= 2)
        {
            for (int i = step / 2; i < points; i += step)
            {
                int before = i - step / 2;
                int after = i + step / 2;
                float offset = (random.nextFloat() * 2 - 1) * jag;
                crackX[i] = (crackX[before] + crackX[after]) / 2 - dirZ * offset;
                crackZ[i] = (crackZ[before] + crackZ[after]) / 2 + dirX * offset;
            }
            jag *= CRACK_JAG_DECAY;
        }
    }

    float crackAngle()
    {
        return crackAngle;
    }

    /**
     * How far from the zenith the flesh begins in this direction.
     */
    float rim(float angle)
    {
        float c = Mth.cos(angle);
        float s = Mth.sin(angle);
        float rim = RIM_MEAN
                + RIM_STRETCH * Mth.cos(2 * (angle - crackAngle))
                + RIM_MEAN * RIM_LOBES * (noise(c * RIM_LOBES_AT + 11, s * RIM_LOBES_AT + 7) - 0.5F)
                + RIM_MEAN * RIM_RAGS * (noise(c * RIM_RAGS_AT + 31, s * RIM_RAGS_AT + 53) - 0.5F);
        return Mth.clamp(rim, RIM_LEAST, RIM_MOST);
    }

    /**
     * How far from the zenith the flesh ends in this direction.
     */
    float outer(float angle)
    {
        float c = Mth.cos(angle);
        float s = Mth.sin(angle);
        return rim(angle) * 1.25F + OUTER_LEAST + OUTER_REACH * noise(c * OUTER_AT + 67, s * OUTER_AT + 89);
    }

    /**
     * The closest point of the crack to (x, z), as x, z and distance into {@code out}.
     */
    void nearestOnCrack(float x, float z, float[] out)
    {
        float best = Float.MAX_VALUE;
        for (int i = 0; i + 1 < crackX.length; i++)
        {
            float ax = crackX[i];
            float az = crackZ[i];
            float bx = crackX[i + 1] - ax;
            float bz = crackZ[i + 1] - az;
            float along = Mth.clamp(((x - ax) * bx + (z - az) * bz) / (bx * bx + bz * bz), 0, 1);
            float px = ax + bx * along;
            float pz = az + bz * along;
            float distance = (x - px) * (x - px) + (z - pz) * (z - pz);
            if (distance < best)
            {
                best = distance;
                out[0] = px;
                out[1] = pz;
            }
        }
        out[2] = (float) Math.sqrt(best);
    }

    /**
     * The opening at which the sky has torn this far, from 0 to 1. Above {@link #TEAR_LAST} it never does. Uneven, so that the tear goes further on one side.
     */
    float tearTime(float x, float z, float crackDistance)
    {
        float angle = (float) Mth.atan2(z, x);
        float rim = rim(angle) * TEAR_UNDER_FLESH;
        float rho = Mth.sqrt(x * x + z * z);
        if (rho > rim)
            return 1;

        float uneven = 1 + TEAR_UNEVEN * (noise(x * TEAR_UNEVEN_AT + 3, z * TEAR_UNEVEN_AT + 17) - 0.5F);
        float nicks = 1 + TEAR_NICKS * (noise(x * TEAR_NICKS_AT + 91, z * TEAR_NICKS_AT + 13) - 0.5F);
        float widening = (float) Math.pow(Math.min(1, crackDistance / rim * uneven * nicks), TEAR_POWER);
        return CRACK_RUN + widening * (TEAR_LAST - CRACK_RUN);
    }

    /**
     * The tear as texels: red is the opening at which it opens, green says whether it is the crack itself, blue when the tear has swallowed that crack and only the gullet shows.
     */
    int[] bakeTear()
    {
        var time = new float[TEAR_SIZE * TEAR_SIZE];
        var mark = new byte[TEAR_SIZE * TEAR_SIZE];
        var nearest = new float[3];
        for (int y = 0; y < TEAR_SIZE; y++)
        {
            float z = (y + 0.5F) * CELL - TEAR_EXTENT;
            for (int x = 0; x < TEAR_SIZE; x++)
            {
                float px = (x + 0.5F) * CELL - TEAR_EXTENT;
                float rho = Mth.sqrt(px * px + z * z);
                if (rho > RIM_MOST * TEAR_UNDER_FLESH)
                {
                    time[y * TEAR_SIZE + x] = 1;
                    continue;
                }
                nearestOnCrack(px, z, nearest);
                time[y * TEAR_SIZE + x] = tearTime(px, z, nearest[2]);
            }
        }

        var torn = time.clone();
        for (int i = 0; i + 1 < crackX.length; i++)
        {
            float at = Math.abs(i / (crackX.length - 1F) * 2 - 1);
            line(time, mark, crackX[i], crackZ[i], crackX[i + 1], crackZ[i + 1], at * at * CRACK_RUN);
        }
        for (int i = 0; i < HAIRLINES; i++)
            hairline(time, mark);

        var texels = new int[TEAR_SIZE * TEAR_SIZE];
        for (int i = 0; i < texels.length; i++)
            texels[i] = 0xFF000000 | Math.round(Mth.clamp(time[i], 0, 1) * 255) << 16 | (mark[i] & 0xFF) * 100 << 8 | Math.round(Mth.clamp(torn[i], 0, 1) * 255);
        return texels;
    }

    private void hairline(float[] time, byte[] mark)
    {
        float angle = random.nextFloat() * Mth.TWO_PI;
        // From under the edge of the flesh, outward
        float rho = outer(angle) * HAIRLINE_UNDER;
        float x = Mth.cos(angle) * rho;
        float z = Mth.sin(angle) * rho;
        float heading = angle + (random.nextFloat() - 0.5F) * 0.8F;
        int zig = random.nextBoolean() ? 1 : -1;
        float from = HAIRLINE_FROM + random.nextFloat() * HAIRLINE_SPREAD;
        int steps = HAIRLINE_STEPS / 2 + random.nextInt(HAIRLINE_STEPS);
        for (int step = 0; step < steps; step++)
        {
            zig = -zig;
            float going = heading + zig * HAIRLINE_ZIG * (0.5F + random.nextFloat());
            heading += (random.nextFloat() - 0.5F) * HAIRLINE_WANDER;
            float length = HAIRLINE_STEP * (0.5F + random.nextFloat());
            float nextX = x + Mth.cos(going) * length;
            float nextZ = z + Mth.sin(going) * length;
            line(time, mark, x, z, nextX, nextZ, Math.min(0.96F, from + step / (float) steps * HAIRLINE_ALONG));
            x = nextX;
            z = nextZ;
        }
    }

    private static void line(float[] time, byte[] mark, float ax, float az, float bx, float bz, float at)
    {
        int steps = Math.max(1, (int) (Math.max(Math.abs(bx - ax), Math.abs(bz - az)) / CELL * 1.5F));
        for (int step = 0; step <= steps; step++)
        {
            float x = Mth.lerp(step / (float) steps, ax, bx);
            float z = Mth.lerp(step / (float) steps, az, bz);
            int cellX = Mth.floor((x + TEAR_EXTENT) / CELL);
            int cellZ = Mth.floor((z + TEAR_EXTENT) / CELL);
            for (int dz = -1; dz <= 1; dz++)
            {
                for (int dx = -1; dx <= 1; dx++)
                    stamp(time, mark, cellX + dx, cellZ + dz, at, dx == 0 && dz == 0 ? CORE : LIP);
            }
        }
    }

    private static void stamp(float[] time, byte[] mark, int x, int z, float at, int kind)
    {
        if (x < 0 || z < 0 || x >= TEAR_SIZE || z >= TEAR_SIZE)
            return;
        int i = z * TEAR_SIZE + x;
        if (kind > mark[i])
            mark[i] = (byte) kind;
        time[i] = Math.min(time[i], at);
    }

    float noise(float x, float y)
    {
        int ix = Mth.floor(x);
        int iy = Mth.floor(y);
        float tx = x - ix;
        float ty = y - iy;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        return Mth.lerp(ty, Mth.lerp(tx, at(ix, iy, 0), at(ix + 1, iy, 0)), Mth.lerp(tx, at(ix, iy + 1, 0), at(ix + 1, iy + 1, 0)));
    }

    float noise(float x, float y, float z)
    {
        int ix = Mth.floor(x);
        int iy = Mth.floor(y);
        int iz = Mth.floor(z);
        float tx = x - ix;
        float ty = y - iy;
        float tz = z - iz;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        tz = tz * tz * (3 - 2 * tz);
        float low = Mth.lerp(ty, Mth.lerp(tx, at(ix, iy, iz), at(ix + 1, iy, iz)), Mth.lerp(tx, at(ix, iy + 1, iz), at(ix + 1, iy + 1, iz)));
        float high = Mth.lerp(ty, Mth.lerp(tx, at(ix, iy, iz + 1), at(ix + 1, iy, iz + 1)), Mth.lerp(tx, at(ix, iy + 1, iz + 1), at(ix + 1, iy + 1, iz + 1)));
        return Mth.lerp(tz, low, high);
    }

    private float at(int x, int y, int z)
    {
        int hash = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        return lattice[(hash ^ hash >>> 13) & (LATTICE - 1)];
    }
}
