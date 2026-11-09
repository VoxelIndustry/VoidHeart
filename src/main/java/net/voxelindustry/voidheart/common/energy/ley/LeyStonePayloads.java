package net.voxelindustry.voidheart.common.energy.ley;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * One-off effects the client can't work out alone: a ward holding back a player's corruption, and a grower quickening a plant.
 */
public final class LeyStonePayloads
{
    private LeyStonePayloads()
    {
    }

    public record WardHold(BlockPos ward, int playerId) implements CustomPacketPayload
    {
        public static final Type<WardHold> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "ley_ward_hold"));

        public static final StreamCodec<ByteBuf, WardHold> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, WardHold::ward,
                ByteBufCodecs.VAR_INT, WardHold::playerId,
                WardHold::new);

        @Override
        public Type<WardHold> type()
        {
            return TYPE;
        }
    }

    public record GrowerGlint(BlockPos plant) implements CustomPacketPayload
    {
        public static final Type<GrowerGlint> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "ley_grower_glint"));

        public static final StreamCodec<ByteBuf, GrowerGlint> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, GrowerGlint::plant,
                GrowerGlint::new);

        @Override
        public Type<GrowerGlint> type()
        {
            return TYPE;
        }
    }

    static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(WardHold.TYPE, WardHold.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GrowerGlint.TYPE, GrowerGlint.CODEC);
    }
}
