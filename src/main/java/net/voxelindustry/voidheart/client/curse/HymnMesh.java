package net.voxelindustry.voidheart.client.curse;

import net.voxelindustry.voidheart.client.curse.HymnLayout.Edge;
import net.voxelindustry.voidheart.client.curse.HymnLayout.Eye;
import net.voxelindustry.voidheart.client.curse.HymnLayout.Tentacle;
import net.voxelindustry.voidheart.client.curse.HymnLayout.Tooth;

import static net.voxelindustry.voidheart.client.screen.MawFlesh.*;

/**
 * Builds the flesh at one moment in the same pixel language as Maw's mouth behind the research map ({@code MawFlesh}): flat colour, lumps of flesh
 * shaded with its stepped dither, dark outlines, tapered bone teeth, tendrils as chains of blocks, and eyes with a bloodshot rim and torn slits. It is
 * drawn a GUI unit to the pixel, as fine as the hearts and hotbar, where the research map's flesh uses blocks of two. Back to front: tentacles behind the jaws, teeth, the rim of lips they grow from, then the eyes set in it.
 * <p>
 * Each edge is worked in its own frame, {@code a} along it and {@code d} in from it, and every block is cut to {@link HymnLayout#REACH} of its edge.
 * Per frame at 16:9: about 1500 rim blocks, 20 tentacles of up to 25 links, 100 teeth and 6 eyes of a few hundred units, all axis-aligned quads.
 */
public final class HymnMesh
{
    public static final int IRISES = 3;
    /** One pixel of the HUD's own sprites. */
    public static final int CELL   = 1;

    public interface Quads
    {
        void quad(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3);
    }

    private static final float RIM_BASE        = 0.032F;
    private static final float RIM_WAVE        = 0.014F;
    private static final float RIM_WAVE_NORM   = 1.6F;
    private static final float RIM_CORNER      = 0.024F;
    private static final float RIM_CORNER_SPAN = 0.16F;
    private static final float RIM_SWELL       = 0.14F;
    private static final float RIM_CRAWL       = 16;
    /** How long a lump of lip is along the edge, as on the research map's mouth. */
    private static final float LUMP            = 9;
    private static final float SEAM_CHANCE     = 0.55F;
    /** How much of the rim's depth is dark throat before the lips. */
    private static final float THROAT          = 0.3F;

    private static final float TENTACLE_SUNK_PX  = 2;
    private static final float TENTACLE_CURL     = 0.09F;
    /** Widths at the root, in pixels. */
    private static final int   TENTACLE_THINNEST = 6;
    private static final int   TENTACLE_FATTEST  = 10;
    private static final int   TENTACLE_TIP      = 2;
    private static final float TENTACLE_TAPER    = 0.6F;
    /** Pixels between the light and plain rings down a tentacle. */
    private static final int   TENTACLE_RING     = 4;
    private static final int   SUCKER_EVERY      = 6;
    private static final int   SUCKER_SIZE       = 2;

    private static final float TOOTH_CRAWL        = 12;
    private static final float TOOTH_CHATTER      = 0.1F;
    private static final float TOOTH_CHATTER_RATE = 1.9F;
    private static final float TOOTH_BITE         = 0.15F;

    private static final float EYE_CRAWL    = 9;
    private static final float EYE_OPEN_LAG = 5;
    private static final float EYE_OPEN     = 10;
    /** How far in the eye sits, in radii of its iris, so that its socket only just runs off the screen's edge. */
    private static final float EYE_SET      = 1.35F;
    private static final float IRIS_SIZE    = 0.24F;
    private static final float WHITES_SIZE  = 1.9F;
    /** Even wide open its lids hang half shut, narrowing it to an almond. */
    private static final float LIDS_OPEN    = 0.62F;
    private static final float SOCKET_WIDE  = 2.4F;
    private static final float SOCKET_TALL  = 1.55F;
    private static final float BLINK_HALF   = 2.5F;
    private static final float GAZE_HOLD    = 13;
    private static final float GAZE_DART    = 0.2F;
    private static final float GAZE_INWARD  = 0.55F;
    private static final float GAZE_WANDER  = 0.6F;
    private static final float PUPIL_DILATE = 0.5F;

    private static final int[] IRIS      = {EYE_IRIS, 0xFFA88CD0, 0xFFC03A30};
    private static final int[] IRIS_DARK = {EYE_IRIS_DARK, 0xFF5E4A88, 0xFF6E1A18};

    private final HymnLayout layout;
    private final float      width;
    private final float      height;
    private final float      shorter;
    private final float      time;
    private final float      pulse;
    private final long       salt;
    private final Quads      out;

