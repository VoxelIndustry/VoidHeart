package net.voxelindustry.voidheart.client.render.temple;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntBinaryOperator;
import java.util.function.IntPredicate;

/**
 * The temple mosaic as its waking needs to know it, written by {@code .claude/tools/temple_mosaic.lua} with the tiles: which tessera, region and figure each
 * pixel of the picture belongs to, and where its eyes, tendrils, wheels and the rest are. Pixels are counted from the picture's top left as someone facing it
 * sees it, row by row.
 */
public final class MosaicArt
{
    public static final int WIDTH = 240;
    public static final int HEIGHT = 128;
    public static final int TILE = 16;
    public static final int COLUMNS = WIDTH / TILE;
    /** The tessera of a pixel laid on its own, as lines and small details are. */
    public static final int OWN_TESSERA = 0xFFFF;

    // Regions, numbered as the script numbers them
    public static final int FLESH = 3;
    public static final int GULLET = 4;
    public static final int TOOTH = 5;
    public static final int EYE = 6;
    public static final int LIP = 7;
    public static final int RING = 8;
    public static final int VEIL = 16;
    public static final int HALO = 17;
    public static final int VEIN = 18;
    public static final int BROOD = 20;

    // Figures, numbered as the script numbers them
    public static final int NOBODY = 0;
    public static final int FLESH_BODY = 1;
    public static final int TENDRIL_BODY = 2;
    public static final int ANGEL_BODY = 3;
    public static final int WHEEL_BODY = 4;
    public static final int VEILED_BODY = 5;
    public static final int WORLD_BODY = 6;
    public static final int FAITHFUL_BODY = 7;
    public static final int BORDER_BODY = 8;
    public static final int ALTAR_BODY = 9;

    /** Of the veiled one, the parts the script numbers: its halo, its veil, and the dark under its hood. */
    public static final int HALO_PART = 0;
    public static final int HOOD_PART = 2;
    /** How far above and below its head's middle the hood reaches, in pixels: down to where its shoulders start. */
    private static final int HOOD_ABOVE = 10;
    private static final int HOOD_BELOW = 5;

    /** An almond eye as the script draws it, its colours as 0xRRGGBB, on the figure and part it was drawn for. */
    public record Eye(float x, float y, float rx, float ry, int iris, int irisDark, int rim, int figure, int part)
    {
    }

    /** A tendril's painted path from its root at the flesh to its tip, one point a row, with the half width drawn there. */
    public record Tendril(int[] x, int[] y, int[] halfWidth)
    {
        public int length()
        {
            return x.length;
        }
    }

    /** A closed eye, its lid's middle. */
    public record Shut(int x, int y)
    {
    }

    /** One of the wheels around the angel's great eye: an ellipse turned by a tilt in degrees, with eyes set round it. */
    public record Wheel(float rx, float ry, float tilt, int eyes)
    {
    }

    /** One of the faithful kneeling along the foot: the middle of its head, and which way it faces, 1 for right. */
    public record Head(float x, float y, int facing)
    {
    }

    private final int[] tessera;
    private final byte[] region;
    private final boolean[] joint;
    private final boolean[] bare;
    private final byte[] figure;
    private final byte[] part;
    private final float[] tesseraX;
    private final float[] tesseraY;
    private final float[] fleshEdge;
    private final float[] fleshDepth;
    private final int[] fleshPixels;
    private final int[][] tendrilPixels;
    private final int[][] wheelPixels;
    private final int[] veinPixels;
    private final int[] haloPixels;
    private final int[] hoodPixels;
    private final int[][] brood;
    private final int[] eyeAt;
    private final List<Eye> eyes;
    private final List<Shut> shut;
    private final List<Tendril> tendrils;
    private final List<Wheel> wheels;
    private final List<Head> heads;
    private final int mouthX;
    private final int hubX;
    private final int hubY;
    private final int veiledX;
    private final int veiledHead;

