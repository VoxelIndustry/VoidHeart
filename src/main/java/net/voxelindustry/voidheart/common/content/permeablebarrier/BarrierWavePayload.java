package net.voxelindustry.voidheart.common.content.permeablebarrier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Tells clients a block joined a barrier structure, so a wave can flow from it across the structure.
 */
public record BarrierWavePayload(BlockPos origin) implements CustomPacketPayload
{
    public static final Type<BarrierWavePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "barrier_wave"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BarrierWavePayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(BarrierWavePayload::new, BarrierWavePayload::origin).cast();

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
