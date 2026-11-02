package net.voxelindustry.voidheart.client.curse;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.voxelindustry.voidheart.client.curse.sky.StarField;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

/**
 * Which star opens into what eye for one curse. Worked out once from its seed, largest first, each later star taking only the room left, so that no two eyes
 * can ever touch however they are tilted or how wide they stare: all of an eye stays inside a disc around its star, and from the camera at the center of the
 * dome such a disc is exactly a circle of known angular radius.
 */
public final class RaptureFieldLayout
{
    /**
     * In the order the shaders index their per-family tables.
     */
    public enum Family
    {
        ALMOND, ROUND, NARROW, HEAVY, GOAT, SLIT, WRONG
    }

    private static final float DOME_RADIUS = 100;

    // Half widths in blocks at the dome's radius, at their widest while staring. The least just fits five texels across, the fewest that read as an eye
    public static final float WIDTH_LEAST = 1F;
    public static final float WIDTH_MOST  = 8F;

    // The same as TEXEL in the field shaders
    private static final float TEXEL = 0.5F;
    // A texel is drawn whole once its center is inside the eye, so one can stick out by half its diagonal
    private static final float TEXEL_REACH = TEXEL * 0.7072F;

    // How many of each over the whole dome, of which half is above the horizon at any moment, and how far apart they keep
    private static final int   GIANTS_LEAST         = 13;
    private static final int   GIANTS_MOST          = 17;
    private static final float GIANT_WIDTH_LEAST    = 5.5F;
    private static final float GIANT_SPACING        = 31 * Mth.DEG_TO_RAD;
    private static final int   LARGE_LEAST          = 22;
    private static final int   LARGE_MOST           = 30;
    private static final float LARGE_WIDTH_LEAST    = 3F;
    private static final float LARGE_WIDTH_MOST     = 5F;
    private static final float LARGE_SPACING        = 12 * Mth.DEG_TO_RAD;
    // Everything else falls off steeply from a little under the smallest size, those under it ending up as the smallest
    private static final float FILL_WIDTH_LEAST     = 0.9F;
    private static final float FILL_WIDTH_MOST      = 3F;
    private static final float FILL_TAIL            = 1.7F;

    // The sky stays calm over a few wide patches and thins out everywhere else. Giants and large eyes ignore this, standing alone in the calm
    private static final int   CALM_PATCHES         = 11;
    private static final float CALM_RADIUS_LEAST    = 15 * Mth.DEG_TO_RAD;
    private static final float CALM_RADIUS_MOST     = 30 * Mth.DEG_TO_RAD;
    private static final float CALM_OPEN_SHARE      = 0.06F;
    private static final float OPEN_SHARE           = 0.76F;

    // Between two discs whatever their sizes, in blocks, and the wider berth an eye keeps as a share of its own width
    public static final float GAP_LEAST             = 0.5F;
    private static final float GAP_SHARE            = 0.5F;

    // Below these the simple shapes are all that can read. The first is the same as SPECK_BELOW in the field vertex shader
    private static final float SIMPLE_BELOW         = 1.5F;
    private static final float WRONG_FROM           = 3F;
    // How often one of the giants of a curse is a wrong one
    private static final float WRONG_GIANT_CHANCE   = 0.6F;

    private static final int[] SMALL_FAMILIES       = {55, 30, 15, 0, 0, 0, 0};
    private static final int[] MEDIUM_FAMILIES      = {28, 16, 14, 14, 12, 16, 0};
    private static final int[] LARGE_FAMILIES       = {18, 14, 15, 16, 17, 15, 5};

    private static final float TILT_MOST            = 0.34F;
    // Wrong eyes may stand on end
    private static final float WRONG_UPRIGHT_CHANCE = 0.45F;
    private static final float LEAN_MOST            = 0.2F;

    // A large eye draws its family and its iris this many times and keeps whichever it has the fewest of around it, for no part of the sky to fill with the same thing
    private static final int   LARGE_DRAWS          = 4;
    private static final float LARGE_NEIGHBOURS     = 50 * Mth.DEG_TO_RAD;
    // However the draws fall, large eyes are never greener than this share of them
    private static final float LARGE_GREEN_MOST     = 0.2F;
    private static final float COLOR_JITTER         = 0.07F;
    private static final float VIVID_SHARE          = 0.05F;
    private static final float VIVID_SATURATION     = 1.35F;
    private static final float VIVID_VALUE          = 1.08F;
    // A goat fills most of its opening with iris, so that much color would be the loudest thing in the sky
    private static final float GOAT_IRIS_CHROMA     = 0.68F;
    // Small eyes are texture behind the large ones: this dark and this grey at the smallest, in full color from the width below
    private static final float VALUE_AT_SMALLEST    = 0.72F;
    private static final float CHROMA_AT_SMALLEST   = 0.62F;
    private static final float FULL_COLOR_FROM      = 3.2F;

