package net.voxelindustry.voidheart.client.render.energy.grid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameCoreTile;
import net.voxelindustry.voidheart.common.energy.grid.AzuriumPylonTile;
import net.voxelindustry.voidheart.common.energy.grid.PylonBundle;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridContent;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.voxelindustry.voidheart.client.render.energy.FacingStrips.disc;
import static net.voxelindustry.voidheart.client.render.energy.FacingStrips.perpendicular;
import static net.voxelindustry.voidheart.client.render.energy.FacingStrips.ribbon;

/**
 * Draws the void grid: each bundle's glow as its pylon loads, the leaping arcs between pylons, and, on demand, a pylon's field as a dashed ring on the
 * ground with threads to what it feeds. Everything glows additively and writes no depth, except the ring, which is blended so it keeps its hue on bright ground.
 */
public final class VoidGridRender
{
    // Tones sampled from the azurium bundle textures: their lightest, and their middle blue
    private static final int AZURIUM_LIGHT = 0xCCFFF2;
    private static final int AZURIUM_MID   = 0x3D7ED9;
    // A Deep field reaching through a portal: a violet thread into the near portal, and a fainter ring around the far one
    private static final int   DEEP_LIGHT          = 0xBFA9E8;
    private static final int   DEEP_MID            = 0x7A4FD0;
    private static final int   DEEP_THREAD_ALPHA   = 90;
    private static final int   DEEP_PULSE_ALPHA    = 200;
    private static final float DEEP_PULSE_SPACING  = 1.6F;
    private static final float DEEP_RING_SHARE     = 0.7F;

    private static final double DRAW_DISTANCE       = 128;
    private static final double FIELD_SHOW_DISTANCE = 48;

    // Arcs
    private static final float CORE_HALF_WIDTH  = 0.035F;
    private static final float HALO_HALF_WIDTH  = 0.08F;
    private static final float CAP_WIDEN        = 1.5F;
    private static final int   CORE_ALPHA       = 200;
    private static final int   HALO_ALPHA       = 70;
    private static final float LEAP_WIDEN       = 1.4F;
    private static final int   LEAP_SLICES      = 6;
    private static final int   LEAP_CORE_ALPHA  = 255;
    private static final int   LEAP_HALO_ALPHA  = 150;
    private static final float IDLE_HALF_WIDTH  = 0.02F;
    private static final int   IDLE_ALPHA       = 64;
    private static final float FORK_HALF_WIDTH  = 0.025F;
    private static final int   FORK_ALPHA       = 190;

    private static final double SOUND_DISTANCE        = 16;
    private static final float  SOUND_VOLUME          = 0.05F;
    private static final float  SOUND_VOLUME_AT_CAP   = 0.09F;
    private static final int    SOUND_COOLDOWN_TICKS  = 8;

    // Bundle glow
    private static final float HALO_RADIUS     = 1.1F;
    private static final int   GLOW_HALO_ALPHA = 120;
    private static final float BRIGHT_RADIUS   = 0.34F;
    private static final int   BRIGHT_ALPHA    = 190;
    private static final float MIN_GLOW        = 0.02F;

    // Field
    private static final int   FADE_IN_TICKS    = 5;
    private static final int   FADE_OUT_TICKS   = 10;
    private static final int   RING_ALPHA       = 128;
    private static final float RING_HALF_WIDTH  = 0.07F;
    private static final float RING_LIFT        = 0.03F;
    private static final float RING_STEP        = 0.25F;
    private static final float DASH_LENGTH      = 1;
    private static final float GAP_LENGTH       = 1;
    private static final int   RING_TURN_TICKS  = 1200;
    private static final int   RING_REBUILD_TICKS = 40;
    private static final float THREAD_HALF_WIDTH = 0.015F;
    private static final int   THREAD_ALPHA      = 55;
    private static final float PULSE_LENGTH      = 0.35F;
    private static final float PULSE_HALF_WIDTH  = 0.035F;
    private static final int   PULSE_ALPHA       = 170;
    private static final float PULSE_SPEED       = 0.25F;
    private static final float PULSE_TICKS_SLOW  = 40;
    private static final float PULSE_TICKS_FAST  = 5;

