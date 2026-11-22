package net.voxelindustry.voidheart.client.render.oculus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.content.oculus.OculusFootprint;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableBlock;
import net.voxelindustry.voidheart.common.content.works.WorksGraph;
import net.voxelindustry.voidheart.common.content.works.WorksLayout;
import net.voxelindustry.voidheart.common.content.works.WorksPayloads;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The order a Reckoning table shows, laid on the table as a map of its work: each item stands upright on a voidstone plate rimmed in its state's colour
 * (teal waiting, gold being worked, lilac done, rose lacking), lines run flat between them from the raw kinds to the product, and motes run along the
 * lines into what is being worked. The map turns in quarter steps to read left to right from where the viewer stands. An order rises out of the table
 * column by column, a finished one flares at its product and sinks back; with nothing running, a faint memoriam sigil breathes at the centre.
 * Each player's tables show that player's own oldest order.
 * <p>
 * Layers: plates and line sheaths are {@link VoidHeartRenderTypes#tint} so they keep their dark against a bright sky; rims, lines, motes and the sigil
 * are {@link VoidHeartRenderTypes#radiant}, added without writing depth so their overlaps can't flicker; items are their own models, lit full bright.
 * Each layer sits a fraction of a pixel above the last, so none share a plane with the table or each other.
 * <p>
 * Per frame, at most about 40 items and 60 lines: an item model each, 2 quads per plate, 2 × 12 quads per line, 1 per mote. The layout is worked out
 * once per order.
 */
public final class ReckoningHologram
{
    private static final Identifier PLATE_TEXTURE  = Identifier.fromNamespaceAndPath(MODID, "textures/misc/reckoning_plate.png");
    private static final Identifier RIM_TEXTURE    = Identifier.fromNamespaceAndPath(MODID, "textures/misc/reckoning_rim.png");
    private static final Identifier SIGIL_TEXTURE  = Identifier.fromNamespaceAndPath(MODID, "textures/misc/reckoning_sigil.png");
    private static final Identifier LINE_TEXTURE   = Identifier.fromNamespaceAndPath(MODID, "textures/misc/ley_sight_core.png");
    private static final Identifier SHEATH_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/misc/ley_sight_halo.png");
    private static final Identifier MOTE_TEXTURE   = Identifier.fromNamespaceAndPath(MODID, "textures/misc/ley_sight_mote.png");

    private static final RenderType PLATE_TYPE  = VoidHeartRenderTypes.tint(PLATE_TEXTURE);
    private static final RenderType SHEATH_TYPE = VoidHeartRenderTypes.tint(SHEATH_TEXTURE);
    private static final RenderType RIM_TYPE    = VoidHeartRenderTypes.radiant(RIM_TEXTURE);
    private static final RenderType LINE_TYPE   = VoidHeartRenderTypes.radiant(LINE_TEXTURE);
    private static final RenderType MOTE_TYPE   = VoidHeartRenderTypes.radiant(MOTE_TEXTURE);
    private static final RenderType SIGIL_TYPE  = VoidHeartRenderTypes.radiant(SIGIL_TEXTURE);

    private static final int WAITING_RGB = 0x7CE8D6;
    private static final int WORKING_RGB = 0xF4D670;
    private static final int DONE_RGB    = 0xD6C9F5;
    private static final int LACKING_RGB = 0xEF8A98;
    private static final int LINE_RGB    = 0xC9B8F2;
    private static final int SHEATH_RGB  = 0x14091F;

    private static final int   PLATE_RGB          = 0xC8BCD0;
    private static final float PLATE_INSET        = 12 / 16F;
    private static final float PLATE_ALPHA        = 0.92F;
    private static final float BLOCK_SCALE        = 0.7F;
    /**
     * How much of the viewer's height above an item it leans back by, and at most how far.
     */
    private static final float TILT_SHARE         = 0.7F;
    private static final float MAX_TILT           = 50 * Mth.DEG_TO_RAD;
    private static final float RIM_ALPHA          = 0.9F;
    private static final float DONE_DIM           = 0.5F;
    private static final float LINE_HALF          = 0.022F;
    private static final float LINE_ALPHA         = 0.85F;
    private static final float LINE_WORKING_ALPHA = 0.8F;
    private static final float LINE_TILE          = 0.5F;
    private static final float SHEATH_HALF        = 0.06F;
    private static final float SHEATH_ALPHA       = 0.55F;
    private static final int   ARC_SAMPLES        = 12;
    private static final float MOTE_LENGTH        = 0.15F;
    private static final float MOTE_HALF          = 0.035F;
    private static final float MOTE_ALPHA         = 0.95F;
    private static final long  MOTE_MILLIS        = 1400;
    private static final float MOTE_SPREAD        = 0.15F;

    private static final long  BREATH_MILLIS = 2000;
    private static final float BREATH_DEPTH  = 0.35F;
    private static final long  SPIN_MILLIS   = 4000;
    private static final long  BOB_MILLIS    = 3000;
    private static final float BOB_HEIGHT    = 1 / 32F;
    private static final float DONE_SINK     = 1 / 48F;
    private static final float RISE_DEPTH    = 1.3F;

    private static final long  RISE_MILLIS    = 600;
    private static final long  STAGGER_MILLIS = 200;
    private static final long  FLARE_MILLIS   = 300;
    private static final long  SINK_MILLIS    = 1000;
    private static final float FLARE_GROWTH   = 1.2F;
    private static final long  QUARTER_MILLIS = 500;

    /**
     * Table blocks over which everything fades out toward the rim.
     */
    private static final float FADE = 0.25F;

    private static final float SHEATH_LIFT = 0.006F;
    private static final float LINE_LIFT   = 0.009F;
    private static final float PLATE_LIFT  = 0.012F;
    private static final float RIM_LIFT    = 0.015F;
    private static final float MOTE_LIFT   = 0.018F;
    private static final float ITEM_LIFT   = 0.03F;

    private static final float SIGIL_SIZE   = 0.9F;
    private static final float SIGIL_ALPHA  = 0.3F;
    private static final float SIGIL_BREATH = 0.12F;
    private static final long  SIGIL_MILLIS = 4000;

    private static final float PIP_SIZE = 0.07F;
    private static final float PIP_GAP  = 0.05F;
    private static final int   MAX_PIPS = 8;

    private static final float HALF_PI = Mth.HALF_PI;

    private static WorksPayloads.@Nullable Job shown;
    private static int others;
    private static long shownAt;
    private static WorksPayloads.@Nullable Job leaving;
    private static long leftAt;
    private static boolean flare;
    private static long idleAt;

    private static final Map<BlockPos, Float> TURNS = new HashMap<>();
    private static final Map<WorksGraph, WorksLayout<Integer>> LAYOUTS = new IdentityHashMap<>();
    private static long lastFrame;
    private static int drawnLastFrame;

    private ReckoningHologram()
    {
    }

    public static void receive(WorksPayloads.Shown payload, ClientPlayNetworking.Context context)
    {
        long now = Util.getMillis();
        var next = payload.job().orElse(null);
        if (shown != null && (next == null || next.id() != shown.id()))
        {
            leaving = shown;
            leftAt = now;
            // Gone with its last craft at work, it finished; with more left, it was cancelled
            flare = shown.left() <= 1;
        }
        if (next != null && (shown == null || next.id() != shown.id()))
            shownAt = leaving != null ? Math.max(now, leftAt + leaveMillis()) : now;
        shown = next;
        others = payload.others();
    }

    public static void clear()
    {
        shown = null;
        leaving = null;
        others = 0;
        TURNS.clear();
        LAYOUTS.clear();
    }

    private static long leaveMillis()
    {
        return (flare ? FLARE_MILLIS : 0) + SINK_MILLIS;
    }

    /**
     * How many items were drawn on tables last frame, for tests.
     */
    public static int drawnLastFrame()
    {
        return drawnLastFrame;
    }

    /**
     * The order this client's tables show, for tests.
     */
    public static WorksPayloads.@Nullable Job shown()
    {
        return shown;
    }

    // Drawing

    /**
     * A table's frame: its top, centre and the two directions the map lies along, as the viewer stands now.
     */
    private record Frame(OculusFootprint footprint, double surface, double centerX, double centerZ, float alongX, float alongZ, float towardX, float towardZ,
                         float length, float breadth, Vec3 camera)
    {
        double worldX(float along, float toward)
        {
            return centerX + along * alongX + toward * towardX;
        }

        double worldZ(float along, float toward)
        {
            return centerZ + along * alongZ + toward * towardZ;
        }

        float fade(double x, double z)
        {
            if (!footprint.contains(Mth.floor(x), Mth.floor(z)))
                return 0;
            double edge = Math.min(Math.min(x - footprint.minX(), footprint.minX() + footprint.width() - x),
                    Math.min(z - footprint.minZ(), footprint.minZ() + footprint.depth() - z));
            return (float) Mth.clamp(edge / FADE, 0, 1);
        }

        void vertex(Layer layer, float along, float toward, float lift, float u, float v, int rgb, float alpha)
        {
            double x = worldX(along, toward);
            double z = worldZ(along, toward);
            layer.add((float) (x - camera.x), (float) (surface + lift - camera.y), (float) (z - camera.z), u, v,
                    ARGB.color(Mth.clamp((int) (alpha * fade(x, z) * 255), 0, 255), rgb));
        }

        /**
         * A square lying flat, its sides along and across the map.
         */
        void square(Layer layer, float along, float toward, float half, float lift, int rgb, float alpha)
        {
            vertex(layer, along - half, toward - half, lift, 0, 0, rgb, alpha);
            vertex(layer, along - half, toward + half, lift, 0, 1, rgb, alpha);
            vertex(layer, along + half, toward + half, lift, 1, 1, rgb, alpha);
            vertex(layer, along + half, toward - half, lift, 1, 0, rgb, alpha);
        }
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        long now = Util.getMillis();
        long frameMillis = lastFrame == 0 ? 0 : Math.min(200, now - lastFrame);
        lastFrame = now;
        drawnLastFrame = 0;
        if (leaving != null && now >= leftAt + leaveMillis())
            leaving = null;
        if (shown == null && leaving == null)
        {
            if (idleAt == 0)
                idleAt = now;
        }
        else
            idleAt = 0;

        var level = Minecraft.getInstance().level;
        if (level == null)
            return;
        var camera = context.levelState().cameraRenderState;
        var live = new HashSet<BlockPos>();
        for (var projector : OculusRenderer.projectors())
        {
            var view = projector.view();
            if (!view.reckoning() || view.asleep())
                continue;
            var footprint = projector.footprint();
            live.add(footprint.anchor());
            drawTable(context, level, footprint, camera.pos, now, frameMillis);
        }
        TURNS.keySet().retainAll(live);
        if (LAYOUTS.size() > 4)
            LAYOUTS.keySet().removeIf(graph -> (shown == null || graph != shown.graph()) && (leaving == null || graph != leaving.graph()));
    }

    private static void drawTable(LevelRenderContext context, ClientLevel level, OculusFootprint footprint, Vec3 camera, long now, long frameMillis)
    {
        double centerX = footprint.centerX();
        double centerZ = footprint.centerZ();

        // The map reads left to right from the side the viewer stands on, turning there in quarter steps
        double dx = camera.x - centerX;
        double dz = camera.z - centerZ;
        float target = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? HALF_PI : -HALF_PI) : (dz > 0 ? 0 : Mth.PI);
        float angle = TURNS.getOrDefault(footprint.anchor(), target);
        float diff = Mth.wrapDegrees((target - angle) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        float step = frameMillis * HALF_PI / QUARTER_MILLIS;
        angle = Math.abs(diff) <= step ? target : angle + Math.signum(diff) * step;
        TURNS.put(footprint.anchor(), angle);

        boolean flowAlongZ = Math.abs(Mth.sin(target)) > 0.5F;
        float length = flowAlongZ ? footprint.depth() : footprint.width();
        float breadth = flowAlongZ ? footprint.width() : footprint.depth();
        var frame = new Frame(footprint, footprint.y() + OculusTableBlock.HEIGHT, centerX, centerZ, Mth.cos(angle), -Mth.sin(angle), Mth.sin(angle),
                Mth.cos(angle), length, breadth, camera);

        var layers = new Layers();
        if (leaving != null)
            drawJob(context, level, frame, layers, leaving, now, true);
        if (shown != null && now >= shownAt)
        {
            drawJob(context, level, frame, layers, shown, now, false);
            drawPips(frame, layers, now);
        }
        if (shown == null && leaving == null)
            drawSigil(frame, layers, now);
        layers.submit(context.submitNodeCollector(), context.poseStack());
    }

    private static float smooth(float t)
    {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float wave(long now, long period, float phase)
    {
        return Mth.sin((float) ((now % period) / (double) period + phase) * Mth.TWO_PI);
    }

    /**
     * A number in [0, 1) fixed for a seed, to set nodes and lines out of step.
     */
    private static float scatter(long seed)
    {
        seed = (seed ^ 0x5DEECE66DL) * 0x9E3779B97F4A7C15L;
        seed ^= seed >>> 29;
        return (seed >>> 40) / (float) (1L << 24);
    }

    private static void drawJob(LevelRenderContext context, ClientLevel level, Frame frame, Layers layers, WorksPayloads.Job job, long now, boolean gone)
    {
        var graph = job.graph();
        int count = graph.nodes().size();
        if (count == 0)
            return;
        var layout = LAYOUTS.computeIfAbsent(graph, key -> WorksLayout.of(key.asPlan()));
        var place = ReckoningPlacement.of(layout, count, frame.length(), frame.breadth());
        float half = place.plate() / 2;

        // How far each node has risen out of the table, or sunk back into it
        var presence = new float[count];
        long sinceLeft = now - leftAt;
        long flareFor = flare ? FLARE_MILLIS : 0;
        for (int node = 0; node < count; node++)
        {
            var spot = layout.spots().get(node);
            int column = spot == null ? 0 : spot.column();
            presence[node] = gone
                    ? 1 - smooth((sinceLeft - flareFor) / (float) SINK_MILLIS)
                    : smooth((now - shownAt - column * STAGGER_MILLIS) / (float) RISE_MILLIS);
        }

        var rgb = new int[count];
        var working = new boolean[count];
        var done = new boolean[count];
        for (int node = 0; node < count; node++)
        {
            var info = graph.nodes().get(node);
            if (info.lacking() > 0)
                rgb[node] = LACKING_RGB;
            else if (!info.isMade() || job.left(info.recipe()) <= 0 && job.working().getOrDefault(info.recipe(), 0L) <= 0)
            {
                rgb[node] = DONE_RGB;
                done[node] = true;
            }
            else if (job.working().getOrDefault(info.recipe(), 0L) > 0)
            {
                rgb[node] = WORKING_RGB;
                working[node] = true;
            }
            else
                rgb[node] = WAITING_RGB;
        }

        // Lines, from the right of each ingredient to the left of what it goes into, with motes into what is being worked
        int edgeIndex = 0;
        for (var edge : graph.edges())
        {
            int from = edge.ingredient();
            int to = edge.product();
            float alpha = Math.min(presence[from], presence[to]);
            if (alpha > 0)
            {
                drawLine(frame, layers, place.along()[from] + half, place.across()[from], place.along()[to] - half, place.across()[to], alpha,
                        working[to]);
                if (working[to] && !gone)
                {
                    float seed = scatter(job.id() * 31L + edgeIndex);
                    float speed = 1 + MOTE_SPREAD * (scatter(job.id() * 17L + edgeIndex + 7) * 2 - 1);
                    float t = (float) (((now * speed) % MOTE_MILLIS) / (double) MOTE_MILLIS + seed) % 1;
                    drawMote(frame, layers, place.along()[from] + half, place.across()[from], place.along()[to] - half, place.across()[to], t, alpha);
                }
            }
            edgeIndex++;
        }

        var resolver = Minecraft.getInstance().getItemModelResolver();
        var poseStack = context.poseStack();
        for (int node = 0; node < count; node++)
        {
            float alpha = presence[node];
            if (alpha <= 0)
                continue;
            float along = place.along()[node];
            float toward = place.across()[node];
            float breath = working[node] ? 1 - BREATH_DEPTH / 2 + BREATH_DEPTH / 2 * wave(now, BREATH_MILLIS, scatter(node)) : 1;
            float rimAlpha = RIM_ALPHA * alpha * breath * (done[node] ? DONE_DIM : 1);
            // The plate stops inside its rim: blended, it would dim the rim wherever it is drawn after it
            frame.square(layers.plates, along, toward, half * PLATE_INSET, PLATE_LIFT, PLATE_RGB, PLATE_ALPHA * alpha);
            frame.square(layers.rims, along, toward, half, RIM_LIFT, rgb[node], rimAlpha);

            // The product flares as its order finishes
            if (gone && node == 0 && flare && sinceLeft < FLARE_MILLIS)
            {
                float flash = sinceLeft / (float) FLARE_MILLIS;
                frame.square(layers.rims, along, toward, half * (1 + FLARE_GROWTH * flash), RIM_LIFT, WORKING_RGB, 1 - flash);
                frame.square(layers.rims, along, toward, half * 0.92F, RIM_LIFT, WORKING_RGB, 1 - flash);
            }

            var state = new ItemStackRenderState();
            resolver.updateForTopItem(state, graph.nodes().get(node).item().toStack(), ItemDisplayContext.FIXED, level, null, node);
            if (state.isEmpty())
                continue;
            var box = state.getModelBoundingBox();
            double size = Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize()));
            // Blocks fill their whole box, so they stand smaller to weigh as much as a flat item
            float scale = (float) (place.item() / (size <= 0 ? 1 : size)) * (state.usesBlockLight() ? BLOCK_SCALE : 1);
            float bob = BOB_HEIGHT * (0.5F + 0.5F * wave(now, BOB_MILLIS, scatter(node * 13L + 3)));
            float sink = (1 - alpha) * place.item() * RISE_DEPTH + (done[node] ? DONE_SINK : 0);
            double x = frame.worldX(along, toward);
            double y = frame.surface() + ITEM_LIFT + bob - sink;
            double z = frame.worldZ(along, toward);
            double toCameraX = frame.camera().x - x;
            double toCameraZ = frame.camera().z - z;
            float yaw = (float) Math.atan2(toCameraX, toCameraZ);
            // Leaning back toward a viewer above, so a flat item never shows only its edge
            float tilt = (float) Mth.clamp(Math.atan2(frame.camera().y - y, Math.sqrt(toCameraX * toCameraX + toCameraZ * toCameraZ)) * TILT_SHARE, 0, MAX_TILT);
            float spin = working[node] ? (float) ((now % SPIN_MILLIS) / (double) SPIN_MILLIS) * Mth.TWO_PI : 0;

            poseStack.pushPose();
            poseStack.translate(x - frame.camera().x, y - frame.camera().y, z - frame.camera().z);
            poseStack.rotate(new Quaternionf().rotationY(yaw).rotateX(-tilt).rotateY(spin));
            poseStack.scale(scale, scale, scale);
            poseStack.translate(-(box.minX + box.maxX) / 2, -box.minY, -(box.minZ + box.maxZ) / 2);
            state.submit(poseStack, context.submitNodeCollector(), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
            drawnLastFrame++;
        }
    }

    /**
     * The curve between two plates, flat on the table: it leaves and arrives level, bending in its middle, as on the screen.
     */
    private static float[] arc(float x1, float y1, float x2, float y2, float t)
    {
        float middle = (x1 + x2) / 2;
        float u = 1 - t;
        float x = u * u * u * x1 + 3 * u * u * t * middle + 3 * u * t * t * middle + t * t * t * x2;
        float y = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2;
        // Its direction there
        float tx = 3 * u * u * (middle - x1) + 3 * t * t * (x2 - middle);
        float ty = 6 * u * t * (y2 - y1);
        float norm = Mth.sqrt(tx * tx + ty * ty);
        if (norm < 1e-5F)
        {
            tx = 1;
            ty = 0;
            norm = 1;
        }
        return new float[] { x, y, tx / norm, ty / norm };
    }

    private static void drawLine(Frame frame, Layers layers, float x1, float y1, float x2, float y2, float alpha, boolean intoWorking)
    {
        int rgb = intoWorking ? WORKING_RGB : LINE_RGB;
        float lineAlpha = (intoWorking ? LINE_WORKING_ALPHA : LINE_ALPHA) * alpha;
        float travelled = 0;
        float[] last = null;
        for (int i = 0; i < ARC_SAMPLES; i++)
        {
            var a = last == null ? arc(x1, y1, x2, y2, 0) : last;
            var b = arc(x1, y1, x2, y2, (i + 1) / (float) ARC_SAMPLES);
            float segment = Mth.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]));
            float u0 = travelled / LINE_TILE;
            float u1 = (travelled + segment) / LINE_TILE;
            ribbon(frame, layers.sheaths, a, b, SHEATH_HALF, SHEATH_LIFT, u0, u1, SHEATH_RGB, SHEATH_ALPHA * alpha);
            ribbon(frame, layers.lines, a, b, LINE_HALF, LINE_LIFT, u0, u1, rgb, lineAlpha);
            travelled += segment;
            last = b;
        }
    }

    /**
     * One stretch of a flat ribbon, its ends square to the curve there, so stretches meet without notches.
     */
    private static void ribbon(Frame frame, Layer layer, float[] a, float[] b, float half, float lift, float u0, float u1, int rgb, float alpha)
    {
        // Across the curve, in the map's plane
        float ax = -a[3] * half, ay = a[2] * half;
        float bx = -b[3] * half, by = b[2] * half;
        frame.vertex(layer, a[0] - ax, a[1] - ay, lift, u0, 0, rgb, alpha);
        frame.vertex(layer, a[0] + ax, a[1] + ay, lift, u0, 1, rgb, alpha);
        frame.vertex(layer, b[0] + bx, b[1] + by, lift, u1, 1, rgb, alpha);
        frame.vertex(layer, b[0] - bx, b[1] - by, lift, u1, 0, rgb, alpha);
    }

    private static void drawMote(Frame frame, Layers layers, float x1, float y1, float x2, float y2, float t, float alpha)
    {
        var head = arc(x1, y1, x2, y2, t);
        float tailX = head[0] - head[2] * MOTE_LENGTH;
        float tailY = head[1] - head[3] * MOTE_LENGTH;
        float sideX = -head[3] * MOTE_HALF, sideY = head[2] * MOTE_HALF;
        // Fading in as it leaves and out as it arrives
        float ends = Math.min(1, Math.min(t, 1 - t) * 6);
        float moteAlpha = MOTE_ALPHA * alpha * ends;
        frame.vertex(layers.motes, tailX - sideX, tailY - sideY, MOTE_LIFT, 0, 0, WORKING_RGB, moteAlpha);
        frame.vertex(layers.motes, tailX + sideX, tailY + sideY, MOTE_LIFT, 0, 1, WORKING_RGB, moteAlpha);
        frame.vertex(layers.motes, head[0] + sideX, head[1] + sideY, MOTE_LIFT, 1, 1, WORKING_RGB, moteAlpha);
        frame.vertex(layers.motes, head[0] - sideX, head[1] - sideY, MOTE_LIFT, 1, 0, WORKING_RGB, moteAlpha);
    }

    /**
     * One pip on the near rim for each running order, the one shown in gold.
     */
    private static void drawPips(Frame frame, Layers layers, long now)
    {
        int pips = Math.min(MAX_PIPS, others + 1);
        if (pips < 2)
            return;
        float alpha = smooth((now - shownAt) / (float) RISE_MILLIS);
        float toward = frame.breadth() / 2 - ReckoningPlacement.MARGIN * 0.45F;
        float spacing = PIP_SIZE + PIP_GAP;
        float start = -(pips - 1) * spacing / 2;
        for (int pip = 0; pip < pips; pip++)
            frame.square(layers.rims, start + pip * spacing, toward, PIP_SIZE / 2, RIM_LIFT, pip == 0 ? WORKING_RGB : WAITING_RGB, RIM_ALPHA * alpha);
    }

    private static void drawSigil(Frame frame, Layers layers, long now)
    {
        float size = Math.min(SIGIL_SIZE, Math.min(frame.length(), frame.breadth()) - 2 * ReckoningPlacement.MARGIN);
        if (size <= 0)
            return;
        float appear = smooth((now - idleAt) / (float) RISE_MILLIS);
        float alpha = (SIGIL_ALPHA + SIGIL_BREATH * wave(now, SIGIL_MILLIS, 0)) * appear;
        frame.square(layers.sigils, 0, 0, size / 2, PLATE_LIFT, DONE_RGB, alpha);
    }

    // Geometry, gathered per layer and handed over once per table

    private static final class Layer
    {
        private final FloatArrayList vertices = new FloatArrayList();
        private final IntArrayList   colors   = new IntArrayList();

        void add(float x, float y, float z, float u, float v, int color)
        {
            vertices.add(x);
            vertices.add(y);
            vertices.add(z);
            vertices.add(u);
            vertices.add(v);
            colors.add(color);
        }

        void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType type)
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

    private static final class Layers
    {
        final Layer sheaths = new Layer();
        final Layer lines   = new Layer();
        final Layer plates  = new Layer();
        final Layer rims    = new Layer();
        final Layer motes   = new Layer();
        final Layer sigils  = new Layer();

        void submit(SubmitNodeCollector collector, PoseStack poseStack)
        {
            sheaths.submit(collector, poseStack, SHEATH_TYPE);
            lines.submit(collector, poseStack, LINE_TYPE);
            plates.submit(collector, poseStack, PLATE_TYPE);
            rims.submit(collector, poseStack, RIM_TYPE);
            motes.submit(collector, poseStack, MOTE_TYPE);
            sigils.submit(collector, poseStack, SIGIL_TYPE);
        }
    }
}