    private static final int SCLERA_WHITE     = 0xD6CCB2;
    private static final int SCLERA_YELLOWED  = 0xC2AB6C;
    private static final int SCLERA_BLOODSHOT = 0xC89684;
    private static final int SCLERA_GREY      = 0x8C8C86;
    private static final int SCLERA_BLACK     = 0x120E14;

    public static final  int IRIS_AMBER  = 0xB47A26;
    private static final int IRIS_OCHRE  = 0xA47E36;
    private static final int IRIS_RUST   = 0x8C4A2A;
    private static final int IRIS_BLUE   = 0x8CB0C2;
    private static final int IRIS_BLIND  = 0xBFC3BC;
    private static final int IRIS_VIOLET = 0x6E5298;
    private static final int IRIS_DEAD   = 0x6E6E6A;
    public static final  int IRIS_GREEN  = 0x70964C;

    private static final int LID_PALE        = 0xAC8270;
    private static final int LID_TAN         = 0x8A5E46;
    private static final int LID_DARK        = 0x583A2C;
    private static final int LID_ASH         = 0x625C5E;
    private static final int LID_BRUISED     = 0x4C3644;
    private static final int LID_SCALE_GREEN = 0x3C4632;
    private static final int LID_SCALE_GREY  = 0x444442;
    private static final int LID_SCALE_RUST  = 0x583A24;
    private static final int LID_FUR         = 0x322C2A;
    private static final int LID_VOID        = 0x2D262F;

    // How much veining each sclera gets once the eye is large enough to show any
    private static final float VEINS_WHITE     = 0.25F;
    private static final float VEINS_YELLOWED  = 0.4F;
    private static final float VEINS_BLOODSHOT = 1;
    private static final float VEINS_GREY      = 0.15F;
    private static final float VEINS_BLACK     = 0;
    // On anything smaller a black sclera melts into the sky, leaving a broken ring where the iris is
    private static final float BLACK_SCLERA_FROM = 4.5F;

    // A blind eye's pupil barely shows
    private static final float PUPIL_STRENGTH       = 1;
    private static final float PUPIL_STRENGTH_BLIND = 0.2F;

