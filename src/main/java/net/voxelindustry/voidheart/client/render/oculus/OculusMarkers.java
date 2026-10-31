package net.voxelindustry.voidheart.client.render.oculus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.oculus.OculusEntityPayloads;
import net.voxelindustry.voidheart.common.content.oculus.OculusEntityPayloads.Kind;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Glowing pins for the players and mobs on the land a table shows. Entities this client tracks are drawn where it
 * sees them, the others where the server last said they were.
 */
public final class OculusMarkers
{
    /**
     * Past this distance from the viewer, parts of the land shown may hold mobs this client does not track.
     */
    private static final double TRACKED_DISTANCE = 96;
    private static final int REQUEST_INTERVAL = 10;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float NAME_SCALE = 1 / 220F;
    private static final double GAZE_REACH = 8;
    /**
     * How far from a pin, in pin sizes, the view ray may pass and still count as looking at it.
     */
    private static final double GAZE_TOLERANCE = 1.8;

    private static final float PLAYER_SIZE = 0.07F;
    private static final float MOB_SIZE = 0.045F;
    private static final int PLAYER_COLOR = 0xEFE6FF;
    private static final int FOLLOWED_COLOR = 0xC77DFF;
    private static final int HOSTILE_COLOR = 0xFF4D4D;
    private static final int PASSIVE_COLOR = 0x7CFF7A;

    private record Pin(Kind kind, Vec3 position, Optional<UUID> player)
    {
    }

    /**
     * What the server said about a table's land, the previous answer kept to glide between the two.
     */
    private record ServerMarkers(Map<Integer, OculusEntityPayloads.Marker> current, Map<Integer, OculusEntityPayloads.Marker> previous, long receivedTick)
    {
    }

    private static final Map<BlockPos, ServerMarkers> FROM_SERVER = new HashMap<>();

    private static @Nullable UUID gazedPlayer;

    private OculusMarkers()
    {
    }

    public static void tick(ClientLevel level, long tick)
    {
        var player = Minecraft.getInstance().player;
        if (player == null || tick % REQUEST_INTERVAL != 0)
            return;

        for (var projector : OculusRenderer.projectors())
        {
            var area = projector.shownArea();
            double farX = Math.max(Math.abs(area.minX - player.getX()), Math.abs(area.maxX - player.getX()));
            double farZ = Math.max(Math.abs(area.minZ - player.getZ()), Math.abs(area.maxZ - player.getZ()));
            if (Math.hypot(farX, farZ) > TRACKED_DISTANCE)
                ClientPlayNetworking.send(new OculusEntityPayloads.Request(projector.anchor, projector.windowBottom(), projector.scale()));
            else
                FROM_SERVER.remove(projector.anchor);
        }
        FROM_SERVER.keySet().removeIf(anchor -> OculusRenderer.projectors().stream().noneMatch(projector -> projector.anchor.equals(anchor)));
    }

    public static void receive(OculusEntityPayloads.Markers payload, ClientPlayNetworking.Context context)
    {
        var current = new HashMap<Integer, OculusEntityPayloads.Marker>();
        payload.markers().forEach(marker -> current.put(marker.entityId(), marker));
        var previous = FROM_SERVER.get(payload.table());
        FROM_SERVER.put(payload.table(), new ServerMarkers(current, previous == null ? current : previous.current(), OculusRenderer.ticks()));
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || OculusRenderer.projectors().isEmpty())
            return;

