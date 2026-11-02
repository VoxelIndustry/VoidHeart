package net.voxelindustry.voidheart.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.client.screen.ResearchLayout.Polar;
import net.voxelindustry.voidheart.client.screen.ResearchLayout.Projection;
import net.voxelindustry.voidheart.client.screen.ResearchScreen.NodeState;
import net.voxelindustry.voidheart.common.content.research.Research;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The hole in the slab: an eye that opens as knowledge fills, rings of research around it, and the dark beyond them.
 */
final class ResearchMapView
{
    static final Identifier SPRITES = Identifier.fromNamespaceAndPath(MODID, "textures/gui/research.png");
    static final int        ATLAS   = 256;

    static final int NODE_SIZE = 24;

    private static final int GLYPH_ROW     = 24;
    private static final int EYE_ROW       = 48;
    private static final int SCLERA_W      = 56;
    private static final int SCLERA_H      = 36;
    private static final int IRIS_U        = 56;
    private static final int IRIS_SIZE     = 24;
    private static final int PUPIL_U       = 80;
    private static final int PUPIL_W       = 6;
    private static final int PUPIL_H       = 20;
    private static final int LARGE_EYE_MIN = 350;

    private static final float MIN_OPEN = 0.24F;
    private static final float LOWER_LID_REST = 0.5F;
    private static final int   UPPER_LID_EDGE = 2;
    private static final float LOWER_LID = 0.86F;

    private static final int  LABEL_WIDTH     = 78;
    static final int          BOTTOM_BAND     = 40;
    private static final int  MOTES_MAX       = 40;
    private static final int  MOTES_REACH     = 9;
    private static final float MOTES_SPEED    = 0.00011F;
    private static final int  LABEL_DROP      = 4;
    private static final int  LABEL_BACK_COLOR = 0xC8050308;
    private static final int  TEAR_CLEARANCE  = 18;
    private static final int  LINK_PIECES     = 20;
    private static final int  BLOOM_DOTS      = 32;
    private static final int  WATCHERS        = 9;
    private static final int  WATCHER_FLINCH  = 46;
    private static final long BLOOM_MILLIS    = 1100;
    private static final float BLOOM_REACH    = 44;
    private static final float FINISH_REACH   = 110;
    private static final long FINISH_MILLIS   = 1800;
    private static final long APPEAR_MILLIS   = 1200;
    private static final long BREATH_MILLIS   = 2600;
    private static final long BLINK_EVERY     = 8300;
    private static final long BLINK_MILLIS    = 190;

    private static final float GLOW_HALF_WIDTH = 1.6F;
    private static final float CORE_HALF_WIDTH = 0.55F;
    private static final float WOBBLE          = 2.4F;
    private static final float RISE            = 0.38F;
    private static final double LANE           = 0.68;
    private static final double LANE_SPREAD    = 0.14;
    private static final double SAME_RING_BOW  = 0.09;

    private record Link(Identifier parent, Identifier child, float[] xs, float[] ys, float length)
    {
    }

    private record Watcher(int x, int y, float phase)
    {
    }

    private record Bloom(Identifier id, long start, boolean finished)
    {
    }

    private final Font                      font;
    private final Map<Identifier, Research> tree;
    private final Map<Identifier, Polar>    polar;
    private final Projection                projection;
    private final List<Link>                links = new ArrayList<>();
    private final List<Watcher>             watchers = new ArrayList<>();
    private final List<Bloom>               blooms = new ArrayList<>();
    private final Map<Identifier, Long>     appeared = new HashMap<>();
    private final int                       left;
    private final int                       right;
    private final int                       mapWidth;
    private final int                       eyeScale;
    private long                            startledAt = -1;

