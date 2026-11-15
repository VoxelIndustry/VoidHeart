package net.voxelindustry.voidheart.client.render.temple;

import net.minecraft.util.Mth;

/**
 * What the mosaic shows in one frame of its waking, pixel by pixel: which pixels stand out of the wall and how far, which show a glowing seam where something
 * came away, and which take a colour of their own, as an eye looking elsewhere. Pixels it leaves alone are not drawn at all, so the wall shows through as it
 * always does.
 * <p>
 * The flesh swells out as one body under its tesserae. Its mound rises smoothly from nothing at its edge, so it grows out of the picture rather than standing
 * on it, deepest at its middle, the teeth and eyes a little proud of it and the gullet sunk into it. Each tessera keeps its shape and colour, and the grout
 * between them sinks a little and glows, so the body shows through its cracks. Around it, the ground's veins pulse, the halo glows, the brood drips toward the
 * faithful and the faithful turn their eyes on the player.
 */
public final class MosaicPicture
{
    public static final byte NONE = 0;
    /** A pixel of the picture out of the wall by its height. */
    public static final byte TESSERA = 1;
    /** Glowing grout or a groove where something came away. */
    public static final byte SEAM = 2;
    /** A pixel laid flat on the wall, in a colour of its own or another pixel's. */
    public static final byte FLAT = 3;

    // How a closed eye opens, from the middle of its lid
    static final float SHUT_RX = 3.2F;
    static final float SHUT_RY = 1.8F;
    static final float SHUT_DROP = 0.5F;
    private static final int SHUT_RIM = 0x1E080C;

    private static final int SCLERA = 0xCCC096;
    private static final int PUPIL = 0x080604;

    // The mound at full reach, in pixels out of the wall, rising over this many pixels in from the flesh's edge
    static final float DOME_DEPTH = 9F;
    static final float FEATHER = 16F;
    /** Tesserae stand up to half this proud of the mound or sunk into it. */
    static final float JITTER = 0.8F;
    static final float TOOTH_JUT = 2.5F;
    static final float LIP_SWELL = 1F;
    static final float EYE_BULGE = 1.2F;
    static final float GULLET_SHARE = 0.35F;
    /** How much of the mound is left at the far ends of the flesh, against its middle. */
    static final float DOME_ENDS = 0.35F;
    // It spreads from these points over this share of the reach, reaching this far in pixels, and each tessera lags its neighbours by up to this much
    static final int FRONT_SEEDS = 3;
    static final float FRONT_SPAN = 0.55F;
    static final float FRONT_REACH = 130F;
    static final float FRONT_JITTER = 0.08F;
    // Its breath while it stirs, in pixels and radians a tick, with a slower swell over it so no two breaths match
    static final float BREATH = 1.1F;
    static final float BREATH_RATE = 0.07F;
    static final float BREATH_DRIFT = 0.017F;
    /** A tessera rises this far, in pixels, before its grout shows as a seam, which then lies this far under it. */
    static final float JOINT_GAP = 0.35F;
    static final float SEAM_SINK = 0.8F;
    /** The cracks between the flesh's tesserae are its grout's dark, leaking at most this much violet, the dimmest this share of the brightest. */
    private static final int CRACK_DARK = 0x2A0E1C;
    private static final int CRACK_LIT = 0x8A3DF0;
    static final float CRACK_GLOW = 0.25F;
    static final float CRACK_LEAST = 0.2F;
    static final float MIN_LIFT = 0.05F;
    /** How much the seams, irises and halo glow while it only stirs, against when it reaches. */
    static final float SEAM_STIR_GLOW = 0.3F;
    static final float IRIS_STIR_GLOW = 0.35F;
    static final float HALO_STIR_GLOW = 0.4F;

    // The veins' pulses run out from the flesh's middle
    private static final float VEIN_FROM_Y = 20F;
    static final float VEIN_RATE = 0.12F;
    static final float VEIN_WAVE = 0.06F;
    static final int VEIN_SHARPNESS = 4;
    private static final float VEIN_MIN = 0.04F;
    private static final int VEIN_TOP = 0x3E2250;
    private static final int VEIN_FOOT = 0x281634;
    private static final int VEIN_LIT = 0x9A4DFF;

