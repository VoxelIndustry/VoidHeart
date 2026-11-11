package net.voxelindustry.voidheart.common.content.kleinstar;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A Klein Star burst, sent to everyone near enough to see it, who draw the flash and light the blocks around it for themselves.
 *
 * @param coverTicks how long the lit blocks keep burning
 */
public record KleinStarBurstPayload(Vec3 center, float radius, int coverTicks) implements CustomPacketPayload
{
    public static final Type<KleinStarBurstPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "klein_star_burst"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KleinStarBurstPayload> CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, KleinStarBurstPayload::center,
            ByteBufCodecs.FLOAT, KleinStarBurstPayload::radius,
            ByteBufCodecs.VAR_INT, KleinStarBurstPayload::coverTicks,
            KleinStarBurstPayload::new);

    public static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }

    @Override
    public Type<KleinStarBurstPayload> type()
    {
        return TYPE;
    }
}