    private static final Map<Long, PylonArc>        ARCS   = new HashMap<>();
    private static final Map<BlockPos, FieldState>  FIELDS = new HashMap<>();
    private static final Map<Long, Long>            SOUND_COOLDOWNS = new HashMap<>();
    private static final Map<BlockPos, FieldState>  FAR_RINGS = new HashMap<>();

    private static final class FieldState
    {
        float fade;
        float previousFade;
        boolean shown;
        int radius;
        long builtAt = Long.MIN_VALUE;
        List<@Nullable Vec3> ring = List.of();

        float fade(float partialTicks)
        {
            return previousFade + (fade - previousFade) * partialTicks;
        }
    }

    private VoidGridRender()
    {
    }

    private static long arcKey(BlockPos low, BlockPos high)
    {
        return low.asLong() * 31 + high.asLong();
    }

    private static Map<BlockPos, AzuriumPylonTile> loadedPylons(Level level)
    {
        var byPos = new HashMap<BlockPos, AzuriumPylonTile>();
        AzuriumPylonTile.CLIENT_PYLONS.removeIf(pylon -> pylon.isRemoved() || pylon.getLevel() != level);
        for (var pylon : AzuriumPylonTile.CLIENT_PYLONS)
            byPos.put(pylon.getBlockPos(), pylon);
        return byPos;
    }

    public static void tick(Minecraft minecraft)
    {
        var level = minecraft.level;
        var player = minecraft.player;
        if (level == null || player == null)
        {
            ARCS.clear();
            FIELDS.clear();
            FAR_RINGS.clear();
            SOUND_COOLDOWNS.clear();
            AzuriumPylonTile.CLIENT_PYLONS.clear();
            PortalFrameCoreTile.CLIENT_CORES.clear();
            return;
        }

        // A frozen level holds its arcs still too
        if (!level.tickRateManager().runsNormally())
            return;

        long time = level.getGameTime();
        var pylons = loadedPylons(level);
        boolean holding = showsFields(player.getItemInHand(InteractionHand.MAIN_HAND)) || showsFields(player.getItemInHand(InteractionHand.OFF_HAND));
        var looked = lookedAtPylon(minecraft, level);

        for (var pylon : pylons.values())
        {
            var pos = pylon.getBlockPos();
            var bundle = pylon.bundle();
            boolean shown = bundle != PylonBundle.NONE
                    && (pos.equals(looked) || holding && player.position().distanceToSqr(Vec3.atCenterOf(pos)) < FIELD_SHOW_DISTANCE * FIELD_SHOW_DISTANCE);
            var field = shown ? FIELDS.computeIfAbsent(pos, key -> new FieldState()) : FIELDS.get(pos);
            if (field != null)
            {
                field.shown = shown;
                field.previousFade = field.fade;
                field.fade = shown ? Math.min(1, field.fade + 1F / FADE_IN_TICKS) : Math.max(0, field.fade - 1F / FADE_OUT_TICKS);
                if (field.fade > 0 && (field.radius != bundle.radius() || time - field.builtAt >= RING_REBUILD_TICKS))
                {
                    field.radius = bundle.radius();
                    field.builtAt = time;
                    field.ring = buildRing(level, pylon.bundlePos(), bundle.radius());
                }
            }

            if (bundle == PylonBundle.NONE)
                continue;
            for (var flow : pylon.arcs())
            {
                if (flow.pos().asLong() <= pos.asLong())
                    continue;
                var other = pylons.get(flow.pos());
                if (other == null || other.bundle() == PylonBundle.NONE)
                    continue;

                long key = arcKey(pos, flow.pos());
                var arc = ARCS.computeIfAbsent(key, PylonArc::new);
                arc.seenAt = time;
                float load = arcLoad(flow.rate(), bundle, other.bundle());
                var from = pylon.tip();
                var to = other.tip();
                if (arc.tick(from.distanceTo(to), load))
                    crackle(minecraft, level, key, from.lerp(to, 0.5), load, time);
            }
        }

        tickFarRings(level, player, time);
        ARCS.values().removeIf(arc -> arc.seenAt != time);
        FIELDS.values().removeIf(field -> !field.shown && field.fade <= 0);
        SOUND_COOLDOWNS.values().removeIf(until -> until < time);
    }