    // The brood slides down at its own pace, up to its own depth, leaving a wet trail
    static final float DRIP_MIN = 3F;
    static final float DRIP_MAX = 9F;
    private static final int DRIP_TRAIL = 0x2A1A22;

    // The faithful open pale eyes one after another while it stirs
    private static final int FAITHFUL_EYES = 0xDCD0AA;
    static final float FAITHFUL_FROM = 0.15F;
    static final float FAITHFUL_SPREAD = 0.7F;
    static final float FAITHFUL_GLOW = 0.45F;

    private final float[] height = new float[MosaicArt.WIDTH * MosaicArt.HEIGHT];
    private final byte[] kind = new byte[MosaicArt.WIDTH * MosaicArt.HEIGHT];
    private final int[] colour = new int[MosaicArt.WIDTH * MosaicArt.HEIGHT];
    private final int[] source = new int[MosaicArt.WIDTH * MosaicArt.HEIGHT];
    private final float[] emission = new float[MosaicArt.WIDTH * MosaicArt.HEIGHT];
    private final int[] drawn = new int[MosaicArt.WIDTH * MosaicArt.HEIGHT];
    private int drawnCount;
    private float seamGlow;

    /**
     * @param wear how each of the 120 tiles fared, as {@link MosaicWall#WHOLE}, {@link MosaicWall#CHIPPED} or {@link MosaicWall#FALLEN}
     * @param time ticks, with the partial one
     * @param seed what keeps one temple's mosaic waking its own way
     */
    public void compute(MosaicArt art, int[] wear, MosaicWake wake, MosaicGaze gaze, MosaicMotion motion, float partialTicks, float time, long seed)
    {
        for (int i = 0; i < drawnCount; i++)
        {
            int index = drawn[i];
            kind[index] = NONE;
            height[index] = 0;
            colour[index] = 0;
            emission[index] = 0;
        }
        drawnCount = 0;

        float stir = wake.stir(partialTicks);
        float reach = wake.reach(partialTicks);
        seamGlow = SEAM_STIR_GLOW * stir + (1 - SEAM_STIR_GLOW) * reach;
        if (stir > 0)
        {
            flesh(art, wear, stir, reach, time, seed, seamGlow);
            for (int tendril = 0; tendril < art.tendrils().size(); tendril++)
            {
                if (MosaicTendrils.rooted(art, wear, tendril))
                    vacate(art, wear, art.tendrilPixels(tendril));
            }
            // The wheels lift off the wall as they turn, and their hub follows once they reach out
            for (int wheel = 0; wheel < art.wheels().size(); wheel++)
                vacate(art, wear, art.wheelPixels(wheel));
            if (reach > 0)
                vacate(art, wear, art.wheelPixels(art.wheels().size()));
            veins(art, wear, stir, time);
            float halo = Math.max(HALO_STIR_GLOW * stir, reach);
            for (int index : art.haloPixels())
            {
                if (shows(art, wear, index))
                    flat(index, 0, index, halo);
            }
            brood(art, wear, stir, motion.drip(partialTicks), seed);
            faithful(art, wear, stir, seed);
        }
        eyes(art, wear, wake, gaze, partialTicks, stir, reach, Math.max(IRIS_STIR_GLOW * stir, reach));
    }

    private void flesh(MosaicArt art, int[] wear, float stir, float reach, float time, long seed, float seamGlow)
    {
        var seedsX = new float[FRONT_SEEDS];
        var seedsY = new float[FRONT_SEEDS];
        for (int i = 0; i < FRONT_SEEDS; i++)
        {
            seedsX[i] = 30 + (float) MosaicArt.hash(i, 11, seed) * 180;
            seedsY[i] = 5 + (art.fleshEdge((int) seedsX[i]) - 5) * (0.2F + 0.6F * (float) MosaicArt.hash(i, 12, seed));
        }
        for (int index : art.fleshPixels())
        {
            if (!shows(art, wear, index))
                continue;
            float lift = lift(art, index, stir, reach, time, seed, seedsX, seedsY);
            if (art.joint(index) && lift > JOINT_GAP)
            {
                // Dark grout, a little violet light leaking through some cracks more than others
                float leak = seamGlow * CRACK_GLOW * (CRACK_LEAST + (1 - CRACK_LEAST) * (float) MosaicArt.hash(art.tessera(index), 5, seed));
                set(index, SEAM, Math.max(0, lift - SEAM_SINK));
                colour[index] = 0xFF000000 | lerpColour(CRACK_DARK, CRACK_LIT, leak);
                emission[index] = leak;
            }
            else if (lift > MIN_LIFT)
                set(index, TESSERA, lift);
        }
    }