    private int quads;

    private HymnMesh(HymnLayout layout, float width, float height, float time, Quads out)
    {
        this.layout = layout;
        this.width = width;
        this.height = height;
        this.shorter = Math.min(width, height);
        this.time = time;
        this.pulse = HymnTimeline.pulse(time);
        this.salt = layout.salt();
        this.out = out;
    }

    /**
     * @return how many quads went out
     */
    public static int build(HymnLayout layout, float width, float height, float time, Quads out)
    {
        if (HymnTimeline.held(time) <= 0)
            return 0;
        var mesh = new HymnMesh(layout, width, height, time, out);
        for (int index = 0; index < layout.tentacles().size(); index++)
            mesh.tentacle(layout.tentacles().get(index), index);
        layout.teeth().forEach(mesh::tooth);
        for (var edge : Edge.values())
            mesh.rim(edge);
        for (int index = 0; index < layout.eyes().size(); index++)
            mesh.eye(layout.eyes().get(index), index);
        return mesh.quads;
    }

    /**
     * How far in from its edge something may come, in pixels.
     */
    public static float reach(Edge edge, float width, float height)
    {
        return HymnLayout.REACH * (edge.horizontal() ? height : width);
    }

    private float reach(Edge edge)
    {
        return reach(edge, width, height);
    }

    private float span(Edge edge)
    {
        return edge.horizontal() ? width : height;
    }

    // ---- Rim ----

    private float rimDepth(Edge edge, float a)
    {
        float span = span(edge);
        float wave = layout.rim(edge).wave(a / span, time) / RIM_WAVE_NORM;
        float fromCorner = Math.min(a, span - a) / (shorter * RIM_CORNER_SPAN);
        float corner = Math.max(0, 1 - fromCorner);
        float depth = shorter * (RIM_BASE + RIM_WAVE * wave + RIM_CORNER * corner * corner);
        return depth * HymnTimeline.extent(time, HymnLayout.reached(a / span), RIM_CRAWL) * (1 + RIM_SWELL * pulse);
    }

    /**
     * Lips heaped in lumps along the edge, dark where they meet the screen's edge as if it were the throat, lit toward the middle of the screen and
     * outlined dark where they end.
     */
    private void rim(Edge edge)
    {
        float span = span(edge);
        int columns = (int) Math.ceil(span / CELL);
        float bulge = layout.rim(edge).phase()[0] * 10;
        for (int column = 0; column < columns; column++)
        {
            float a = (column + 0.5F) * CELL;
            int rows = Math.round(rimDepth(edge, a) / CELL);
            if (rows <= 0)
                continue;
            float lumpAt = (a + bulge) / LUMP;
            int lump = (int) Math.floor(lumpAt);
            boolean seam = lumpAt - lump < CELL / LUMP && hash(lump, edge.ordinal(), salt) < SEAM_CHANCE;
            int runFrom = 0;
            int runColor = 0;
            for (int row = 0; row < rows; row++)
            {
                int color;
                // Dark where the lips sink into the screen's edge, as if it were the throat, stepping out into flesh
                float throat = 1 - row / (rows * THROAT);
                if (row == rows - 1)
                    color = FLESH_DARK;
                else if (throat > 0.5F + bayer(column, row) * 0.5F)
                    color = GULLET_DEEP;
                else if (throat > bayer(column, row))
                    color = GULLET_MID;
                else if (seam)
                    color = FLESH_DARK;
                else
                    color = lipTone(column, row, rows, lump, edge);
                if (row > 0 && color != runColor)
                {
                    rimRun(edge, column, runFrom, row, runColor);
                    runFrom = row;
                }
                runColor = color;
            }
            rimRun(edge, column, runFrom, rows, runColor);
        }
    }

    private int lipTone(int column, int row, int rows, int lump, Edge edge)
    {
        float speck = hash(column, row + edge.ordinal() * 1000, salt);
        if (speck < 0.06F)
            return FLESH_SPECK;
        if (speck > 0.97F)
            return FLESH_SHINE;
        // Rounded toward the middle of the screen, where the light reaches the lips
        float lit = 0.15F + 0.6F * row / Math.max(1, rows - 2);
        return lit > bayer(column, row) ? fleshLit(lump) : flesh(lump);
    }

    /**
     * Rows {@code from} to {@code to} of one column of the rim, all of one colour.
     */
    private void rimRun(Edge edge, int column, int from, int to, int color)
    {
        float along = column * CELL;
        float near = from * CELL;
        float far = to * CELL;
        switch (edge)
        {
            case TOP -> rect(edge, along, near, along + CELL, far, color);
            case BOTTOM -> rect(edge, along, height - far, along + CELL, height - near, color);
            case LEFT -> rect(edge, near, along, far, along + CELL, color);
            case RIGHT -> rect(edge, width - far, along, width - near, along + CELL, color);
        }
    }

