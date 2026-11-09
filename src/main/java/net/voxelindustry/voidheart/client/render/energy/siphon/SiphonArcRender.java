package net.voxelindustry.voidheart.client.render.energy.siphon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryTile;
import net.voxelindustry.voidheart.common.energy.grid.AzuriumPylonTile;
import net.voxelindustry.voidheart.common.energy.grid.PylonBundle;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static net.voxelindustry.voidheart.client.render.energy.siphon.SiphonGeometry.perpendicular;
import static net.voxelindustry.voidheart.client.render.energy.siphon.SiphonGeometry.ribbon;

/**
 * At an armillary in the overworld: whenever a deep siphon delivers through it, small azurium leaping arcs run from its rings to the pylons whose fields
 * cover it, in the pylon arc's style, with the leaps running outward. They fade in and out with the flow.
 * <p>
 * Render type: additive {@code GLOW}, no depth writes, so the arcs and their halos add up in any order. Per armillary and frame: about 12 segments of core
 * and halo per arc and a few leap slices, under 100 quads for a base with 3 pylons, rebuilt each frame from the arcs' tick state.
 */
public final class SiphonArcRender
{
    private static final int AZURIUM_LIGHT = 0xCCFFF2;
    private static final int AZURIUM_MID   = 0x3D7ED9;

    // The middle of the rings, as ArmillaryRender draws them, and where arcs leave: by the middle ring, so they come out of the turning rings at any angle
    private static final double RINGS_Y      = 2.5;
    private static final double START_RADIUS = 1.1;

    private static final double DRAW_DISTANCE = 128;

    // Smaller than the pylon arcs, which this follows in style
    private static final float CORE_HALF       = 0.025F;
    private static final float HALO_HALF       = 0.06F;
    private static final int   CORE_ALPHA      = 190;
    private static final int   HALO_ALPHA      = 60;
    private static final float LEAP_WIDEN      = 1.5F;
    private static final int   LEAP_SLICES     = 6;
    private static final int   LEAP_CORE_ALPHA = 255;
    private static final int   LEAP_HALO_ALPHA = 140;

    private static final double SOUND_DISTANCE       = 16;
    private static final float  SOUND_VOLUME         = 0.04F;
    private static final int    SOUND_COOLDOWN_TICKS = 10;

    /** Armillaries loaded on the client, kept by the block entity load and unload events. */
    static final Set<ArmillaryTile> ARMILLARIES = ConcurrentHashMap.newKeySet();

    private static final Map<ArcKey, SiphonArc> ARCS            = new HashMap<>();
    private static final Map<ArcKey, Long>      SOUND_COOLDOWNS = new HashMap<>();

    private record ArcKey(BlockPos armillary, BlockPos pylon)
    {
    }

    private record Link(BlockPos armillary, BlockPos pylon, Vec3 from, Vec3 to)
    {
    }

    private SiphonArcRender()
    {
    }

    static void onLoad(BlockEntity tile, ClientLevel level)
    {
        if (tile instanceof ArmillaryTile armillary)
            ARMILLARIES.add(armillary);
    }

    static void onUnload(BlockEntity tile, ClientLevel level)
    {
        if (tile instanceof ArmillaryTile armillary)
            ARMILLARIES.remove(armillary);
    }

    private static ArcKey key(BlockPos armillary, BlockPos pylon)
    {
        return new ArcKey(armillary, pylon);
    }

    /**
     * Every arc an armillary could throw: one to each pylon whose field covers it.
     */
    private static List<Link> links(Level level)
    {
        var links = new ArrayList<Link>();
        ARMILLARIES.removeIf(armillary -> armillary.isRemoved() || armillary.getLevel() != level);
        for (var armillary : ARMILLARIES)
        {
            var pos = armillary.getBlockPos();
            var rings = new Vec3(pos.getX() + 0.5, pos.getY() + RINGS_Y, pos.getZ() + 0.5);
            for (var pylon : AzuriumPylonTile.CLIENT_PYLONS)
            {
                var bundle = pylon.bundle();
                if (pylon.isRemoved() || pylon.getLevel() != level || bundle == PylonBundle.NONE)
                    continue;
                var pylonPos = pylon.getBlockPos();
                if (pylonPos.above().distSqr(pos) > (double) bundle.radius() * bundle.radius())
                    continue;

                var tip = new Vec3(pylonPos.getX() + 0.5, pylonPos.getY() + 1 + bundle.height() / 16D, pylonPos.getZ() + 0.5);
                var flat = new Vec3(tip.x - rings.x, 0, tip.z - rings.z);
                var from = flat.lengthSqr() < 1.0E-4 ? rings.add(0, START_RADIUS, 0) : rings.add(flat.normalize().scale(START_RADIUS));
                links.add(new Link(pos, pylonPos, from, tip));
            }
        }
        return links;
    }

    public static void tick(Minecraft minecraft)
    {
        var level = minecraft.level;
        if (level == null || minecraft.player == null)
        {
            ARCS.clear();
            SOUND_COOLDOWNS.clear();
            ARMILLARIES.clear();
            return;
        }
        if (!level.tickRateManager().runsNormally())
            return;

        long time = level.getGameTime();
        var flowing = new HashMap<BlockPos, Boolean>();
        for (var armillary : ARMILLARIES)
            flowing.put(armillary.getBlockPos(), armillary.isSiphonFlowing());

        for (var link : links(level))
        {
            boolean flows = flowing.getOrDefault(link.armillary(), false);
            var key = key(link.armillary(), link.pylon());
            var arc = flows ? ARCS.computeIfAbsent(key, k -> new SiphonArc(k.hashCode())) : ARCS.get(key);
            if (arc == null)
                continue;
            arc.seenAt = time;
            if (arc.tick(link.from().distanceTo(link.to()), flows))
                crackle(minecraft, level, key, link.from().lerp(link.to(), 0.5), time);
        }
        ARCS.values().removeIf(arc -> arc.seenAt != time || arc.fade <= 0 && arc.previousFade <= 0);
        SOUND_COOLDOWNS.values().removeIf(until -> until < time);
    }

