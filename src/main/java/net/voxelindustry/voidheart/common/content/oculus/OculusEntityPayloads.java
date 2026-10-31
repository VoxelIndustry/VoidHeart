package net.voxelindustry.voidheart.common.content.oculus;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Living entities on the land a table shows, for the parts of it farther than the viewer's client tracks entities.
 */
public final class OculusEntityPayloads
{
    public static final int MAX_MARKERS = 256;
    private static final int REQUEST_INTERVAL = 10;
    private static final double VIEWER_DISTANCE = 48;

    private static final Map<RequestKey, Long> LAST_REQUEST = new HashMap<>();

    private OculusEntityPayloads()
    {
    }

    public enum Kind
    {
        PLAYER,
        HOSTILE,
        PASSIVE
    }

    public record Request(BlockPos table, int windowBottom, int windowHeight) implements CustomPacketPayload
    {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "oculus_entity_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Request::table,
                ByteBufCodecs.VAR_INT, Request::windowBottom,
                ByteBufCodecs.VAR_INT, Request::windowHeight,
                Request::new);

        @Override
        public Type<Request> type()
        {
            return TYPE;
        }
    }

    /**
     * @param player for players, who they are so their name can be shown
     */
    public record Marker(int entityId, Kind kind, double x, double y, double z, Optional<UUID> player)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, Marker> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Marker::entityId,
                ByteBufCodecs.idMapper(index -> Kind.values()[index], Kind::ordinal), Marker::kind,
                ByteBufCodecs.DOUBLE, Marker::x,
                ByteBufCodecs.DOUBLE, Marker::y,
                ByteBufCodecs.DOUBLE, Marker::z,
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Marker::player,
                Marker::new);
    }

    public record Markers(BlockPos table, List<Marker> markers) implements CustomPacketPayload
    {
        public static final Type<Markers> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "oculus_entity_markers"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Markers> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Markers::table,
                Marker.CODEC.apply(ByteBufCodecs.list(MAX_MARKERS)), Markers::markers,
                Markers::new);

        @Override
        public Type<Markers> type()
        {
            return TYPE;
        }
    }

    public static void register()
    {
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Markers.TYPE, Markers.CODEC);
    }

    public static Kind kindOf(LivingEntity entity)
    {
        if (entity instanceof Player)
            return Kind.PLAYER;
        return entity instanceof Enemy ? Kind.HOSTILE : Kind.PASSIVE;
    }

    /**
     * Whether an entity shows on a hologram at all: spectators and invisible entities do not.
     */
    public static boolean isShown(LivingEntity entity)
    {
        return entity.isAlive() && !entity.isSpectator() && !entity.isInvisible();
    }

    private record RequestKey(UUID player, BlockPos table)
    {
    }

    public static void handle(Request request, ServerPlayNetworking.Context context)
    {
        var player = context.player();
        var level = player.level();
        if (request.windowHeight() <= 0 || request.windowHeight() > OculusView.WIDEST_SCALE)
            return;
        if (player.position().distanceToSqr(Vec3.atCenterOf(request.table())) > VIEWER_DISTANCE * VIEWER_DISTANCE)
            return;

        long now = level.getGameTime();
        var key = new RequestKey(player.getUUID(), request.table());
        var last = LAST_REQUEST.get(key);
        if (last != null && now - last < REQUEST_INTERVAL)
            return;

        var tile = OculusTableStructure.getTile(level, request.table());
        var footprint = OculusTableStructure.footprint(level, request.table());
        if (tile == null || footprint.isEmpty())
            return;
        LAST_REQUEST.put(key, now);

        var view = tile.getView();
        var center = view.follow()
                .map(id -> level.getServer().getPlayerList().getPlayer(id))
                .filter(target -> target.level() == level)
                .map(target -> target.position())
                .orElse(new Vec3(footprint.get().centerX(), 0, footprint.get().centerZ()));
        double halfX = footprint.get().width() * view.scale() / 2D;
        double halfZ = footprint.get().depth() * view.scale() / 2D;
        var area = new AABB(center.x - halfX, request.windowBottom(), center.z - halfZ,
                center.x + halfX, request.windowBottom() + request.windowHeight(), center.z + halfZ);

        ServerPlayNetworking.send(player, new Markers(request.table(), collect(level, area)));
    }

    private static List<Marker> collect(ServerLevel level, AABB area)
    {
        var markers = new ArrayList<Marker>();
        for (var entity : level.getEntitiesOfClass(LivingEntity.class, area, OculusEntityPayloads::isShown))
        {
            if (markers.size() >= MAX_MARKERS)
                break;
            var kind = kindOf(entity);
            markers.add(new Marker(entity.getId(), kind, entity.getX(), entity.getY(), entity.getZ(),
                    kind == Kind.PLAYER ? Optional.of(entity.getUUID()) : Optional.empty()));
        }
        return markers;
    }

    public static void clear()
    {
        LAST_REQUEST.clear();
    }
}