    // ---- Tentacles ----

    private float bend(Tentacle tentacle, float u)
    {
        float wave = (float) Math.sin(tentacle.waves() * Math.PI * 2 * u - tentacle.speed() * time + tentacle.phase());
        float curl = tentacle.curl() * u * u * u * (0.6F + 0.4F * (float) Math.sin(time * TENTACLE_CURL + tentacle.phase()));
        return tentacle.tilt() + tentacle.swing() * wave * (0.35F + 0.65F * u) + curl;
    }

    /**
     * A chain of blocks out of the lips, fat at its root and thinning to single blocks, ringed light and plain like the research map's tendrils, with a
     * pale sucker on its underside here and there.
     */
    private void tentacle(Tentacle tentacle, int index)
    {
        float grown = HymnTimeline.extent(time, tentacle.start(), tentacle.crawl());
        if (grown <= 0)
            return;
        var edge = tentacle.edge();
        // Walking it out bend by bend keeps its length whatever it does, so its tip never gets further in than its length
        float length = tentacle.length() * (reach(edge) + TENTACLE_SUNK_PX) * grown;
        int links = Math.max(1, Math.round(length / CELL));
        int fattest = Math.clamp(Math.round(tentacle.width() * shorter / 2), TENTACLE_THINNEST, TENTACLE_FATTEST);

        var a = new float[links + 1];
        var d = new float[links + 1];
        var side = new float[links + 1];
        var size = new int[links + 1];
        float at = tentacle.along() * span(edge);
        float in = -TENTACLE_SUNK_PX;
        for (int link = 0; link <= links; link++)
        {
            float u = link / (float) links;
            float angle = bend(tentacle, u);
            a[link] = at;
            d[link] = in;
            side[link] = angle;
            size[link] = Math.max(TENTACLE_TIP, Math.round(fattest * (float) Math.pow(1 - u, TENTACLE_TAPER) * (float) Math.sqrt(grown)));
            at += (float) Math.sin(angle) * length / links;
            in += (float) Math.cos(angle) * length / links;
        }

        for (int link = 0; link <= links; link++)
            block(edge, a[link], d[link], size[link], FLESH_DARK, 1);
        for (int link = 0; link <= links; link++)
        {
            int color = hash(link, index, salt) < 0.04F ? FLESH_SHINE : link / TENTACLE_RING % 2 == 0 ? fleshLit(index) : flesh(index);
            block(edge, a[link], d[link], size[link], color, 0);
        }
        for (int link = 2; link < links - 1; link += SUCKER_EVERY)
        {
            if (size[link] < SUCKER_SIZE * 3)
                continue;
            // Along its underside, the side turned from the way it bends
            float off = size[link] / 2F - SUCKER_SIZE;
            float angle = side[link];
            block(edge, a[link] - (float) Math.cos(angle) * off, d[link] + (float) Math.sin(angle) * off, SUCKER_SIZE, LIP_WET, 0);
        }
    }

    // ---- Teeth ----

    /**
     * A fang of bone out of the lip, tapering row by row to a point, shaded down its far side and at its tip, outlined dark, and leaning or hooking as it
     * goes.
     */
    private void tooth(Tooth tooth)
    {
        float grown = HymnTimeline.extent(time, tooth.start(), TOOTH_CRAWL);
        if (grown <= 0)
            return;
        var edge = tooth.edge();
        float a = tooth.along() * span(edge);
        float lip = rimDepth(edge, a);
        float chatter = 1 + TOOTH_CHATTER * (float) Math.sin(time * TOOTH_CHATTER_RATE + tooth.phase()) + TOOTH_BITE * pulse;
        int rows = Math.round(tooth.length() * shorter * chatter * grown);
        if (rows <= 0)
            return;
        float halfWidth = tooth.width() * shorter / 2;
        // From under the dark edge of the lip
        float base = Math.round(lip) - 1;

        for (int pass = 0; pass < 2; pass++)
        {
            for (int row = 0; row < rows; row++)
            {
                float toTip = row / (float) rows;
                float lean = tooth.lean() * row + tooth.hook() * Math.min(row, rows - row);
                int half = Math.max(0, Math.round(halfWidth * (1 - toTip) - 0.25F));
                int centre = Math.round(a + lean);
                float in = base + row;
                if (pass == 0)
                    strip(edge, centre - half, centre + half + 1, in, FLESH_DARK, 1);
                else if (row == rows - 1 || half == 0)
                    strip(edge, centre - half, centre + half + 1, in, BONE_SHADE, 0);
                else
                {
                    strip(edge, centre - half, centre + half, in, BONE, 0);
                    strip(edge, centre + half, centre + half + 1, in, BONE_SHADE, 0);
                }
            }
        }
    }