    ResearchMapView(Font font, Map<Identifier, Research> tree, int left, int right, int height, long seed)
    {
        this.font = font;
        this.tree = tree;
        this.polar = ResearchLayout.compute(tree);
        this.left = left;
        this.right = right;
        this.mapWidth = right - left;

        double radiusY = Math.min((height - BOTTOM_BAND) / 2D - NODE_SIZE / 2D - 4, 300);
        double radiusX = Math.min(mapWidth / 2D - NODE_SIZE / 2D - 8, radiusY * 1.4);
        this.projection = new Projection((left + right) / 2D, (height - BOTTOM_BAND) / 2D, radiusX, radiusY);
        this.eyeScale = height >= LARGE_EYE_MIN ? 2 : 1;

        tree.forEach((id, research) ->
        {
            if (research.requires().isEmpty())
                links.add(linkOf(null, id));
            research.requires().stream().filter(tree::containsKey).forEach(parent -> links.add(linkOf(parent, id)));
        });

        var random = new Random(seed);
        for (int i = 0; i < WATCHERS * 8 && watchers.size() < WATCHERS; i++)
        {
            int x = left + 14 + random.nextInt(Math.max(1, mapWidth - 28));
            int y = 12 + random.nextInt(Math.max(1, height - 24));
            double dx = (x - projection.centerX()) / radiusX;
            double dy = (y - projection.centerY()) / radiusY;
            if (dx * dx + dy * dy > 0.8)
                watchers.add(new Watcher(x, y, random.nextFloat()));
        }
    }

    Projection projection()
    {
        return projection;
    }

    private Link linkOf(Identifier parent, Identifier child)
    {
        var to = polar.get(child);
        var from = parent == null ? new Polar(ResearchLayout.EYE_FRACTION, to.angle()) : polar.get(parent);

        double delta = to.angle() - from.angle();
        delta = Math.atan2(Math.sin(delta), Math.cos(delta));
        // A branch leaning the same way along its whole length reads as grown rather than wired
        float lean = (child.hashCode() & 1) == 0 ? WOBBLE : -WOBBLE;
        // Branches sharing a stretch of lane are kept on separate tracks, so they can each be followed
        double spread = LANE_SPREAD * ((Math.floorMod(child.hashCode(), 5) / 4D) - 0.5);

        var xs = new float[LINK_PIECES + 1];
        var ys = new float[LINK_PIECES + 1];
        float length = 0;
        for (int i = 0; i <= LINK_PIECES; i++)
        {
            double t = i / (double) LINK_PIECES;
            double angle = from.angle() + delta * (t + smooth(t)) / 2;
            double fraction = laneFraction(from.fraction(), to.fraction(), spread, t);
            double offset = Math.sin(t * Math.PI) * lean;
            xs[i] = (float) (projection.x(fraction, angle) + Math.cos(angle) * offset);
            ys[i] = (float) (projection.y(fraction, angle) + Math.sin(angle) * offset);
            if (i > 0)
                length += (float) Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
        }
        return new Link(parent, child, xs, ys, length);
    }

    /**
     * A branch rises off its research, sweeps around in the lane between two rings where no research sits, then settles onto its target.
     */
    private static double laneFraction(double from, double to, double spread, double t)
    {
        double lane = to > from ? from + (to - from) * (LANE + spread) : from + SAME_RING_BOW;
        if (t < RISE)
            return from + (lane - from) * smooth(t / RISE);
        if (t > 1 - RISE)
            return lane + (to - lane) * smooth((t - (1 - RISE)) / RISE);
        return lane;
    }

    private static double smooth(double t)
    {
        double clamped = Math.clamp(t, 0, 1);
        return clamped * clamped * (3 - 2 * clamped);
    }

    int nodeX(Identifier id)
    {
        var at = polar.get(id);
        return (int) Math.round(projection.x(at.fraction(), at.angle()));
    }

    int nodeY(Identifier id)
    {
        var at = polar.get(id);
        return (int) Math.round(projection.y(at.fraction(), at.angle()));
    }

    /**
     * Where the light falls: around the eye, and around all that is known or within reach.
     */
    List<ResearchVoid.Light> lights()
    {
        var lights = new ArrayList<ResearchVoid.Light>();
        double reach = Math.max(38, projection.radiusY() * 0.5);
        lights.add(new ResearchVoid.Light(projection.centerX(), projection.centerY(), reach * 0.9, 0.95));
        tree.forEach((id, research) ->
        {
            var state = ResearchScreen.stateOf(id, research);
            if (state != NodeState.HIDDEN)
                lights.add(new ResearchVoid.Light(nodeX(id), nodeY(id), reach * (state == NodeState.UNKNOWN ? 0.42 : 0.62), state == NodeState.UNKNOWN ? 0.34 : 0.62));
        });
        return lights;
    }

