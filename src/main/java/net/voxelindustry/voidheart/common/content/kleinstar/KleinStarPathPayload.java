package net.voxelindustry.voidheart.common.content.kleinstar;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The way a Klein Star takes from where it is, sent to everyone who sees it when it leaves the staff and again each time it finds a new way, so they
 * draw it on its curve rather than from the positions a tick apart.
 *
 * @param points      the points of its {@link KleinStarPath}
 * @param flyingTicks how long it had been flying when it took this way, which sets its speed along it
 */
public record KleinStarPathPayload(int starId, List<Vec3> points, int flyingTicks) implements CustomPacketPayload
{
    public static final Type<KleinStarPathPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "klein_star_path"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KleinStarPathPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KleinStarPathPayload::starId,
            Vec3.STREAM_CODEC.apply(ByteBufCodecs.list()), KleinStarPathPayload::points,
            ByteBufCodecs.VAR_INT, KleinStarPathPayload::flyingTicks,
            KleinStarPathPayload::new);

    public static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }

    @Override
    public Type<KleinStarPathPayload> type()
    {
        return TYPE;
    }
}
