package net.voxelindustry.voidheart.common.content.works;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The small folds an Officiant opens over its station, told to the players near it.
 */
public final class OfficiantFolds
{
    /** An item laid out rises out of the fold onto its block, the item being the block's own. */
    public static final int LAY  = 0;
    /** A result taken back sinks into the fold. */
    public static final int TAKE = 1;
    /** A table's result rises out of the fold and sinks back in. */
    public static final int PASS = 2;

    /**
     * @param pos  the block the fold lies on
     * @param rest the height above that block the item floats at
     * @param item the item it shows, empty for one the block shows itself
     */
    public record Payload(BlockPos pos, float rest, ItemStack item, int kind) implements CustomPacketPayload
    {
        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "officiant_fold"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Payload::pos,
                ByteBufCodecs.FLOAT, Payload::rest,
                ItemStack.OPTIONAL_STREAM_CODEC, Payload::item,
                ByteBufCodecs.VAR_INT, Payload::kind,
                Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private OfficiantFolds()
    {
    }

    static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.STREAM_CODEC);
    }
}