    private MosaicArt(int[] tessera, byte[] region, boolean[] joint, boolean[] bare, byte[] figure, byte[] part, float[] fleshEdge, List<Eye> eyes, List<Shut> shut,
                      List<Tendril> tendrils, List<Wheel> wheels, List<Head> heads, int mouthX, int hubX, int hubY, int veiledX, int veiledHead)
    {
        this.tessera = tessera;
        this.region = region;
        this.joint = joint;
        this.bare = bare;
        this.figure = figure;
        this.part = part;
        this.fleshEdge = fleshEdge;
        this.eyes = eyes;
        this.shut = shut;
        this.tendrils = tendrils;
        this.wheels = wheels;
        this.heads = heads;
        this.mouthX = mouthX;
        this.hubX = hubX;
        this.hubY = hubY;
        this.veiledX = veiledX;
        this.veiledHead = veiledHead;

        int most = 0;
        for (int id : tessera)
        {
            if (id != OWN_TESSERA)
                most = Math.max(most, id);
        }
        tesseraX = new float[most + 1];
        tesseraY = new float[most + 1];
        var count = new int[most + 1];
        for (int index = 0; index < WIDTH * HEIGHT; index++)
        {
            int id = tessera[index];
            if (id != OWN_TESSERA)
            {
                tesseraX[id] += index % WIDTH + 0.5F;
                tesseraY[id] += index / WIDTH + 0.5F;
                count[id]++;
            }
        }
        for (int id = 0; id <= most; id++)
        {
            if (count[id] > 0)
            {
                tesseraX[id] /= count[id];
                tesseraY[id] /= count[id];
            }
        }

        fleshPixels = pixels(index -> figure[index] == FLESH_BODY);
        tendrilPixels = new int[tendrils.size()][];
        for (int i = 0; i < tendrils.size(); i++)
        {
            int tendril = i;
            tendrilPixels[i] = pixels(index -> figure[index] == TENDRIL_BODY && part[index] == tendril);
        }
        wheelPixels = new int[wheels.size() + 1][];
        for (int i = 0; i <= wheels.size(); i++)
        {
            int wheel = i;
            wheelPixels[i] = pixels(index -> figure[index] == WHEEL_BODY && part[index] == wheel);
        }
        veinPixels = pixels(index -> region[index] == VEIN && figure[index] == NOBODY);
        haloPixels = pixels(index -> figure[index] == VEILED_BODY && part[index] == HALO_PART);
        hoodPixels = pixels(index -> figure[index] == VEILED_BODY && part[index] != HALO_PART
                && index / WIDTH >= veiledHead - HOOD_ABOVE && index / WIDTH < veiledHead + HOOD_BELOW);
        brood = clumps(pixels(index -> region[index] == BROOD));
        fleshDepth = depthInside(index -> figure[index] == FLESH_BODY);

        // Eyes painted later cover earlier ones where they meet, as on the wall
        eyeAt = new int[WIDTH * HEIGHT];
        Arrays.fill(eyeAt, -1);
        for (int i = 0; i < eyes.size(); i++)
        {
            var eye = eyes.get(i);
            for (int y = (int) Math.floor(eye.y() - eye.ry() - 1); y <= Math.ceil(eye.y() + eye.ry() + 1); y++)
            {
                for (int x = (int) Math.floor(eye.x() - eye.rx() - 1); x <= Math.ceil(eye.x() + eye.rx() + 1); x++)
                {
                    float e = (x - eye.x()) / eye.rx() * ((x - eye.x()) / eye.rx()) + (y - eye.y()) / eye.ry() * ((y - eye.y()) / eye.ry());
                    if (x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && e < 1.5F)
                        eyeAt[index(x, y)] = i;
                }
            }
        }
        for (int index = 0; index < WIDTH * HEIGHT; index++)
        {
            if (region[index] != EYE)
                eyeAt[index] = -1;
        }
    }

