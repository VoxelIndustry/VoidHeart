package net.voxelindustry.voidheart.common.content.research;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class KnowledgePayloads
{
    /**
     * What the repository screen shows, sent every second while it is open and after every action.
     */
    /** What the columns of a repository come to. Told by the server since research and its config both decide it. */
    public record ColumnInfo(int count, double capacity, int radius)
    {
        public static final ColumnInfo NONE = new ColumnInfo(0, 0, 0);

        public static final StreamCodec<RegistryFriendlyByteBuf, ColumnInfo> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ColumnInfo::count,
                ByteBufCodecs.DOUBLE, ColumnInfo::capacity,
                ByteBufCodecs.VAR_INT, ColumnInfo::radius,
                ColumnInfo::new);

        public double total()
        {
            return count * capacity;
        }
    }

    /** What attunement, Meat Proxies, Cogitants and the Void Loom come to around a repository. */
    public record UpgradeInfo(int attunement, int tolerance, int proxies, int proxyStrain, int cogitants, int cogitantLimit, double cogitantRate, boolean loom,
                              int proxyLimit, double proxyInsightsPerHour)
    {
        public static final UpgradeInfo NONE = new UpgradeInfo(0, 0, 0, 0, 0, 0, 0, false, 0, 0);

        public static final StreamCodec<RegistryFriendlyByteBuf, UpgradeInfo> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, UpgradeInfo::attunement,
                ByteBufCodecs.VAR_INT, UpgradeInfo::tolerance,
                ByteBufCodecs.VAR_INT, UpgradeInfo::proxies,
                ByteBufCodecs.VAR_INT, UpgradeInfo::proxyStrain,
                ByteBufCodecs.VAR_INT, UpgradeInfo::cogitants,
                ByteBufCodecs.VAR_INT, UpgradeInfo::cogitantLimit,
                ByteBufCodecs.DOUBLE, UpgradeInfo::cogitantRate,
                ByteBufCodecs.BOOL, UpgradeInfo::loom,
                ByteBufCodecs.VAR_INT, UpgradeInfo::proxyLimit,
                ByteBufCodecs.DOUBLE, UpgradeInfo::proxyInsightsPerHour,
                UpgradeInfo::new);

        public int bearable()
        {
            return tolerance + proxies * proxyStrain;
        }

        /** Insights the counted proxies make in an hour. */
        public double insightsPerHour()
        {
            return Math.min(proxies, proxyLimit) * proxyInsightsPerHour;
        }

        public int countedCogitants()
        {
            return Math.min(cogitants, cogitantLimit);
        }
    }

    public record Snapshot(double knowledge, double cap, double rate, int insights, double nextInsightCost, double insightCostGrowth, Optional<Identifier> active,
                           Map<Identifier, Double> progress, List<Identifier> revealed, List<Identifier> completed, Map<Identifier, Integer> fed, ColumnInfo columns,
                           UpgradeInfo upgrades) implements CustomPacketPayload
    {
        public static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0, 1, Optional.empty(), Map.of(), List.of(), List.of(), Map.of(), ColumnInfo.NONE, UpgradeInfo.NONE);

        public static final Type<Snapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "knowledge_snapshot"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Snapshot::knowledge,
                ByteBufCodecs.DOUBLE, Snapshot::cap,
                ByteBufCodecs.DOUBLE, Snapshot::rate,
                ByteBufCodecs.VAR_INT, Snapshot::insights,
                ByteBufCodecs.DOUBLE, Snapshot::nextInsightCost,
                ByteBufCodecs.DOUBLE, Snapshot::insightCostGrowth,
                ByteBufCodecs.optional(Identifier.STREAM_CODEC), Snapshot::active,
                ByteBufCodecs.<RegistryFriendlyByteBuf, Identifier, Double, Map<Identifier, Double>>map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.DOUBLE), Snapshot::progress,
                Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), Snapshot::revealed,
                Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), Snapshot::completed,
                ByteBufCodecs.<RegistryFriendlyByteBuf, Identifier, Integer, Map<Identifier, Integer>>map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT), Snapshot::fed,
                ColumnInfo.STREAM_CODEC, Snapshot::columns,
                UpgradeInfo.STREAM_CODEC, Snapshot::upgrades,
                Snapshot::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /**
     * What the repository's pages need to know of recipes, which otherwise never leave the server. Sent when the screen opens.
     */
    public record Recipes(List<RecipeSheet> sheets) implements CustomPacketPayload
    {
        public static final Type<Recipes> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "knowledge_recipes"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Recipes> STREAM_CODEC = StreamCodec.composite(
                RecipeSheet.STREAM_CODEC.apply(ByteBufCodecs.list()), Recipes::sheets,
                Recipes::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /**
     * Sent when a player presses something on the repository screen.
     */
    public record Action(Kind kind, Optional<Identifier> research) implements CustomPacketPayload
    {
        public static final Type<Action> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "knowledge_action"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.idMapper(index -> Kind.values()[index], Kind::ordinal), Action::kind,
                ByteBufCodecs.optional(Identifier.STREAM_CODEC), Action::research,
                Action::new);

        public enum Kind
        {
            BUY_INSIGHT,
            REVEAL,
            TOGGLE
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Pausing is always allowed, starting only with the strain borne. */
    private static boolean mayToggle(PlayerKnowledge data, Map<Identifier, Research> tree, Identifier id)
    {
        var research = tree.get(id);
        return research != null && (data.getActive().filter(id::equals).isPresent() || data.canBear(research, Knowledge.rules()));
    }

    public static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(Snapshot.TYPE, Snapshot.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Recipes.TYPE, Recipes.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, KnowledgePayloads::handle);
    }

    private static void handle(Action action, ServerPlayNetworking.Context context)
    {
        var player = context.player();
        if (!(player.containerMenu instanceof KnowledgeRepositoryMenu))
            return;

        var server = player.level().getServer();
        var data = Knowledge.of(server, player.getUUID());
        var tree = Knowledge.tree(server.registryAccess());

        boolean changed = switch (action.kind())
        {
            case BUY_INSIGHT -> data.buyInsight(Knowledge.rules());
            case REVEAL -> action.research().filter(id -> data.reveal(id, tree)).isPresent();
            case TOGGLE -> action.research().filter(id -> mayToggle(data, tree, id) && data.toggleActive(id)).isPresent();
        };

        if (changed)
            KnowledgeState.get(server).setDirty();
        Knowledge.sendSnapshot(player);
    }
}
