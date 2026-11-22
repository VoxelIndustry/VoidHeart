package net.voxelindustry.voidheart.common.content.hoard;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The hoard view's traffic: the server sends what the hoard holds, in pages, whenever it changes; the client asks to take a stack or put back what
 * it carries.
 */
public final class HoardPayloads
{
    /** Kinds sent per page, so a large hoard never makes one packet too big. */
    static final int PAGE_SIZE = 512;

    public record Entry(ItemVariant variant, long count)
    {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemVariant.PACKET_CODEC, Entry::variant,
                ByteBufCodecs.VAR_LONG, Entry::count,
                Entry::new);
    }

    /**
     * One page of what the hoard holds. The first page starts a new listing.
     */
    public record Page(int containerId, int page, int pages, long total, long capacity, List<Entry> entries) implements CustomPacketPayload
    {
        public static final Type<Page> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "hoard_page"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Page> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Page::containerId,
                ByteBufCodecs.VAR_INT, Page::page,
                ByteBufCodecs.VAR_INT, Page::pages,
                ByteBufCodecs.VAR_LONG, Page::total,
                ByteBufCodecs.VAR_LONG, Page::capacity,
                Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), Page::entries,
                Page::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /**
     * Takes a stack of one kind, onto the cursor or into the inventory.
     */
    public record Take(int containerId, ItemVariant variant, boolean toInventory) implements CustomPacketPayload
    {
        public static final Type<Take> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "hoard_take"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Take> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Take::containerId,
                ItemVariant.PACKET_CODEC, Take::variant,
                ByteBufCodecs.BOOL, Take::toInventory,
                Take::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        static void handle(Take take, ServerPlayNetworking.Context context)
        {
            if (context.player().containerMenu instanceof HoardMenu menu && menu.containerId == take.containerId())
                menu.take(take.variant(), take.toInventory());
        }
    }

    /**
     * Puts what the cursor carries into the hoard.
     */
    public record Put(int containerId) implements CustomPacketPayload
    {
        public static final Type<Put> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "hoard_put"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Put> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Put::new, Put::containerId).cast();

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        static void handle(Put put, ServerPlayNetworking.Context context)
        {
            if (context.player().containerMenu instanceof HoardMenu menu && menu.containerId == put.containerId())
                menu.putCarried();
        }
    }

    private HoardPayloads()
    {
    }

    static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(Page.TYPE, Page.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Take.TYPE, Take.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Put.TYPE, Put.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Take.TYPE, Take::handle);
        ServerPlayNetworking.registerGlobalReceiver(Put.TYPE, Put::handle);
    }
}
