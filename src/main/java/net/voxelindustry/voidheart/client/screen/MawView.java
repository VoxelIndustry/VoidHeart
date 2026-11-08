package net.voxelindustry.voidheart.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.screen.ResearchMapPlan.Spot;
import net.voxelindustry.voidheart.client.screen.ResearchScreen.NodeState;
import net.voxelindustry.voidheart.common.content.research.Constellation;
import net.voxelindustry.voidheart.common.content.research.Research;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Maw tearing its way in behind the research map, as far as the player has studied the Deities: see {@link MawFlesh} for each stage. Its flesh and teeth are
 * baked once a stage and breathe, its eye down the gullet follows the cursor, and from the fourth stage on tendrils sway out of its lips and lash at the
 * researches nearby. Everything is in the map's own units, so it pans with the map.
 */
final class MawView
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "dynamic/research_maw");
    /** The research that seals Maw off from the player's mind, closing the ravine for good. */
    static final Identifier         SEAL    = Identifier.fromNamespaceAndPath(MODID, "sealed_mind");
    /** The stage of a ravine sealed shut, past all the others. */
    static final int                SEALED  = MawFlesh.STAGES;

    /** Every so often the sealed mouth heaves against its sutures, swelling a little, then settles. */
    private static final long  HEAVE_EVERY   = 4600;
    private static final long  HEAVE_MILLIS  = 650;
    private static final float HEAVE         = 0.07F;

    /** How far beyond the furthest of the Deities the mouth opens, away from the eye, more as it grows, so that the path of study leads in over its lips. */
    private static final float BEYOND        = 18;
    private static final float BEYOND_GROWTH = 9;
    /** Room kept around the lips for the tendrils when the map works out how far it pans. */
    private static final int   TENDRIL_ROOM  = 30;
    private static final float BREATH        = 0.014F;
    private static final long  BREATH_MILLIS = 3400;
    private static final long  BLINK_EVERY   = 6300;
    private static final long  BLINK_MILLIS  = 220;
    private static final float LOOK_REACH    = 140;

    private static final float IDLE_LENGTH   = 14;
    private static final float SWAY          = 5;
    private static final long  SWAY_MILLIS   = 700;
    private static final long  LASH_EVERY    = 5200;
    private static final long  LASH_SPREAD   = 3200;
    private static final long  LASH_OUT      = 170;
    private static final long  LASH_HOLD     = 220;
    private static final long  LASH_BACK     = 560;
    private static final float LASH_REACH    = 170;
    /** A lash stops short of the research it reaches for, so it seems to strike at it rather than pass under it. */
    private static final float LASH_SHORT    = 14;

    private final Map<Identifier, Research> tree;
    private final Map<Identifier, Spot>     nodes;
    private final List<Identifier>          deities;
    private final long                      seed;
    private final float                     lastX;
    private final float                     lastY;
    private float                           mouthX;
    private float                           mouthY;
    private MawFlesh.Shape                  shape;
    private int                             stage = -1;
    private int                             textureX;
    private int                             textureY;
    private int                             textureWidth;
    private int                             textureHeight;

    MawView(Map<Identifier, Research> tree, Map<Identifier, Spot> nodes, long seed)
    {
        this.tree = tree;
        this.nodes = nodes;
        this.seed = seed;
        this.deities = tree.entrySet().stream().filter(entry -> entry.getValue().constellation() == Constellation.DEITIES).map(Map.Entry::getKey).sorted().toList();

        var last = deities.stream().map(nodes::get).max((first, second) -> Double.compare(Math.hypot(first.x(), first.y()), Math.hypot(second.x(), second.y())));
        this.lastX = last.map(Spot::x).orElse(0);
        this.lastY = last.map(Spot::y).orElse(0);
    }

    /** The middle of the mouth for a stage: past the furthest of the Deities, further as it grows. */
    private void place(int now)
    {
        float away = (float) Math.max(1, Math.hypot(lastX, lastY));
        int grown = Math.min(now, MawFlesh.STAGES - 1);
        float beyond = BEYOND + grown * BEYOND_GROWTH + shape(now).radiusY() * 0.5F;
        mouthX = lastX + lastX / away * beyond;
        mouthY = lastY + lastY / away * beyond;
    }

    /** What it covers now, tendrils at rest included, or null while the Deities are hidden. */
    ResearchMapPlan.Bounds bounds()
    {
        int now = stage();
        if (now < 0)
            return null;
        place(now);
        var shape = shape(now);
        int reachX = (int) Math.ceil(shape.radiusX() * 1.15F) + TENDRIL_ROOM;
        int reachY = (int) Math.ceil(shape.radiusY() * 1.15F) + TENDRIL_ROOM;
        return new ResearchMapPlan.Bounds((int) mouthX - reachX, (int) mouthY - reachY, (int) mouthX + reachX, (int) mouthY + reachY);
    }

    /** How far it has come through, or -1 while the Deities are still hidden. */
    int stage()
    {
        boolean shown = deities.stream().anyMatch(id -> ResearchScreen.stateOf(id, tree.get(id)) != NodeState.HIDDEN);
        if (!shown)
            return -1;
        if (ResearchScreen.snapshot.completed().contains(SEAL))
            return SEALED;
        int done = (int) deities.stream().filter(ResearchScreen.snapshot.completed()::contains).count();
        return Math.min(done, MawFlesh.STAGES - 1);
    }

    /** A red dark around it, deeper as it grows. In the map's units, as the dark is painted. */
    void addLight(List<ResearchVoid.Light> lights)
    {
        int now = stage();
        if (now < 0)
            return;
        place(now);
        // Sealed, its red dark sinks to what it was at the first tear
        int glow = now == SEALED ? 0 : now;
        lights.add(new ResearchVoid.Light(mouthX, mouthY, 34 + glow * 26, 0.5 + glow * 0.1, ConstellationLook.of(Constellation.DEITIES).lightRgb()));
    }

    void extract(GuiGraphicsExtractor graphics, int originX, int originY, int mouseX, int mouseY, long now)
    {
        int current = stage();
        if (current < 0)
            return;
        if (current != stage)
            rebuild(current);

        float centerX = originX + mouthX;
        float centerY = originY + mouthY;
        float breath = 1 + BREATH * (float) Math.sin(now * Math.PI * 2 / BREATH_MILLIS);

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(centerX, centerY);
        pose.scale(breath, breath * (1 + heave(now)));

        for (int root = 0; root < shape.roots().size(); root++)
            cells(graphics, tendril(root, originX, originY, now));

        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, textureX, textureY, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight);

        float blink = 1;
        long intoBlink = (now + seed % BLINK_EVERY) % BLINK_EVERY;
        if (intoBlink < BLINK_MILLIS)
            blink = Math.abs(intoBlink / (BLINK_MILLIS / 2F) - 1);
        float lookX = Math.clamp((mouseX - centerX) / LOOK_REACH, -1, 1);
        float lookY = Math.clamp((mouseY - centerY - shape.eyeY()) / LOOK_REACH, -1, 1);
        pose.pushMatrix();
        pose.translate(0, shape.eyeY());
        cells(graphics, MawFlesh.eye(shape.eyeRadius(), lookX, lookY, blink, seed));
        pose.popMatrix();

        pose.popMatrix();
    }

    /** How far the sealed mouth swells against its sutures now, nothing while it is open. */
    private float heave(long now)
    {
        if (stage != SEALED)
            return 0;
        long into = (now + seed % HEAVE_EVERY) % HEAVE_EVERY;
        if (into >= HEAVE_MILLIS)
            return 0;
        return HEAVE * (float) Math.sin(Math.PI * into / HEAVE_MILLIS);
    }

    private MawFlesh.Shape shape(int now)
    {
        return now == SEALED ? MawFlesh.sealed(seed) : MawFlesh.shape(now, seed);
    }

    /** Swaying out of the lips, and every few seconds lashing at a research within reach before drawing back. */
    private List<MawFlesh.Cell> tendril(int index, int originX, int originY, long now)
    {
        var root = shape.roots().get(index);
        long every = LASH_EVERY + (long) (MawFlesh.hash(index, 41, seed) * LASH_SPREAD);
        long offset = (long) (MawFlesh.hash(index, 43, seed) * every);
        long cycle = (now + offset) / every;
        long into = (now + offset) % every;

        float idle = IDLE_LENGTH + (stage >= MawFlesh.STAGES - 1 ? 10 : 0) + index % 3 * 5;
        float out;
        if (into < LASH_OUT)
            out = 1 - (1 - into / (float) LASH_OUT) * (1 - into / (float) LASH_OUT);
        else if (into < LASH_OUT + LASH_HOLD)
            out = 1;
        else if (into < LASH_OUT + LASH_HOLD + LASH_BACK)
        {
            float back = (into - LASH_OUT - LASH_HOLD) / (float) LASH_BACK;
            out = 1 - back * back;
        }
        else
            out = 0;

        float angle = root.angle();
        float length = idle;
        var target = out > 0 ? target(root, index, cycle) : null;
        if (target != null)
        {
            float dx = target.x() - mouthX - root.x();
            float dy = target.y() - mouthY - root.y();
            float toward = (float) Math.atan2(dy, dx);
            float turn = (float) Math.atan2(Math.sin(toward - angle), Math.cos(toward - angle));
            angle += turn * out;
            length = idle + (Math.min(LASH_REACH, (float) Math.hypot(dx, dy) - LASH_SHORT) - idle) * out;
        }
        float phase = now / (float) SWAY_MILLIS + index * 1.7F;
        return MawFlesh.tendril(new MawFlesh.Root(root.x(), root.y(), angle), Math.max(idle, length), SWAY * (1 - out * 0.7F), phase, seed + index);
    }

    /** The research a lash goes for this time round: one in sight and within reach, the same one for the whole lash. */
    private Spot target(MawFlesh.Root root, int index, long cycle)
    {
        var within = new ArrayList<Spot>();
        tree.forEach((id, research) ->
        {
            var at = nodes.get(id);
            if (ResearchScreen.stateOf(id, research) == NodeState.HIDDEN)
                return;
            if (Math.hypot(at.x() - mouthX - root.x(), at.y() - mouthY - root.y()) < LASH_REACH + LASH_SHORT)
                within.add(at);
        });
        if (within.isEmpty())
            return null;
        within.sort((first, second) -> first.x() != second.x() ? Integer.compare(first.x(), second.x()) : Integer.compare(first.y(), second.y()));
        return within.get((int) (MawFlesh.hash((int) cycle, index, seed) * within.size()));
    }

    private static void cells(GuiGraphicsExtractor graphics, List<MawFlesh.Cell> cells)
    {
        for (var cell : cells)
            graphics.fill(cell.x(), cell.y(), cell.x() + cell.size(), cell.y() + cell.size(), cell.argb());
    }

    /** Bakes the flesh and teeth of a stage into one texture, so that thousands of blocks of flesh cost a single quad a frame. */
    private void rebuild(int now)
    {
        stage = now;
        place(now);
        shape = shape(now);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        var all = new ArrayList<MawFlesh.Cell>(shape.cells());
        all.addAll(shape.teeth());
        for (var cell : all)
        {
            minX = Math.min(minX, cell.x());
            minY = Math.min(minY, cell.y());
            maxX = Math.max(maxX, cell.x() + cell.size());
            maxY = Math.max(maxY, cell.y() + cell.size());
        }
        textureX = minX;
        textureY = minY;
        textureWidth = Math.max(1, maxX - minX);
        textureHeight = Math.max(1, maxY - minY);

        var image = new NativeImage(textureWidth, textureHeight, true);
        for (var cell : all)
        {
            for (int y = 0; y < cell.size(); y++)
            {
                for (int x = 0; x < cell.size(); x++)
                {
                    int px = cell.x() - minX + x;
                    int py = cell.y() - minY + y;
                    if (px >= 0 && py >= 0 && px < textureWidth && py < textureHeight)
                        image.setPixel(px, py, cell.argb());
                }
            }
        }
        ResearchVoid.upload(Minecraft.getInstance(), TEXTURE, image);
    }
}