        var cameraState = context.levelState().cameraRenderState;
        float partialTick = cameraState.cameraEntityPartialTicks;
        var viewer = minecraft.player;
        Pin gazed = null;
        double gazedDistance = Double.MAX_VALUE;
        for (var projector : OculusRenderer.projectors())
        {
            var pins = pins(level, projector, partialTick);
            if (pins.isEmpty())
                continue;

            if (viewer != null)
            {
                var eye = viewer.getEyePosition(partialTick);
                var look = viewer.getViewVector(partialTick);
                for (var pin : pins)
                {
                    double along = gazeDistance(pin, eye, look);
                    if (pin.player().isPresent() && along < gazedDistance)
                    {
                        gazed = pin;
                        gazedDistance = along;
                    }
                }
            }

            var followed = projector.view().follow().orElse(null);
            var right = new Vector3f(1, 0, 0).rotate(cameraState.orientation);
            var up = new Vector3f(0, 1, 0).rotate(cameraState.orientation);
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.MARKER, (pose, buffer) ->
            {
                for (var pin : pins)
                    drawPin(pose, buffer, pin, cameraState.pos, right, up, followed, projector.hologramTop());
            });
        }

        gazedPlayer = gazed == null ? null : gazed.player().orElse(null);
        if (gazed != null)
            drawName(context.poseStack(), context, cameraState, gazed);
    }

    /**
     * The player whose pin was looked at last frame, its name shown above it.
     */
    public static Optional<UUID> gazedPlayer()
    {
        return Optional.ofNullable(gazedPlayer);
    }

    /**
     * How far along the view ray a pin is, if the ray passes close enough to it to count as looking at it.
     *
     * @return the distance, or {@link Double#MAX_VALUE} when the pin is not looked at
     */
    private static double gazeDistance(Pin pin, Vec3 eye, Vec3 look)
    {
        var center = pin.position().add(0, PLAYER_SIZE, 0);
        var toPin = center.subtract(eye);
        double along = toPin.dot(look);
        if (along <= 0 || along > GAZE_REACH)
            return Double.MAX_VALUE;
        double off = toPin.subtract(look.scale(along)).length();
        return off <= PLAYER_SIZE * GAZE_TOLERANCE ? along : Double.MAX_VALUE;
    }

    private static List<Pin> pins(ClientLevel level, OculusProjector projector, float partialTick)
    {
        var pins = new ArrayList<Pin>();
        var seen = new HashSet<Integer>();
        for (var entity : level.getEntitiesOfClass(LivingEntity.class, projector.shownArea(), OculusEntityPayloads::isShown))
        {
            seen.add(entity.getId());
            var position = entity.getPosition(partialTick);
            var holo = projector.toHologram(position.x, position.y, position.z);
            if (holo != null && pins.size() < OculusEntityPayloads.MAX_MARKERS)
            {
                var kind = OculusEntityPayloads.kindOf(entity);
                pins.add(new Pin(kind, holo, kind == Kind.PLAYER ? Optional.of(entity.getUUID()) : Optional.empty()));
            }
        }

        var fromServer = FROM_SERVER.get(projector.anchor);
        if (fromServer == null)
            return pins;

        float glide = Mth.clamp((OculusRenderer.ticks() - fromServer.receivedTick() + partialTick) / REQUEST_INTERVAL, 0, 1);
        for (var marker : fromServer.current().values())
        {
            if (seen.contains(marker.entityId()) || pins.size() >= OculusEntityPayloads.MAX_MARKERS)
                continue;
            var before = fromServer.previous().getOrDefault(marker.entityId(), marker);
            var holo = projector.toHologram(Mth.lerp(glide, before.x(), marker.x()), Mth.lerp(glide, before.y(), marker.y()), Mth.lerp(glide, before.z(), marker.z()));
            if (holo != null)
                pins.add(new Pin(marker.kind(), holo, marker.player()));
        }
        return pins;
    }

    /**
     * A diamond facing the camera from any angle, its dark halo keeping it readable over any land.
     */
    private static void drawPin(PoseStack.Pose pose, VertexConsumer buffer, Pin pin, Vec3 camera, Vector3f right, Vector3f up, @Nullable UUID followed, double top)
    {
        boolean isFollowed = followed != null && pin.player().map(followed::equals).orElse(false);
        int rgb = switch (pin.kind())
        {
            case PLAYER -> isFollowed ? FOLLOWED_COLOR : PLAYER_COLOR;
            case HOSTILE -> HOSTILE_COLOR;
            case PASSIVE -> PASSIVE_COLOR;
        };
        float size = pin.kind() == Kind.PLAYER ? PLAYER_SIZE : MOB_SIZE;
        var center = pin.position().add(0, size, 0).subtract(camera).toVector3f();

        // Players get a faint beam up to the hologram top, so they can be found behind hills
        if (pin.kind() == Kind.PLAYER)
        {
            var across = new Vector3f(-center.z, 0, center.x);
            if (across.lengthSquared() < 1.0E-6F)
                across.set(1, 0, 0);
            across.normalize(size * 0.12F);
            float beamTop = (float) (top - camera.y);
            int color = ARGB.color(90, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
            buffer.addVertex(pose, center.x - across.x, center.y, center.z - across.z).setColor(color);
            buffer.addVertex(pose, center.x + across.x, center.y, center.z + across.z).setColor(color);
            buffer.addVertex(pose, center.x + across.x, beamTop, center.z + across.z).setColor(color);
            buffer.addVertex(pose, center.x - across.x, beamTop, center.z - across.z).setColor(color);
        }

        diamond(pose, buffer, center, right, up, size * 1.6F, ARGB.color(150, 10, 18, 20));
        diamond(pose, buffer, center, right, up, size, ARGB.color(255, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb)));
    }

    private static void diamond(PoseStack.Pose pose, VertexConsumer buffer, Vector3f center, Vector3f right, Vector3f up, float size, int color)
    {
        float width = size * 0.7F;
        buffer.addVertex(pose, center.x - up.x * size, center.y - up.y * size, center.z - up.z * size).setColor(color);
        buffer.addVertex(pose, center.x + right.x * width, center.y + right.y * width, center.z + right.z * width).setColor(color);
        buffer.addVertex(pose, center.x + up.x * size, center.y + up.y * size, center.z + up.z * size).setColor(color);
        buffer.addVertex(pose, center.x - right.x * width, center.y - right.y * width, center.z - right.z * width).setColor(color);
    }

    /**
     * The name of a gazed player, just above their pin. Text hangs down from its anchor, so the anchor sits a line higher.
     */
    private static void drawName(PoseStack poseStack, LevelRenderContext context, CameraRenderState camera, Pin pin)
    {
        var connection = Minecraft.getInstance().getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(pin.player().get());
        if (info == null)
            return;

        var font = Minecraft.getInstance().font;
        var name = Component.literal(info.getProfile().name()).getVisualOrderText();
        var offset = pin.position().add(0, PLAYER_SIZE * 2.3 + font.lineHeight * NAME_SCALE, 0).subtract(camera.pos);

        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);
        poseStack.rotate(camera.orientation);
        poseStack.rotate(MathUtil.quatFromAngleDegrees(180, MathUtil.POSITIVE_Y));
        poseStack.scale(NAME_SCALE, -NAME_SCALE, NAME_SCALE);
        context.submitNodeCollector().submitText(poseStack, -font.width(name) / 2F, 0, name, false, Font.DisplayMode.NORMAL, FULL_BRIGHT, 0xFFF2FFFE, 0x40000000, 0);
        poseStack.popPose();
    }

    public static void clear()
    {
        FROM_SERVER.clear();
    }
}
