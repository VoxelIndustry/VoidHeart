package net.voxelindustry.voidheart.common.ley.anchor;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.util.TeleportUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Moves a player from an active anchor to an active anchor linked to it, as picked on the travel map.
 */
public class AnchorTravel
{
    private static final int COOLDOWN_TICKS = 40;
    private static final int REACH = OldAnchors.NEAR_RADIUS + 2;

    private static final Map<UUID, Integer> lastTravelTick = new HashMap<>();

    public static void handle(AnchorPayloads.TravelRequest request, ServerPlayNetworking.Context context)
    {
        var player = context.player();
        int tick = player.level().getServer().getTickCount();
        var last = lastTravelTick.get(player.getUUID());
        if (last != null && tick - last < COOLDOWN_TICKS)
            return;

        OldAnchors.get(player.level()).ifPresent(anchors ->
        {
            var network = anchors.network();
            var source = network.anchor(request.source());
            var destination = network.anchor(request.destination());
            if (source.isEmpty() || destination.isEmpty() || !anchors.isActive(source.get()))
                return;

            var sourcePos = new BlockPos(source.get().x(), anchors.lowerY(source.get()), source.get().z());
            if (player.position().distanceToSqr(Vec3.atCenterOf(sourcePos)) > REACH * REACH)
                return;

            boolean linked = network.linked(source.get()).stream().anyMatch(anchor -> anchor.nodeId() == request.destination());
            if (!linked)
                return;
            if (!anchors.isActive(destination.get()))
            {
                player.sendOverlayMessage(Component.translatable("voidheart.anchor.dormant", destination.get().name()));
                return;
            }

            travel(player, anchors, sourcePos, destination.get());
            lastTravelTick.put(player.getUUID(), tick);
        });
    }

    private static void travel(ServerPlayer player, OldAnchors anchors, BlockPos sourcePos, OldAnchor destination)
    {
        var level = anchors.level();

        // Generates the destination if nobody went there yet, which also builds its anchor
        level.getChunk(destination.x() >> 4, destination.z() >> 4);
        anchors.placeIfMissing(destination);

        var destinationPos = new BlockPos(destination.x(), anchors.lowerY(destination), destination.z());
        var arrival = arrival(level, destinationPos);

        effects(level, sourcePos);
        double dx = destinationPos.getX() + 0.5 - arrival.x;
        double dz = destinationPos.getZ() + 0.5 - arrival.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        TeleportUtil.teleport(player, level, arrival, yaw, 0);
        effects(level, destinationPos);

        player.sendOverlayMessage(Component.translatable("voidheart.anchor.arrived", destination.name()));
    }

    /**
     * A free two block high spot beside the anchor, on top of it as a last resort.
     */
    private static Vec3 arrival(ServerLevel level, BlockPos anchorPos)
    {
        for (var direction : Direction.Plane.HORIZONTAL)
        {
            for (int dy : new int[] { 0, 1, -1 })
            {
                var feet = anchorPos.relative(direction, 2).above(dy);
                if (isFree(level, feet) && isFree(level, feet.above()) && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP))
                    return Vec3.atBottomCenterOf(feet);
            }
        }
        return Vec3.atBottomCenterOf(anchorPos.above(2));
    }

    private static boolean isFree(ServerLevel level, BlockPos pos)
    {
        var state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static void effects(ServerLevel level, BlockPos anchorPos)
    {
        var center = Vec3.atCenterOf(anchorPos.above());
        level.playSound(null, anchorPos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 0.8F);
        level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 80, 0.6, 1, 0.6, 0.4);
    }

    public static void clear()
    {
        lastTravelTick.clear();
    }
}
