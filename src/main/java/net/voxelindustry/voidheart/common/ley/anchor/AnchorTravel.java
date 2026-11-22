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
 * Moves a player between anchors, as picked on the travel map. From an Old Anchor the trip is free and reaches the anchors linked to it,
 * wrought ones included. From a wrought anchor it first gathers void energy, then reaches its Old Anchors or any wrought anchor of the level.
 */
public class AnchorTravel
{
    private static final int COOLDOWN_TICKS = 40;
    static final int REACH = OldAnchors.NEAR_RADIUS + 2;

    private static final Map<UUID, Integer> lastTravelTick = new HashMap<>();

    public static void handle(AnchorPayloads.TravelRequest request, ServerPlayNetworking.Context context)
    {
        var player = context.player();
        if (coolingDown(player))
            return;

        OldAnchors.get(player.level()).ifPresent(anchors ->
        {
            if (WroughtAnchor.isWrought(request.source()))
                anchors.wrought().requestTravel(player, request.source(), request.destination());
            else
                fromOldAnchor(player, anchors, request);
        });
    }

    public static void handleRename(AnchorPayloads.RenameRequest request, ServerPlayNetworking.Context context)
    {
        var player = context.player();
        OldAnchors.get(player.level()).ifPresent(anchors -> anchors.wrought().rename(player, request.anchor(), request.name()));
    }

    private static boolean coolingDown(ServerPlayer player)
    {
        var last = lastTravelTick.get(player.getUUID());
        return last != null && player.level().getServer().getTickCount() - last < COOLDOWN_TICKS;
    }

    private static void fromOldAnchor(ServerPlayer player, OldAnchors anchors, AnchorPayloads.TravelRequest request)
    {
        var network = anchors.network();
        var source = network.anchor(request.source());
        if (source.isEmpty() || !anchors.isActive(source.get()))
            return;

        var sourcePos = new BlockPos(source.get().x(), anchors.lowerY(source.get()), source.get().z());
        if (player.position().distanceToSqr(Vec3.atCenterOf(sourcePos)) > REACH * REACH)
            return;

        if (WroughtAnchor.isWrought(request.destination()))
        {
            boolean linked = anchors.wrought().linkedTo(source.get()).stream().anyMatch(anchor -> anchor.id() == request.destination());
            if (linked)
                arrive(player, anchors, sourcePos, request.destination());
            return;
        }

        var destination = network.anchor(request.destination());
        boolean linked = network.linked(source.get()).stream().anyMatch(anchor -> anchor.nodeId() == request.destination());
        if (destination.isEmpty() || !linked)
            return;
        if (!anchors.isActive(destination.get()))
        {
            player.sendOverlayMessage(Component.translatable("voidheart.anchor.dormant", destination.get().name()));
            return;
        }

        arrive(player, anchors, sourcePos, request.destination());
    }

    /**
     * Sends the player from the anchor at sourcePos to the destination, generating it if nobody went there yet.
     *
     * @return whether the player left
     */
    static boolean arrive(ServerPlayer player, OldAnchors anchors, BlockPos sourcePos, long destinationId)
    {
        var level = anchors.level();
        BlockPos destinationPos;
        String destinationName;

        if (WroughtAnchor.isWrought(destinationId))
        {
            var destination = anchors.wrought().get(destinationId);
            if (destination.isEmpty() || !anchors.wrought().standing(destination.get()))
            {
                player.sendOverlayMessage(Component.translatable("voidheart.wrought_anchor.gone"));
                return false;
            }
            destinationPos = destination.get().pos();
            destinationName = destination.get().name();
        }
        else
        {
            var destination = anchors.network().anchor(destinationId);
            if (destination.isEmpty())
                return false;
            // Generates the destination if nobody went there yet, which also builds its anchor
            level.getChunk(destination.get().x() >> 4, destination.get().z() >> 4);
            anchors.placeIfMissing(destination.get());
            destinationPos = new BlockPos(destination.get().x(), anchors.lowerY(destination.get()), destination.get().z());
            destinationName = destination.get().name();
        }

        var arrival = arrival(level, destinationPos);
        effects(level, sourcePos);
        double dx = destinationPos.getX() + 0.5 - arrival.x;
        double dz = destinationPos.getZ() + 0.5 - arrival.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        TeleportUtil.teleport(player, level, arrival, yaw, 0);
        effects(level, destinationPos);

        player.sendOverlayMessage(Component.translatable("voidheart.anchor.arrived", destinationName));
        lastTravelTick.put(player.getUUID(), level.getServer().getTickCount());
        return true;
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