    // ---- Eyes ----

    private float blinking(Eye eye)
    {
        float closed = 0;
        for (float blink : eye.blinks())
            closed = Math.max(closed, 1 - Math.abs(time - blink) / BLINK_HALF);
        return closed;
    }

    private float glance(Eye eye, float offset)
    {
        float slots = (time + eye.phase() * 10) / GAZE_HOLD;
        float slot = (float) Math.floor(slots);
        float dart = HymnTimeline.clamp((slots - slot - (1 - GAZE_DART)) / GAZE_DART);
        return lerp(dart, noise(slot, eye.phase() + offset), noise(slot + 1, eye.phase() + offset));
    }

    private static float noise(float slot, float offset)
    {
        double value = Math.sin(slot * 12.9898 + offset * 78.233) * 43758.5453;
        return (float) (value - Math.floor(value)) * 2 - 1;
    }

    /**
     * Set in a lump of flesh on the lip, drawn a unit at a time as the eye down the research map's ravine is: a bloodshot rim, veined whites and an iris
     * with torn slits, one, two or three of them. Its lids open along its edge, so the eyes up the sides of the screen stand on end.
     */
    private void eye(Eye eye, int index)
    {
        float grown = HymnTimeline.extent(time, eye.start(), EYE_CRAWL);
        if (grown <= 0)
            return;
        var edge = eye.edge();
        float a = eye.along() * span(edge);
        float radius = eye.size() * shorter * IRIS_SIZE * grown;
        float centreD = radius * EYE_SET;
        float open = HymnTimeline.extent(time, eye.start() + EYE_OPEN_LAG, EYE_OPEN) * (1 - blinking(eye));
        int cx = Math.round(x(edge, a, centreD));
        int cy = Math.round(y(edge, a, centreD));
        boolean upright = !edge.horizontal();

        // The socket, a lump of lip
        float socketX = radius * SOCKET_WIDE;
        float socketY = radius * SOCKET_TALL;
        int columns = (int) Math.ceil(socketX / CELL);
        int rows = (int) Math.ceil(socketY / CELL);
        for (int row = -rows; row < rows; row++)
            for (int column = -columns; column < columns; column++)
            {
                float reachOut = (float) Math.hypot((column + 0.5F) * CELL / socketX, (row + 0.5F) * CELL / socketY);
                if (reachOut > 1)
                    continue;
                int color = reachOut > 0.88F ? FLESH_DARK : 0.55F - row * CELL / socketY * 0.4F > bayer(column, row) ? fleshLit(index) : flesh(index);
                eyeRect(edge, cx, cy, upright, column * CELL, row * CELL, CELL, color);
            }
        if (open <= 0.02F || radius < 1)
            return;

        // In the eye's own frame x runs along the edge and y in from it. Mostly looking in at the player, glancing about now and then
        float inward = edge == Edge.TOP || edge == Edge.LEFT ? 1 : -1;
        float gazeX = GAZE_WANDER * glance(eye, 0) * radius * 0.35F;
        float gazeY = (GAZE_INWARD * inward + GAZE_WANDER * 0.5F * glance(eye, 3.7F)) * radius * 0.25F;

        float rim = radius * WHITES_SIZE;
        float lid = rim * open * LIDS_OPEN;
        int span = (int) Math.ceil(rim);
        float slitWide = Math.max(0.6F, radius * 0.14F) * (1 + PUPIL_DILATE * pulse) * (eye.pupils() > 1 ? 0.8F : 1);
        float pupilReach = radius * (eye.pupils() > 1 ? 0.55F : 0.85F);
        for (int y = -span; y <= span; y++)
        {
            if (Math.abs(y) > lid)
                continue;
            int runFrom = 0;
            int runColor = 0;
            for (int x = -span; x <= span + 1; x++)
            {
                float reachOut = x > span ? 2 : (float) Math.hypot(x / rim, y / Math.max(1, lid));
                if (reachOut > 1)
                {
                    if (runColor != 0)
                        eyeRun(edge, cx, cy, upright, runFrom, x, y, runColor);
                    runColor = 0;
                    continue;
                }
                float speck = hash(x, y, salt + index);
                float fromIris = (float) Math.hypot(x - gazeX, y - gazeY) / radius;
                int color;
                if (reachOut > 0.88F)
                    color = EYE_RIM;
                else if (fromIris > 1)
                    color = speck < 0.3F ? EYE_VEIN : EYE_WHITE;
                else if (pupil(eye, x, y, gazeX, gazeY, radius, slitWide, pupilReach, index))
                    color = EYE_PUPIL;
                else
                    color = fromIris > 0.7F || speck < 0.15F ? IRIS_DARK[eye.iris()] : IRIS[eye.iris()];
                if (color != runColor)
                {
                    if (runColor != 0)
                        eyeRun(edge, cx, cy, upright, runFrom, x, y, runColor);
                    runFrom = x;
                    runColor = color;
                }
            }
        }
    }

