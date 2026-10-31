package net.voxelindustry.voidheart.common.content.oculus;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Sent when a player presses one of the holographic controls floating above a table.
 */
public record OculusControlPayload(BlockPos pos, Action action) implements CustomPacketPayload
{
    public static final Type<OculusControlPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "oculus_control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OculusControlPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OculusControlPayload::pos,
            ByteBufCodecs.idMapper(index -> Action.values()[index], Action::ordinal), OculusControlPayload::action,
            OculusControlPayload::new);

    /**
     * Controls float a little above the table edge, so they are reached from slightly further than the block itself.
     */
    private static final double REACH_BONUS = 2;

    public enum Action
    {
        ZOOM_IN,
        ZOOM_OUT,
        CYCLE_FOLLOW,
        TOGGLE_LEY_LINES
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(OculusControlPayload payload, ServerPlayNetworking.Context context)
    {
        var player = context.player();
        var level = player.level();
        if (!player.isWithinBlockInteractionRange(payload.pos(), REACH_BONUS) || OculusTableStructure.getTile(level, payload.pos()) == null)
            return;

        switch (payload.action())
        {
            case ZOOM_IN -> OculusTableStructure.updateView(level, payload.pos(), OculusView::zoomIn);
            case ZOOM_OUT -> OculusTableStructure.updateView(level, payload.pos(), OculusView::zoomOut);
            case TOGGLE_LEY_LINES -> OculusTableStructure.updateView(level, payload.pos(), OculusView::toggleLeyLines);
            case CYCLE_FOLLOW -> OculusTableStructure.footprint(level, payload.pos()).ifPresent(footprint ->
            {
                var candidates = followCandidates(level, footprint).stream().map(ServerPlayer::getUUID).toList();
                OculusTableStructure.updateView(level, payload.pos(), view -> view.following(nextTarget(candidates, view.follow())));
            });
        }
    }

    /**
     * Players within range of the table, in a stable order so repeated presses walk through all of them.
     */
    public static List<ServerPlayer> followCandidates(ServerLevel level, OculusFootprint footprint)
    {
        double range = VoidHeartConfig.get().oculusTableFollowRange;
        return level.players().stream()
                .filter(candidate -> !candidate.isSpectator())
                .filter(candidate -> horizontalDistanceSq(candidate, footprint) <= range * range)
                .sorted(Comparator.comparing(candidate -> candidate.getGameProfile().name(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public static double horizontalDistanceSq(ServerPlayer player, OculusFootprint footprint)
    {
        double dx = player.getX() - footprint.centerX();
        double dz = player.getZ() - footprint.centerZ();
        return dx * dx + dz * dz;
    }

    /**
     * The candidate after the current target, wrapping back to following no one after the last.
     */
    static Optional<UUID> nextTarget(List<UUID> candidates, Optional<UUID> current)
    {
        int index = current.map(candidates::indexOf).orElse(-1);
        return index + 1 < candidates.size() ? Optional.of(candidates.get(index + 1)) : Optional.empty();
    }
}
