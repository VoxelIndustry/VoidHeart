package net.voxelindustry.voidheart.common.content.works;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.voxelindustry.voidheart.common.content.hoard.HoardMenu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The works view's traffic, all through the Reckoning's menu: what the stations can make, a plan asked for, orders begun and cancelled, levels to
 * keep, and the orders running with their progress.
 */
public final class WorksPayloads
{
    static final int PAGE_SIZE = 512;

    public record Makeable(int containerId, int page, int pages, List<ItemVariant> items) implements CustomPacketPayload
    {
        public static final Type<Makeable> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "works_makeable"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Makeable> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Makeable::containerId,
                ByteBufCodecs.VAR_INT, Makeable::page,
                ByteBufCodecs.VAR_INT, Makeable::pages,
                ItemVariant.PACKET_CODEC.apply(ByteBufCodecs.list()), Makeable::items,
                Makeable::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record AskPlan(int containerId, ItemVariant item, long count) implements CustomPacketPayload
    {
        public static final Type<AskPlan> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "works_ask_plan"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AskPlan> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AskPlan::containerId,
                ItemVariant.PACKET_CODEC, AskPlan::item,
                ByteBufCodecs.VAR_LONG, AskPlan::count,
                AskPlan::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record Plan(int containerId, ItemVariant item, long count, WorksGraph graph) implements CustomPacketPayload
    {
        public static final Type<Plan> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "works_plan"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Plan> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Plan::containerId,
                ItemVariant.PACKET_CODEC, Plan::item,
                ByteBufCodecs.VAR_LONG, Plan::count,
                WorksGraph.STREAM_CODEC, Plan::graph,
                Plan::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record Begin(int containerId, ItemVariant item, long count) implements CustomPacketPayload
    {
        public static final Type<Begin> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "works_begin"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Begin> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Begin::containerId,
                ItemVariant.PACKET_CODEC, Begin::item,
                ByteBufCodecs.VAR_LONG, Begin::count,
                Begin::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record Cancel(int containerId, int job) implements CustomPacketPayload
    {
        public static final Type<Cancel> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "works_cancel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cancel> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Cancel::containerId,
                ByteBufCodecs.VAR_INT, Cancel::job,
                Cancel::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /**
     * @param level how many to keep in the hoard, 0 to stop keeping it
     */
    public record Keep(int containerId, ItemVariant item, long level) implements CustomPacketPayload
    {
        public static final Type<Keep> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "works_keep"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Keep> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Keep::containerId,
                ItemVariant.PACKET_CODEC, Keep::item,
                ByteBufCodecs.VAR_LONG, Keep::level,
                Keep::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /**
     * @param remaining crafts not yet claimed, by recipe id
     * @param working   crafts being worked, by recipe id
     */
    public record Job(int id, String what, long count, WorksGraph graph, Map<String, Long> remaining, Map<String, Long> working)
    {
        private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, Long>> COUNTS = ByteBufCodecs.<RegistryFriendlyByteBuf, String, Long, Map<String, Long>>map(
                LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG);

        static final StreamCodec<RegistryFriendlyByteBuf, Job> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Job::id,
                ByteBufCodecs.STRING_UTF8, Job::what,
                ByteBufCodecs.VAR_LONG, Job::count,
                WorksGraph.STREAM_CODEC, Job::graph,
                COUNTS, Job::remaining,
                COUNTS, Job::working,
                Job::new);

        /**
         * @return crafts of a recipe not yet finished
         */
        public long left(String recipe)
        {
            return remaining.getOrDefault(recipe, 0L) + working.getOrDefault(recipe, 0L);
        }
    }

    public record Kept(ItemVariant item, long level)
    {
        static final StreamCodec<RegistryFriendlyByteBuf, Kept> STREAM_CODEC = StreamCodec.composite(
                ItemVariant.PACKET_CODEC, Kept::item,
                ByteBufCodecs.VAR_LONG, Kept::level,
                Kept::new);
    }

    public record Jobs(int containerId, List<Job> jobs, List<Kept> keeps) implements CustomPacketPayload
    {
        public static final Type<Jobs> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "works_jobs"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Jobs> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Jobs::containerId,
                Job.STREAM_CODEC.apply(ByteBufCodecs.list()), Jobs::jobs,
                Kept.STREAM_CODEC.apply(ByteBufCodecs.list()), Jobs::keeps,
                Jobs::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private WorksPayloads()
    {
    }

    private static void session(ServerPlayer player, int containerId, Consumer<WorksSession> action)
    {
        if (player.containerMenu instanceof HoardMenu menu && menu.containerId == containerId && menu.session() != null)
            action.accept(menu.session());
    }

    static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(Makeable.TYPE, Makeable.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Plan.TYPE, Plan.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Jobs.TYPE, Jobs.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AskPlan.TYPE, AskPlan.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Begin.TYPE, Begin.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Cancel.TYPE, Cancel.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Keep.TYPE, Keep.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AskPlan.TYPE, (ask, context) -> session(context.player(), ask.containerId(), s -> s.plan(ask.item(), ask.count())));
        ServerPlayNetworking.registerGlobalReceiver(Begin.TYPE, (begin, context) -> session(context.player(), begin.containerId(), s -> s.begin(begin.item(), begin.count())));
        ServerPlayNetworking.registerGlobalReceiver(Cancel.TYPE, (cancel, context) -> session(context.player(), cancel.containerId(), s -> s.cancel(cancel.job())));
        ServerPlayNetworking.registerGlobalReceiver(Keep.TYPE, (keep, context) -> session(context.player(), keep.containerId(), s -> s.keep(keep.item(), keep.level())));
    }
}