    List<ResearchVoid.Ring> rings()
    {
        int tiers = tree.values().stream().mapToInt(Research::tier).max().orElse(1);
        var rings = new ArrayList<ResearchVoid.Ring>();
        for (int tier = 1; tier <= tiers; tier++)
        {
            double fraction = ResearchLayout.fractionOf(tier, tiers);
            rings.add(new ResearchVoid.Ring(projection.centerX(), projection.centerY(), projection.radiusX() * fraction, projection.radiusY() * fraction));
        }
        return rings;
    }

    void bloom(Identifier id, boolean finished)
    {
        blooms.add(new Bloom(id, Util.getMillis(), finished));
    }

    void appear(Identifier id)
    {
        appeared.put(id, Util.getMillis());
    }

    Optional<Identifier> nodeAt(double mouseX, double mouseY)
    {
        return tree.entrySet().stream()
                .filter(entry -> ResearchScreen.stateOf(entry.getKey(), entry.getValue()) != NodeState.HIDDEN)
                .filter(entry -> Math.abs(mouseX - nodeX(entry.getKey())) <= NODE_SIZE / 2D && Math.abs(mouseY - nodeY(entry.getKey())) <= NODE_SIZE / 2D)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * Where an insight joins the others circling the eye.
     */
    int orbitY()
    {
        return (int) projection.centerY() - SCLERA_H * eyeScale / 2 - MOTES_REACH;
    }

    void startle(long at)
    {
        startledAt = at;
    }

    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, Identifier background, int screenW, int screenH,
                 float fullness, boolean flowing, boolean canRefine, Identifier selected, Identifier hovered)
    {
        long now = Util.getMillis();
        var lineage = new HashSet<Identifier>();
        addLineage(selected, lineage);
        addLineage(hovered, lineage);
        var running = new HashSet<Identifier>();
        ResearchScreen.snapshot.active().ifPresent(active -> addLineage(active, running));

        extractWatchers(graphics, mouseX, mouseY, now);
        extractEmbers(graphics, now);
        links.forEach(link -> extractLink(graphics, link, lineage, running, flowing, now));
        extractEye(graphics, running, mouseX, mouseY, background, screenW, screenH, fullness, canRefine, now);
        extractInsights(graphics, now);
        tree.forEach((id, research) -> extractNode(graphics, id, research, now));
        extractMarks(graphics, selected, hovered);
        tree.forEach((id, research) -> extractLabel(graphics, id, research, selected, hovered, screenH));
        extractBlooms(graphics, screenH, now);
    }

    private void addLineage(Identifier id, Set<Identifier> into)
    {
        for (var at = id; at != null && tree.containsKey(at) && into.add(at); at = ResearchLayout.parentOf(tree.get(at), tree))
        {
        }
    }

    /**
     * Pairs of eyes in the dark. They close when the cursor comes near.
     */
    private void extractWatchers(GuiGraphicsExtractor graphics, int mouseX, int mouseY, long now)
    {
        for (var watcher : watchers)
        {
            float wave = (float) Math.sin((now / 7000D + watcher.phase()) * Math.PI * 2);
            if (wave < 0.35F || Math.hypot(mouseX - watcher.x(), mouseY - watcher.y()) < WATCHER_FLINCH)
                continue;

            int color = ARGB.color((int) (70 + 150 * (wave - 0.35F) / 0.65F), ResearchScreen.KNOWN_RGB);
            graphics.fill(watcher.x(), watcher.y(), watcher.x() + 1, watcher.y() + 3, color);
            graphics.fill(watcher.x() + 4, watcher.y(), watcher.x() + 5, watcher.y() + 3, color);
        }
    }

    private void extractEmbers(GuiGraphicsExtractor graphics, long now)
    {
        tree.forEach((id, research) ->
        {
            if (ResearchScreen.stateOf(id, research) != NodeState.HIDDEN)
                return;

            float wave = 0.5F + 0.5F * (float) Math.sin(now / 1900D + id.hashCode() * 0.7);
            int x = nodeX(id);
            int y = nodeY(id);
            graphics.fill(x - 1, y - 1, x + 1, y + 1, ARGB.color((int) (70 + 90 * wave), ResearchScreen.REVEALED_RGB));
            graphics.fill(x - 3, y, x + 3, y + 1, ARGB.color((int) (18 + 26 * wave), ResearchScreen.REVEALED_RGB));
            graphics.fill(x, y - 3, x + 1, y + 3, ARGB.color((int) (18 + 26 * wave), ResearchScreen.REVEALED_RGB));
        });
    }

