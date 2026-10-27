package net.voxelindustry.voidheart.common.ley.anchor;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public final class AnchorPayloads
{
    private AnchorPayloads()
    {
    }

    public static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(Waypoints.TYPE, Waypoints.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TravelMap.TYPE, TravelMap.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TravelRequest.TYPE, TravelRequest.CODEC);
    }

    /**
     * The anchor the player stands by and the anchors linked to it. No source clears the waypoints.
     */
    public record Waypoints(Optional<AnchorView> source, List<AnchorView> linked) implements CustomPacketPayload
    {
        public static final Type<Waypoints> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "anchor_waypoints"));
        public static final Waypoints CLEAR = new Waypoints(Optional.empty(), List.of());

        public static final StreamCodec<RegistryFriendlyByteBuf, Waypoints> CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(AnchorView.CODEC), Waypoints::source,
                AnchorView.CODEC.apply(ByteBufCodecs.list()), Waypoints::linked,
                Waypoints::new);

        @Override
        public Type<Waypoints> type()
        {
            return TYPE;
        }
    }

    public record Segment(int fromX, int fromZ, int toX, int toZ)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, Segment> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Segment::fromX,
                ByteBufCodecs.VAR_INT, Segment::fromZ,
                ByteBufCodecs.VAR_INT, Segment::toX,
                ByteBufCodecs.VAR_INT, Segment::toZ,
                Segment::new);
    }

    /**
     * Opens the travel map at an active anchor, with the ley lines around it for context.
     */
    public record TravelMap(AnchorView source, List<AnchorView> linked, List<Segment> lines) implements CustomPacketPayload
    {
        public static final Type<TravelMap> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "anchor_travel_map"));

        public static final StreamCodec<RegistryFriendlyByteBuf, TravelMap> CODEC = StreamCodec.composite(
                AnchorView.CODEC, TravelMap::source,
                AnchorView.CODEC.apply(ByteBufCodecs.list()), TravelMap::linked,
                Segment.CODEC.apply(ByteBufCodecs.list()), TravelMap::lines,
                TravelMap::new);

        @Override
        public Type<TravelMap> type()
        {
            return TYPE;
        }
    }

    public record TravelRequest(long source, long destination) implements CustomPacketPayload
    {
        public static final Type<TravelRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "anchor_travel_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, TravelRequest> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, TravelRequest::source,
                ByteBufCodecs.VAR_LONG, TravelRequest::destination,
                TravelRequest::new);

        @Override
        public Type<TravelRequest> type()
        {
            return TYPE;
        }
    }
}
