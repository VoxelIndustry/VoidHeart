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
    public record Snapshot(double knowledge, double cap, double rate, int insights, double nextInsightCost, double insightCostGrowth, Optional<Identifier> active,
                           Map<Identifier, Double> progress, List<Identifier> revealed, List<Identifier> completed, Map<Identifier, Integer> fed) implements CustomPacketPayload
    {
        public static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0, 1, Optional.empty(), Map.of(), List.of(), List.of(), Map.of());

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
                Snapshot::new);

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

    public static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(Snapshot.TYPE, Snapshot.STREAM_CODEC);
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
            case TOGGLE -> action.research().filter(data::toggleActive).isPresent();
        };

        if (changed)
            KnowledgeState.get(server).setDirty();
        Knowledge.sendSnapshot(player);
    }
}