    private void extractLink(GuiGraphicsExtractor graphics, Link link, Set<Identifier> lineage, Set<Identifier> running, boolean flowing, long now)
    {
        var child = tree.get(link.child());
        var state = ResearchScreen.stateOf(link.child(), child);
        if (state == NodeState.HIDDEN)
            return;
        if (link.parent() != null && ResearchScreen.stateOf(link.parent(), tree.get(link.parent())) == NodeState.HIDDEN)
            return;

        boolean walked = link.parent() == null || ResearchScreen.snapshot.completed().contains(link.parent());
        boolean burning = running.contains(link.child());
        boolean traced = lineage.contains(link.child());
        int rgb = burning ? ResearchScreen.ACTIVE_RGB : state == NodeState.DONE ? ResearchScreen.KNOWN_RGB : walked ? ResearchScreen.FRONTIER_RGB : ResearchScreen.REVEALED_RGB;
        float strength = traced || burning ? 1 : state == NodeState.DONE ? 0.62F : walked ? 0.78F : 0.3F;

        for (int i = 0; i < LINK_PIECES; i++)
        {
            float fade = strength;
            // What has not been read yet is only reached for, the branch thinning out before it arrives
            if (state == NodeState.UNKNOWN)
                fade *= 1 - 0.62F * (i + 1) / LINK_PIECES;
            TravelMapScreen.line(graphics, link.xs()[i], link.ys()[i], link.xs()[i + 1], link.ys()[i + 1], GLOW_HALF_WIDTH, ARGB.color((int) (48 * fade), rgb));
            TravelMapScreen.line(graphics, link.xs()[i], link.ys()[i], link.xs()[i + 1], link.ys()[i + 1], CORE_HALF_WIDTH, ARGB.color((int) (235 * fade), rgb));
        }

        if (burning)
            extractMotes(graphics, link, ResearchScreen.ACTIVE_RGB, flowing ? 20 : 34, flowing ? 26 : 5, 230, now);
        else if (state == NodeState.DONE)
            extractMotes(graphics, link, ResearchScreen.KNOWN_RGB, 64, 7, 110, now);
        else if (walked)
            extractMotes(graphics, link, ResearchScreen.FRONTIER_RGB, 34, 13, state == NodeState.UNKNOWN ? 120 : 190, now);
    }

    /**
     * Always flowing from the eye outward, so the eye reads as where all of it comes from.
     */
    private void extractMotes(GuiGraphicsExtractor graphics, Link link, int rgb, float spacing, float speed, int alpha, long now)
    {
        float drift = (now % 10_000_000L) / 1000F * speed;
        for (float along = drift % spacing; along < link.length(); along += spacing)
        {
            float t = along / link.length() * LINK_PIECES;
            int piece = Math.min(LINK_PIECES - 1, (int) t);
            float within = t - piece;
            float x = link.xs()[piece] + (link.xs()[piece + 1] - link.xs()[piece]) * within;
            float y = link.ys()[piece] + (link.ys()[piece + 1] - link.ys()[piece]) * within;
            float angle = (float) Math.atan2(link.ys()[piece + 1] - link.ys()[piece], link.xs()[piece + 1] - link.xs()[piece]);
            float ends = Math.min(1, Math.min(along, link.length() - along) / 8);

            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.rotate(angle - (float) (Math.PI / 2));
            pose.scale(0.6F, 1);
            graphics.fillGradient(-1, -6, 1, 0, ARGB.color(0, rgb), ARGB.color((int) (alpha * ends), rgb));
            pose.popMatrix();
        }
    }