    private static int[] pixels(IntPredicate keep)
    {
        var found = new int[WIDTH * HEIGHT];
        int count = 0;
        for (int index = 0; index < WIDTH * HEIGHT; index++)
        {
            if (keep.test(index))
                found[count++] = index;
        }
        return Arrays.copyOf(found, count);
    }

    /** Groups pixels into the clumps they touch in, each listed top row first. */
    private static int[][] clumps(int[] pixels)
    {
        var in = new boolean[WIDTH * HEIGHT];
        for (int index : pixels)
            in[index] = true;
        var seen = new boolean[WIDTH * HEIGHT];
        var clumps = new ArrayList<int[]>();
        var stack = new int[pixels.length];
        for (int start : pixels)
        {
            if (seen[start])
                continue;
            var clump = new ArrayList<Integer>();
            int top = 0;
            stack[top++] = start;
            seen[start] = true;
            while (top > 0)
            {
                int index = stack[--top];
                clump.add(index);
                int x = index % WIDTH;
                int y = index / WIDTH;
                int[][] around = {{x - 1, y}, {x + 1, y}, {x, y - 1}, {x, y + 1}};
                for (var next : around)
                {
                    if (next[0] < 0 || next[1] < 0 || next[0] >= WIDTH || next[1] >= HEIGHT)
                        continue;
                    int other = index(next[0], next[1]);
                    if (in[other] && !seen[other])
                    {
                        seen[other] = true;
                        stack[top++] = other;
                    }
                }
            }
            clumps.add(clump.stream().mapToInt(Integer::intValue).sorted().toArray());
        }
        return clumps.toArray(new int[0][]);
    }

    /** How far each pixel inside a shape is from its edge, in pixels, by two sweeps of a chamfer; the picture's own edge counts as the shape's. */
    private static float[] depthInside(IntPredicate inside)
    {
        final float diagonal = 1.4142F;
        var depth = new float[WIDTH * HEIGHT];
        for (int index = 0; index < WIDTH * HEIGHT; index++)
            depth[index] = inside.test(index) ? Float.MAX_VALUE : 0;
        for (int y = 0; y < HEIGHT; y++)
        {
            for (int x = 0; x < WIDTH; x++)
            {
                int index = index(x, y);
                if (depth[index] == 0)
                    continue;
                float best = depth[index];
                best = Math.min(best, (x > 0 ? depth[index - 1] : 0) + 1);
                best = Math.min(best, (y > 0 ? depth[index - WIDTH] : 0) + 1);
                best = Math.min(best, (x > 0 && y > 0 ? depth[index - WIDTH - 1] : 0) + diagonal);
                best = Math.min(best, (x < WIDTH - 1 && y > 0 ? depth[index - WIDTH + 1] : 0) + diagonal);
                depth[index] = best;
            }
        }
        for (int y = HEIGHT - 1; y >= 0; y--)
        {
            for (int x = WIDTH - 1; x >= 0; x--)
            {
                int index = index(x, y);
                if (depth[index] == 0)
                    continue;
                float best = depth[index];
                best = Math.min(best, (x < WIDTH - 1 ? depth[index + 1] : 0) + 1);
                best = Math.min(best, (y < HEIGHT - 1 ? depth[index + WIDTH] : 0) + 1);
                best = Math.min(best, (x < WIDTH - 1 && y < HEIGHT - 1 ? depth[index + WIDTH + 1] : 0) + diagonal);
                best = Math.min(best, (x > 0 && y < HEIGHT - 1 ? depth[index + WIDTH - 1] : 0) + diagonal);
                depth[index] = best;
            }
        }
        return depth;
    }