    /** @return how far a pixel of the flesh is out of the wall, in pixels, the same over its whole tessera */
    static float lift(MosaicArt art, int index, float stir, float reach, float time, long seed, float[] seedsX, float[] seedsY)
    {
        float x = art.centreX(index);
        float y = art.centreY(index);
        int id = art.tessera(index);
        boolean own = id == MosaicArt.OWN_TESSERA;
        // A tessera of its own follows the mound smoothly, so the lines and eyes laid in them hold together
        float lag = own ? 0.5F : (float) MosaicArt.hash(id, 1, seed);
        float size = own ? 0.5F : (float) MosaicArt.hash(id, 2, seed);

        float across = (x - art.mouthX()) / 118F;
        float middle = (float) Math.sqrt(Math.max(0, 1 - across * across));
        float shape = MosaicWake.ease(art.fleshDepth(x, y), 0, FEATHER) * (DOME_ENDS + (1 - DOME_ENDS) * middle);

        float breath = BREATH * stir * shape * (0.55F + 0.45F * Mth.sin(time * BREATH_RATE - x * 0.035F + lag * 1.2F + 0.8F * Mth.sin(time * BREATH_DRIFT)));
        if (reach <= 0)
            return breath;

        float nearest = Float.MAX_VALUE;
        for (int i = 0; i < seedsX.length; i++)
            nearest = Math.min(nearest, (float) Math.hypot(x - seedsX[i], y - seedsY[i]));
        float arrival = Math.min(1, nearest / FRONT_REACH) * (1 - FRONT_JITTER) + lag * FRONT_JITTER;
        float local = MosaicWake.ease(reach - arrival * FRONT_SPAN, 0, 1 - FRONT_SPAN) * shape;
        float swell = local * (DOME_DEPTH + JITTER * (size - 0.5F));
        switch (art.region(index))
        {
            case MosaicArt.GULLET -> swell *= GULLET_SHARE;
            case MosaicArt.TOOTH -> swell += TOOTH_JUT * local * (0.6F + 0.4F * (float) MosaicArt.hash((long) (x / 5), 3, seed));
            case MosaicArt.LIP -> swell += LIP_SWELL * local;
            case MosaicArt.EYE -> swell += EYE_BULGE * local;
            default ->
            {
            }
        }
        return breath + swell;
    }

    /** Leaves glowing grooves where something lifted off the wall. */
    private void vacate(MosaicArt art, int[] wear, int[] pixels)
    {
        for (int index : pixels)
        {
            if (shows(art, wear, index))
                set(index, SEAM, 0);
        }
    }

    private void veins(MosaicArt art, int[] wear, float stir, float time)
    {
        for (int index : art.veinPixels())
        {
            if (!shows(art, wear, index))
                continue;
            int x = index % MosaicArt.WIDTH;
            int y = index / MosaicArt.WIDTH;
            float distance = (float) Math.hypot(x - art.mouthX(), y - VEIN_FROM_Y);
            float wave = Math.max(0, Mth.sin(time * VEIN_RATE - distance * VEIN_WAVE));
            float pulse = stir * (float) Math.pow(wave, VEIN_SHARPNESS);
            if (pulse < VEIN_MIN)
                continue;
            int base = lerpColour(VEIN_TOP, VEIN_FOOT, y / (float) MosaicArt.HEIGHT);
            flat(index, 0xFF000000 | lerpColour(base, VEIN_LIT, pulse), index, pulse);
        }
    }

