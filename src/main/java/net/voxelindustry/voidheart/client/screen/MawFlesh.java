package net.voxelindustry.voidheart.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The mouth that tears open behind the research map as the Deities are studied, worked out from a seed and how far the player has gone. Pure, so that it can
 * be drawn without a game: everything is in screen units around the middle of the mouth, as flat cells shaded with the same stepped dither as the dark
 * behind the map.
 * <p>
 * Stage 0 is a crack. From stage 1 it is a ravine of flesh: a long jagged tear with lumpy lips, a gullet going dark down its middle, small teeth just inside
 * its edges and an eye far down it. Each stage it runs longer and gapes a little wider, rather than opening into a round mouth.
 * <p>
 * Once the player seals it off, see {@link #sealed}, its lips are pressed shut along the whole ravine and stitched across, pinched at every stitch.
 */
public final class MawFlesh
{
    public static final int STAGES = 5;

    /** One block of colour. */
    public record Cell(int x, int y, int size, int argb)
    {
    }

    /** Where a tendril can leave the lips, and which way is out. */
    public record Root(float x, float y, float angle)
    {
    }

    /**
     * @param radiusX   half the length of the ravine, lips included
     * @param radiusY   half its height at its widest, lips and bends included
     * @param opening   half the gape of the gullet in the middle, where the eye is
     * @param eyeRadius the iris of the eye down the gullet, 0 while there is no eye yet
     * @param eyeY      where the middle of the gullet is, as the ravine bends
     */
    public record Shape(List<Cell> cells, List<Cell> teeth, List<Root> roots, float radiusX, float radiusY, float opening, float eyeRadius, float eyeY)
    {
    }

    static final int CELL = 2;

    private static final float[] LENGTH = { 34, 34, 62, 92, 128 };
    /** How wide the gullet gapes at its middle, so the ravine mostly runs longer as it grows and only opens a little. */
    private static final float[] OPEN   = { 0, 4, 7, 10, 13 };
    private static final float[] LIP    = { 0, 6, 8, 10, 12 };
    /** How far the ravine wanders up and down along its length. */
    private static final float[] BEND   = { 0, 2, 4, 6, 8 };
    private static final float[] EYE    = { 0, 3, 4, 5, 6 };
    /** Teeth per 10 units of ravine, along each edge. */
    private static final float[] TEETH  = { 0, 0, 0.9F, 1.1F, 1.3F };
    private static final int[]   ROOTS  = { 0, 0, 0, 4, 7 };
    /** How long a lump of lip is along the ravine, so the lips bulge in a row like the lumps of the mouth in the sky. */
    private static final float   LUMP   = 9;

    private static final int[] FLESH = { 0xFF5E2A33, 0xFF6B3038, 0xFF54242D, 0xFF72363F };
    private static final int[] FLESH_LIT = { 0xFF7A3E46, 0xFF844650, 0xFF6E3840, 0xFF8C4C55 };
    static final int  FLESH_DARK    = 0xFF2A1218;
    static final int  FLESH_SPECK   = 0xFF3E1C24;
    static final int  FLESH_SHINE   = 0xFF9A5A62;
    static final int  LIP_WET       = 0xFFA4626A;
    private static final int GULLET_RIM  = 0xFF3A161E;
    private static final int GULLET_MID  = 0xFF1E0A10;
    private static final int GULLET_DEEP = 0xFF0A0408;
    static final int  BONE          = 0xFFA49A88;
    static final int  BONE_SHADE    = 0xFF7E766A;
    static final int  CRACK_EDGE    = 0xFF4A0C14;
    static final int  CRACK_CORE    = 0xFFB02838;

    private MawFlesh()
    {
    }

    public static Shape shape(int stage, long seed)
    {
        stage = Math.clamp(stage, 0, STAGES - 1);
        var cells = new ArrayList<Cell>();
        var teeth = new ArrayList<Cell>();
        var roots = new ArrayList<Root>();
        if (stage == 0)
        {
            crack(cells, LENGTH[0], seed);
            return new Shape(List.copyOf(cells), List.of(), List.of(), LENGTH[0], 6, 0, 0, 0);
        }

        var random = new Random(seed * 31 + stage);
        var ravine = new Ravine(LENGTH[stage], OPEN[stage], LIP[stage], BEND[stage], random.nextFloat() * 7, random.nextFloat() * 7, 0, seed);
        ravine.flesh(cells);
        ravine.teeth(teeth, random, TEETH[stage]);

        for (int root = 0; root < ROOTS[stage]; root++)
        {
            float x = (float) ((root + 0.5 + (random.nextFloat() - 0.5) * 0.6) / ROOTS[stage] * 2 - 1) * ravine.length * 0.8F;
            boolean below = root % 2 == 1;
            float edge = ravine.open(x) + ravine.lip(x) * 0.7F;
            float y = ravine.middle(x) + (below ? edge : -edge);
            float tilt = x / ravine.length * 0.6F;
            roots.add(new Root(x, y, (float) (below ? Math.PI / 2 - tilt : -Math.PI / 2 + tilt)));
        }

        float radiusY = OPEN[stage] + LIP[stage] * 1.3F + BEND[stage];
        float opening = ravine.open(0);
        float eye = Math.min(EYE[stage], opening * 0.6F);
        return new Shape(List.copyOf(cells), List.copyOf(teeth), List.copyOf(roots), LENGTH[stage] + LIP[stage], radiusY, opening, eye, ravine.middle(0));
    }

    /**
     * The ravine at its fullest, shut: no gullet, no eye and no tendrils, only the lips fused along a seam, bruised along it and crossed by sutures that
     * pinch the flesh where they go through.
     */
    public static Shape sealed(long seed)
    {
        int stage = STAGES - 1;
        var random = new Random(seed * 31 + stage);
        var ravine = new Ravine(LENGTH[stage], 0, LIP[stage] * SWOLLEN, BEND[stage], random.nextFloat() * 7, random.nextFloat() * 7, STITCH, seed);
        var cells = new ArrayList<Cell>();
        ravine.flesh(cells);
        ravine.sutures(cells);
        return new Shape(List.copyOf(cells), List.of(), List.of(), LENGTH[stage] + LIP[stage], LIP[stage] * SWOLLEN * 1.3F + BEND[stage], 0, 0, ravine.middle(0));
    }

    /** How far apart the sutures are along the seam. */
    private static final float STITCH       = 11;
    /** Shut lips swell, so the seam sits in a thicker ridge of flesh than the open ravine had. */
    private static final float SWOLLEN      = 1.35F;
    /** How far a suture reaches into each lip, as a share of the lip. */
    private static final float STITCH_REACH = 0.6F;
    static final int           THREAD       = 0xFF8E8478;
    static final int           THREAD_LIT   = 0xFFB4AC9C;
    static final int           BRUISE       = 0xFF4A2A3A;

    /** The tear and the flesh along it, measured across from a middle line that wanders as it goes. */
    private record Ravine(float length, float gape, float thickness, float bend, float wander, float bulge, float stitch, long seed)
    {
        float middle(float x)
        {
            return (float) (Math.sin(x * 0.045 + wander) * bend + (smooth(x * 0.2F) - 0.5F) * 2);
        }

        /** Half the gape at x, closing to nothing at either end and jagged along the way. */
        float open(float x)
        {
            float along = x / length;
            if (Math.abs(along) >= 1)
                return 0;
            return (float) (gape * Math.pow(1 - along * along, 0.6) * (0.75 + 0.5 * smooth(x * 0.18F + 3)));
        }

        /** How thick the lip is at x: lumps in a row, thinning to the ravine's ends. */
        float lip(float x)
        {
            float reach = length + thickness;
            float along = Math.abs(x) / reach;
            if (along >= 1)
                return 0;
            float lump = (float) Math.sin(Math.PI * Math.floorMod((int) Math.floor((x + bulge * 10) / LUMP * 8), 8) / 8F);
            // Stitched flesh is drawn in where each suture goes through and bulges between them
            float pinch = stitch > 0 ? 0.72F + 0.28F * (float) Math.abs(Math.sin(Math.PI * x / stitch)) : 1;
            return (float) (thickness * Math.pow(1 - along * along, 0.45) * (0.65 + 0.35 * lump + 0.2 * (smooth(x * 0.11F + bulge) - 0.5F)) * pinch);
        }

        void flesh(List<Cell> cells)
        {
            int columns = (int) Math.ceil((length + thickness) / CELL);
            int rows = (int) Math.ceil((gape + thickness * 1.3F + bend + 3) / CELL);
            for (int column = -columns; column <= columns; column++)
            {
                float x = (column + 0.5F) * CELL;
                float middle = middle(x);
                float open = open(x);
                float lip = lip(x);
                if (lip <= 0)
                    continue;
                for (int row = -rows; row <= rows; row++)
                {
                    float y = (row + 0.5F) * CELL;
                    float across = Math.abs(y - middle);
                    boolean above = y < middle;
                    float bayer = ResearchVoid.BAYER[Math.floorMod(row, 4)][Math.floorMod(column, 4)];
                    int color;
                    if (across < open)
                        color = gullet(across / open, bayer);
                    else if (across < open + lip)
                        color = lip((across - open) / lip, above, x, column, row, bayer);
                    else
                        continue;
                    cells.add(new Cell(column * CELL, row * CELL, CELL, color));
                }
            }
        }

        /** The throat darkens towards the middle of the ravine in dithered steps, like the light in the dark behind the map. */
        private static int gullet(float fromMiddle, float bayer)
        {
            float step = fromMiddle + (bayer - 0.5F) * 0.3F;
            if (step > 0.78F)
                return GULLET_RIM;
            if (step > 0.4F)
                return GULLET_MID;
            return GULLET_DEEP;
        }

        /**
         * A lip is lit along its top and shadowed below, each lump of it round from the dither between a lit and a plain tone, with a wet edge where it
         * drops into the ravine on the far side, where the light reaches in.
         */
        private int lip(float out, boolean above, float x, int column, int row, float bayer)
        {
            if (out > 0.86F)
                return FLESH_DARK;
            // Shut, the lips meet on a dark seam, bruised along it where they are forced together
            if (stitch > 0 && out < 0.12F)
                return GULLET_DEEP;
            if (stitch > 0 && out < 0.3F)
                return above ? BRUISE : FLESH_DARK;
            if (out < 0.16F)
                return above ? FLESH_DARK : LIP_WET;
            int lump = Math.floorMod((int) Math.floor((x + bulge * 10) / LUMP), FLESH.length);
            float speck = hash(column, row, seed);
            if (speck < 0.06F)
                return FLESH_SPECK;
            if (speck > 0.97F)
                return FLESH_SHINE;
            // The upper lip faces the light, the lower one turns from it, and both round off towards their outer edge
            float lit = (above ? 0.75F : 0.35F) - out * 0.5F;
            return lit > bayer ? FLESH_LIT[lump] : FLESH[lump];
        }

        /** A straight stitch of dark thread across the seam every so often, lit on its upper end, going into the flesh through a dark puncture at each end. */
        void sutures(List<Cell> cells)
        {
            for (float x = -length * 0.85F + stitch / 2; x <= length * 0.85F; x += stitch)
            {
                int column = Math.round((x - CELL / 2F) / CELL);
                int seam = Math.round((middle(x) - CELL / 2F) / CELL);
                int steps = Math.max(2, Math.round(lip(x) * STITCH_REACH / CELL));
                for (int step = -steps; step <= steps; step++)
                {
                    int color = Math.abs(step) == steps ? FLESH_DARK : step == 1 - steps ? THREAD_LIT : THREAD;
                    cells.add(new Cell(column * CELL, (seam + step) * CELL, CELL, color));
                }
            }
        }

        /** Small teeth along both edges of the gullet, just inside it and pointing across, a few broken short or missing. */
        void teeth(List<Cell> teeth, Random random, float density)
        {
            int count = Math.max(2, Math.round(length * 2 / 10 * density));
            for (int tooth = 0; tooth < count; tooth++)
            {
                float x = ((tooth + 0.5F + (random.nextFloat() - 0.5F) * 0.7F) / count * 2 - 1) * length * 0.85F;
                float open = open(x);
                if (open < CELL * 1.5F || random.nextFloat() < 0.2F)
                    continue;
                boolean below = random.nextBoolean();
                int column = Math.round(x / CELL);
                float edge = middle(x) + (below ? open : -open);
                int cells = Math.max(1, Math.min(Math.round(open * (0.3F + random.nextFloat() * 0.3F) / CELL), 3));
                for (int step = 0; step < cells; step++)
                {
                    float y = edge + (below ? -(step + 0.5F) * CELL : (step + 0.5F) * CELL);
                    teeth.add(new Cell(column * CELL, Math.round((y - CELL / 2F) / CELL) * CELL, CELL, step == cells - 1 ? BONE_SHADE : BONE));
                }
            }
        }

        private float smooth(float t)
        {
            return ResearchVoid.smooth(t, 0.5F, seed + 11);
        }
    }

    /** A jagged tear across the dark, dark at its edges and glowing along its middle. */
    private static void crack(List<Cell> cells, float length, long seed)
    {
        float y = 0;
        for (float x = -length; x <= length; x += CELL)
        {
            float along = 1 - Math.abs(x) / length;
            y += (hash((int) (x / CELL), 3, seed) - 0.5F) * CELL * 1.6F;
            y *= 0.9F;
            int width = Math.max(1, Math.round(along * 2.4F));
            int top = Math.round(y / CELL) * CELL - width * CELL / 2;
            for (int row = -1; row <= width; row++)
                cells.add(new Cell(Math.round(x / CELL) * CELL, top + row * CELL, CELL, row < 0 || row == width ? CRACK_EDGE : CRACK_CORE));
        }
    }

    /**
     * The eye far down the gullet: a bloodshot rim, a yellow iris and a crooked slit of a pupil, turned towards where it looks.
     *
     * @param lookX where it looks, from -1 to 1 across
     * @param lookY likewise down
     */
    public static List<Cell> eye(float radius, float lookX, float lookY, float open, long seed)
    {
        var cells = new ArrayList<Cell>();
        if (radius <= 0 || open <= 0)
            return cells;
        float rim = radius * 1.4F;
        int span = (int) Math.ceil(rim);
        float pupilX = lookX * radius * 0.35F;
        float pupilY = lookY * radius * 0.25F;
        for (int y = -span; y <= span; y++)
        {
            for (int x = -span; x <= span; x++)
            {
                float lid = rim * open;
                if (Math.abs(y) > lid)
                    continue;
                float reach = (float) Math.hypot(x / rim, y / Math.max(1, lid));
                if (reach > 1)
                    continue;
                float speck = hash(x, y, seed + 5);
                int color;
                float fromIris = (float) Math.hypot(x - pupilX * 0.5F, y - pupilY * 0.5F) / radius;
                // The slit wanders as it goes down, so the pupil looks torn rather than drawn
                float slit = pupilX + (float) Math.sin(y * 0.9F + seed) * radius * 0.12F;
                if (reach > 0.88F)
                    color = EYE_RIM;
                else if (fromIris > 1)
                    color = speck < 0.3F ? EYE_VEIN : EYE_WHITE;
                else if (Math.abs(x - slit) < Math.max(0.6F, radius * 0.14F) && Math.abs(y - pupilY) < radius * 0.85F)
                    color = EYE_PUPIL;
                else
                    color = fromIris > 0.7F ? EYE_IRIS_DARK : speck < 0.15F ? EYE_IRIS_DARK : EYE_IRIS;
                cells.add(new Cell(x, y, 1, color));
            }
        }
        return cells;
    }

    static final int EYE_RIM       = 0xFF2A0A08;
    static final int EYE_WHITE     = 0xFFD8CC8A;
    static final int EYE_VEIN      = 0xFF9A1C1C;
    static final int EYE_IRIS      = 0xFFC8D040;
    static final int EYE_IRIS_DARK = 0xFF8A9420;
    static final int EYE_PUPIL     = 0xFF0A0A04;

    /**
     * A tendril out of the lips: a chain of flesh blocks thinning to its tip, swaying side to side.
     *
     * @param length how far it reaches now
     * @param sway   how far it bends, in screen units at its tip
     * @param phase  where along its sway it is
     */
    public static List<Cell> tendril(Root root, float length, float sway, float phase, long seed)
    {
        var cells = new ArrayList<Cell>();
        var flesh = new ArrayList<Cell>();
        int steps = Math.max(1, (int) (length / CELL));
        double cos = Math.cos(root.angle());
        double sin = Math.sin(root.angle());
        for (int step = 0; step <= steps; step++)
        {
            float t = step / (float) steps;
            float along = t * length;
            float bend = (float) Math.sin(phase + t * 3.2) * sway * t;
            float x = (float) (root.x() + cos * along - sin * bend);
            float y = (float) (root.y() + sin * along + cos * bend);
            int size = t < 0.45F ? CELL * 2 : CELL;
            int left = Math.round((x - size / 2F) / CELL) * CELL;
            int top = Math.round((y - size / 2F) / CELL) * CELL;
            cells.add(new Cell(left - 1, top - 1, size + 2, FLESH_DARK));
            flesh.add(new Cell(left, top, size, hash(step, 9, seed) < 0.12F ? FLESH_SHINE : step % 4 < 2 ? FLESH_LIT[0] : FLESH[0]));
        }
        cells.addAll(flesh);
        return cells;
    }

    static int flesh(int index)
    {
        return FLESH[Math.floorMod(index, FLESH.length)];
    }

    static float hash(int x, int y, long seed)
    {
        return ResearchVoid.hash(x, y, seed);
    }
}
