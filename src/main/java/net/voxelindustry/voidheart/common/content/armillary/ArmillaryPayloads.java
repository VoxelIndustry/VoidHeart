package net.voxelindustry.voidheart.common.content.armillary;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusTheme;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public final class ArmillaryPayloads
{
    public static final int HIDDEN_THEME = -1;

    /**
     * A bubble as its chart shows it. The theme stays hidden from clients until a root has reached the bubble.
     */
    public record BubbleView(int id, int difficulty, int size, int theme, float x, long seed, int age, int fadesIn, int searchDuration, boolean broken)
    {
        public static final int NEVER = -1;

        private static final StreamCodec<ByteBuf, BubbleView> STREAM_CODEC = new StreamCodec<>()
        {
            @Override
            public BubbleView decode(ByteBuf buffer)
            {
                return new BubbleView(ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.FLOAT.decode(buffer), ByteBufCodecs.LONG.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.BOOL.decode(buffer));
            }

            @Override
            public void encode(ByteBuf buffer, BubbleView view)
            {
                ByteBufCodecs.VAR_INT.encode(buffer, view.id());
                ByteBufCodecs.VAR_INT.encode(buffer, view.difficulty());
                ByteBufCodecs.VAR_INT.encode(buffer, view.size());
                ByteBufCodecs.VAR_INT.encode(buffer, view.theme());
                ByteBufCodecs.FLOAT.encode(buffer, view.x());
                ByteBufCodecs.LONG.encode(buffer, view.seed());
                ByteBufCodecs.VAR_INT.encode(buffer, view.age());
                ByteBufCodecs.VAR_INT.encode(buffer, view.fadesIn());
                ByteBufCodecs.VAR_INT.encode(buffer, view.searchDuration());
                ByteBufCodecs.BOOL.encode(buffer, view.broken());
            }
        };

        static BubbleView of(VoidBubble bubble, ArmillaryChart chart, ArmillaryChart.Rules rules)
        {
            boolean revealed = bubble.id() == chart.getTargetId() && chart.getPhase().ordinal() >= ArmillaryChart.Phase.REACHED.ordinal();
            long age = chart.age() - bubble.foundAt();
            int fadesIn = bubble.fadeAt() == VoidBubble.NEVER ? NEVER : (int) Math.max(0, bubble.fadeAt() - chart.age());
            return new BubbleView(bubble.id(), bubble.difficulty(), bubble.size().ordinal(), revealed ? bubble.theme().id() : HIDDEN_THEME, bubble.x(), bubble.seed(), (int) age, fadesIn,
                    ArmillaryChart.searchDuration(bubble.difficulty(), rules), bubble.broken());
        }
    }

    /**
     * The Daedalus as this viewer's chart shows it: only a shadow until they have studied it. Where the root comes down and the district it opens on stay unknown until the root gets there.
     */
    public record StrataView(boolean known, boolean reveal, float x, long seed, int theme, Optional<Component> district, int searchDuration, int cost)
    {
        public static final StrataView NONE = new StrataView(false, false, 0, 0, HIDDEN_THEME, Optional.empty(), 0, 0);

        private static final StreamCodec<RegistryFriendlyByteBuf, StrataView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, StrataView::known,
                ByteBufCodecs.BOOL, StrataView::reveal,
                ByteBufCodecs.FLOAT, StrataView::x,
                ByteBufCodecs.LONG, StrataView::seed,
                ByteBufCodecs.VAR_INT, StrataView::theme,
                ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC, StrataView::district,
                ByteBufCodecs.VAR_INT, StrataView::searchDuration,
                ByteBufCodecs.VAR_INT, StrataView::cost,
                StrataView::new);

        /**
         * @param reveal whether this viewer is seeing it come into focus, the first time they look after studying it
         */
        static StrataView of(ArmillaryChart chart, ArmillaryChart.Rules rules, boolean known, boolean reveal, int cost, long worldSeed)
        {
            var point = chart.getStrata();
            if (point == null)
                return new StrataView(known, reveal, 0, 0, HIDDEN_THEME, Optional.empty(), rules.strataSearchTicks(), cost);

            boolean revealed = chart.getPhase().ordinal() >= ArmillaryChart.Phase.REACHED.ordinal();
            var district = new DistrictPos(point.districtX(), point.districtZ());
            return new StrataView(known, reveal, point.x(), point.seed(), revealed && known ? DaedalusTheme.at(district).ordinal() : HIDDEN_THEME,
                    revealed && known ? Optional.of(VoidDaedalus.nameOf(worldSeed, district)) : Optional.empty(), rules.strataSearchTicks(), cost);
        }
    }

    /**
     * What an armillary's screen draws, sent every second while it is open and after every change.
     */
    public record View(List<BubbleView> bubbles, int target, int phase, int searchTicks, int searchDuration, int stage, int pearls, int connectionTicks, int connectionDuration,
                       StrataView strata) implements CustomPacketPayload
    {
        public static final Type<View> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "armillary_view"));

        /** In place of a pearl count, for a player who pays nothing. */
        public static final int FREE = -1;
        /** In place of a connection's length, for one that holds until it is severed. */
        public static final int STABLE = -1;

        public static final View EMPTY = new View(List.of(), -1, 0, 0, 0, 0, 0, 0, 0, StrataView.NONE);

        public static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC = StreamCodec.composite(
                BubbleView.STREAM_CODEC.apply(ByteBufCodecs.list()), View::bubbles,
                ByteBufCodecs.VAR_INT, View::target,
                ByteBufCodecs.VAR_INT, View::phase,
                ByteBufCodecs.VAR_INT, View::searchTicks,
                ByteBufCodecs.VAR_INT, View::searchDuration,
                ByteBufCodecs.VAR_INT, View::stage,
                ByteBufCodecs.VAR_INT, View::pearls,
                ByteBufCodecs.VAR_INT, View::connectionTicks,
                ByteBufCodecs.VAR_INT, View::connectionDuration,
                StrataView.STREAM_CODEC, View::strata,
                View::new);

        static View of(ArmillaryChart chart, ArmillaryChart.Rules rules, ConnectionTimeline.Stage stage, int pearls, int connectionTicks, int connectionDuration, StrataView strata)
        {
            var bubbles = new ArrayList<BubbleView>();
            for (var bubble : chart.bubbles())
                bubbles.add(BubbleView.of(bubble, chart, rules));
            return new View(bubbles, chart.getTargetId(), chart.getPhase().ordinal(), chart.getSearchTicks(), chart.getSearchDuration(), stage.ordinal(), pearls, connectionTicks, connectionDuration,
                    strata);
        }

        public boolean targetsStrata()
        {
            return target == ArmillaryChart.STRATA;
        }

        public boolean isStable()
        {
            return connectionDuration == STABLE;
        }

        public ArmillaryChart.Phase phaseValue()
        {
            return ArmillaryChart.Phase.values()[phase];
        }

        public ConnectionTimeline.Stage stageValue()
        {
            return ConnectionTimeline.Stage.values()[stage];
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public enum ActionKind
    {
        TARGET,
        ABANDON,
        OPEN,
        SEVER
    }

    public record Action(ActionKind kind, int bubble) implements CustomPacketPayload
    {
        public static final Type<Action> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "armillary_action"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.idMapper(index -> ActionKind.values()[index], ActionKind::ordinal), Action::kind,
                ByteBufCodecs.VAR_INT, Action::bubble,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /**
     * The ground shaking under a player, from 0 to 1. Dies out on its own unless it is sent again.
     */
    public record Tremor(float strength) implements CustomPacketPayload
    {
        public static final Type<Tremor> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "armillary_tremor"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Tremor> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Tremor::strength,
                Tremor::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private ArmillaryPayloads()
    {
    }

    public static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(View.TYPE, View.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Tremor.TYPE, Tremor.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, ArmillaryPayloads::handle);
    }

    private static void handle(Action action, ServerPlayNetworking.Context context)
    {
        var player = context.player();
        if (player.containerMenu instanceof ArmillaryMenu menu && menu.stillValid(player) && player.level().getBlockEntity(menu.getPos()) instanceof ArmillaryTile armillary)
            armillary.act(player, action);
    }
}