    private static final Palette[] PALETTES = {
            // Almond
            new Palette(new int[][] {{SCLERA_WHITE, 6}, {SCLERA_YELLOWED, 3}, {SCLERA_BLOODSHOT, 2}, {SCLERA_GREY, 1}},
                    new int[][] {{IRIS_AMBER, 3}, {IRIS_OCHRE, 2}, {IRIS_RUST, 3}, {IRIS_BLUE, 4}, {IRIS_DEAD, 2}, {IRIS_VIOLET, 1}, {IRIS_GREEN, 2}},
                    new int[][] {{LID_PALE, 5}, {LID_TAN, 4}, {LID_DARK, 3}, {LID_ASH, 2}}),
            // Round
            new Palette(new int[][] {{SCLERA_YELLOWED, 4}, {SCLERA_BLOODSHOT, 4}, {SCLERA_WHITE, 2}, {SCLERA_GREY, 2}, {SCLERA_BLACK, 1}},
                    new int[][] {{IRIS_RUST, 3}, {IRIS_VIOLET, 3}, {IRIS_BLUE, 2}, {IRIS_BLIND, 3}, {IRIS_AMBER, 2}, {IRIS_DEAD, 2}, {IRIS_GREEN, 3}},
                    new int[][] {{LID_BRUISED, 3}, {LID_DARK, 3}, {LID_ASH, 2}, {LID_VOID, 3}}),
            // Narrow
            new Palette(new int[][] {{SCLERA_YELLOWED, 3}, {SCLERA_GREY, 1}, {SCLERA_BLACK, 1}},
                    new int[][] {{IRIS_OCHRE, 3}, {IRIS_AMBER, 3}, {IRIS_RUST, 2}, {IRIS_GREEN, 3}},
                    new int[][] {{LID_SCALE_GREEN, 3}, {LID_SCALE_GREY, 3}, {LID_SCALE_RUST, 2}}),
            // Heavy
            new Palette(new int[][] {{SCLERA_YELLOWED, 4}, {SCLERA_BLOODSHOT, 4}, {SCLERA_GREY, 2}},
                    new int[][] {{IRIS_DEAD, 4}, {IRIS_BLIND, 3}, {IRIS_BLUE, 3}, {IRIS_RUST, 2}, {IRIS_AMBER, 1}},
                    new int[][] {{LID_ASH, 3}, {LID_PALE, 2}, {LID_TAN, 2}, {LID_DARK, 2}, {LID_BRUISED, 1}}),
            // Goat
            new Palette(new int[][] {{SCLERA_YELLOWED, 3}, {SCLERA_WHITE, 1}, {SCLERA_GREY, 2}},
                    new int[][] {{IRIS_OCHRE, 5}, {IRIS_DEAD, 3}, {IRIS_AMBER, 2}, {IRIS_BLUE, 1}},
                    new int[][] {{LID_FUR, 3}, {LID_DARK, 3}, {LID_ASH, 1}}),
            // Slit
            new Palette(new int[][] {{SCLERA_YELLOWED, 3}, {SCLERA_WHITE, 2}, {SCLERA_GREY, 2}},
                    new int[][] {{IRIS_GREEN, 6}, {IRIS_AMBER, 4}, {IRIS_BLUE, 2}, {IRIS_OCHRE, 2}, {IRIS_VIOLET, 1}},
                    new int[][] {{LID_FUR, 4}, {LID_VOID, 2}, {LID_SCALE_GREY, 2}}),
            // Wrong
            new Palette(new int[][] {{SCLERA_BLACK, 4}, {SCLERA_BLOODSHOT, 1}},
                    new int[][] {{IRIS_GREEN, 3}, {IRIS_VIOLET, 3}, {IRIS_AMBER, 2}, {IRIS_BLUE, 2}, {IRIS_BLIND, 1}},
                    new int[][] {{LID_VOID, 3}, {LID_BRUISED, 2}}),
    };

    private static final long SEED_SALT = 0x52415054L;

    private final List<Eye>  eyes;
    private final float[]    directions;
    private final float[]    widths;

    private RaptureFieldLayout(float[] directions, float[] widths, List<Eye> eyes)
    {
        this.directions = directions;
        this.widths = widths;
        this.eyes = eyes;
    }

    /**
     * @param stars as {@link StarField#vanillaStars()} gives them
     */
    public static RaptureFieldLayout of(long seed, ByteBuffer stars)
    {
        int count = stars.remaining() / StarField.STAR_BYTES;
        var directions = new float[count * 3];
        for (int star = 0; star < count; star++)
        {
            int at = stars.position() + star * StarField.STAR_BYTES + StarField.CENTER_OFFSET;
            float x = stars.getFloat(at);
            float y = stars.getFloat(at + Float.BYTES);
            float z = stars.getFloat(at + 2 * Float.BYTES);
            float length = Mth.sqrt(x * x + y * y + z * z);
            directions[star * 3] = x / length;
            directions[star * 3 + 1] = y / length;
            directions[star * 3 + 2] = z / length;
        }

        var random = RandomSource.create(seed ^ SEED_SALT);
        var wanted = wantedWidths(directions, count, random);
        var widths = place(directions, wanted, count);
        return new RaptureFieldLayout(directions, widths, dress(directions, widths, count, random));
    }

