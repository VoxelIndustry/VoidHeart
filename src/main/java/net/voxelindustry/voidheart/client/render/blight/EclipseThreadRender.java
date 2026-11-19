package net.voxelindustry.voidheart.client.render.blight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.content.blight.EclipseLink;
import net.voxelindustry.voidheart.common.content.blight.EclipseLinks;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The crossbow blight's links: taut black threads between the mobs one wielder has stitched together, each held to the one struck before it. A thread
 * shoots out from the newly struck mob, sways a little, carries a pale glint away from whichever mob shared its damage, and snaps in its middle when the
 * link lets it go, its halves whipping back.
 * The threads are blended over the scene without writing depth and hidden behind walls; the glint is added light on top. Times are counted from
 * when this client saw each change, since its clock can trail the server's.
 */
public final class EclipseThreadRender
{
    private static final RenderType THREAD = VoidHeartRenderTypes.tint(Identifier.fromNamespaceAndPath(MODID, "textures/entity/blight/thread.png"));
    private static final RenderType GLINT  = VoidHeartRenderTypes.radiant(Identifier.fromNamespaceAndPath(MODID, "textures/entity/blight/thread_glint.png"));

    /** One pixel of the world. */
    private static final float WIDTH        = 1 / 16F;
    private static final int   SEGMENTS     = 10;
    /** How far a thread's middle sways off its line, and how fast. */
    private static final float SWAY         = 0.06F;
    private static final float SWAY_RATE    = 0.07F;
    /** Where on its body a thread is tied, as a share of its height. */
    private static final float TIED_AT      = 0.6F;
    private static final float FORM_TICKS   = 3;
    private static final float GLINT_TICKS  = 6;
    private static final float GLINT_LENGTH = 0.8F;
    private static final float GLINT_WIDTH  = 4 / 16F;
    private static final int   GLINT_COLOR  = 0xD9C8FF;
    private static final float SNAP_TICKS   = 5;
    /** How far a snapped half whips off its line as it goes back. */
    private static final float SNAP_WHIP    = 0.5F;

    /** A thread between two members of one wielder's link, the older first. */
    private record Pair(int wielder, int older, int newer)
    {
    }

    /** A thread let go of, where it last was and when this client saw it go. */
    private record Snap(Vec3 from, Vec3 to, double at)
    {
    }

    private static final Map<Pair, Double>          SEEN       = new HashMap<>();
    /** When each share was seen, by its wielder and its own server time, since several may come in one sync. */
    private static final Map<String, Double>        SHARE_SEEN = new HashMap<>();
    private static final Map<Pair, Vec3[]>          LAST_ENDS  = new HashMap<>();
    private static final List<Snap>                 SNAPS      = new ArrayList<>();

    private EclipseThreadRender()
    {
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null)
            return;
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = FoldLook.now(level, partial);
        var camera = context.levelState().cameraRenderState.pos;
        var orientation = context.levelState().cameraRenderState.orientation;
        var facing = orientation.transform(new Vector3f(0, 0, -1));

        var threads = new ArrayList<Thread>();
        var glints = new ArrayList<Glint>();
        var present = new HashSet<Pair>();
        var shareKeys = new HashSet<String>();
        for (var player : level.players())
            collect(level, player, partial, now, present, shareKeys, threads, glints);

        // Threads seen last frame but gone now snap where they were
        for (var gone : new HashSet<>(SEEN.keySet()))
        {
            if (present.contains(gone))
                continue;
            var ends = LAST_ENDS.get(gone);
            if (ends != null)
                SNAPS.add(new Snap(ends[0], ends[1], now));
            SEEN.remove(gone);
            LAST_ENDS.remove(gone);
        }
        SNAPS.removeIf(snap -> now - snap.at() > SNAP_TICKS || now < snap.at());
        // A share is forgotten only once it has left every link, or it would run along its threads again
        SHARE_SEEN.keySet().retainAll(shareKeys);
        if (threads.isEmpty() && SNAPS.isEmpty())
            return;