    /**
     * Rings around the portals a Deep field reaches out of, shown to anyone near enough to stand in them.
     */
    private static void tickFarRings(Level level, net.minecraft.world.entity.player.Player player, long time)
    {
        PortalFrameCoreTile.CLIENT_CORES.removeIf(core -> core.isRemoved() || core.getLevel() != level);
        for (var core : PortalFrameCoreTile.CLIENT_CORES)
        {
            var pos = core.getBlockPos();
            int radius = (int) core.getDeepReach();
            boolean shown = radius > 0 && player.position().distanceToSqr(Vec3.atCenterOf(pos)) < Math.pow(radius + FIELD_SHOW_DISTANCE, 2);
            var ring = shown ? FAR_RINGS.computeIfAbsent(pos.immutable(), key -> new FieldState()) : FAR_RINGS.get(pos);
            if (ring == null)
                continue;
            ring.shown = shown;
            ring.previousFade = ring.fade;
            ring.fade = shown ? Math.min(1, ring.fade + 1F / FADE_IN_TICKS) : Math.max(0, ring.fade - 1F / FADE_OUT_TICKS);
            if (ring.fade > 0 && radius > 0 && (ring.radius != radius || time - ring.builtAt >= RING_REBUILD_TICKS))
            {
                ring.radius = radius;
                ring.builtAt = time;
                ring.ring = buildRing(level, pos, radius);
            }
        }
        FAR_RINGS.entrySet().removeIf(entry -> !entry.getValue().shown && entry.getValue().fade <= 0
                || PortalFrameCoreTile.CLIENT_CORES.stream().noneMatch(core -> core.getBlockPos().equals(entry.getKey())));
    }

    private static boolean showsFields(ItemStack stack)
    {
        return stack.is(VoidGridContent.AZURIUM_PYLON.asItem()) || PylonBundle.ofBlock(Block.byItem(stack.getItem())) != PylonBundle.NONE;
    }

    private static @Nullable BlockPos lookedAtPylon(Minecraft minecraft, Level level)
    {
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return null;
        var pos = hit.getBlockPos();
        if (level.getBlockState(pos).is(VoidGridContent.AZURIUM_PYLON))
            return pos;
        return PylonBundle.holder(level, pos);
    }

    private static float arcLoad(long rate, PylonBundle a, PylonBundle b)
    {
        long cap = Math.min(a.cap(), b.cap());
        return cap <= 0 ? 0 : Math.min(1, Math.abs(rate) / (float) cap);
    }