    /**
     * Lids are the background itself drawn back over the eye a column at a time, so they cut any curve at no more than a few hundred quads.
     */
    private void extractEye(GuiGraphicsExtractor graphics, Set<Identifier> running, int mouseX, int mouseY, Identifier background, int screenW, int screenH, float fullness, boolean canRefine, long now)
    {
        int centerX = (int) projection.centerX();
        int centerY = (int) projection.centerY();
        int halfW = SCLERA_W * eyeScale / 2;
        int halfH = SCLERA_H * eyeScale / 2;

        float blink = 1;
        long intoBlink = now % BLINK_EVERY;
        if (intoBlink < BLINK_MILLIS)
            blink = Math.abs(intoBlink / (BLINK_MILLIS / 2F) - 1);

        float startle = 0;
        for (var bloom : blooms)
            startle = Math.max(startle, 1 - (now - bloom.start()) / (float) (bloom.finished() ? FINISH_MILLIS : BLOOM_MILLIS));

        if (startledAt >= 0 && now >= startledAt)
            startle = Math.max(startle, 1 - (now - startledAt) / (float) BLOOM_MILLIS);

        // The upper lid carries the closing, so a hungry eye looks hooded rather than squashed
        float open = Math.clamp(MIN_OPEN + (1 - MIN_OPEN) * fullness + startle * 0.5F, 0, 1);
        float upperOpen = open * blink;
        float lowerOpen = (LOWER_LID_REST + (1 - LOWER_LID_REST) * open) * LOWER_LID * blink;

        graphics.blit(RenderPipelines.GUI_TEXTURED, SPRITES, centerX - halfW, centerY - halfH, 0, EYE_ROW, SCLERA_W * eyeScale, SCLERA_H * eyeScale, SCLERA_W, SCLERA_H, ATLAS, ATLAS);

        float gazeX = Math.clamp((mouseX - centerX) / 150F, -1, 1) * 11 * eyeScale;
        float gazeY = Math.clamp((mouseY - centerY) / 110F, -1, 1) * 4 * eyeScale;
        int irisSize = IRIS_SIZE * eyeScale;
        int irisX = centerX + (int) gazeX - irisSize / 2;
        int irisY = centerY + (int) gazeY - irisSize / 2;
        int irisRgb = ResearchScreen.snapshot.active().isPresent() ? ResearchScreen.ACTIVE_RGB : ResearchScreen.FRONTIER_RGB;
        graphics.blit(RenderPipelines.GUI_TEXTURED, SPRITES, irisX, irisY, IRIS_U, EYE_ROW, irisSize, irisSize, IRIS_SIZE, IRIS_SIZE, ATLAS, ATLAS, ARGB.color(255, irisRgb));

        int pupilW = Math.max(2, Math.round((2 + 4 * (1 - fullness) + startle * -2) * eyeScale));
        int pupilH = PUPIL_H * eyeScale;
        graphics.blit(RenderPipelines.GUI_TEXTURED, SPRITES, centerX + (int) gazeX - pupilW / 2, centerY + (int) gazeY - pupilH / 2, PUPIL_U, EYE_ROW, pupilW, pupilH, PUPIL_W, PUPIL_H, ATLAS, ATLAS);

        // Knowledge enough for an insight shows as a lid breathing towards the frontier's colour
        float ready = canRefine ? 0.5F + 0.5F * (float) Math.sin(now * Math.PI * 2 / BREATH_MILLIS) : 0;
        int lidColor = ARGB.srgbLerp(ready, ResearchScreen.LID_COLOR, 0xFF000000 | ResearchScreen.FRONTIER_RGB);

        for (int column = -halfW; column < halfW; column += eyeScale)
        {
            float across = (column + eyeScale / 2F) / halfW;
            int upper = Math.round(lidHeight(across, halfH, upperOpen));
            int lower = Math.round(lidHeight(across, halfH, lowerOpen));
            int x = centerX + column;

            graphics.blit(RenderPipelines.GUI_TEXTURED, background, x, centerY - halfH - 1, x, centerY - halfH - 1, eyeScale, halfH + 1 - upper, screenW, screenH);
            graphics.blit(RenderPipelines.GUI_TEXTURED, background, x, centerY + lower, x, centerY + lower, eyeScale, halfH + 1 - lower, screenW, screenH);
        }

        // Between the background and the lids' edges, so that a branch ends tucked under the lid whatever its slant
        extractRoots(graphics, running, centerX, centerY, halfW, halfH, upperOpen, lowerOpen);

        for (int column = -halfW; column < halfW; column += eyeScale)
        {
            float across = (column + eyeScale / 2F) / halfW;
            int upper = Math.round(lidHeight(across, halfH, upperOpen));
            int lower = Math.round(lidHeight(across, halfH, lowerOpen));
            int x = centerX + column;

            graphics.fill(x, centerY - upper - eyeScale * UPPER_LID_EDGE, x + eyeScale, centerY - upper, lidColor);
            graphics.fill(x, centerY + lower, x + eyeScale, centerY + lower + eyeScale, ResearchScreen.LID_LOW_COLOR);
        }
    }