    /**
     * @param map  the map image's colour at a pixel, as 0xRRGGBB: over the picture's height each pixel's tessera in red and green and its region and flags in blue, its figure and part under it
     * @param json the features file
     */
    public static MosaicArt parse(IntBinaryOperator map, String json)
    {
        var tessera = new int[WIDTH * HEIGHT];
        var region = new byte[WIDTH * HEIGHT];
        var joint = new boolean[WIDTH * HEIGHT];
        var bare = new boolean[WIDTH * HEIGHT];
        var figure = new byte[WIDTH * HEIGHT];
        var part = new byte[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++)
        {
            for (int x = 0; x < WIDTH; x++)
            {
                int index = y * WIDTH + x;
                int top = map.applyAsInt(x, y);
                int under = map.applyAsInt(x, y + HEIGHT);
                tessera[index] = top >> 8 & 0xFFFF;
                int flags = top & 0xFF;
                region[index] = (byte) (flags & 63);
                joint[index] = (flags & 64) != 0;
                bare[index] = (flags & 128) != 0;
                figure[index] = (byte) (under >> 16 & 0xFF);
                part[index] = (byte) (under >> 8 & 0xFF);
            }
        }

        var root = JsonParser.parseString(json).getAsJsonObject();
        var edges = root.getAsJsonArray("fleshEdge");
        var fleshEdge = new float[WIDTH];
        for (int x = 0; x < WIDTH; x++)
            fleshEdge[x] = edges.get(x).getAsFloat();
        var eyes = new ArrayList<Eye>();
        for (var element : root.getAsJsonArray("eyes"))
        {
            var eye = element.getAsJsonObject();
            eyes.add(new Eye(eye.get("x").getAsFloat(), eye.get("y").getAsFloat(), eye.get("rx").getAsFloat(), eye.get("ry").getAsFloat(),
                    rgb(eye.getAsJsonArray("iris")), rgb(eye.getAsJsonArray("irisDark")), rgb(eye.getAsJsonArray("rim")), eye.get("body").getAsInt(), eye.get("part").getAsInt()));
        }
        var shut = new ArrayList<Shut>();
        for (var element : root.getAsJsonArray("shut"))
            shut.add(new Shut(element.getAsJsonObject().get("x").getAsInt(), element.getAsJsonObject().get("y").getAsInt()));
        var tendrils = new ArrayList<Tendril>();
        for (var element : root.getAsJsonArray("tendrils"))
        {
            var points = element.getAsJsonArray();
            var xs = new int[points.size()];
            var ys = new int[points.size()];
            var widths = new int[points.size()];
            for (int i = 0; i < points.size(); i++)
            {
                var point = points.get(i).getAsJsonArray();
                xs[i] = point.get(0).getAsInt();
                ys[i] = point.get(1).getAsInt();
                widths[i] = point.get(2).getAsInt();
            }
            tendrils.add(new Tendril(xs, ys, widths));
        }
        var wheels = new ArrayList<Wheel>();
        for (var element : root.getAsJsonArray("wheels"))
        {
            var wheel = element.getAsJsonObject();
            wheels.add(new Wheel(wheel.get("rx").getAsFloat(), wheel.get("ry").getAsFloat(), wheel.get("tilt").getAsFloat(), wheel.get("eyes").getAsInt()));
        }
        var heads = new ArrayList<Head>();
        for (var element : root.getAsJsonArray("heads"))
        {
            var head = element.getAsJsonObject();
            heads.add(new Head(head.get("x").getAsFloat(), head.get("y").getAsFloat(), head.get("facing").getAsInt()));
        }
        var mouth = root.getAsJsonObject("mouth");
        var hub = root.getAsJsonObject("hub");
        var veiled = root.getAsJsonObject("veiled");
        return new MosaicArt(tessera, region, joint, bare, figure, part, fleshEdge, List.copyOf(eyes), List.copyOf(shut), List.copyOf(tendrils), List.copyOf(wheels),
                List.copyOf(heads), mouth.get("x").getAsInt(), hub.get("x").getAsInt(), hub.get("y").getAsInt(), veiled.get("x").getAsInt(), veiled.get("head").getAsInt());
    }

    private static int rgb(JsonArray colour)
    {
        return colour.get(0).getAsInt() << 16 | colour.get(1).getAsInt() << 8 | colour.get(2).getAsInt();
    }

