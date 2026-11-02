package net.voxelindustry.voidheart.common.curse;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Tells the afflicted player's client what to perceive.
 */
public final class CursePayloads
{
    private CursePayloads()
    {
    }

    public static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(Begin.TYPE, Begin.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(End.TYPE, End.CODEC);
    }

    /**
     * The seed is shared so that both sides agree on anything the curse decides at random.
     */
    public record Begin(CurseKind kind, long seed) implements CustomPacketPayload
    {
        public static final Type<Begin> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "curse_begin"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Begin> CODEC = StreamCodec.composite(
                CurseKind.STREAM_CODEC, Begin::kind,
                ByteBufCodecs.VAR_LONG, Begin::seed,
                Begin::new);

        @Override
        public Type<Begin> type()
        {
            return TYPE;
        }
    }

    /**
     * Cuts the running curse short.
     */
    public record End() implements CustomPacketPayload
    {
        public static final Type<End> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "curse_end"));

        public static final StreamCodec<RegistryFriendlyByteBuf, End> CODEC = StreamCodec.unit(new End());

        @Override
        public Type<End> type()
        {
            return TYPE;
        }
    }
}
