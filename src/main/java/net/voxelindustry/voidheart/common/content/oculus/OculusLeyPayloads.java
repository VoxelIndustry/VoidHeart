package net.voxelindustry.voidheart.common.content.oculus;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.ley.LeyDebugPayload;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.ley.anchor.AnchorView;
import net.voxelindustry.voidheart.common.ley.anchor.OldAnchors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Ley lines, nodes and Old Anchors under the land a table shows, for tables with their ley layer on.
 */
public final class OculusLeyPayloads
{
    private static final int REQUEST_INTERVAL = 20;
    private static final double VIEWER_DISTANCE = 48;
    /**
     * World blocks gathered past the land shown, so lines entering it from outside are drawn to the edge.
     */
    private static final double AREA_MARGIN = 48;

    private static final Map<RequestKey, Long> LAST_REQUEST = new HashMap<>();

    private OculusLeyPayloads()
    {
    }

    private record RequestKey(UUID player, BlockPos table)
    {
    }

    private record EdgeKey(long from, long to)
    {
    }

    public record Request(BlockPos table) implements CustomPacketPayload
    {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "oculus_ley_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Request::table,
                Request::new);

        @Override
        public Type<Request> type()
        {
            return TYPE;
        }
    }

    /**
     * Everything within the radius around the center. Outside the overworld, where no lattice exists, it is empty.
     */
    public record Layer(BlockPos table, double centerX, double centerZ, double radius,
                        List<LeyDebugPayload.Line> lines, List<LeyDebugPayload.Node> nodes, List<AnchorView> anchors) implements CustomPacketPayload
    {
        public static final Type<Layer> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "oculus_ley_layer"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Layer> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Layer::table,
                ByteBufCodecs.DOUBLE, Layer::centerX,
                ByteBufCodecs.DOUBLE, Layer::centerZ,
                ByteBufCodecs.DOUBLE, Layer::radius,
                LeyDebugPayload.Line.CODEC.apply(ByteBufCodecs.list()), Layer::lines,
                LeyDebugPayload.Node.CODEC.apply(ByteBufCodecs.list()), Layer::nodes,
                AnchorView.CODEC.apply(ByteBufCodecs.list()), Layer::anchors,
                Layer::new);

        @Override
        public Type<Layer> type()
        {
            return TYPE;
        }
    }

    public static void register()
    {
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Layer.TYPE, Layer.CODEC);
    }

    public static void handle(Request request, ServerPlayNetworking.Context context)
    {
        var player = context.player();
        var level = player.level();
        if (player.position().distanceToSqr(Vec3.atCenterOf(request.table())) > VIEWER_DISTANCE * VIEWER_DISTANCE)
            return;

        long now = level.getGameTime();
        var key = new RequestKey(player.getUUID(), request.table());
        var last = LAST_REQUEST.get(key);
        if (last != null && now - last < REQUEST_INTERVAL)
            return;

        var tile = OculusTableStructure.getTile(level, request.table());
        var footprint = OculusTableStructure.footprint(level, request.table());
        if (tile == null || footprint.isEmpty() || !tile.getView().leyLines())
            return;
        LAST_REQUEST.put(key, now);

        var view = tile.getView();
        var center = view.follow()
                .map(id -> level.getServer().getPlayerList().getPlayer(id))
                .filter(target -> target.level() == level)
                .map(target -> target.position())
                .orElse(new Vec3(footprint.get().centerX(), 0, footprint.get().centerZ()));
        double radius = Math.hypot(footprint.get().width(), footprint.get().depth()) * view.scale() / 2 + AREA_MARGIN;

        ServerPlayNetworking.send(player, collect(level, request.table(), center.x, center.z, radius));
    }

    private static Layer collect(ServerLevel level, BlockPos table, double centerX, double centerZ, double radius)
    {
        var lines = new ArrayList<LeyDebugPayload.Line>();
        var nodes = new ArrayList<LeyDebugPayload.Node>();
        var anchors = new ArrayList<AnchorView>();

        var lattice = LeyLattice.get(level);
        if (lattice.isPresent())
        {
            var heights = lattice.get().heights();
            var seenEdges = new HashSet<EdgeKey>();
            int x = (int) centerX, z = (int) centerZ;
            for (var region : lattice.get().regionsAround(x, z, (int) Math.ceil(radius), true))
            {
                for (var edge : region.edges())
                {
                    // Edges crossing a region border are stored by both regions
                    if (edge.distanceSq(centerX, centerZ) > radius * radius || !seenEdges.add(new EdgeKey(edge.from(), edge.to())))
                        continue;
                    var profile = heights.profile(edge);
                    var sampled = new ArrayList<Float>(profile.length);
                    for (float height : profile)
                        sampled.add(height);
                    lines.add(new LeyDebugPayload.Line(edge.fromX(), edge.fromZ(), edge.toX(), edge.toZ(), sampled));
                }
                for (var node : region.nodes())
                {
                    if (node.distanceSq(centerX, centerZ) <= radius * radius)
                        nodes.add(new LeyDebugPayload.Node(node.x(), heights.nodeHeight(node.x(), node.z()), node.z(), node.kind(), node.lines(), node.power()));
                }
            }

            OldAnchors.get(level).ifPresent(oldAnchors -> oldAnchors.network()
                    .anchorsWithin((int) (centerX - radius), (int) (centerZ - radius), (int) (centerX + radius), (int) (centerZ + radius))
                    .forEach(anchor -> anchors.add(oldAnchors.view(anchor))));
        }
        return new Layer(table, centerX, centerZ, radius, lines, nodes, anchors);
    }

    public static void clear()
    {
        LAST_REQUEST.clear();
    }
}
