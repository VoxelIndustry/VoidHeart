package net.voxelindustry.voidheart.client.render.divining;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.client.render.energy.ley.LeyLook;
import net.voxelindustry.voidheart.common.content.divining.LeySight;
import net.voxelindustry.voidheart.common.content.divining.LeySightPayload;
import net.voxelindustry.voidheart.common.ley.LeyHeights;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What a Ley Divining Rod reveals, drawn in the world as a current: each line a textured strand in a soft halo, both as thick and bright as the line's
 * flow, with chevrons and tailed motes running downstream and the whole line fading toward where it flows. Nodes rise as slow vortices of light from the
 * ground, colored by kind. Standing stones' spurs show the same way, thinner, from where they leave their line to each stone. Taint dims and greys all of
 * it. Behind terrain only the strands and node columns show, faintly. While the reveal spreads, a faint violet shell grows out with its front, strongest at
 * the horizon, so the search itself shows; terrain nearer than the shell hides it, so it reads as a wall of light sweeping out over the land.
 * <p>
 * All layers are {@link VoidHeartRenderTypes#radiant}: textured, added to what is behind and never writing depth, so they overlap in any order without
 * flicker. Paths are worked out once when a reveal arrives; a frame only places camera-facing quads on them. At the worst realistic size, a dozen lines
 * of 130 points and a few nodes, that is around 6000 quads a frame for the few seconds a reveal lasts, with spurs adding a few hundred and the shell about
 * {@value #SHELL_QUADS} for its first second.
 */
public final class LeyDiviningRender
{
    private static final Identifier CORE_TEXTURE    = Identifier.fromNamespaceAndPath(MODID, "textures/misc/ley_sight_core.png");
    private static final Identifier HALO_TEXTURE    = Identifier.fromNamespaceAndPath(MODID, "textures/misc/ley_sight_halo.png");
    private static final Identifier CHEVRON_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/misc/ley_sight_chevron.png");
    private static final Identifier MOTE_TEXTURE    = Identifier.fromNamespaceAndPath(MODID, "textures/misc/ley_sight_mote.png");

    private static final RenderType CORE_TYPE         = VoidHeartRenderTypes.radiant(CORE_TEXTURE);
    private static final RenderType CORE_THROUGH_TYPE = VoidHeartRenderTypes.radiantThrough(CORE_TEXTURE);
    private static final RenderType HALO_TYPE         = VoidHeartRenderTypes.radiant(HALO_TEXTURE);
    private static final RenderType HALO_THROUGH_TYPE = VoidHeartRenderTypes.radiantThrough(HALO_TEXTURE);
    private static final RenderType CHEVRON_TYPE      = VoidHeartRenderTypes.radiant(CHEVRON_TEXTURE);
    private static final RenderType MOTE_TYPE         = VoidHeartRenderTypes.radiant(MOTE_TEXTURE);
    private static final RenderType SHEATH_TYPE       = VoidHeartRenderTypes.tint(HALO_TEXTURE);

    // Colors: the ley violet of the debug view and Oculus, greying to the mauve of a dormant anchor under taint
    private static final int   CORE_RGB       = 0xB48CFF;
    private static final int   HALO_RGB       = 0x6A3CFF;
    private static final int   STREAK_RGB     = 0xDCC8FF;
    private static final int   TAINT_RGB      = 0x8A6A94;
    /**
     * A dark violet blended under the glow, so it keeps its hue against a bright day instead of washing out to white.
     */
    private static final int   SHEATH_RGB     = 0x24104F;
    private static final int   SHEATH_TAINT_RGB = 0x24122C;
    private static final float SHEATH_ALPHA   = 0.6F;
    private static final float SHEATH_WIDTH   = 0.75F;
    private static final int[] NODE_RGB       = { 0xB8A9D9, 0x6FCF97, 0xF2C94C, 0xEB5757 };
    /**
     * Share of brightness a fully tainted line or node loses.
     */
    private static final float TAINT_DIM      = 0.55F;
    /**
     * Share of brightness a line loses from its from node to its to node, so the source stays obvious.
     */
    private static final float DOWNSTREAM_FADE = 0.55F;
    /**
     * A line carrying nothing still shows this much of a full one, so starved lines flicker rather than vanish.
     */
    private static final float WEAK_FLOOR     = 0.18F;

    // Strand and halo, half widths in blocks at no flow and the added width at full flow
    private static final float CORE_HALF       = 0.05F;
    private static final float CORE_HALF_FLOW  = 0.08F;
    private static final float CORE_ALPHA      = 0.95F;
    private static final float HALO_HALF       = 0.28F;
    private static final float HALO_HALF_FLOW  = 0.5F;
    private static final float HALO_ALPHA      = 0.55F;
    /**
     * Steps the strand's texture spans before it repeats.
     */
    private static final int   CORE_TILE_STEPS = 4;
    /**
     * Share of the visible brightness that shows through terrain.
     */
    private static final float THROUGH_ALPHA   = 0.25F;

    // Chevrons running downstream: faster and closer together on a strong line
    private static final float STREAK_SPEED_WEAK    = 0.08F;
    private static final float STREAK_SPEED_STRONG  = 0.45F;
    private static final float STREAK_SPACING_WEAK  = 9;
    private static final float STREAK_SPACING_STRONG = 3.5F;
    private static final float STREAK_LENGTH        = 1.4F;
    private static final float STREAK_WIDTH         = 2.4F;
    private static final float STREAK_ALPHA         = 0.85F;
    private static final float STREAK_GAP_CHANCE    = 0.2F;
    private static final float STREAK_JITTER        = 0.45F;
    private static final int   STREAK_PIECES        = 3;

    // Motes drifting downstream with short tails, a little slower than the chevrons and swaying around the strand
    private static final float MOTE_SPEED_SHARE   = 0.7F;
    private static final float MOTE_SPACING_WEAK  = 14;
    private static final float MOTE_SPACING_STRONG = 3;
    private static final float MOTE_TAIL          = 0.9F;
    private static final float MOTE_HALF          = 0.06F;
    private static final float MOTE_ALPHA         = 0.9F;
    private static final float MOTE_SWAY          = 0.3F;
    private static final float MOTE_GAP_CHANCE    = 0.3F;

    // Node vortices, from the ground under the node up to its height
    private static final float NODE_HEIGHT        = 3;
    private static final float NODE_HEIGHT_FLOW   = 5;
    private static final float NODE_RADIUS        = 0.55F;
    private static final float NODE_RADIUS_FLOW   = 0.5F;
    private static final float NODE_TWIST         = 5.5F;
    private static final float NODE_SPIN          = 0.035F;
    private static final int   NODE_ARMS          = 3;
    private static final int   NODE_ARM_PIECES    = 28;
    private static final float NODE_ARM_HALF      = 0.06F;
    private static final float NODE_ARM_ALPHA     = 0.9F;
    private static final float NODE_HALO_HALF     = 0.7F;
    private static final float NODE_HALO_HALF_FLOW = 0.7F;
    private static final float NODE_HALO_ALPHA    = 0.45F;
    private static final int   NODE_COLUMN_PIECES = 10;
    /**
     * Nodes show their flow against this, since a node carries every line leaving it.
     */
    private static final float NODE_FULL_FLOW     = LeyLook.FULL_FLOW * 3;

    // Spurs: a line's look at this share of its widths, fading less toward the stone since they are short
    private static final float SPUR_WIDTH           = 0.5F;
    private static final float SPUR_DOWNSTREAM_FADE = 0.25F;
    /**
     * Blocks a spur's start may move to meet the drawn line, a little more than the line's sway.
     */
    private static final float SPUR_SNAP            = 0.6F;

    // The searching shell around the rod while the reveal spreads
    private static final int   SHELL_RGB          = 0x8A5CFF;
    private static final float SHELL_ALPHA        = 0.5F;
    /**
     * A faint violet blended under the shell's glow. Much more reads as grey fog over grass, whatever the hue.
     */
    private static final int   SHELL_TINT_RGB     = 0x6A3CE8;
    private static final float SHELL_TINT_ALPHA   = 0.18F;
    /**
     * How tightly the shell's light gathers at its horizon: the higher, the narrower the band and the clearer the sky above.
     */
    private static final float SHELL_BAND_POWER   = 1.5F;
    private static final int   SHELL_SEGMENTS     = 48;
    private static final int   SHELL_RINGS        = 24;
    private static final int   SHELL_QUADS        = SHELL_SEGMENTS * SHELL_RINGS * 2;
    /**
     * Height of the shell's center above the rod bearer's feet, near their eyes, so it grows evenly all around the view.
     */
    private static final float SHELL_CENTER_Y     = 1.5F;
    /**
     * Where the shell samples the 16 pixel halo texture: the center of a pixel in its bright middle, never the edge between two.
     */
    private static final float SHELL_TEXEL        = 7.5F / 16;

    // The shell's border where it meets the terrain: a strand like a line's, widening as the shell grows so it still reads far off
    private static final int   BORDER_DIRECTIONS  = 160;
    private static final float BORDER_LIFT        = 0.15F;
    /**
     * Height in blocks between neighbouring border points past which the border breaks, over a cliff or a wall, instead of stretching up it.
     */
    private static final float BORDER_BREAK       = 2.5F;
    private static final float BORDER_CORE_HALF   = 0.05F;
    private static final float BORDER_CORE_GROW   = 0.003F;
    private static final float BORDER_HALO_HALF   = 0.3F;
    private static final float BORDER_HALO_GROW   = 0.012F;
    private static final float BORDER_CORE_ALPHA  = 0.95F;
    private static final float BORDER_HALO_ALPHA  = 0.5F;
    /**
     * Blocks the rod may be under the surface before the border is left out, since the surface would then be far from the cave around its bearer.
     */
    private static final int   BORDER_UNDERGROUND = 6;

    // Motes drifting slowly over the shell, gathered near its horizon
    private static final int   SHELL_MOTES        = 90;
    private static final int   SHELL_MOTE_RGB     = 0xC4A4FF;
    /**
     * Turn of a mote around the shell in radians a tick, a little more or less for each.
     */
    private static final float SHELL_MOTE_DRIFT   = 0.0035F;
    private static final float SHELL_MOTE_LOWEST  = -0.1F;
    private static final float SHELL_MOTE_HIGHEST = 0.35F;
    private static final float SHELL_MOTE_BOB     = 0.02F;
    private static final float SHELL_MOTE_BOB_SPEED = 0.04F;
    private static final float SHELL_MOTE_HALF    = 0.06F;
    private static final float SHELL_MOTE_GROW    = 0.0035F;
    private static final float SHELL_MOTE_TAIL    = 0.4F;
    private static final float SHELL_MOTE_TAIL_GROW = 0.008F;
    private static final float SHELL_MOTE_ALPHA   = 0.6F;
    /**
     * Shell radius below which motes fade, so they do not crowd the bearer's face as it starts.
     */
    private static final float SHELL_MOTE_FROM    = 8;

    /**
     * @param width share of a line's widths it is drawn at
     * @param fade  share of brightness lost from its from end to its to end
     */
    private record ShownLine(LeySightPlan.Path path, float level, float fromTaint, float toTaint, long seed, float width, float fade)
    {
    }

    private record ShownNode(double x, double y, double z, float distance, int rgb, float level, float taint, long seed)
    {
    }

    /**
     * @param level   the world it was raised in: a reveal never follows its bearer into another dimension
     * @param terrain terrain tops sampled a block apart outward from the rod in each border direction, or null when the rod was raised underground
     */
    private record Sight(ClientLevel level, long startTick, double originX, double originY, double originZ, List<ShownLine> lines, List<ShownNode> nodes,
                         int spurs, float @Nullable [][] terrain, long seed)
    {
        boolean revealsAnything()
        {
            return !lines.isEmpty() || !nodes.isEmpty();
        }
    }

    private static volatile Sight sight;

    private LeyDiviningRender()
    {
    }

    public static void receive(LeySightPayload payload, ClientPlayNetworking.Context context)
    {
        var level = context.client().level;
        if (level == null)
            return;

        float radius = LeySight.RADIUS;
        var lines = new ArrayList<ShownLine>();
        for (var line : payload.lines())
        {
            var heights = new float[line.heights().size()];
            for (int i = 0; i < heights.length; i++)
                heights[i] = line.heights().get(i);
            long seed = seed(line.fromX(), line.fromZ(), line.toX(), line.toZ());
            var path = LeySightPlan.line(line.fromX(), line.fromZ(), line.toX(), line.toZ(), heights, payload.originX(), payload.originZ(), radius, seed);
            if (!path.isEmpty())
                lines.add(new ShownLine(path, LeyLook.flowLevel(line.flow()), line.fromTaint(), line.toTaint(), seed, 1, DOWNSTREAM_FADE));
        }

        int shownLines = lines.size();
        int spurs = 0;
        for (var spur : payload.spurs())
        {
            long seed = seed(Mth.floor(spur.fromX()), Mth.floor(spur.fromZ()), Mth.floor(spur.toX()), Mth.floor(spur.toZ())) ^ SPUR_SEED_SALT;
            double fromX = spur.fromX(), fromY = spur.fromY(), fromZ = spur.fromZ();
            if (spur.fromLine())
            {
                // Start on the swaying strand as drawn, not the straight edge under it
                double[] snapped = null;
                for (int i = 0; i < shownLines; i++)
                {
                    var point = LeySightPlan.nearest(lines.get(i).path(), fromX, fromZ);
                    if (point != null && point[3] <= SPUR_SNAP * SPUR_SNAP && (snapped == null || point[3] < snapped[3]))
                        snapped = point;
                }
                if (snapped != null)
                {
                    fromX = snapped[0];
                    fromY = snapped[1];
                    fromZ = snapped[2];
                }
            }
            var path = LeySightPlan.spur(fromX, fromY, fromZ, spur.toX(), spur.toY(), spur.toZ(), payload.originX(), payload.originZ(), radius, seed);
            if (path.isEmpty())
                continue;
            lines.add(new ShownLine(path, LeyLook.flowLevel(spur.flow()), spur.taint(), spur.taint(), seed, SPUR_WIDTH, SPUR_DOWNSTREAM_FADE));
            spurs++;
        }

        var nodes = new ArrayList<ShownNode>();
        for (var node : payload.nodes())
        {
            double x = node.x() + 0.5, z = node.z() + 0.5;
            float distance = (float) Math.hypot(x - payload.originX(), z - payload.originZ());
            float level01 = node.flow() <= 0 ? 0 : Mth.clamp((float) Math.sqrt(node.flow() / NODE_FULL_FLOW), 0, 1);
            nodes.add(new ShownNode(x, node.y() + 0.5, z, distance, NODE_RGB[node.kind().ordinal()], level01, node.taint(), seed(node.x(), node.z(), 0, 0)));
        }

        // Kept even when nothing was found, so the searching shell still grows out
        sight = new Sight(level, level.getGameTime(), payload.originX(), payload.originY(), payload.originZ(), lines, nodes, spurs,
                terrain(level, payload.originX(), payload.originY(), payload.originZ()),
                seed(Mth.floor(payload.originX()), Mth.floor(payload.originY()), Mth.floor(payload.originZ()), (int) level.getGameTime()));
    }

    /**
     * The terrain the shell's border runs over, sampled once: in each direction, the top of the ground every block out to past the radius, looking under
     * leaves so the border runs on the ground beneath trees. A direction's samples stop at the first chunk not loaded, whose height would read as the
     * bottom of the world.
     */
    private static float @Nullable [][] terrain(ClientLevel level, double originX, double originY, double originZ)
    {
        var pos = new BlockPos.MutableBlockPos();
        if (ground(level, Mth.floor(originX), Mth.floor(originZ), pos) - originY > BORDER_UNDERGROUND)
            return null;

        int samples = LeySight.RADIUS + 3;
        var terrain = new float[BORDER_DIRECTIONS][];
        for (int direction = 0; direction < BORDER_DIRECTIONS; direction++)
        {
            float angle = 2 * Mth.PI * direction / BORDER_DIRECTIONS;
            float dx = Mth.cos(angle), dz = Mth.sin(angle);
            var tops = new float[samples];
            int loaded = 0;
            while (loaded < samples)
            {
                int x = Mth.floor(originX + dx * loaded), z = Mth.floor(originZ + dz * loaded);
                if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)))
                    break;
                tops[loaded++] = ground(level, x, z, pos);
            }
            terrain[direction] = Arrays.copyOf(tops, loaded);
        }
        return terrain;
    }

    /**
     * Top of the ground at a column, beneath any leaves and the open air under them, so a canopy over open ground is looked through.
     */
    private static int ground(ClientLevel level, int x, int z, BlockPos.MutableBlockPos pos)
    {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        while (top > level.getMinY())
        {
            var below = level.getBlockState(pos.set(x, top - 1, z));
            if (!below.is(BlockTags.LEAVES) && (!below.getCollisionShape(level, pos).isEmpty() || !below.getFluidState().isEmpty()))
                break;
            top--;
        }
        return top;
    }

    private static final long SPUR_SEED_SALT = 0x2545F4914F6CDD1DL;

    public static void clear()
    {
        sight = null;
    }

    /**
     * Whether lines, spurs or nodes are showing, not counting the shell of a search that found nothing.
     */
    public static boolean isShowing()
    {
        var current = sight;
        return current != null && current.revealsAnything();
    }

    /**
     * Lines, nodes and spurs shown by the current reveal, for tests. Spurs are not counted among the lines.
     */
    public static int[] shownCounts()
    {
        var current = sight;
        return current == null ? new int[3] : new int[] { current.lines().size() - current.spurs(), current.nodes().size(), current.spurs() };
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var current = sight;
        if (current == null)
            return;
        if (current.level() != Minecraft.getInstance().level)
        {
            sight = null;
            return;
        }

        var levelState = context.levelState();
        float age = (levelState.gameTime - current.startTick()) + levelState.worldPartialTicks;
        if (age >= LeySightPlan.LIFE_TICKS || age < -1 || !current.revealsAnything() && age >= LeySightPlan.SHELL_TICKS)
        {
            sight = null;
            return;
        }
        float time = (levelState.gameTime % 100_000L) + levelState.worldPartialTicks;
        var camera = levelState.cameraRenderState.pos;

        var frame = new Frame();
        drawShell(frame, current, camera, age, time);
        for (var line : current.lines())
            drawLine(frame, line, camera, age, time);
        for (var node : current.nodes())
            drawNode(frame, node, camera, age, time);

        var collector = context.submitNodeCollector();
        var poseStack = context.poseStack();
        frame.sheath.submit(collector, poseStack, SHEATH_TYPE);
        frame.through.submit(collector, poseStack, CORE_THROUGH_TYPE);
        frame.haloThrough.submit(collector, poseStack, HALO_THROUGH_TYPE);
        frame.halo.submit(collector, poseStack, HALO_TYPE);
        frame.core.submit(collector, poseStack, CORE_TYPE);
        frame.chevrons.submit(collector, poseStack, CHEVRON_TYPE);
        frame.motes.submit(collector, poseStack, MOTE_TYPE);
    }

    private static void drawLine(Frame frame, ShownLine line, Vec3 camera, float age, float time)
    {
        var path = line.path();
        int n = path.size();
        float radius = LeySight.RADIUS;

        var points = new Vector3f[n];
        var sides = new Vector3f[n];
        var bright = new float[n];
        var coreColor = new int[n];
        var haloColor = new int[n];
        var sheathColor = new int[n];
        boolean any = false;
        for (int i = 0; i < n; i++)
        {
            points[i] = new Vector3f((float) (path.x()[i] - camera.x), (float) (path.y()[i] - camera.y), (float) (path.z()[i] - camera.z));
            float along = path.along()[i];
            float taint = Mth.lerp(along, line.fromTaint(), line.toTaint());
            float shown = LeySightPlan.reveal(age, path.distance()[i], radius) + LeySightPlan.frontFlash(age, path.distance()[i], radius);
            bright[i] = shown * (1 - line.fade() * along) * (1 - TAINT_DIM * taint);
            coreColor[i] = lerp(CORE_RGB, TAINT_RGB, taint);
            haloColor[i] = lerp(HALO_RGB, TAINT_RGB, taint);
            sheathColor[i] = lerp(SHEATH_RGB, SHEATH_TAINT_RGB, taint);
            any |= bright[i] > 0.004F;
        }
        if (!any)
            return;
        for (int i = 0; i < n; i++)
            sides[i] = facingSide(points[Math.max(0, i - 1)], points[Math.min(n - 1, i + 1)], points[i]);

        float level = WEAK_FLOOR + (1 - WEAK_FLOOR) * line.level();
        float coreHalf = (CORE_HALF + CORE_HALF_FLOW * line.level()) * line.width();
        float haloHalf = (HALO_HALF + HALO_HALF_FLOW * line.level()) * line.width();
        int firstStep = Math.round(path.start() / LeySightPlan.STEP);

        for (int i = 0; i < n - 1; i++)
        {
            if (bright[i] <= 0.004F && bright[i + 1] <= 0.004F)
                continue;

            float sheathA = SHEATH_ALPHA * level;
            float sheathHalf = haloHalf * SHEATH_WIDTH;
            frame.sheath.ribbon(points[i], points[i + 1], sides[i], sides[i + 1], sheathHalf, sheathHalf, 0, 1,
                    color(sheathColor[i], sheathA * Math.min(1, bright[i])), color(sheathColor[i + 1], sheathA * Math.min(1, bright[i + 1])));

            float haloA = HALO_ALPHA * level;
            frame.halo.ribbon(points[i], points[i + 1], sides[i], sides[i + 1], haloHalf, haloHalf, 0, 1,
                    color(haloColor[i], haloA * bright[i]), color(haloColor[i + 1], haloA * bright[i + 1]));

            int tile = Math.floorMod(firstStep + i, CORE_TILE_STEPS);
            float u0 = (float) tile / CORE_TILE_STEPS, u1 = (float) (tile + 1) / CORE_TILE_STEPS;
            float coreA = CORE_ALPHA * level;
            frame.core.ribbon(points[i], points[i + 1], sides[i], sides[i + 1], coreHalf, coreHalf, u0, u1,
                    color(coreColor[i], coreA * bright[i]), color(coreColor[i + 1], coreA * bright[i + 1]));
            frame.through.ribbon(points[i], points[i + 1], sides[i], sides[i + 1], coreHalf, coreHalf, u0, u1,
                    color(coreColor[i], coreA * THROUGH_ALPHA * bright[i]), color(coreColor[i + 1], coreA * THROUGH_ALPHA * bright[i + 1]));
        }

        var along = new PathSampler(path, points, sides, bright);
        float speed = Mth.lerp(line.level(), STREAK_SPEED_WEAK, STREAK_SPEED_STRONG);

        float spacing = Mth.lerp(line.level(), STREAK_SPACING_WEAK, STREAK_SPACING_STRONG);
        runAlong(along, spacing, time * speed, line.seed(), STREAK_GAP_CHANCE, (head, k) ->
        {
            float jittered = head + (LeySightPlan.hashUnit(line.seed(), k * 7 + 3) - 0.5F) * STREAK_JITTER * spacing;
            drawRun(frame.chevrons, along, jittered - STREAK_LENGTH, jittered, STREAK_PIECES, coreHalf * STREAK_WIDTH, STREAK_RGB, STREAK_ALPHA * level, null);
        });

        float moteSpacing = Mth.lerp(line.level(), MOTE_SPACING_WEAK, MOTE_SPACING_STRONG);
        runAlong(along, moteSpacing, time * speed * MOTE_SPEED_SHARE, line.seed() ^ 0x5DEECE66DL, MOTE_GAP_CHANCE, (head, k) ->
        {
            float phase = LeySightPlan.hashUnit(line.seed(), k * 13 + 5) * (float) (2 * Math.PI);
            float jittered = head + LeySightPlan.hashUnit(line.seed(), k * 13 + 6) * moteSpacing * 0.5F;
            float sway = MOTE_SWAY * line.width();
            drawRun(frame.motes, along, jittered - MOTE_TAIL, jittered, 2, MOTE_HALF * line.width(), STREAK_RGB, MOTE_ALPHA * level,
                    s -> new float[] { (float) Math.sin(phase + s * 0.35F) * sway, (float) Math.cos(phase * 1.3F + s * 0.27F) * sway * 0.7F });
        });
    }

    private interface RunDrawer
    {
        void draw(float head, long k);
    }

    private interface Sway
    {
        float[] at(float s);
    }

    /**
     * Calls the drawer for every run whose head is somewhere along the kept stretch, spaced along the whole line so runs keep their place as the
     * reveal moves, and skipping a few by hash.
     */
    private static void runAlong(PathSampler along, float spacing, float offset, long seed, float gapChance, RunDrawer drawer)
    {
        long first = (long) Math.floor((along.startS() - offset) / spacing) - 1;
        long last = (long) Math.ceil((along.endS() + STREAK_LENGTH - offset) / spacing) + 1;
        for (long k = first; k <= last; k++)
        {
            if (LeySightPlan.hashUnit(seed, k) < gapChance)
                continue;
            drawer.draw(k * spacing + offset, k);
        }
    }

    /**
     * A textured run from its tail to its head along the line, its texture's u from 0 at the tail to 1 at the head.
     */
    private static void drawRun(Quads quads, PathSampler along, float tail, float head, int pieces, float half, int rgb, float alpha, Sway sway)
    {
        if (head < along.startS() || tail > along.endS())
            return;

        var previous = along.at(tail, sway);
        for (int p = 1; p <= pieces; p++)
        {
            float s = tail + (head - tail) * p / pieces;
            var next = along.at(s, sway);
            if (previous.inside() && next.inside() && (previous.bright() > 0.004F || next.bright() > 0.004F))
                quads.ribbon(previous.point(), next.point(), previous.side(), next.side(), half, half, (float) (p - 1) / pieces, (float) p / pieces,
                        color(rgb, alpha * previous.bright()), color(rgb, alpha * next.bright()));
            previous = next;
        }
    }

    private record Sample(Vector3f point, Vector3f side, float bright, boolean inside)
    {
    }

    /**
     * Places along a kept stretch by blocks from the line's from node, between its cached points.
     */
    private record PathSampler(LeySightPlan.Path path, Vector3f[] points, Vector3f[] sides, float[] bright)
    {
        float startS()
        {
            return path.start();
        }

        float endS()
        {
            return path.start() + (points.length - 1) * LeySightPlan.STEP;
        }

        Sample at(float s, Sway sway)
        {
            float position = (s - startS()) / LeySightPlan.STEP;
            boolean inside = position >= 0 && position <= points.length - 1;
            position = Mth.clamp(position, 0, points.length - 1);
            int i = Math.min((int) position, points.length - 2);
            float f = position - i;

            var point = new Vector3f(points[i]).lerp(points[i + 1], f);
            var side = new Vector3f(sides[i]).lerp(sides[i + 1], f);
            if (sway != null)
            {
                var offset = sway.at(s);
                point.add(side.x * offset[0], side.y * offset[0] + offset[1], side.z * offset[0]);
            }
            return new Sample(point, side, Mth.lerp(f, bright[i], bright[i + 1]), inside);
        }
    }

    private static void drawNode(Frame frame, ShownNode node, Vec3 camera, float age, float time)
    {
        float radius = LeySight.RADIUS;
        float shown = LeySightPlan.reveal(age, node.distance(), radius) + LeySightPlan.frontFlash(age, node.distance(), radius);
        float bright = shown * (1 - TAINT_DIM * node.taint());
        if (bright <= 0.004F)
            return;

        int rgb = lerp(node.rgb(), TAINT_RGB, node.taint());
        float level = WEAK_FLOOR + (1 - WEAK_FLOOR) * node.level();
        var center = new Vector3f((float) (node.x() - camera.x), (float) (node.y() - camera.y), (float) (node.z() - camera.z));
        float bottom = center.y - LeyHeights.HOVER - 0.5F;
        float top = center.y + NODE_HEIGHT + NODE_HEIGHT_FLOW * node.level();

        // A soft column, camera facing around its upright axis
        var toCamera = new Vector3f(-center.x, 0, -center.z);
        var side = toCamera.lengthSquared() < 1.0E-6F ? new Vector3f(1, 0, 0) : new Vector3f(toCamera.z, 0, -toCamera.x).normalize();
        float haloHalf = NODE_HALO_HALF + NODE_HALO_HALF_FLOW * node.level();
        for (int p = 0; p < NODE_COLUMN_PIECES; p++)
        {
            float h0 = (float) p / NODE_COLUMN_PIECES, h1 = (float) (p + 1) / NODE_COLUMN_PIECES;
            var from = new Vector3f(center.x, Mth.lerp(h0, bottom, top), center.z);
            var to = new Vector3f(center.x, Mth.lerp(h1, bottom, top), center.z);
            float a0 = NODE_HALO_ALPHA * level * bright * columnFade(h0);
            float a1 = NODE_HALO_ALPHA * level * bright * columnFade(h1);
            float sheath0 = SHEATH_ALPHA * level * Math.min(1, bright) * columnFade(h0);
            float sheath1 = SHEATH_ALPHA * level * Math.min(1, bright) * columnFade(h1);
            frame.sheath.ribbon(from, to, side, side, haloHalf * SHEATH_WIDTH, haloHalf * SHEATH_WIDTH, 0, 1, color(SHEATH_RGB, sheath0), color(SHEATH_RGB, sheath1));
            frame.halo.ribbon(from, to, side, side, haloHalf, haloHalf, 0, 1, color(rgb, a0), color(rgb, a1));
            frame.haloThrough.ribbon(from, to, side, side, haloHalf, haloHalf, 0, 1, color(rgb, a0 * THROUGH_ALPHA), color(rgb, a1 * THROUGH_ALPHA));
        }

        // Arms twisting up around it, turning slowly
        float armRadius = NODE_RADIUS + NODE_RADIUS_FLOW * node.level();
        float phase = LeySightPlan.hashUnit(node.seed(), 9) * (float) (2 * Math.PI);
        for (int arm = 0; arm < NODE_ARMS; arm++)
        {
            var points = new Vector3f[NODE_ARM_PIECES + 1];
            for (int p = 0; p <= NODE_ARM_PIECES; p++)
            {
                float h = (float) p / NODE_ARM_PIECES;
                float angle = phase + arm * (float) (2 * Math.PI / NODE_ARMS) + h * NODE_TWIST + time * NODE_SPIN;
                float r = armRadius * (0.35F + 0.65F * h);
                points[p] = new Vector3f(center.x + Mth.cos(angle) * r, Mth.lerp(h, bottom, top), center.z + Mth.sin(angle) * r);
            }
            for (int p = 0; p < NODE_ARM_PIECES; p++)
            {
                var side0 = facingSide(points[Math.max(0, p - 1)], points[p + 1], points[p]);
                var side1 = facingSide(points[p], points[Math.min(NODE_ARM_PIECES, p + 2)], points[p + 1]);
                float h0 = (float) p / NODE_ARM_PIECES, h1 = (float) (p + 1) / NODE_ARM_PIECES;
                float u0 = (float) Math.floorMod(p, CORE_TILE_STEPS) / CORE_TILE_STEPS;
                frame.core.ribbon(points[p], points[p + 1], side0, side1, NODE_ARM_HALF, NODE_ARM_HALF, u0, u0 + 1F / CORE_TILE_STEPS,
                        color(rgb, NODE_ARM_ALPHA * level * bright * columnFade(h0)), color(rgb, NODE_ARM_ALPHA * level * bright * columnFade(h1)));
            }
        }
    }

    /**
     * The searching shell: a sphere around the rod growing with the reveal's front, its light gathered at the horizon and thinning toward the poles, over a
     * faint blended violet. Its texture is sampled at one pixel of the halo's bright middle, so the color comes from the vertices; spreading the texture
     * over it showed as stripes.
     */
    private static void drawShell(Frame frame, Sight current, Vec3 camera, float age, float time)
    {
        float fade = LeySightPlan.shellFade(age);
        if (fade <= 0.004F)
            return;
        float radius = LeySightPlan.shellRadius(age, LeySight.RADIUS);
        if (radius < 0.5F)
            return;

        float cx = (float) (current.originX() - camera.x);
        float cy = (float) (current.originY() + SHELL_CENTER_Y - camera.y);
        float cz = (float) (current.originZ() - camera.z);
        var rows = new Vector3f[SHELL_RINGS + 1][SHELL_SEGMENTS + 1];
        var band = new float[SHELL_RINGS + 1];
        for (int ring = 0; ring <= SHELL_RINGS; ring++)
        {
            float latitude = Mth.PI * ((float) ring / SHELL_RINGS - 0.5F);
            float y = Mth.sin(latitude), across = Mth.cos(latitude);
            band[ring] = (float) Math.pow(Math.max(0, across), SHELL_BAND_POWER);
            for (int segment = 0; segment <= SHELL_SEGMENTS; segment++)
            {
                float longitude = 2 * Mth.PI * segment / SHELL_SEGMENTS;
                rows[ring][segment] = new Vector3f(cx + Mth.cos(longitude) * across * radius, cy + y * radius, cz + Mth.sin(longitude) * across * radius);
            }
        }

        for (int ring = 0; ring < SHELL_RINGS; ring++)
        {
            // The body thins faster than its border and motes, which carry the shell while it lingers
            float a0 = fade * fade * band[ring], a1 = fade * fade * band[ring + 1];
            if (a0 <= 0.004F && a1 <= 0.004F)
                continue;
            int glow0 = color(SHELL_RGB, SHELL_ALPHA * a0), glow1 = color(SHELL_RGB, SHELL_ALPHA * a1);
            int dark0 = color(SHELL_TINT_RGB, SHELL_TINT_ALPHA * a0), dark1 = color(SHELL_TINT_RGB, SHELL_TINT_ALPHA * a1);
            for (int segment = 0; segment < SHELL_SEGMENTS; segment++)
            {
                var p00 = rows[ring][segment];
                var p01 = rows[ring][segment + 1];
                var p11 = rows[ring + 1][segment + 1];
                var p10 = rows[ring + 1][segment];
                frame.sheath.quad(p00, p01, p11, p10, SHELL_TEXEL, SHELL_TEXEL, dark0, dark0, dark1, dark1);
                frame.halo.quad(p00, p01, p11, p10, SHELL_TEXEL, SHELL_TEXEL, glow0, glow0, glow1, glow1);
            }
        }

        if (current.terrain() != null)
            drawBorder(frame, current, camera, radius, fade);
        drawShellMotes(frame, current, cx, cy, cz, radius, fade, age, time);
    }

    /**
     * A strand along where the shell meets the terrain, found each frame from the terrain sampled when the reveal arrived. It breaks where neighbouring
     * points differ too much in height rather than stretching up a cliff. About {@value #BORDER_DIRECTIONS} points, three quads each.
     */
    private static void drawBorder(Frame frame, Sight current, Vec3 camera, float radius, float fade)
    {
        var terrain = current.terrain();
        float centerY = (float) current.originY() + SHELL_CENTER_Y;
        var points = new Vector3f[BORDER_DIRECTIONS];
        var heights = new float[BORDER_DIRECTIONS];
        for (int direction = 0; direction < BORDER_DIRECTIONS; direction++)
        {
            var contact = LeySightPlan.shellContact(terrain[direction], 1, centerY, radius);
            // A contact at the rod's own column, while the shell is still smaller than its height off the ground, has no ring to draw
            if (contact == null || contact[0] <= 0)
                continue;
            float angle = 2 * Mth.PI * direction / BORDER_DIRECTIONS;
            heights[direction] = contact[1];
            points[direction] = new Vector3f((float) (current.originX() + Mth.cos(angle) * contact[0] - camera.x),
                    (float) (contact[1] + BORDER_LIFT - camera.y),
                    (float) (current.originZ() + Mth.sin(angle) * contact[0] - camera.z));
        }

        var sides = new Vector3f[BORDER_DIRECTIONS];
        for (int direction = 0; direction < BORDER_DIRECTIONS; direction++)
        {
            if (points[direction] == null)
                continue;
            var before = points[Math.floorMod(direction - 1, BORDER_DIRECTIONS)];
            var after = points[(direction + 1) % BORDER_DIRECTIONS];
            sides[direction] = facingSide(before == null ? points[direction] : before, after == null ? points[direction] : after, points[direction]);
        }

        float coreHalf = BORDER_CORE_HALF + BORDER_CORE_GROW * radius;
        float haloHalf = BORDER_HALO_HALF + BORDER_HALO_GROW * radius;
        int core = color(CORE_RGB, BORDER_CORE_ALPHA * fade);
        int halo = color(HALO_RGB, BORDER_HALO_ALPHA * fade);
        int sheath = color(SHEATH_RGB, SHEATH_ALPHA * fade);
        for (int direction = 0; direction < BORDER_DIRECTIONS; direction++)
        {
            int next = (direction + 1) % BORDER_DIRECTIONS;
            if (points[direction] == null || points[next] == null || Math.abs(heights[direction] - heights[next]) > BORDER_BREAK)
                continue;
            var from = points[direction];
            var to = points[next];
            frame.sheath.ribbon(from, to, sides[direction], sides[next], haloHalf * SHEATH_WIDTH, haloHalf * SHEATH_WIDTH, 0, 1, sheath, sheath);
            frame.halo.ribbon(from, to, sides[direction], sides[next], haloHalf, haloHalf, 0, 1, halo, halo);
            float u0 = (float) Math.floorMod(direction, CORE_TILE_STEPS) / CORE_TILE_STEPS;
            frame.core.ribbon(from, to, sides[direction], sides[next], coreHalf, coreHalf, u0, u0 + 1F / CORE_TILE_STEPS, core, core);
        }
    }

    /**
     * Motes drifting slowly around the shell, each its own way and pace, bobbing a little. Each is a short tailed run along the shell, placed from the
     * reveal's seed and its age alone. Terrain in front hides them like the shell.
     */
    private static void drawShellMotes(Frame frame, Sight current, float cx, float cy, float cz, float radius, float fade, float age, float time)
    {
        float alpha = SHELL_MOTE_ALPHA * fade * Math.min(1, radius / SHELL_MOTE_FROM);
        if (alpha <= 0.004F)
            return;
        float half = SHELL_MOTE_HALF + SHELL_MOTE_GROW * radius;
        float tail = SHELL_MOTE_TAIL + SHELL_MOTE_TAIL_GROW * radius;
        int rgb = color(SHELL_MOTE_RGB, alpha);
        for (int mote = 0; mote < SHELL_MOTES; mote++)
        {
            long seed = current.seed();
            float start = LeySightPlan.hashUnit(seed, mote * 5L) * 2 * Mth.PI;
            float pace = 0.5F + LeySightPlan.hashUnit(seed, mote * 5L + 1);
            float way = LeySightPlan.hashUnit(seed, mote * 5L + 2) < 0.5F ? -1 : 1;
            // Squared so more of them gather near the horizon
            float rise = LeySightPlan.hashUnit(seed, mote * 5L + 3);
            float latitude = Mth.lerp(rise * rise, SHELL_MOTE_LOWEST, SHELL_MOTE_HIGHEST)
                    + Mth.sin(LeySightPlan.hashUnit(seed, mote * 5L + 4) * 2 * Mth.PI + time * SHELL_MOTE_BOB_SPEED) * SHELL_MOTE_BOB;
            float turned = start + way * pace * SHELL_MOTE_DRIFT * age;
            float across = Math.max(0.05F, Mth.cos(latitude)) * radius;
            float behind = turned - way * tail / across;

            var head = new Vector3f(cx + Mth.cos(turned) * across, cy + Mth.sin(latitude) * radius, cz + Mth.sin(turned) * across);
            var back = new Vector3f(cx + Mth.cos(behind) * across, head.y, cz + Mth.sin(behind) * across);
            var side = facingSide(back, head, head);
            frame.motes.ribbon(back, head, side, side, half, half, 0, 1, rgb, rgb);
        }
    }

    /**
     * Fades a node's column in from the ground and out at its top.
     */
    private static float columnFade(float h)
    {
        return (float) Math.sqrt(Math.max(0, Mth.sin(h * Mth.PI)));
    }

    /**
     * Unit vector across a path at a point, perpendicular to the path and the view, so the ribbon faces the camera.
     */
    private static Vector3f facingSide(Vector3f before, Vector3f after, Vector3f at)
    {
        var tangent = new Vector3f(after).sub(before);
        var side = tangent.cross(-at.x, -at.y, -at.z);
        return side.lengthSquared() < 1.0E-8F ? new Vector3f(1, 0, 0) : side.normalize();
    }

    private static long seed(int a, int b, int c, int d)
    {
        return ((long) a * 73856093L) ^ ((long) b * 19349663L) ^ ((long) c * 83492791L) ^ ((long) d * 2654435761L);
    }

    private static int lerp(int from, int to, float t)
    {
        return ARGB.srgbLerp(Mth.clamp(t, 0, 1), from | 0xFF000000, to | 0xFF000000) & 0xFFFFFF;
    }

    private static int color(int rgb, float alpha)
    {
        return ARGB.color(Mth.clamp((int) (alpha * 255), 0, 255), rgb);
    }

    private static final class Frame
    {
        final Quads sheath      = new Quads();
        final Quads core        = new Quads();
        final Quads through     = new Quads();
        final Quads halo        = new Quads();
        final Quads haloThrough = new Quads();
        final Quads chevrons    = new Quads();
        final Quads motes       = new Quads();
    }

    /**
     * Quads gathered while collecting and written out later in the frame, when the geometry is built.
     */
    private static final class Quads
    {
        private final FloatArrayList vertices = new FloatArrayList();
        private final IntArrayList   colors   = new IntArrayList();

        void ribbon(Vector3f start, Vector3f end, Vector3f startSide, Vector3f endSide, float startHalf, float endHalf, float u0, float u1, int startColor,
                    int endColor)
        {
            if (ARGB.alpha(startColor) == 0 && ARGB.alpha(endColor) == 0)
                return;
            vertex(start.x - startSide.x * startHalf, start.y - startSide.y * startHalf, start.z - startSide.z * startHalf, u0, 0, startColor);
            vertex(start.x + startSide.x * startHalf, start.y + startSide.y * startHalf, start.z + startSide.z * startHalf, u0, 1, startColor);
            vertex(end.x + endSide.x * endHalf, end.y + endSide.y * endHalf, end.z + endSide.z * endHalf, u1, 1, endColor);
            vertex(end.x - endSide.x * endHalf, end.y - endSide.y * endHalf, end.z - endSide.z * endHalf, u1, 0, endColor);
        }

        /**
         * A quad of four corners in order, its texture sampled at one point only, so its color comes from its vertices alone.
         */
        void quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, float u, float v, int colorA, int colorB, int colorC, int colorD)
        {
            if (ARGB.alpha(colorA) == 0 && ARGB.alpha(colorB) == 0 && ARGB.alpha(colorC) == 0 && ARGB.alpha(colorD) == 0)
                return;
            vertex(a.x, a.y, a.z, u, v, colorA);
            vertex(b.x, b.y, b.z, u, v, colorB);
            vertex(c.x, c.y, c.z, u, v, colorC);
            vertex(d.x, d.y, d.z, u, v, colorD);
        }

        private void vertex(float x, float y, float z, float u, float v, int color)
        {
            vertices.add(x);
            vertices.add(y);
            vertices.add(z);
            vertices.add(u);
            vertices.add(v);
            colors.add(color);
        }

        void submit(net.minecraft.client.renderer.SubmitNodeCollector collector, PoseStack poseStack, RenderType type)
        {
            if (!colors.isEmpty())
                collector.submitCustomGeometry(poseStack, type, this::emit);
        }

        private void emit(PoseStack.Pose pose, VertexConsumer buffer)
        {
            for (int i = 0; i < colors.size(); i++)
            {
                int at = i * 5;
                buffer.addVertex(pose, vertices.getFloat(at), vertices.getFloat(at + 1), vertices.getFloat(at + 2))
                        .setColor(colors.getInt(i))
                        .setUv(vertices.getFloat(at + 3), vertices.getFloat(at + 4))
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightCoordsUtil.FULL_BRIGHT)
                        .setNormal(pose, 0, 1, 0);
            }
        }
    }
}
