package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile;
import net.voxelindustry.voidheart.common.content.essence.StorageParts;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Shows what a relay links to: thin lines from the hovered relay to every relay, collector and storage it reaches,
 * and, while a relay is held, from where it would be placed, along with what sits in range but out of its sight.
 * A hovered storage or matrix shows the relays that link to it the same way.
 */
public class RelayLinkPreviewRender
{
    private static final int RESCAN_TICKS = 5;

    private static final float LINE_HALF_WIDTH    = 0.012F;
    private static final float LINE_GLOW_WIDTH    = 0.04F;
    private static final float OUTLINE_HALF_WIDTH = 0.01F;
    private static final float OUTLINE_INFLATE    = 0.03F;
    private static final float END_INSET          = 0.2F;

    private static final int LINE_ALPHA    = 170;
    private static final int GLOW_ALPHA    = 45;
    private static final int OUTLINE_ALPHA = 120;
    private static final int BLOCKED_ALPHA = 90;

    private static final int RELAY_COLOR   = 0xE8F04A;
    private static final int BLOCKED_COLOR = 0xFF4646;

    // Dashes run from the relay toward what it links to
    private static final float DASH_LENGTH = 0.5F;
    private static final float DASH_SPEED  = 0.05F;

    private static @Nullable BlockPos               scannedOrigin;
    private static long                             scannedAt;
    private static RavenousRelayTile.@Nullable Links scanned;

    private static @Nullable BlockPos       reachedTarget;
    private static long                     reachedAt;
    private static @Nullable List<BlockPos> reached;

