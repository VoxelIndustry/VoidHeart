package net.voxelindustry.voidheart.common.content.sin;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Sent to a player whose proficiency in a sin just passed a stage, so their weapon's inlay flares.
 */
public record SinStageCrossedPayload(Sin sin) implements CustomPacketPayload
{
    public static final Type<SinStageCrossedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "sin_stage_crossed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SinStageCrossedPayload> CODEC = Sin.STREAM_CODEC
            .<RegistryFriendlyByteBuf>cast()
            .map(SinStageCrossedPayload::new, SinStageCrossedPayload::sin);

    public static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }

    @Override
    public Type<SinStageCrossedPayload> type()
    {
        return TYPE;
    }
}
