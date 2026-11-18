package net.voxelindustry.voidheart.common.content.blight;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public final class BlueprintPayloads
{
    private static final StreamCodec<ByteBuf, InteractionHand> HAND = ByteBufCodecs.VAR_INT.map(
            ordinal -> InteractionHand.values()[Math.clamp(ordinal, 0, InteractionHand.values().length - 1)], InteractionHand::ordinal);

    /** One design shown on a contraption, with the icon of the research it brings and the name key of the constellation that research is in. */
    public record Design(Identifier blueprint, Identifier icon, String constellation)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, Design> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Design::blueprint,
                Identifier.STREAM_CODEC, Design::icon,
                ByteBufCodecs.STRING_UTF8, Design::constellation,
                Design::new);
    }

    /** The designs a contraption held in that hand shows, which opens the choice. */
    public record Offer(InteractionHand hand, List<Design> designs) implements CustomPacketPayload
    {
        public static final Type<Offer> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "blueprint_offer"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Offer> STREAM_CODEC = StreamCodec.composite(
                HAND, Offer::hand,
                Design.STREAM_CODEC.apply(ByteBufCodecs.list(Blueprints.OFFERED)), Offer::designs,
                Offer::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** The design picked on the contraption held in that hand. */
    public record Choose(InteractionHand hand, Identifier blueprint) implements CustomPacketPayload
    {
        public static final Type<Choose> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "blueprint_choose"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Choose> STREAM_CODEC = StreamCodec.composite(
                HAND, Choose::hand,
                Identifier.STREAM_CODEC, Choose::blueprint,
                Choose::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void handle(Choose choose, ServerPlayNetworking.Context context)
        {
            BlessedContraptionItem.choose(context.player(), choose.hand(), choose.blueprint());
        }
    }

    private BlueprintPayloads()
    {
    }

    public static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(Offer.TYPE, Offer.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Choose.TYPE, Choose.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Choose.TYPE, Choose::handle);
    }
}