    /** The script's own hash, so colours worked out here match the painted ones to the unit. @return a value from 0 to 1 */
    public static double hash(long x, long y, long seed)
    {
        long n = x * 73856093L ^ y * 19349663L ^ seed * 83492791L;
        n = (n ^ n >>> 13) * 1274126177L;
        return ((n ^ n >>> 16) & 0xFFFF) / (double) 0xFFFF;
    }

    public static int index(int x, int y)
    {
        return y * WIDTH + x;
    }

    /** @return which of the 120 blocks of the mosaic a pixel is on, counted row by row from the top left */
    public static int tileOf(int index)
    {
        return index / WIDTH / TILE * COLUMNS + index % WIDTH / TILE;
    }

    public int tessera(int index)
    {
        return tessera[index];
    }

    public int region(int index)
    {
        return region[index];
    }

    /** Whether a pixel is grout on the right or lower side of its tessera. */
    public boolean joint(int index)
    {
        return joint[index];
    }

    /** Whether a chipped tile shows its bare bed at a pixel. */
    public boolean bare(int index)
    {
        return bare[index];
    }

    public int figure(int index)
    {
        return figure[index];
    }

    public int part(int index)
    {
        return part[index];
    }

    /** The middle of a pixel's tessera, or of the pixel itself when it is a tessera of its own. */
    public float centreX(int index)
    {
        int id = tessera[index];
        return id == OWN_TESSERA ? index % WIDTH + 0.5F : tesseraX[id];
    }

    public float centreY(int index)
    {
        int id = tessera[index];
        return id == OWN_TESSERA ? index / WIDTH + 0.5F : tesseraY[id];
    }

    /** @return how far down the flesh hangs at a column, in pixels */
    public float fleshEdge(int x)
    {
        return fleshEdge[Math.clamp(x, 0, WIDTH - 1)];
    }

    /** @return how far a place is inside the flesh from its edge, in pixels, or 0 outside it */
    public float fleshDepth(float x, float y)
    {
        return fleshDepth[index(Math.clamp((int) x, 0, WIDTH - 1), Math.clamp((int) y, 0, HEIGHT - 1))];
    }

    /** Every pixel of the flesh across the top, row by row. */
    public int[] fleshPixels()
    {
        return fleshPixels;
    }

    /** Every pixel a tendril was painted on. */
    public int[] tendrilPixels(int tendril)
    {
        return tendrilPixels[tendril];
    }

    /** Every pixel of a wheel and the eyes on it, or past the last wheel those of the great eye at their hub. */
    public int[] wheelPixels(int wheel)
    {
        return wheelPixels[wheel];
    }

    /** The veins run through the ground, row by row. */
    public int[] veinPixels()
    {
        return veinPixels;
    }

    public int[] haloPixels()
    {
        return haloPixels;
    }

    /** The veiled one's hood, from above it down to its shoulders, row by row. */
    public int[] hoodPixels()
    {
        return hoodPixels;
    }

    /** The angel's brood, each clump that drips as one. */
    public int[][] brood()
    {
        return brood;
    }

    public List<Eye> eyes()
    {
        return eyes;
    }

    /** @return which eye the picture shows at a pixel, by its place in {@link #eyes()}, or -1 */
    public int eyeAt(int index)
    {
        return eyeAt[index];
    }

    public List<Shut> shut()
    {
        return shut;
    }

    public List<Tendril> tendrils()
    {
        return tendrils;
    }

    public List<Wheel> wheels()
    {
        return wheels;
    }

    public List<Head> heads()
    {
        return heads;
    }

    public int mouthX()
    {
        return mouthX;
    }

    public int hubX()
    {
        return hubX;
    }

    public int hubY()
    {
        return hubY;
    }

    public int veiledX()
    {
        return veiledX;
    }

    public int veiledHead()
    {
        return veiledHead;
    }
}