    private void eyeRun(Edge edge, int cx, int cy, boolean upright, int fromX, int toX, int y, int color)
    {
        if (upright)
            rect(edge, cx + y, cy + fromX, cx + y + 1, cy + toX, color);
        else
            rect(edge, cx + fromX, cy + y, cx + toX, cy + y + 1, color);
    }

    private boolean pupil(Eye eye, int x, int y, float gazeX, float gazeY, float radius, float slitWide, float reach, int index)
    {
        for (int pupil = 0; pupil < eye.pupils(); pupil++)
        {
            float px = gazeX;
            if (eye.pupils() > 1)
                px += (pupil - (eye.pupils() - 1) / 2F) * radius * 0.62F;
            // The slit wanders as it goes down, so the pupil looks torn rather than drawn
            float slit = px + (float) Math.sin(y * 0.9F + index + pupil * 2.1F) * radius * 0.12F;
            if (eye.slit())
            {
                if (Math.abs(x - slit) < slitWide && Math.abs(y - gazeY) < reach)
                    return true;
            }
            else if (Math.hypot(x - px, y - gazeY) < radius * (eye.pupils() > 1 ? 0.24F : 0.34F) * (1 + PUPIL_DILATE * pulse * 0.5F))
                return true;
        }
        return false;
    }

    private void eyeRect(Edge edge, int cx, int cy, boolean upright, float x, float y, float size, int color)
    {
        float sx = upright ? y : x;
        float sy = upright ? x : y;
        rect(edge, cx + sx, cy + sy, cx + sx + size, cy + sy + size, color);
    }

    // ---- Output ----

    /**
     * A row one pixel deep in the edge's frame, from {@code from} to {@code to} along it, or its dark outline a pixel around it.
     */
    private void strip(Edge edge, float from, float to, float in, int color, int outline)
    {
        float x0 = x(edge, from, in);
        float y0 = y(edge, from, in);
        float x1 = x(edge, to, in + 1);
        float y1 = y(edge, to, in + 1);
        rect(edge, Math.min(x0, x1) - outline, Math.min(y0, y1) - outline, Math.max(x0, x1) + outline, Math.max(y0, y1) + outline, color);
    }

    /**
     * A block of flesh centred on a point of the edge's frame, snapped to the grid of blocks, or the dark outline a pixel around it.
     */
    private void block(Edge edge, float a, float d, int size, int color, int outline)
    {
        float x = x(edge, a, d);
        float y = y(edge, a, d);
        float left = Math.round((x - size / 2F) / CELL) * CELL;
        float top = Math.round((y - size / 2F) / CELL) * CELL;
        rect(edge, left - outline, top - outline, left + size + outline, top + size + outline, color);
    }

    /**
     * An axis-aligned rectangle in screen space, cut to how far its edge may reach.
     */
    private void rect(Edge edge, float x0, float y0, float x1, float y1, int color)
    {
        float reach = reach(edge);
        switch (edge)
        {
            case TOP -> y1 = Math.min(y1, reach);
            case BOTTOM -> y0 = Math.max(y0, height - reach);
            case LEFT -> x1 = Math.min(x1, reach);
            case RIGHT -> x0 = Math.max(x0, width - reach);
        }
        if (x1 <= x0 || y1 <= y0)
            return;
        // Counter-clockwise on screen, as the GUI culls the other way round
        out.quad(x0, y0, color, x0, y1, color, x1, y1, color, x1, y0, color);
        quads++;
    }

    private float x(Edge edge, float a, float d)
    {
        return switch (edge)
        {
            case TOP, BOTTOM -> a;
            case LEFT -> d;
            case RIGHT -> width - d;
        };
    }

    private float y(Edge edge, float a, float d)
    {
        return switch (edge)
        {
            case TOP -> d;
            case BOTTOM -> height - d;
            case LEFT, RIGHT -> a;
        };
    }

    private static float lerp(float delta, float from, float to)
    {
        return from + (to - from) * delta;
    }
}