    /**
     * Insights kept are motes circling the eye, so that having them is seen rather than read.
     */
    private void extractInsights(GuiGraphicsExtractor graphics, long now)
    {
        int count = Math.min(ResearchScreen.snapshot.insights(), MOTES_MAX);
        double radiusX = SCLERA_W * eyeScale / 2D + MOTES_REACH;
        double radiusY = SCLERA_H * eyeScale / 2D + MOTES_REACH;
        for (int mote = 0; mote < count; mote++)
        {
            double angle = mote * Math.PI * 2 / count + now * MOTES_SPEED;
            int x = (int) (projection.centerX() + Math.cos(angle) * radiusX);
            int y = (int) (projection.centerY() + Math.sin(angle) * radiusY);
            graphics.fill(x, y, x + 2, y + 2, ARGB.color(230, ResearchScreen.FRONTIER_RGB));
        }
    }

    private static float lidHeight(float across, int halfH, float open)
    {
        return halfH * open * (float) Math.pow(Math.max(0, 1 - across * across), 0.72);
    }

    /**
     * Root branches start outside of the eye at its widest, so they are carried in to wherever the lids are now.
     */
    private void extractRoots(GuiGraphicsExtractor graphics, Set<Identifier> running, int centerX, int centerY, int halfW, int halfH, float upperOpen, float lowerOpen)
    {
        for (var link : links)
        {
            if (link.parent() != null || ResearchScreen.stateOf(link.child(), tree.get(link.child())) == NodeState.HIDDEN)
                continue;

            float dx = link.xs()[0] - centerX;
            float dy = Math.abs(link.ys()[0] - centerY);
            float side = link.ys()[0] < centerY ? -1 : 1;
            float open = side < 0 ? upperOpen : lowerOpen;
            float tuck = side < 0 ? eyeScale * UPPER_LID_EDGE / 2F : eyeScale / 2F;

            // Follows the branch's own line in until it meets the lid, which itself depends on where along the eye that is
            float x = centerX + dx * 0.35F;
            float height = 0;
            for (int step = 0; step < 3; step++)
            {
                height = lidHeight((x - centerX) / halfW, halfH, open);
                x = centerX + (dy > 1 ? dx * Math.min(1, height / dy) : dx);
            }
            float y = centerY + (height + tuck) * side;

            var state = ResearchScreen.stateOf(link.child(), tree.get(link.child()));
            int rgb = running.contains(link.child()) ? ResearchScreen.ACTIVE_RGB : state == NodeState.DONE ? ResearchScreen.KNOWN_RGB : ResearchScreen.FRONTIER_RGB;
            TravelMapScreen.line(graphics, x, y, link.xs()[0], link.ys()[0], GLOW_HALF_WIDTH, ARGB.color(40, rgb));
            TravelMapScreen.line(graphics, x, y, link.xs()[0], link.ys()[0], CORE_HALF_WIDTH, ARGB.color(190, rgb));
        }
    }

