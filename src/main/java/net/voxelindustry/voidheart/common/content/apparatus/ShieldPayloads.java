package net.voxelindustry.voidheart.common.content.apparatus;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.joml.Vector3fc;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public final class ShieldPayloads
{
    /**
     * A hit the shield of an entity took, for everyone who sees it.
     *
     * @param from     where the hit came from, from the middle of the entity's box, in world axes
     * @param strength how much of the shield the hit took, from 0 to 1
     * @param broke    whether it emptied the shield
     */
    public record Hit(int entity, Vector3fc from, float strength, boolean broke) implements CustomPacketPayload
    {
        public static final Type<Hit> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "shield_hit"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Hit> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Hit::entity,
                ByteBufCodecs.VECTOR3F, Hit::from,
                ByteBufCodecs.FLOAT, Hit::strength,
                ByteBufCodecs.BOOL, Hit::broke,
                Hit::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /**
     * Asks to open the grid of an apparatus right clicked in a screen.
     */
    public record Open(ApparatusHolder holder) implements CustomPacketPayload
    {
        public static final Type<Open> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "apparatus_open"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = ApparatusHolder.STREAM_CODEC.map(Open::new, Open::holder);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void handle(Open open, ServerPlayNetworking.Context context)
        {
            ShieldingApparatusItem.openGrid(context.player(), open.holder());
        }
    }

    private ShieldPayloads()
    {
    }

    public static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(Hit.TYPE, Hit.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Open.TYPE, Open.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Open.TYPE, Open::handle);
    }
}