    private void brood(MosaicArt art, int[] wear, float stir, float drip, long seed)
    {
        var clumps = art.brood();
        for (int i = 0; i < clumps.length; i++)
        {
            float speed = 0.5F + (float) MosaicArt.hash(i, 31, seed);
            float most = Mth.lerp((float) MosaicArt.hash(i, 32, seed), DRIP_MIN, DRIP_MAX);
            int drop = (int) (Math.min(most, drip * speed) * stir);
            if (drop <= 0)
                continue;
            for (int index : clumps[i])
            {
                int x = index % MosaicArt.WIDTH;
                int y = index / MosaicArt.WIDTH;
                for (int trail = 0; trail < drop && y + trail < MosaicArt.HEIGHT; trail++)
                {
                    int at = MosaicArt.index(x, y + trail);
                    if (shows(art, wear, at) && kind[at] == NONE)
                        flat(at, 0xFF000000 | DRIP_TRAIL, at, 0);
                }
            }
            for (int index : clumps[i])
            {
                int y = index / MosaicArt.WIDTH + drop;
                int at = MosaicArt.index(index % MosaicArt.WIDTH, Math.min(y, MosaicArt.HEIGHT - 1));
                if (y < MosaicArt.HEIGHT && shows(art, wear, at) && shows(art, wear, index))
                    flat(at, 0, index, 0);
            }
        }
    }

    private void faithful(MosaicArt art, int[] wear, float stir, long seed)
    {
        var heads = art.heads();
        for (int i = 0; i < heads.size(); i++)
        {
            float from = FAITHFUL_FROM + FAITHFUL_SPREAD * (float) MosaicArt.hash(i, 41, seed);
            if (stir < from)
                continue;
            var head = heads.get(i);
            int y = Mth.floor(head.y() + 0.5F);
            for (int x = Mth.floor(head.x() - 0.5F); x <= Mth.floor(head.x() + 0.5F); x++)
            {
                int at = MosaicArt.index(x, y);
                if (shows(art, wear, at))
                    flat(at, 0xFF000000 | FAITHFUL_EYES, at, FAITHFUL_GLOW * stir);
            }
        }
    }

    private void eyes(MosaicArt art, int[] wear, MosaicWake wake, MosaicGaze gaze, float partialTicks, float stir, float reach, float irisGlow)
    {
        var eyes = art.eyes();
        int hub = art.wheels().size();
        for (int i = 0; i < eyes.size(); i++)
        {
            var eye = eyes.get(i);
            // The wheels' eyes leave the wall with them, and the great eye with its hub
            if (eye.figure() == MosaicArt.WHEEL_BODY && (eye.part() < hub ? stir > 0 : reach > 0))
                continue;
            if (!gaze.awake(wake, i))
                continue;
            paintEye(art, wear, eye.x(), eye.y(), eye.rx(), eye.ry(), gaze.offsetX(i), gaze.offsetY(i), eye.iris(), eye.irisDark(), -1, i, irisGlow);
        }
        var flesh = eyes.stream().filter(eye -> eye.figure() == MosaicArt.FLESH_BODY).findFirst().orElse(null);
        if (flesh == null)
            return;
        for (int i = 0; i < art.shut().size(); i++)
        {
            float open = gaze.openness(wake, i, partialTicks);
            if (open <= 0)
                continue;
            var shut = art.shut().get(i);
            int eye = eyes.size() + i;
            paintEye(art, wear, shut.x(), shut.y() + SHUT_DROP, SHUT_RX, SHUT_RY * open, gaze.offsetX(eye), gaze.offsetY(eye), flesh.iris(), flesh.irisDark(), SHUT_RIM, -1,
                    irisGlow);
        }
    }