    private void extractNode(GuiGraphicsExtractor graphics, Identifier id, Research research, long now)
    {
        var state = ResearchScreen.stateOf(id, research);
        if (state == NodeState.HIDDEN)
            return;

        int x = nodeX(id) - NODE_SIZE / 2;
        int y = nodeY(id) - NODE_SIZE / 2;
        float presence = 1;
        var since = appeared.get(id);
        if (since != null)
            presence = Math.min(1, (now - since) / (float) APPEAR_MILLIS);

        boolean ready = state == NodeState.UNKNOWN && ResearchScreen.canReveal(research);
        if (state == NodeState.ACTIVE || state == NodeState.REVEALED || ready)
        {
            float breath = 0.5F + 0.5F * (float) Math.sin(now * Math.PI * 2 / BREATH_MILLIS + id.hashCode());
            sprite(graphics, 6, x, y, ARGB.color((int) (presence * (40 + 150 * breath)), state == NodeState.ACTIVE ? ResearchScreen.ACTIVE_RGB : ResearchScreen.FRONTIER_RGB));
        }

        int cell = switch (state)
        {
            case DONE -> 0;
            case REVEALED -> 1;
            case ACTIVE -> 2;
            default -> ready ? 4 : 3;
        };
        sprite(graphics, cell, x, y, ARGB.color((int) (presence * 255), 0xFFFFFF));

        double progress = ResearchScreen.snapshot.progress().getOrDefault(id, 0D) / research.knowledgeCost();
        if (state != NodeState.DONE && progress > 0)
        {
            int filled = (int) Math.ceil(NODE_SIZE * Math.min(1, progress));
            graphics.enableScissor(x, y + NODE_SIZE - filled, x + NODE_SIZE, y + NODE_SIZE);
            sprite(graphics, 5, x, y, ARGB.color(state == NodeState.ACTIVE ? 170 : 90, state == NodeState.ACTIVE ? ResearchScreen.ACTIVE_RGB : ResearchScreen.FRONTIER_RGB));
            graphics.disableScissor();
        }

        if (state != NodeState.UNKNOWN)
            graphics.item(iconOf(research), x + 4, y + 4);
    }

    /**
     * A name only shows for what is running and for what the cursor is on. It never leaves the hole in the stone,
     * and the running name gives way rather than cover another research.
     */
    private void extractLabel(GuiGraphicsExtractor graphics, Identifier id, Research research, Identifier selected, Identifier hovered, int screenH)
    {
        var state = ResearchScreen.stateOf(id, research);
        boolean isHovered = id.equals(hovered);
        if (state == NodeState.HIDDEN || id.equals(selected) || !isHovered && state != NodeState.ACTIVE)
            return;

        var lines = font.split(state == NodeState.UNKNOWN ? Component.translatable("voidheart.research.unread") : Component.translatable(Research.nameKey(id)), LABEL_WIDTH);
        int shown = Math.min(2, lines.size());
        int color = state == NodeState.UNKNOWN ? ResearchScreen.MUTED_COLOR : state == NodeState.DONE ? ResearchScreen.KNOWN_COLOR : state == NodeState.ACTIVE ? ResearchScreen.ACTIVE_COLOR : ResearchScreen.FRONTIER_COLOR;
        int widest = lines.stream().limit(shown).mapToInt(font::width).max().orElse(0);
        int centerX = Math.clamp(nodeX(id), left + TEAR_CLEARANCE + widest / 2, Math.max(left + TEAR_CLEARANCE + widest / 2, right - TEAR_CLEARANCE - widest / 2));

        int height = shown * font.lineHeight;
        int top = nodeY(id) + NODE_SIZE / 2 + LABEL_DROP;
        if (top + height > screenH - 2 || coversResearch(id, centerX, top, widest, height))
        {
            int above = nodeY(id) - NODE_SIZE / 2 - LABEL_DROP - height;
            if (above >= 2 && !coversResearch(id, centerX, above, widest, height))
                top = above;
            else if (!isHovered)
                return;
        }

        for (int line = 0; line < shown; line++)
        {
            int textY = top + line * font.lineHeight;
            int halfWidth = font.width(lines.get(line)) / 2 + 2;
            graphics.fill(centerX - halfWidth, textY - 1, centerX + halfWidth, textY + font.lineHeight - 1, LABEL_BACK_COLOR);
            graphics.centeredText(font, lines.get(line), centerX, textY, color);
        }
    }