    public static void collectSubmits(LevelRenderContext context)
    {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return;

        var placing = player.getItemInHand(InteractionHand.MAIN_HAND).is(VoidHeartBlocks.RAVENOUS_RELAY.asItem())
                || player.getItemInHand(InteractionHand.OFF_HAND).is(VoidHeartBlocks.RAVENOUS_RELAY.asItem());
        BlockPos origin;
        if (level.getBlockState(hit.getBlockPos()).is(VoidHeartBlocks.RAVENOUS_RELAY))
        {
            origin = hit.getBlockPos();
            placing = false;
        }
        else if (placing)
        {
            origin = level.getBlockState(hit.getBlockPos()).canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
            if (!level.getBlockState(origin).canBeReplaced())
                return;
        }
        else
        {
            var storage = StorageParts.holderOf(level.getBlockState(hit.getBlockPos()), hit.getBlockPos());
            if (storage != null)
                storageLinks(context, level, storage);
            return;
        }

        var links = links(level, origin);
        var levelState = context.levelState();
        var camera = levelState.cameraRenderState.pos;
        var time = (levelState.gameTime % 24000L) + levelState.worldPartialTicks;
        var showBlocked = placing;

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            var from = Vec3.atCenterOf(origin);
            if (showBlocked)
                outline(pose, buffer, new AABB(origin).deflate(5 / 16D), camera, ARGB.color(OUTLINE_ALPHA, RELAY_COLOR));

            for (var pos : links.relays())
                link(pose, buffer, from, pos, camera, RELAY_COLOR, time);
            for (var pos : links.collectors())
                link(pose, buffer, from, pos, camera, aspectColor(level, pos), time);
            for (var pos : links.receivers())
                link(pose, buffer, from, pos, camera, aspectColor(level, pos), time);
        });
        if (showBlocked && !links.blocked().isEmpty())
        {
            // Blocked links run behind what blocks them, so they are drawn through it
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.GLOW_THROUGH, (pose, buffer) ->
            {
                var from = Vec3.atCenterOf(origin);
                for (var pos : links.blocked())
                    blocked(pose, buffer, from, pos, camera);
            });
        }
    }

    /**
     * Lines from each relay that links to the storage, running toward it as its essence would.
     */
    private static void storageLinks(LevelRenderContext context, Level level, BlockPos storage)
    {
        var relays = relaysReaching(level, storage);
        if (relays.isEmpty())
            return;

        var levelState = context.levelState();
        var camera = levelState.cameraRenderState.pos;
        var time = (levelState.gameTime % 24000L) + levelState.worldPartialTicks;
        var color = aspectColor(level, storage);
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            var to = Vec3.atCenterOf(storage);
            for (var relay : relays)
            {
                dashes(pose, buffer, Vec3.atCenterOf(relay), to, camera, color, time);
                outline(pose, buffer, bounds(relay).inflate(OUTLINE_INFLATE), camera, ARGB.color(OUTLINE_ALPHA, RELAY_COLOR));
            }
        });
    }

    private static List<BlockPos> relaysReaching(Level level, BlockPos storage)
    {
        var now = level.getGameTime();
        if (reached == null || !storage.equals(reachedTarget) || now - reachedAt >= RESCAN_TICKS || now < reachedAt)
        {
            reached = RavenousRelayTile.relaysReaching(level, storage);
            reachedTarget = storage.immutable();
            reachedAt = now;
        }
        return reached;
    }

    /**
     * Rescanned a few times a second, the line of sight checks being too many to run every frame.
     */
    private static RavenousRelayTile.Links links(Level level, BlockPos origin)
    {
        var now = level.getGameTime();
        if (scanned == null || !origin.equals(scannedOrigin) || now - scannedAt >= RESCAN_TICKS || now < scannedAt)
        {
            scanned = RavenousRelayTile.scanLinks(level, origin);
            scannedOrigin = origin.immutable();
            scannedAt = now;
        }
        return scanned;
    }

    private static int aspectColor(Level level, BlockPos pos)
    {
        EssenceType aspect = switch (level.getBlockEntity(pos))
        {
            case RavenousCollectorTile collector -> collector.storage().type();
            case RavenousStorageTile storage -> storage.storage().type();
            case null, default -> EssenceRegistry.EMPTY;
        };
        return VoidBeamRender.aspectColor(aspect);
    }

    private static void link(PoseStack.Pose pose, VertexConsumer buffer, Vec3 from, BlockPos target, Vec3 camera, int color, float time)
    {
        dashes(pose, buffer, from, Vec3.atCenterOf(target), camera, color, time);
        outline(pose, buffer, bounds(target).inflate(OUTLINE_INFLATE), camera, ARGB.color(OUTLINE_ALPHA, color));
    }

    private static void dashes(PoseStack.Pose pose, VertexConsumer buffer, Vec3 from, Vec3 to, Vec3 camera, int color, float time)
    {
        var direction = to.subtract(from).normalize();
        var start = from.add(direction.scale(END_INSET));
        var end = to.subtract(direction.scale(END_INSET));

        var length = (float) start.distanceTo(end);
        var dashes = Math.max(1, Mth.ceil(length / DASH_LENGTH));
        for (var i = 0; i < dashes; i++)
        {
            var a = start.lerp(end, i / (float) dashes);
            var b = start.lerp(end, (i + 1) / (float) dashes);
            var wave = 0.55F + 0.45F * Mth.sin((i * DASH_LENGTH - time * DASH_SPEED * 10) * Mth.TWO_PI / (DASH_LENGTH * 6));
            ribbon(pose, buffer, a, b, camera, LINE_GLOW_WIDTH, ARGB.color((int) (GLOW_ALPHA * wave), color));
            ribbon(pose, buffer, a, b, camera, LINE_HALF_WIDTH, ARGB.color((int) (LINE_ALPHA * wave), color));
        }
    }

    /**
     * A storage outlined with its pillars.
     */
    private static AABB bounds(BlockPos pos)
    {
        var level = Minecraft.getInstance().level;
        var state = level.getBlockState(pos);
        if (state.is(VoidHeartBlocks.RAVENOUS_STORAGE) && level.getBlockState(pos.above()).is(VoidHeartBlocks.RAVENOUS_STORAGE))
            return new AABB(pos).expandTowards(0, 1, 0);
        return new AABB(pos);
    }

    private static void blocked(PoseStack.Pose pose, VertexConsumer buffer, Vec3 from, BlockPos target, Vec3 camera)
    {
        var to = Vec3.atCenterOf(target);
        var direction = to.subtract(from).normalize();
        var start = from.add(direction.scale(END_INSET));
        var end = to.subtract(direction.scale(END_INSET));

        // Dotted, so it reads as a link that does not hold
        var length = (float) start.distanceTo(end);
        var dots = Math.max(1, Mth.floor(length / (DASH_LENGTH * 0.5F)));
        for (var i = 0; i < dots; i += 2)
            ribbon(pose, buffer, start.lerp(end, i / (float) dots), start.lerp(end, (i + 1) / (float) dots), camera, LINE_HALF_WIDTH,
                    ARGB.color(BLOCKED_ALPHA, BLOCKED_COLOR));
        outline(pose, buffer, bounds(target).inflate(OUTLINE_INFLATE), camera, ARGB.color(BLOCKED_ALPHA, BLOCKED_COLOR));
    }

    static void outline(PoseStack.Pose pose, VertexConsumer buffer, AABB box, Vec3 camera, int color)
    {
        outline(pose, buffer, box, camera, color, OUTLINE_HALF_WIDTH);
    }

    static void outline(PoseStack.Pose pose, VertexConsumer buffer, AABB box, Vec3 camera, int color, float halfWidth)
    {
        double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
        for (var y : ys)
        {
            for (var z : zs)
                ribbon(pose, buffer, new Vec3(xs[0], y, z), new Vec3(xs[1], y, z), camera, halfWidth, color);
        }
        for (var x : xs)
        {
            for (var z : zs)
                ribbon(pose, buffer, new Vec3(x, ys[0], z), new Vec3(x, ys[1], z), camera, halfWidth, color);
            for (var y : ys)
                ribbon(pose, buffer, new Vec3(x, y, zs[0]), new Vec3(x, y, zs[1]), camera, halfWidth, color);
        }
    }

    /**
     * Camera-facing quad between two world points, made camera relative in double so it holds far from the origin.
     */
    private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vec3 from, Vec3 to, Vec3 camera, float halfWidth, int color)
    {
        var start = new Vector3f((float) (from.x - camera.x), (float) (from.y - camera.y), (float) (from.z - camera.z));
        var end = new Vector3f((float) (to.x - camera.x), (float) (to.y - camera.y), (float) (to.z - camera.z));
        var direction = new Vector3f(end).sub(start);
        var toCamera = new Vector3f(start).add(end).mul(-0.5F);
        var side = direction.cross(toCamera);
        if (side.lengthSquared() < 1.0E-8F)
            return;
        side.normalize(halfWidth);

        buffer.addVertex(pose, start.x - side.x, start.y - side.y, start.z - side.z).setColor(color);
        buffer.addVertex(pose, start.x + side.x, start.y + side.y, start.z + side.z).setColor(color);
        buffer.addVertex(pose, end.x + side.x, end.y + side.y, end.z + side.z).setColor(color);
        buffer.addVertex(pose, end.x - side.x, end.y - side.y, end.z - side.z).setColor(color);
    }
}