    /**
     * Lays an eye as the script draws it, its iris and slit moved by an offset. With no offset it matches the painted eye pixel for pixel.
     *
     * @param rim     the rim's colour, or -1 to leave the painted rim
     * @param painted the painted eye it moves, so only the pixels the picture shows of it change, or -1 for a new eye
     */
    private void paintEye(MosaicArt art, int[] wear, float cx, float cy, float rx, float ry, float ox, float oy, int iris, int irisDark, int rim, int painted,
                          float irisGlow)
    {
        if (ry < 0.3F)
            return;
        for (int y = Mth.floor(cy - ry - 1); y <= Mth.ceil(cy + ry + 1); y++)
        {
            for (int x = Mth.floor(cx - rx - 1); x <= Mth.ceil(cx + rx + 1); x++)
            {
                if (x < 0 || y < 0 || x >= MosaicArt.WIDTH || y >= MosaicArt.HEIGHT)
                    continue;
                int index = MosaicArt.index(x, y);
                if (!shows(art, wear, index) || painted >= 0 && art.eyeAt(index) != painted)
                    continue;
                float e = sq((x - cx) / rx) + sq((y - cy) / ry);
                int c;
                boolean glows = false;
                if (e < 1)
                {
                    double d = Math.sqrt(sq(x - cx - ox) + sq((y - cy - oy) * 1.2F));
                    float ir = Math.min(rx, ry) * 0.75F;
                    c = SCLERA;
                    if (d < ir)
                    {
                        c = d < ir * 0.65F ? iris : irisDark;
                        glows = true;
                    }
                    if (Math.abs(x - cx - ox) < 0.6F && Math.abs(y - cy - oy) < ry * 0.7F)
                    {
                        c = PUPIL;
                        glows = false;
                    }
                }
                else if (e < 1.5F && rim >= 0)
                    c = rim;
                else
                    continue;
                colour[index] = 0xFF000000 | speckle(c, x, y);
                emission[index] = glows ? irisGlow : 0;
                if (kind[index] == NONE)
                    set(index, FLAT, 0);
            }
        }
    }

    /** A fine pixel's colour as the script lays it, a shade up or down. */
    static int speckle(int rgb, int x, int y)
    {
        double shift = (MosaicArt.hash(x, y, 52) - 0.5) * 8;
        return channel((rgb >> 16 & 0xFF) + shift) << 16 | channel((rgb >> 8 & 0xFF) + shift) << 8 | channel((rgb & 0xFF) + shift);
    }

    private static int channel(double value)
    {
        return Math.clamp((long) Math.floor(value + 0.5), 0, 255);
    }

    static int lerpColour(int from, int to, float t)
    {
        return Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF)) << 16 | Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF)) << 8
                | Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
    }

    private static float sq(float value)
    {
        return value * value;
    }

    /** Whether a pixel is still there to move: not on a fallen tile, nor a chip of bare bed. */
    static boolean shows(MosaicArt art, int[] wear, int index)
    {
        int state = wear[MosaicArt.tileOf(index)];
        return state == MosaicWall.WHOLE || state == MosaicWall.CHIPPED && !art.bare(index);
    }

    private void set(int index, byte kind, float height)
    {
        if (this.kind[index] == NONE)
        {
            drawn[drawnCount++] = index;
            source[index] = index;
        }
        this.kind[index] = kind;
        this.height[index] = height;
    }

    /** Lays a pixel flat on the wall, in its own colour or, with none, in the picture's colour at another pixel. */
    private void flat(int index, int colour, int from, float glow)
    {
        set(index, FLAT, 0);
        this.colour[index] = colour;
        source[index] = from;
        emission[index] = glow;
    }

    public int drawnCount()
    {
        return drawnCount;
    }

    /** The pixels drawn this frame, the flesh first and row by row. */
    public int drawn(int i)
    {
        return drawn[i];
    }

    public byte kind(int index)
    {
        return kind[index];
    }

    public float height(int index)
    {
        return height[index];
    }

    /** @return the pixel's own colour as 0xFFRRGGBB, or 0 when it shows the picture */
    public int colour(int index)
    {
        return colour[index];
    }

    /** @return the pixel of the picture it shows, when it has no colour of its own */
    public int source(int index)
    {
        return source[index];
    }

    /** @return from 0 to 1, how much the pixel lights itself */
    public float emission(int index)
    {
        return emission[index];
    }

    /** @return from 0 to 1, how bright the seams glow */
    public float seamGlow()
    {
        return seamGlow;
    }

    /** The highest any pixel stands out of the wall, for tests. */
    public float highest()
    {
        float most = 0;
        for (int i = 0; i < drawnCount; i++)
            most = Math.max(most, height[drawn[i]]);
        return most;
    }
}