    private boolean coversResearch(Identifier id, int centerX, int top, int width, int height)
    {
        if (Math.abs(centerX - projection.centerX()) < width / 2D + SCLERA_W * eyeScale / 2D && Math.abs(top + height / 2D - projection.centerY()) < height / 2D + SCLERA_H * eyeScale / 2D)
            return true;
        for (var other : tree.keySet())
        {
            if (other.equals(id) || ResearchScreen.stateOf(other, tree.get(other)) == NodeState.HIDDEN)
                continue;
            if (Math.abs(nodeX(other) - centerX) < width / 2 + NODE_SIZE / 2 && nodeY(other) + NODE_SIZE / 2 > top && nodeY(other) - NODE_SIZE / 2 < top + height)
                return true;
        }
        return false;
    }

    private static void sprite(GuiGraphicsExtractor graphics, int cell, int x, int y, int color)
    {
        graphics.blit(RenderPipelines.GUI_TEXTURED, SPRITES, x, y, cell * NODE_SIZE, 0, NODE_SIZE, NODE_SIZE, ATLAS, ATLAS, color);
    }

    static void glyph(GuiGraphicsExtractor graphics, boolean open, int x, int y)
    {
        graphics.blit(RenderPipelines.GUI_TEXTURED, SPRITES, x, y, open ? 16 : 0, GLYPH_ROW, 16, 16, ATLAS, ATLAS);
    }

    static ItemStack iconOf(Research research)
    {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(research.icon()));
    }

    private void extractMarks(GuiGraphicsExtractor graphics, Identifier selected, Identifier hovered)
    {
        if (selected != null && polar.containsKey(selected))
            brackets(graphics, selected, ResearchScreen.TEXT_COLOR);
        if (hovered != null && !hovered.equals(selected) && polar.containsKey(hovered))
            brackets(graphics, hovered, ResearchScreen.MUTED_COLOR);
    }

    private void brackets(GuiGraphicsExtractor graphics, Identifier id, int color)
    {
        int reach = NODE_SIZE / 2 + 2;
        int arm = 4;
        int x = nodeX(id);
        int y = nodeY(id);
        for (int sx = -1; sx <= 1; sx += 2)
        {
            for (int sy = -1; sy <= 1; sy += 2)
            {
                int cornerX = x + sx * reach;
                int cornerY = y + sy * reach;
                graphics.fill(Math.min(cornerX, cornerX - sx * arm), cornerY, Math.max(cornerX, cornerX - sx * arm) + 1, cornerY + 1, color);
                graphics.fill(cornerX, Math.min(cornerY, cornerY - sy * arm), cornerX + 1, Math.max(cornerY, cornerY - sy * arm) + 1, color);
            }
        }
    }

    /**
     * A ring of light leaving a research at the moment it is read or finished.
     */
    private void ring(GuiGraphicsExtractor graphics, Bloom bloom, float t, float reach)
    {
        if (t <= 0)
            return;

        float eased = 1 - (1 - t) * (1 - t) * (1 - t);
        float radius = NODE_SIZE / 2F + eased * reach;
        int dots = bloom.finished() ? BLOOM_DOTS * 2 : BLOOM_DOTS;
        int color = ARGB.color((int) (255 * (1 - t)), bloom.finished() ? ResearchScreen.KNOWN_RGB : ResearchScreen.FRONTIER_RGB);
        for (int dot = 0; dot < dots; dot++)
        {
            double angle = dot * Math.PI * 2 / dots + eased;
            int x = (int) (nodeX(bloom.id()) + Math.cos(angle) * radius);
            int y = (int) (nodeY(bloom.id()) + Math.sin(angle) * radius);
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    private void extractBlooms(GuiGraphicsExtractor graphics, int screenH, long now)
    {
        blooms.removeIf(bloom -> now - bloom.start() > (bloom.finished() ? FINISH_MILLIS : BLOOM_MILLIS));
        graphics.enableScissor(left, 0, right, screenH);
        for (var bloom : blooms)
        {
            if (!polar.containsKey(bloom.id()))
                continue;

            float t = (now - bloom.start()) / (float) (bloom.finished() ? FINISH_MILLIS : BLOOM_MILLIS);
            ring(graphics, bloom, t, bloom.finished() ? FINISH_REACH : BLOOM_REACH);
            if (bloom.finished())
            {
                ring(graphics, bloom, t - 0.14F, FINISH_REACH * 0.8F);
                ring(graphics, bloom, t - 0.28F, FINISH_REACH * 0.6F);
            }
        }
        graphics.disableScissor();
    }
}