    private static float[] wantedWidths(float[] directions, int count, RandomSource random)
    {
        var wanted = new float[count];
        int[] order = shuffled(count, random);

        int giants = random.nextIntBetweenInclusive(GIANTS_LEAST, GIANTS_MOST);
        int large = random.nextIntBetweenInclusive(LARGE_LEAST, LARGE_MOST);
        var taken = new ArrayList<Integer>();
        for (int star : order)
        {
            if (giants > 0 && isClear(directions, star, taken, GIANT_SPACING))
            {
                wanted[star] = Mth.lerp(random.nextFloat(), GIANT_WIDTH_LEAST, WIDTH_MOST);
                taken.add(star);
                giants--;
            }
        }
        for (int star : order)
        {
            if (large > 0 && wanted[star] == 0 && isClear(directions, star, taken, LARGE_SPACING))
            {
                wanted[star] = Mth.lerp(random.nextFloat(), LARGE_WIDTH_LEAST, LARGE_WIDTH_MOST);
                taken.add(star);
                large--;
            }
        }

        var calmCenters = new float[CALM_PATCHES * 3];
        var calmCosines = new float[CALM_PATCHES];
        for (int patch = 0; patch < CALM_PATCHES; patch++)
        {
            float y = random.nextFloat() * 2 - 1;
            float around = random.nextFloat() * Mth.TWO_PI;
            float flat = Mth.sqrt(1 - y * y);
            calmCenters[patch * 3] = flat * Mth.cos(around);
            calmCenters[patch * 3 + 1] = y;
            calmCenters[patch * 3 + 2] = flat * Mth.sin(around);
            calmCosines[patch] = Mth.cos(Mth.lerp(random.nextFloat(), CALM_RADIUS_LEAST, CALM_RADIUS_MOST));
        }

        double reach = 1 - Math.pow(FILL_WIDTH_LEAST / FILL_WIDTH_MOST, FILL_TAIL);
        for (int star = 0; star < count; star++)
        {
            if (wanted[star] > 0)
                continue;

            boolean calm = false;
            for (int patch = 0; patch < CALM_PATCHES && !calm; patch++)
            {
                calm = directions[star * 3] * calmCenters[patch * 3] + directions[star * 3 + 1] * calmCenters[patch * 3 + 1]
                        + directions[star * 3 + 2] * calmCenters[patch * 3 + 2] > calmCosines[patch];
            }
            if (random.nextFloat() >= (calm ? CALM_OPEN_SHARE : OPEN_SHARE))
                continue;

            wanted[star] = Math.max(WIDTH_LEAST, (float) (FILL_WIDTH_LEAST / Math.pow(1 - random.nextFloat() * reach, 1 / FILL_TAIL)));
        }
        return wanted;
    }

    private static boolean isClear(float[] directions, int star, List<Integer> others, float spacing)
    {
        for (int other : others)
        {
            if (angle(directions, star, other) < spacing)
                return false;
        }
        return true;
    }

    /**
     * Largest first. Whatever a star can't fit its wanted size into, it takes what is left, and where that isn't even the smallest eye it stays a star.
     */
    private static float[] place(float[] directions, float[] wanted, int count)
    {
        var widths = new float[count];
        var byWanted = new ArrayList<Integer>();
        for (int star = 0; star < count; star++)
        {
            if (wanted[star] > 0)
                byWanted.add(star);
        }
        byWanted.sort(Comparator.comparingDouble(star -> -wanted[star]));

        var placed = new ArrayList<Integer>();
        double leastAngle = discAngle(WIDTH_LEAST);
        for (int star : byWanted)
        {
            double room = discAngle(wanted[star]);
            for (int other : placed)
            {
                float berth = Math.max(GAP_LEAST, widths[other] * GAP_SHARE);
                room = Math.min(room, angle(directions, star, other) - discAngle(widths[other]) - Math.atan(berth / DOME_RADIUS));
                if (room < leastAngle)
                    break;
            }
            if (room < leastAngle)
                continue;

            widths[star] = Mth.clamp(widthOfDisc(room), WIDTH_LEAST, wanted[star]);
            placed.add(star);
        }
        return widths;
    }

    private static List<Eye> dress(float[] directions, float[] widths, int count, RandomSource random)
    {
        var byWidth = new ArrayList<Integer>();
        for (int star = 0; star < count; star++)
        {
            if (widths[star] > 0)
                byWidth.add(star);
        }
        byWidth.sort(Comparator.comparingDouble(star -> -widths[star]));

        var giants = giantFamilies((int) byWidth.stream().filter(star -> widths[star] >= GIANT_WIDTH_LEAST).count(), random);
        var eyes = new ArrayList<Eye>(byWidth.size());
        int largeGreensLeft = (int) (byWidth.stream().filter(star -> widths[star] >= LARGE_WIDTH_LEAST).count() * LARGE_GREEN_MOST);
        for (int star : byWidth)
        {
            float width = widths[star];
            Family family;
            if (width >= GIANT_WIDTH_LEAST)
                family = giants.removeFirst();
            else if (width >= LARGE_WIDTH_LEAST)
                family = Family.values()[rarestNearby(directions, star, eyes, Eye::familyIndex, () -> pickIndex(LARGE_FAMILIES, random))];
            else
                family = Family.values()[pickIndex(width < SIMPLE_BELOW ? SMALL_FAMILIES : MEDIUM_FAMILIES, random)];

            var palette = PALETTES[family.ordinal()];
            int iris = width >= LARGE_WIDTH_LEAST ? rarestNearby(directions, star, eyes, Eye::irisSwatch, () -> pick(palette.iris, random)) : pick(palette.iris, random);
            if (width >= LARGE_WIDTH_LEAST && iris == IRIS_GREEN && largeGreensLeft-- <= 0)
            {
                while (iris == IRIS_GREEN)
                    iris = pick(palette.iris, random);
            }
            eyes.add(dressed(star, width, family, iris, random));
        }
        return List.copyOf(eyes);
    }

