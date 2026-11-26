package net.voxelindustry.voidheart.client.render.heart;

import net.voxelindustry.voidheart.common.content.heart.HeartBeat;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.client.render.heart.HeartShape.SIZE;

/**
 * Pale motes of marrow drifting off a heart with Marrow: each beat frees one from a crack on the heart's top, which rises slowly, sways and fades, with a
 * short tail of fainter pixels behind it. Worked out from the time and the owner's seed alone, so nothing is kept between frames.
 */
public final class HeartMotes
{
    /** How long a mote drifts, how high it rises in blocks, and how far it sways and leans outward on the way. */
    public static final float LIFE_TICKS = 90;
    private static final float RISE      = 0.45F;
    private static final float SWAY      = 1.5F / 16;
    private static final float SWAY_RATE = 0.07F;
    private static final float LEAN      = 2.5F / 16;
    /** It shows over the first ticks and fades over the last, in a few flat steps. */
    private static final float FADE_IN   = 8;
    private static final float FADE_OUT  = 35;
    private static final float STEPS     = 8;
    /** Its tail: the alpha of each pixel behind its head, one pixel apart. */
    private static final float[] TAIL = { 0.55F, 0.3F, 0.15F };

    /** A pixel of a mote, in blocks from the block's corner. */
    public record Speck(float x, float y, float z, float alpha)
    {
    }

    private HeartMotes()
    {
    }

    /** The pixels a mote leaves from: the tops of the heart's pixels, open to the sky, away from its vessels. */
    public static List<int[]> origins(boolean[][][] flesh)
    {
        var origins = new ArrayList<int[]>();
        for (int x = 0; x < SIZE; x++)
        {
            for (int z = 0; z < SIZE; z++)
            {
                for (int y = SIZE - 1; y >= 0; y--)
                {
                    if (!flesh[x][y][z])
                        continue;
                    // The vessels rise above the body, keep to the shoulders around them
                    if (y < 12)
                        origins.add(new int[] { x, y + 1, z });
                    break;
                }
            }
        }
        return origins;
    }

    /** Every mote pixel showing at this time, heads and tails. */
    public static List<Speck> at(float time, int seed, float period, List<int[]> origins)
    {
        var specks = new ArrayList<Speck>();
        if (origins.isEmpty())
            return specks;
        int latest = HeartBeat.latest(time, seed, period);
        int alive = (int) Math.ceil(LIFE_TICKS / period) + 1;
        for (int beat = latest; beat > latest - alive; beat--)
        {
            float age = time - HeartBeat.start(beat, seed, period);
            if (age < 0 || age >= LIFE_TICKS)
                continue;
            int hash = hash(beat, seed);
            var origin = origins.get(Math.floorMod(hash, origins.size()));
            float phase = (hash >>> 8 & 0xFF) / 255F * 2 * (float) Math.PI;
            float fade = fade(age);
            if (fade <= 0)
                continue;

            float[] head = position(origin, age, phase);
            specks.add(new Speck(head[0], head[1], head[2], fade));
            // The tail trails back down the way it came, a pixel apart however slow it drifts
            float[] before = position(origin, Math.max(0, age - 4), phase);
            float dx = head[0] - before[0], dy = head[1] - before[1], dz = head[2] - before[2];
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1e-5F)
                continue;
            for (int i = 0; i < TAIL.length; i++)
            {
                float back = (i + 1) / 16F / length;
                float alpha = step(fade * TAIL[i]);
                if (alpha > 0)
                    specks.add(new Speck(head[0] - dx * back, head[1] - dy * back, head[2] - dz * back, alpha));
            }
        }
        return specks;
    }

    /** Where a mote is this many ticks after leaving its pixel. */
    static float[] position(int[] origin, float age, float phase)
    {
        float u = age / LIFE_TICKS;
        float ox = (origin[0] + 0.5F) / 16, oy = origin[1] / 16F + 0.5F / 16, oz = (origin[2] + 0.5F) / 16;
        float outX = ox - 0.5F, outZ = oz - 0.5F;
        float out = (float) Math.sqrt(outX * outX + outZ * outZ);
        float lean = LEAN * u / Math.max(out, 0.05F);
        float sway = SWAY * (float) Math.sin(phase + age * SWAY_RATE) * u;
        return new float[] {
                ox + outX * lean + sway * (float) Math.cos(phase),
                oy + RISE * (1 - (1 - u) * (1 - u)) * 0.5F + RISE * u * 0.5F,
                oz + outZ * lean + sway * (float) Math.sin(phase)
        };
    }

    private static float fade(float age)
    {
        float in = Math.min(1, age / FADE_IN);
        float out = Math.min(1, (LIFE_TICKS - age) / FADE_OUT);
        return step(Math.min(in, out));
    }

    private static float step(float alpha)
    {
        return Math.round(alpha * STEPS) / STEPS;
    }

    private static int hash(int beat, int seed)
    {
        int h = beat * 0x9E3779B9 ^ seed * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 13;
        return h;
    }
}
