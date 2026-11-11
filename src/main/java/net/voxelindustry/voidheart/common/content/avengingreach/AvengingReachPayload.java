package net.voxelindustry.voidheart.common.content.avengingreach;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * One arm of a player lashing at an aggressor, sent to everyone who sees the player so they all draw it.
 *
 * @param seed varies the curve of each lash
 */
public record AvengingReachPayload(int ownerId, int targetId, int arm, int seed) implements CustomPacketPayload
{
    public static final Type<AvengingReachPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "avenging_reach"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AvengingReachPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AvengingReachPayload::ownerId,
            ByteBufCodecs.VAR_INT, AvengingReachPayload::targetId,
            ByteBufCodecs.VAR_INT, AvengingReachPayload::arm,
            ByteBufCodecs.INT, AvengingReachPayload::seed,
            AvengingReachPayload::new);

    public static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }

    @Override
    public Type<AvengingReachPayload> type()
    {
        return TYPE;
    }
}