        var poseStack = context.poseStack();
        var collector = context.submitNodeCollector();
        var snaps = List.copyOf(SNAPS);
        collector.submitCustomGeometry(poseStack, THREAD, (pose, buffer) ->
        {
            for (var thread : threads)
                thread(pose, buffer, thread, camera, facing, now);
            for (var snap : snaps)
                snap(pose, buffer, snap, camera, facing, now);
        });
        if (!glints.isEmpty())
            collector.submitCustomGeometry(poseStack, GLINT, (pose, buffer) ->
            {
                for (var glint : glints)
                    glint(pose, buffer, glint, camera, facing);
            });
    }

    /** A thread to draw, from its older end to its newer, seen first at {@code since}. */
    private record Thread(Vec3 older, Vec3 newer, double since, float phase)
    {
    }

    /** A glint at a share of the way along a thread, from its {@code from} end. */
    private record Glint(Vec3 from, Vec3 to, float along)
    {
    }

    private static void collect(ClientLevel level, Player player, float partial, double now, Set<Pair> present, Set<String> shareKeys, List<Thread> threads,
                                List<Glint> glints)
    {
        var link = player.getAttached(EclipseLinks.LINK);
        if (link == null)
            return;
        // Its shares are seen, and kept seen, even while it holds one mob alone. One first seen then has nothing left to run along, so it is spent
        // at once rather than running down a thread that forms after it
        boolean alone = link.members().size() < 2;
        for (var share : link.shares())
        {
            var key = shareKey(player, share);
            shareKeys.add(key);
            SHARE_SEEN.putIfAbsent(key, alone ? Double.NEGATIVE_INFINITY : now);
        }
        if (link.members().size() < 2)
            return;
        var members = link.members();
        var ends = new ArrayList<Vec3>();
        for (int id : members)
            ends.add(level.getEntity(id) instanceof LivingEntity living ? tiedAt(living, partial) : null);

        for (int i = 0; i + 1 < members.size(); i++)
        {
            var older = ends.get(i);
            var newer = ends.get(i + 1);
            if (older == null || newer == null)
                continue;
            var pair = new Pair(player.getId(), members.get(i), members.get(i + 1));
            present.add(pair);
            double since = SEEN.computeIfAbsent(pair, key -> now);
            LAST_ENDS.put(pair, new Vec3[]{ older, newer });
            threads.add(new Thread(older, newer, since, (pair.hashCode() & 0xFF) / 40F));
        }

        // Each share runs out from the mob that took the damage, thread by thread down the link
        for (var share : link.shares())
        {
            double seen = SHARE_SEEN.computeIfAbsent(shareKey(player, share), k -> now);
            int from = members.indexOf(share.from());
            if (from < 0)
                continue;
            for (int i = 0; i + 1 < members.size(); i++)
            {
                if (ends.get(i) == null || ends.get(i + 1) == null)
                    continue;
                boolean outward = i >= from;
                int hops = outward ? i - from : from - i - 1;
                float t = (float) (now - seen - hops * GLINT_TICKS) / GLINT_TICKS;
                if (t < 0 || t > 1)
                    continue;
                glints.add(outward ? new Glint(ends.get(i), ends.get(i + 1), t) : new Glint(ends.get(i + 1), ends.get(i), t));
            }
        }
    }

    private static String shareKey(Player player, EclipseLink.Share share)
    {
        return player.getId() + ":" + share.from() + ":" + share.at();
    }

    private static Vec3 tiedAt(LivingEntity living, float partial)
    {
        return living.getPosition(partial).add(0, living.getBbHeight() * TIED_AT, 0);
    }

    private static void thread(PoseStack.Pose pose, VertexConsumer buffer, Thread thread, Vec3 camera, Vector3f facing, double now)
    {
        // It shoots out from the newly struck mob toward the one before it
        float formed = Mth.clamp((float) (now - thread.since()) / FORM_TICKS, 0, 1);
        var from = thread.newer();
        var to = from.lerp(thread.older(), formed);
        float sway = SWAY * Mth.sin((float) now * SWAY_RATE + thread.phase());
        strand(pose, buffer, from, to, sway, 1, camera, facing, 255);
    }

    private static void snap(PoseStack.Pose pose, VertexConsumer buffer, Snap snap, Vec3 camera, Vector3f facing, double now)
    {
        float t = Mth.clamp((float) (now - snap.at()) / SNAP_TICKS, 0, 1);
        var middle = snap.from().lerp(snap.to(), 0.5);
        int alpha = (int) (255 * (1 - t));
        // Each half shortens back toward its own end, whipping out as it goes
        strand(pose, buffer, snap.from(), snap.from().lerp(middle, 1 - t), SNAP_WHIP * t, 0.5F, camera, facing, alpha);
        strand(pose, buffer, snap.to(), snap.to().lerp(middle, 1 - t), -SNAP_WHIP * t, 0.5F, camera, facing, alpha);
    }

    /**
     * A thin line from one point to another bowed sideways by {@code bow} at its middle, or at its far end for half a thread, facing the camera.
     *
     * @param span the share of a whole thread it is, so a half bows from its tied end
     */
    private static void strand(PoseStack.Pose pose, VertexConsumer buffer, Vec3 from, Vec3 to, float bow, float span, Vec3 camera, Vector3f facing, int alpha)
    {
        var line = to.subtract(from);
        if (line.lengthSqr() < 1.0E-6)
            return;
        var along = new Vector3f((float) line.x, (float) line.y, (float) line.z).normalize();
        var side = new Vector3f(along).cross(facing);
        if (side.lengthSquared() < 1.0E-6F)
            return;
        side.normalize();
        var previous = from;
        for (int i = 1; i <= SEGMENTS; i++)
        {
            float s = i / (float) SEGMENTS;
            float shape = span >= 1 ? Mth.sin(s * Mth.PI) : s;
            var point = from.add(line.scale(s)).add(side.x * bow * shape, side.y * bow * shape, side.z * bow * shape);
            ribbon(pose, buffer, previous.subtract(camera), point.subtract(camera), facing, WIDTH, ARGB.color(alpha, 0xFFFFFF));
            previous = point;
        }
    }

    private static void glint(PoseStack.Pose pose, VertexConsumer buffer, Glint glint, Vec3 camera, Vector3f facing)
    {
        var line = glint.to().subtract(glint.from());
        double length = line.length();
        if (length < 1.0E-3)
            return;
        var direction = line.scale(1 / length);
        double half = Math.min(GLINT_LENGTH, length) / 2;
        var middle = glint.from().add(line.scale(glint.along()));
        int alpha = (int) (255 * Mth.sin(glint.along() * Mth.PI));
        ribbon(pose, buffer, middle.subtract(direction.scale(half)).subtract(camera), middle.add(direction.scale(half)).subtract(camera), facing, GLINT_WIDTH,
                ARGB.color(alpha, GLINT_COLOR));
    }

    /** A flat strip from one point to another, as wide as given, turned to face the camera about its own line. */
    private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vec3 from, Vec3 to, Vector3f facing, float width, int color)
    {
        var line = new Vector3f((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
        var across = new Vector3f(line).cross(facing);
        if (across.lengthSquared() < 1.0E-9F)
            return;
        across.normalize().mul(width / 2);
        float x0 = (float) from.x, y0 = (float) from.y, z0 = (float) from.z;
        float x1 = (float) to.x, y1 = (float) to.y, z1 = (float) to.z;
        vertex(pose, buffer, x0 - across.x, y0 - across.y, z0 - across.z, 0, 1, color);
        vertex(pose, buffer, x1 - across.x, y1 - across.y, z1 - across.z, 1, 1, color);
        vertex(pose, buffer, x1 + across.x, y1 + across.y, z1 + across.z, 1, 0, color);
        vertex(pose, buffer, x0 + across.x, y0 + across.y, z0 + across.z, 0, 0, color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int color)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }
}