    /**
     * One of each until all were dealt, so that the largest are all different things.
     */
    private static List<Family> giantFamilies(int giants, RandomSource random)
    {
        var deck = new ArrayList<Family>();
        while (deck.size() < giants)
        {
            var families = new ArrayList<>(List.of(Family.ALMOND, Family.ROUND, Family.NARROW, Family.HEAVY, Family.GOAT, Family.SLIT));
            while (!families.isEmpty())
                deck.add(families.remove(random.nextInt(families.size())));
        }
        if (giants > 0 && random.nextFloat() < WRONG_GIANT_CHANCE)
            deck.set(random.nextInt(giants), Family.WRONG);
        return deck;
    }

    /**
     * @param eyes the eyes so far, largest first
     */
    private static int rarestNearby(float[] directions, int star, List<Eye> eyes, ToIntFunction<Eye> trait, IntSupplier draw)
    {
        int rarest = 0;
        int fewest = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < LARGE_DRAWS; attempt++)
        {
            int candidate = draw.getAsInt();
            int nearby = 0;
            for (var eye : eyes)
            {
                if (eye.width() < LARGE_WIDTH_LEAST)
                    break;
                if (trait.applyAsInt(eye) == candidate && angle(directions, star, eye.star()) < LARGE_NEIGHBOURS)
                    nearby++;
            }
            if (nearby < fewest)
            {
                fewest = nearby;
                rarest = candidate;
            }
        }
        return rarest;
    }

    private static Eye dressed(int star, float width, Family family, int iris, RandomSource random)
    {
        var palette = PALETTES[family.ordinal()];
        int sclera = pick(palette.sclera, random);
        if (sclera == SCLERA_BLACK && width < BLACK_SCLERA_FROM)
            sclera = SCLERA_GREY;
        int lid = pick(palette.lid, random);

        float fullness = Mth.clamp((width - WIDTH_LEAST) / (FULL_COLOR_FROM - WIDTH_LEAST), 0, 1);
        float value = Mth.lerp(fullness, VALUE_AT_SMALLEST, 1);
        float chroma = Mth.lerp(fullness, CHROMA_AT_SMALLEST, 1);
        boolean vivid = family != Family.GOAT && random.nextFloat() < VIVID_SHARE;
        float irisChroma = family == Family.GOAT ? chroma * GOAT_IRIS_CHROMA : chroma;
        float veins = sclera == SCLERA_BLOODSHOT ? VEINS_BLOODSHOT : sclera == SCLERA_YELLOWED ? VEINS_YELLOWED
                : sclera == SCLERA_WHITE ? VEINS_WHITE : sclera == SCLERA_GREY ? VEINS_GREY : VEINS_BLACK;

        float tilt = (random.nextFloat() * 2 - 1) * TILT_MOST;
        if (family == Family.WRONG && random.nextFloat() < WRONG_UPRIGHT_CHANCE)
            tilt += Mth.HALF_PI;

        return new Eye(star, width, family, tilt, (random.nextFloat() * 2 - 1) * LEAN_MOST, random.nextBoolean(),
                random.nextFloat(), random.nextFloat(), random.nextFloat(),
                varied(sclera, value, chroma, random), veins,
                varied(vivid ? saturated(iris) : iris, value, irisChroma, random), iris == IRIS_BLIND ? PUPIL_STRENGTH_BLIND : PUPIL_STRENGTH,
                iris,
                varied(lid, value, chroma, random));
    }

    private static int saturated(int color)
    {
        return withChroma(color, VIVID_SATURATION, VIVID_VALUE);
    }

    private static int withChroma(int color, float chroma, float value)
    {
        float red = (color >> 16 & 0xFF) / 255F;
        float green = (color >> 8 & 0xFF) / 255F;
        float blue = (color & 0xFF) / 255F;
        float grey = (red + green + blue) / 3;
        return rgb((grey + (red - grey) * chroma) * value, (grey + (green - grey) * chroma) * value, (grey + (blue - grey) * chroma) * value);
    }

    private static int varied(int color, float value, float chroma, RandomSource random)
    {
        int muted = withChroma(color, chroma, value);
        float shared = 1 + (random.nextFloat() * 2 - 1) * COLOR_JITTER;
        return rgb((muted >> 16 & 0xFF) / 255F * shared * (1 + (random.nextFloat() * 2 - 1) * COLOR_JITTER * 0.5F),
                (muted >> 8 & 0xFF) / 255F * shared * (1 + (random.nextFloat() * 2 - 1) * COLOR_JITTER * 0.5F),
                (muted & 0xFF) / 255F * shared * (1 + (random.nextFloat() * 2 - 1) * COLOR_JITTER * 0.5F));
    }

    private static int rgb(float red, float green, float blue)
    {
        return Mth.clamp((int) (red * 255), 0, 255) << 16 | Mth.clamp((int) (green * 255), 0, 255) << 8 | Mth.clamp((int) (blue * 255), 0, 255);
    }

    private static int pick(int[][] swatches, RandomSource random)
    {
        int total = 0;
        for (int[] swatch : swatches)
            total += swatch[1];
        int at = random.nextInt(total);
        for (int[] swatch : swatches)
        {
            at -= swatch[1];
            if (at < 0)
                return swatch[0];
        }
        throw new IllegalStateException();
    }

    private static int pickIndex(int[] weights, RandomSource random)
    {
        int total = 0;
        for (int weight : weights)
            total += weight;
        int at = random.nextInt(total);
        for (int index = 0; index < weights.length; index++)
        {
            at -= weights[index];
            if (at < 0)
                return index;
        }
        throw new IllegalStateException();
    }

    private static int[] shuffled(int count, RandomSource random)
    {
        var order = new int[count];
        for (int i = 0; i < count; i++)
            order[i] = i;
        for (int i = count - 1; i > 0; i--)
        {
            int other = random.nextInt(i + 1);
            int swapped = order[i];
            order[i] = order[other];
            order[other] = swapped;
        }
        return order;
    }

    private static double angle(float[] directions, int a, int b)
    {
        double dot = (double) directions[a * 3] * directions[b * 3] + (double) directions[a * 3 + 1] * directions[b * 3 + 1]
                + (double) directions[a * 3 + 2] * directions[b * 3 + 2];
        return Math.acos(Mth.clamp(dot, -1, 1));
    }

    /**
     * How far an eye of this half width can reach from its star, as seen from the camera at the center of the dome.
     */
    public static double discAngle(float width)
    {
        return Math.atan((width + TEXEL_REACH) / DOME_RADIUS);
    }

    private static float widthOfDisc(double angle)
    {
        return (float) (Math.tan(angle) * DOME_RADIUS) - TEXEL_REACH;
    }

    /**
     * Every star that opens, largest first. Those missing stay plain stars and fade while the others open.
     */
    public List<Eye> eyes()
    {
        return eyes;
    }

    public int stars()
    {
        return widths.length;
    }

    /**
     * The half width of a star's eye, or zero if it never opens.
     */
    public float width(int star)
    {
        return widths[star];
    }

    public float directionX(int star)
    {
        return directions[star * 3];
    }

    public float directionY(int star)
    {
        return directions[star * 3 + 1];
    }

    public float directionZ(int star)
    {
        return directions[star * 3 + 2];
    }

    private record Palette(int[][] sclera, int[][] iris, int[][] lid)
    {
    }

    /**
     * @param tilt          radians, on top of being upright toward the zenith
     * @param lean          which side the arch of the upper lid leans to, as a share of the half width
     * @param mirrored      which way its slant runs
     * @param openness      0..1, how far this one's opening goes within the range of its family
     * @param pupilSize     0..1, the same for the pupil
     * @param irisSize      0..1, the same for the iris
     * @param sclera        final color as 0xRRGGBB, with the size's darkening and this eye's variation
     * @param veins         0..1, the share of the largest eye's veining it would have
     * @param iris          final color
     * @param pupilStrength 0..1, how much the pupil stands out from the iris
     * @param irisSwatch    the palette entry the iris came from
     * @param lid           final color
     */
    public record Eye(int star, float width, Family family, float tilt, float lean, boolean mirrored, float openness, float pupilSize, float irisSize,
                      int sclera, float veins, int iris, float pupilStrength, int irisSwatch, int lid)
    {
        private int familyIndex()
        {
            return family.ordinal();
        }
    }
}
