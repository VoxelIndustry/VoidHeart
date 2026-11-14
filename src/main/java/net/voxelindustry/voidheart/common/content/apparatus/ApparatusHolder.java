package net.voxelindustry.voidheart.common.content.apparatus;

import eu.pb4.trinkets.api.TrinketSlotReference;
import eu.pb4.trinkets.api.TrinketsApi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Where the apparatus an open grid edits is kept: a slot of the player's inventory or the Trinkets slot it is worn in.
 */
public sealed interface ApparatusHolder
{
    StreamCodec<RegistryFriendlyByteBuf, ApparatusHolder> STREAM_CODEC = StreamCodec.of(ApparatusHolder::write, ApparatusHolder::read);

    ItemStack get(Player player);

    /**
     * The inventory slot the apparatus lies in, which its grid keeps it from being moved out of, or -1 when it is worn.
     */
    default int inventorySlot()
    {
        return -1;
    }

    record Carried(int slot) implements ApparatusHolder
    {
        @Override
        public ItemStack get(Player player)
        {
            var inventory = player.getInventory();
            return slot >= 0 && slot < inventory.getContainerSize() ? inventory.getItem(slot) : ItemStack.EMPTY;
        }

        @Override
        public int inventorySlot()
        {
            return slot;
        }
    }

    record Worn(TrinketSlotReference slot) implements ApparatusHolder
    {
        @Override
        public ItemStack get(Player player)
        {
            var attachment = TrinketsApi.getAttachment(player);
            var access = attachment == null ? null : attachment.getSlotAccess(slot);
            return access == null ? ItemStack.EMPTY : access.get();
        }
    }

    /**
     * Where the stack in a slot of a screen the player has open is kept, when it is theirs.
     */
    static @Nullable ApparatusHolder of(Player player, Slot slot, ItemStack stack)
    {
        if (slot.container == player.getInventory())
            return new Carried(slot.getContainerSlot());
        var attachment = TrinketsApi.getAttachment(player);
        if (attachment == null)
            return null;
        return attachment.findFirst(worn -> worn == stack, false).<ApparatusHolder> map(access -> new Worn(access.reference())).orElse(null);
    }

    private static void write(RegistryFriendlyByteBuf buffer, ApparatusHolder holder)
    {
        switch (holder)
        {
            case Carried carried ->
            {
                buffer.writeBoolean(false);
                buffer.writeVarInt(carried.slot());
            }
            case Worn worn ->
            {
                buffer.writeBoolean(true);
                TrinketSlotReference.STREAM_CODEC.encode(buffer, worn.slot());
            }
        }
    }

    private static ApparatusHolder read(RegistryFriendlyByteBuf buffer)
    {
        if (buffer.readBoolean())
            return new Worn(TrinketSlotReference.STREAM_CODEC.decode(buffer));
        return new Carried(buffer.readVarInt());
    }
}