    private static void crackle(Minecraft minecraft, Level level, ArcKey key, Vec3 at, long time)
    {
        var player = minecraft.player;
        if (player == null || player.position().distanceToSqr(at) > SOUND_DISTANCE * SOUND_DISTANCE || SOUND_COOLDOWNS.getOrDefault(key, Long.MIN_VALUE) > time)
            return;
        SOUND_COOLDOWNS.put(key, time + SOUND_COOLDOWN_TICKS);
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, SoundSource.BLOCKS, SOUND_VOLUME, 1.7F + level.getRandom().nextFloat() * 0.3F, false);
    }

    /**
     * How many arcs an armillary at this position shows now, for tests.
     */
    public static int arcsAt(BlockPos armillary)
    {
        return (int) ARCS.entrySet().stream().filter(entry -> entry.getKey().armillary().equals(armillary) && entry.getValue().fade > 0).count();
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var level = Minecraft.getInstance().level;
        if (level == null || ARCS.isEmpty())
            return;

        var levelState = context.levelState();
        var camera = levelState.cameraRenderState.pos;
        float partialTicks = levelState.worldPartialTicks;

        var visible = new ArrayList<Link>();
        for (var link : links(level))
        {
            if (ARCS.containsKey(key(link.armillary(), link.pylon())) && link.from().distanceToSqr(camera) < DRAW_DISTANCE * DRAW_DISTANCE)
                visible.add(link);
        }
        if (visible.isEmpty())
            return;

        var eye = new Vector3f();
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            for (var link : visible)
            {
                var arc = ARCS.get(key(link.armillary(), link.pylon()));
                if (arc == null || arc.joints() == 0)
                    continue;
                float fade = arc.fade(partialTicks);
                if (fade <= 0)
                    continue;
                drawArc(pose, buffer, eye, arc, relative(link.from(), camera), relative(link.to(), camera), fade, partialTicks);
            }
        });
    }

    private static void drawArc(PoseStack.Pose pose, VertexConsumer buffer, Vector3f eye, SiphonArc arc, Vector3f from, Vector3f to, float fade, float partialTicks)
    {
        var points = jagged(from, to, arc);
        for (int i = 0; i + 1 < points.length; i++)
        {
            ribbon(pose, buffer, eye, points[i], points[i + 1], HALO_HALF, ARGB.color((int) (HALO_ALPHA * fade), AZURIUM_MID));
            ribbon(pose, buffer, eye, points[i], points[i + 1], CORE_HALF, ARGB.color((int) (CORE_ALPHA * fade), AZURIUM_LIGHT));
        }

        float length = 0;
        var lengths = new float[points.length];
        for (int i = 1; i < points.length; i++)
            lengths[i] = length += points[i].distance(points[i - 1]);
        // Leaps run outward, from the rings to the pylon
        for (var leap : arc.leaps)
        {
            float head = leap.progress(partialTicks) * (length + SiphonArc.LEAP_LENGTH);
            stretch(pose, buffer, eye, points, lengths, head - SiphonArc.LEAP_LENGTH, head, length, fade);
        }
    }

    private static Vector3f[] jagged(Vector3f from, Vector3f to, SiphonArc arc)
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
     * The brighter part of the polyline between two distances along it, brightest in its middle and fading to nothing at both ends.
     */
    private static void stretch(PoseStack.Pose pose, VertexConsumer buffer, Vector3f eye, Vector3f[] points, float[] lengths, float start, float end, float length,
                                float fade)
    {
        float slice = (end - start) / LEAP_SLICES;
        for (int s = 0; s < LEAP_SLICES; s++)
        {
            float sliceStart = Math.max(0, start + s * slice);
            float sliceEnd = Math.min(length, start + (s + 1) * slice);
            if (sliceEnd <= sliceStart)
                continue;
            float strength = (float) Math.sin(Math.PI * (s + 0.5F) / LEAP_SLICES) * fade;
            for (int i = 0; i + 1 < points.length; i++)
            {
                float a = Math.max(sliceStart, lengths[i]);
                float b = Math.min(sliceEnd, lengths[i + 1]);
                if (b <= a)
                    continue;
                float span = lengths[i + 1] - lengths[i];
                var from = new Vector3f(points[i]).lerp(points[i + 1], (a - lengths[i]) / span);
                var to = new Vector3f(points[i]).lerp(points[i + 1], (b - lengths[i]) / span);
                ribbon(pose, buffer, eye, from, to, HALO_HALF * LEAP_WIDEN, ARGB.color((int) (LEAP_HALO_ALPHA * strength), AZURIUM_MID));
                ribbon(pose, buffer, eye, from, to, CORE_HALF * LEAP_WIDEN, ARGB.color((int) (LEAP_CORE_ALPHA * strength), AZURIUM_LIGHT));
            }
        }
    }

    /**
     * Camera relative position, subtracted in double so it stays precise far from the world origin.
     */
    private static Vector3f relative(Vec3 position, Vec3 camera)
    {
        return new Vector3f((float) (position.x - camera.x), (float) (position.y - camera.y), (float) (position.z - camera.z));
    }
}
