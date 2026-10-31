package net.voxelindustry.voidheart.common.content.oculus;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Land a table projects but its viewer has not loaded: requested by the client, read from the server's loaded chunks.
 */
public final class OculusChunkPayloads
{
    public static final int MAX_CHUNKS_PER_REQUEST = 32;
    public static final int COLUMNS = 16 * 16;

    private OculusChunkPayloads()
    {
    }

    /**
     * Sections between the window bottom and top of these chunks, for the table at that position.
     */
    public record Request(BlockPos table, int windowBottom, int windowHeight, List<Long> chunks) implements CustomPacketPayload
    {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "oculus_chunk_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Request::table,
                ByteBufCodecs.VAR_INT, Request::windowBottom,
                ByteBufCodecs.VAR_INT, Request::windowHeight,
                ByteBufCodecs.LONG.apply(ByteBufCodecs.list(MAX_CHUNKS_PER_REQUEST)), Request::chunks,
                Request::new);

        @Override
        public Type<Request> type()
        {
            return TYPE;
        }
    }

    /**
     * The sections of one chunk within a window, each written as a presence flag then the section in the vanilla
     * network format. An unavailable chunk is one the server has not loaded either.
     *
     * @param cover per column along x then z, the first open block above everything that blocks motion, leaves
     *              aside: what lies below is covered. Empty when unknown
     */
    public record Sections(int chunkX, int chunkZ, int windowBottom, int windowHeight, boolean available, byte[] data, List<Integer> cover)
            implements CustomPacketPayload
    {
        public static final Type<Sections> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "oculus_chunk_sections"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Sections> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Sections::chunkX,
                ByteBufCodecs.VAR_INT, Sections::chunkZ,
                ByteBufCodecs.VAR_INT, Sections::windowBottom,
                ByteBufCodecs.VAR_INT, Sections::windowHeight,
                ByteBufCodecs.BOOL, Sections::available,
                ByteBufCodecs.BYTE_ARRAY, Sections::data,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(COLUMNS)), Sections::cover,
                Sections::new);

        @Override
        public Type<Sections> type()
        {
            return TYPE;
        }
    }

    public static void register()
    {
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Sections.TYPE, Sections.CODEC);
    }
}