    private static void crackle(Minecraft minecraft, Level level, long key, Vec3 at, float load, long time)
    {
        var player = minecraft.player;
        if (player == null || player.position().distanceToSqr(at) > SOUND_DISTANCE * SOUND_DISTANCE || SOUND_COOLDOWNS.getOrDefault(key, Long.MIN_VALUE) > time)
            return;
        SOUND_COOLDOWNS.put(key, time + SOUND_COOLDOWN_TICKS);
        float volume = load >= PylonArc.AT_CAP ? SOUND_VOLUME_AT_CAP : SOUND_VOLUME;
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, SoundSource.BLOCKS, volume, 1.5F + level.getRandom().nextFloat() * 0.4F, false);
    }

    //////////
    // DRAW //
    //////////

    public static void collectSubmits(LevelRenderContext context)
    {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || AzuriumPylonTile.CLIENT_PYLONS.isEmpty() && FAR_RINGS.isEmpty())
            return;

        var levelState = context.levelState();
        var camera = levelState.cameraRenderState.pos;
        float partialTicks = levelState.worldPartialTicks;
        float time = levelState.gameTime + partialTicks;

        var visible = new ArrayList<AzuriumPylonTile>();
        var byPos = new HashMap<BlockPos, AzuriumPylonTile>();
        for (var pylon : AzuriumPylonTile.CLIENT_PYLONS)
        {
            if (pylon.isRemoved() || pylon.getLevel() != level)
                continue;
            byPos.put(pylon.getBlockPos(), pylon);
            if (Vec3.atCenterOf(pylon.getBlockPos()).distanceToSqr(camera) < DRAW_DISTANCE * DRAW_DISTANCE)
                visible.add(pylon);
        }
        if (visible.isEmpty() && FAR_RINGS.isEmpty())
            return;

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            for (var pylon : visible)
            {
                var bundle = pylon.bundle();
                if (bundle == PylonBundle.NONE)
                    continue;
                drawGlow(pose, buffer, pylon, bundle, camera, partialTicks);
                drawArcs(pose, buffer, pylon, bundle, byPos, camera, partialTicks);

                var field = FIELDS.get(pylon.getBlockPos());
                if (field != null && field.fade(partialTicks) > 0)
                    drawThreads(pose, buffer, pylon, bundle, field.fade(partialTicks), camera, time);
                drawDeepThreads(pose, buffer, pylon, camera, time);
            }
        });

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.MARKER, (pose, buffer) ->
        {
            for (var pylon : visible)
            {
                var field = FIELDS.get(pylon.getBlockPos());
                if (field != null && field.fade(partialTicks) > 0 && !field.ring.isEmpty())
                    drawRing(pose, buffer, pylon.bundlePos(), field, field.fade(partialTicks), camera, time, AZURIUM_MID);
            }
            FAR_RINGS.forEach((core, ring) ->
            {
                if (ring.fade(partialTicks) > 0 && !ring.ring.isEmpty())
                    drawRing(pose, buffer, core, ring, ring.fade(partialTicks) * DEEP_RING_SHARE, camera, time, DEEP_MID);
            });
        });
    }

    /**
     * Plain when idle; at cap a bright core over the crystals and a soft halo around them.
     */
    private static void drawGlow(PoseStack.Pose pose, VertexConsumer buffer, AzuriumPylonTile pylon, PylonBundle bundle, Vec3 camera, float partialTicks)
    {
        float glow = pylon.glow(partialTicks);
        if (glow < MIN_GLOW)
            return;
        var center = relative(pylon.bundleCenter(), camera);
        float size = bundle.height() / 15F;
        disc(pose, buffer, center, HALO_RADIUS * (0.6F + 0.4F * size) * (0.7F + 0.3F * glow), ARGB.color((int) (GLOW_HALO_ALPHA * glow), AZURIUM_MID));
        disc(pose, buffer, center, BRIGHT_RADIUS * (0.6F + 0.4F * size), ARGB.color((int) (BRIGHT_ALPHA * glow), AZURIUM_LIGHT));
    }

    private static void drawArcs(PoseStack.Pose pose, VertexConsumer buffer, AzuriumPylonTile pylon, PylonBundle bundle, Map<BlockPos, AzuriumPylonTile> byPos,
                                 Vec3 camera, float partialTicks)
    {
        var pos = pylon.getBlockPos();
        for (var flow : pylon.arcs())
        {
            if (flow.pos().asLong() <= pos.asLong())
                continue;
            var other = byPos.get(flow.pos());
            if (other == null || other.bundle() == PylonBundle.NONE)
                continue;
            var arc = ARCS.get(arcKey(pos, flow.pos()));
            if (arc == null || arc.joints() == 0)
                continue;

            var from = relative(pylon.tip(), camera);
            var to = relative(other.tip(), camera);
            float load = arcLoad(flow.rate(), bundle, other.bundle());
            if (load <= 0)
            {
                ribbon(pose, buffer, from, to, IDLE_HALF_WIDTH, ARGB.color(IDLE_ALPHA, AZURIUM_LIGHT));
                continue;
            }

            var points = jagged(from, to, arc);
            float widen = load >= PylonArc.AT_CAP ? CAP_WIDEN : 1;
            for (int i = 0; i + 1 < points.length; i++)
            {
                ribbon(pose, buffer, points[i], points[i + 1], HALO_HALF_WIDTH * widen, ARGB.color(HALO_ALPHA, AZURIUM_MID));
                ribbon(pose, buffer, points[i], points[i + 1], CORE_HALF_WIDTH * widen, ARGB.color(CORE_ALPHA, AZURIUM_LIGHT));
            }

            // Leaps run from the giving pylon: this one when the flow toward the other is positive
            boolean forward = flow.rate() > 0;
            float length = 0;
            var lengths = new float[points.length];
            for (int i = 1; i < points.length; i++)
                lengths[i] = length += points[i].distance(points[i - 1]);
            for (var leap : arc.leaps)
            {
                float head = leap.progress(partialTicks) * (length + PylonArc.LEAP_LENGTH);
                float start = forward ? head - PylonArc.LEAP_LENGTH : length - head;
                stretch(pose, buffer, points, lengths, start, start + PylonArc.LEAP_LENGTH, length, widen);
            }

            for (var fork : arc.forks)
                drawFork(pose, buffer, points, fork);
        }
    }

    private static Vector3f[] jagged(Vector3f from, Vector3f to, PylonArc arc)
    {
        int segments = arc.joints();
        var direction = new Vector3f(to).sub(from);
        var side = perpendicular(direction);
        var up = new Vector3f(direction).cross(side).normalize();
        var points = new Vector3f[segments + 1];
        for (int i = 0; i <= segments; i++)
        {
            var point = new Vector3f(from).lerp(to, i / (float) segments);
            if (i > 0 && i < segments)
            {
                point.add(side.x * arc.offsets[i * 2], side.y * arc.offsets[i * 2], side.z * arc.offsets[i * 2]);
                point.add(up.x * arc.offsets[i * 2 + 1], up.y * arc.offsets[i * 2 + 1], up.z * arc.offsets[i * 2 + 1]);
            }
            points[i] = point;
        }
        return points;
    }

    /**
     * The brighter part of the polyline between two distances along it, brightest in its middle and fading to nothing at both ends, so it reads as light
     * running along the arc rather than a solid piece of it.
     */
    private static void stretch(PoseStack.Pose pose, VertexConsumer buffer, Vector3f[] points, float[] lengths, float start, float end, float length,
                                float widen)
    {
        float slice = (end - start) / LEAP_SLICES;
        for (int s = 0; s < LEAP_SLICES; s++)
        {
            float sliceStart = Math.max(0, start + s * slice);
            float sliceEnd = Math.min(length, start + (s + 1) * slice);
            if (sliceEnd <= sliceStart)
                continue;
            float strength = (float) Math.sin(Math.PI * (s + 0.5F) / LEAP_SLICES);
            for (int i = 0; i + 1 < points.length; i++)
            {
                float a = Math.max(sliceStart, lengths[i]);
                float b = Math.min(sliceEnd, lengths[i + 1]);
                if (b <= a)
                    continue;
                float span = lengths[i + 1] - lengths[i];
                var from = new Vector3f(points[i]).lerp(points[i + 1], (a - lengths[i]) / span);
                var to = new Vector3f(points[i]).lerp(points[i + 1], (b - lengths[i]) / span);
                ribbon(pose, buffer, from, to, HALO_HALF_WIDTH * widen * LEAP_WIDEN, ARGB.color((int) (LEAP_HALO_ALPHA * strength), AZURIUM_MID));
                ribbon(pose, buffer, from, to, CORE_HALF_WIDTH * widen * LEAP_WIDEN, ARGB.color((int) (LEAP_CORE_ALPHA * strength), AZURIUM_LIGHT));
            }
        }
    }

    private static void drawFork(PoseStack.Pose pose, VertexConsumer buffer, Vector3f[] points, PylonArc.Fork fork)
    {
        int joint = Math.min(fork.joint(), points.length - 2);
        var origin = points[joint];
        var along = new Vector3f(points[joint + 1]).sub(origin).normalize();
        var side = perpendicular(along);
        var up = new Vector3f(along).cross(side).normalize();
        var direction = new Vector3f(along).mul(fork.along())
                .add(new Vector3f(side).mul(fork.side()))
                .add(new Vector3f(up).mul(fork.up()))
                .normalize();
        var crossSide = perpendicular(direction);

        int joints = fork.jag().length / 2;
        var previous = origin;
        for (int i = 1; i <= joints; i++)
        {
            float t = i / (float) joints;
            var next = new Vector3f(origin).add(new Vector3f(direction).mul(fork.length() * t));
            if (i < joints)
                next.add(new Vector3f(crossSide).mul(fork.jag()[i * 2])).add(new Vector3f(up).mul(fork.jag()[i * 2 + 1]));
            ribbon(pose, buffer, previous, next, FORK_HALF_WIDTH, ARGB.color((int) (FORK_ALPHA * (1 - t * 0.6F)), AZURIUM_LIGHT));
            previous = next;
        }
    }

    /**
     * A thin thread from the bundle to each machine fed, with pulses running toward it, more often the more it draws.
     */
    private static void drawThreads(PoseStack.Pose pose, VertexConsumer buffer, AzuriumPylonTile pylon, PylonBundle bundle, float fade, Vec3 camera, float time)
    {
        var from = relative(pylon.bundleCenter(), camera);
        for (var fed : pylon.fed())
        {
            var to = relative(Vec3.atCenterOf(fed.pos()), camera);
            float length = from.distance(to);
            if (length < 0.1F)
                continue;
            ribbon(pose, buffer, from, to, THREAD_HALF_WIDTH, ARGB.color((int) (THREAD_ALPHA * fade), AZURIUM_LIGHT));

            float share = Math.min(1, fed.rate() / (float) Math.max(1, bundle.cap()));
            float spacing = PULSE_SPEED * (PULSE_TICKS_SLOW + (PULSE_TICKS_FAST - PULSE_TICKS_SLOW) * share);
            float phase = (time * PULSE_SPEED + (fed.pos().hashCode() & 0xFF)) % spacing;
            for (float head = phase; head < length + PULSE_LENGTH; head += spacing)
            {
                float start = Math.max(0, head - PULSE_LENGTH) / length;
                float end = Math.min(length, head) / length;
                if (end <= start)
                    continue;
                ribbon(pose, buffer, new Vector3f(from).lerp(to, start), new Vector3f(from).lerp(to, end), PULSE_HALF_WIDTH,
                        ARGB.color((int) (PULSE_ALPHA * fade), AZURIUM_LIGHT));
            }
        }
    }

    /**
     * Dashes lying on the ground where the field's edge meets it, drifting slowly around.
     */
    /**
     * A Deep field's violet thread from its bundle into each portal it reaches through, with pulses running into the portal.
     */
    private static void drawDeepThreads(PoseStack.Pose pose, VertexConsumer buffer, AzuriumPylonTile pylon, Vec3 camera, float time)
    {
        if (pylon.deepReaches().isEmpty())
            return;
        var from = relative(pylon.bundleCenter(), camera);
        for (var middle : pylon.deepReaches())
        {
            var to = relative(middle, camera);
            float length = from.distance(to);
            if (length < 0.1F)
                continue;
            ribbon(pose, buffer, from, to, THREAD_HALF_WIDTH, ARGB.color(DEEP_THREAD_ALPHA, DEEP_LIGHT));
            float phase = (time * PULSE_SPEED) % DEEP_PULSE_SPACING;
            for (float head = phase; head < length + PULSE_LENGTH; head += DEEP_PULSE_SPACING)
            {
                float start = Math.max(0, head - PULSE_LENGTH) / length;
                float end = Math.min(length, head) / length;
                if (end > start)
                    ribbon(pose, buffer, new Vector3f(from).lerp(to, start), new Vector3f(from).lerp(to, end), PULSE_HALF_WIDTH,
                            ARGB.color(DEEP_PULSE_ALPHA, DEEP_MID));
            }
        }
    }

    private static void drawRing(PoseStack.Pose pose, VertexConsumer buffer, BlockPos bundle, FieldState field, float fade, Vec3 camera, float time, int rgb)
    {
        var ring = field.ring;
        int count = ring.size();
        double cx = bundle.getX() + 0.5;
        double cz = bundle.getZ() + 0.5;
        float circumference = (float) (Math.PI * 2 * field.radius);
        float drift = time / RING_TURN_TICKS * circumference;
        int color = ARGB.color((int) (RING_ALPHA * fade), rgb);

        for (int i = 0; i < count; i++)
        {
            var a = ring.get(i);
            var b = ring.get((i + 1) % count);
            if (a == null || b == null)
                continue;
            float along = (i + 0.5F) / count * circumference + drift;
            if (along % (DASH_LENGTH + GAP_LENGTH) >= DASH_LENGTH)
                continue;

            var sideA = radial(a, cx, cz);
            var sideB = radial(b, cx, cz);
            var ra = relative(a, camera);
            var rb = relative(b, camera);
            buffer.addVertex(pose, ra.x - sideA.x, ra.y, ra.z - sideA.z).setColor(color);
            buffer.addVertex(pose, ra.x + sideA.x, ra.y, ra.z + sideA.z).setColor(color);
            buffer.addVertex(pose, rb.x + sideB.x, rb.y, rb.z + sideB.z).setColor(color);
            buffer.addVertex(pose, rb.x - sideB.x, rb.y, rb.z - sideB.z).setColor(color);
        }
    }

    private static Vector3f radial(Vec3 point, double cx, double cz)
    {
        var side = new Vector3f((float) (point.x - cx), 0, (float) (point.z - cz));
        return side.lengthSquared() < 1.0E-6F ? new Vector3f(RING_HALF_WIDTH, 0, 0) : side.normalize().mul(RING_HALF_WIDTH);
    }

    /**
     * Where the sphere of the field meets the ground, one point every quarter block around it; null where no ground is near.
     */
    private static List<@Nullable Vec3> buildRing(Level level, BlockPos bundle, int radius)
    {
        double cx = bundle.getX() + 0.5;
        double cy = bundle.getY() + 0.5;
        double cz = bundle.getZ() + 0.5;
        int steps = Math.max(16, (int) Math.ceil(Math.PI * 2 * radius / RING_STEP));
        var ring = new ArrayList<@Nullable Vec3>(steps);
        var cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < steps; i++)
        {
            double angle = Math.PI * 2 * i / steps;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double reach = radius;
            double ground = Double.NaN;
            for (int pass = 0; pass < 3; pass++)
            {
                ground = groundAt(level, cursor, cx + cos * reach, cz + sin * reach, cy, radius);
                if (Double.isNaN(ground))
                    break;
                double dy = ground - cy;
                reach = Math.sqrt(Math.max(0, (double) radius * radius - dy * dy));
            }
            ring.add(Double.isNaN(ground) ? null : new Vec3(cx + cos * reach, ground + RING_LIFT, cz + sin * reach));
        }
        return ring;
    }

    /**
     * The top of the ground under a column: down from the bundle's height when there is air, up out of a hill when there isn't.
     */
    private static double groundAt(Level level, BlockPos.MutableBlockPos cursor, double x, double z, double cy, int radius)
    {
        int startY = (int) Math.floor(cy);
        cursor.set(x, startY, z);
        if (!level.isLoaded(cursor))
            return Double.NaN;
        if (solid(level, cursor))
        {
            for (int up = 1; up <= radius; up++)
            {
                cursor.setY(startY + up);
                if (!solid(level, cursor))
                    return startY + up;
            }
            return Double.NaN;
        }
        for (int down = 1; down <= radius; down++)
        {
            cursor.setY(startY - down);
            if (solid(level, cursor))
                return cursor.getY() + level.getBlockState(cursor).getCollisionShape(level, cursor).max(Direction.Axis.Y);
        }
        return Double.NaN;
    }

    private static boolean solid(Level level, BlockPos pos)
    {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    ////////////
    // SHAPES //
    ////////////

    /**
     * Camera relative position, subtracted in double so it stays precise far from the world origin.
     */
    private static Vector3f relative(Vec3 position, Vec3 camera)
    {
        return new Vector3f((float) (position.x - camera.x), (float) (position.y - camera.y), (float) (position.z - camera.z));
    }

}
