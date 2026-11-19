package net.voxelindustry.voidheart.common.content.armor;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The worn frame's own hotbar. Stepping in sets the frame's racks in the first four hotbar slots, as real items, so every weapon works from them as it
 * would from the hand, and puts the wearer's own hotbar and off hand aside until they step out. The five slots after the racks stay empty: their keys switch
 * the frame's sights instead, and the selection never rests there.
 * <p>
 * While worn, nothing goes into or out of the hotbar or off hand but through the racks' own use: pickups go to the inventory, and drop, swap, pick block
 * and menu clicks on those slots do nothing. A thrown weapon comes back to the rack it left.
 */
public final class ArmorHotbar
{
    public static final int RACKS   = ArmorBalance.RACKS;
    public static final int OFFHAND = Inventory.SLOT_OFFHAND;

    /** The wearer's own hotbar, then their off hand, while the frame's racks take their place. */
    public static final AttachmentType<List<ItemStack>> STASH = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(MODID, "void_armor_stash"),
            builder -> builder.persistent(ItemStack.OPTIONAL_CODEC.listOf()));

    /** The hotbar slot the wearer had selected when they stepped in, given back once they are out. */
    public static final AttachmentType<Integer> SELECTED = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(MODID, "void_armor_selected"),
            builder -> builder.persistent(Codec.INT));

    /** The rack a thrown weapon left, so it comes back there. */
    public static final AttachmentType<Integer> THROWN_FROM = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(MODID, "void_armor_thrown_from"),
            builder -> builder.persistent(Codec.INT));

    private ArmorHotbar()
    {
    }

    /**
     * Whether a slot of the player's inventory is the frame's while they wear it: the hotbar and the off hand.
     */
    public static boolean locked(Player player, int slot)
    {
        return (Inventory.isHotbarSlot(slot) || slot == OFFHAND) && ArmorWear.wearing(player);
    }

    /**
     * The frame's sights in the order of their keys, after the racks'.
     */
    public static List<ArmorModule> capabilities(ItemStack frame)
    {
        var stats = VoidArmorItem.stats(frame);
        var capabilities = new ArrayList<ArmorModule>();
        if (stats.nightVision())
            capabilities.add(ArmorModule.NIGHT_VISION);
        if (stats.spectralLens())
            capabilities.add(ArmorModule.SPECTRAL_LENS);
        return capabilities;
    }

    /**
     * Puts the wearer's hotbar and off hand aside and sets the frame's racks in the first slots, selecting the first.
     *
     * @return the frame with its racks emptied, since they are in the hotbar now
     */
    public static ItemStack take(ServerPlayer player, ItemStack frame)
    {
        var inventory = player.getInventory();
        var stash = new ArrayList<ItemStack>();
        for (int i = 0; i < Inventory.getSelectionSize(); i++)
            stash.add(inventory.removeItemNoUpdate(i));
        stash.add(inventory.removeItemNoUpdate(OFFHAND));
        player.setAttached(STASH, stash);
        player.setAttached(SELECTED, inventory.getSelectedSlot());

        var slots = slots(frame);
        for (int i = 0; i < RACKS; i++)
        {
            inventory.setItem(i, slots.get(i));
            slots.set(i, ItemStack.EMPTY);
        }
        inventory.setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));

        var emptied = frame.copy();
        emptied.set(VoidArmorContent.ARMOR_SLOTS, ItemContainerContents.fromItems(slots));
        return emptied;
    }

    /**
     * Puts what the racks hold back on the frame and gives the wearer their own hotbar and off hand back. Anything else that found its way into those slots
     * goes to the inventory. Does nothing once done, so stepping out and dying can both call it.
     *
     * @return the frame with its racks filled again
     */
    public static ItemStack give(ServerPlayer player, ItemStack frame)
    {
        var stash = player.getAttached(STASH);
        if (stash == null)
            return frame;
        player.removeAttached(STASH);

        var inventory = player.getInventory();
        var slots = slots(frame);
        for (int i = 0; i < RACKS; i++)
            slots.set(i, inventory.removeItemNoUpdate(i));

        var strays = new ArrayList<ItemStack>();
        for (int i = RACKS; i < Inventory.getSelectionSize(); i++)
            strays.add(inventory.removeItemNoUpdate(i));
        strays.add(inventory.removeItemNoUpdate(OFFHAND));

        for (int i = 0; i < Inventory.getSelectionSize(); i++)
            inventory.setItem(i, i < stash.size() ? stash.get(i) : ItemStack.EMPTY);
        if (stash.size() > Inventory.getSelectionSize())
            inventory.setItem(OFFHAND, stash.get(Inventory.getSelectionSize()));
        for (var stray : strays)
            if (!stray.isEmpty())
                inventory.placeItemBackInInventory(stray, Prediction.SERVER_ONLY);

        var filled = frame.copy();
        filled.set(VoidArmorContent.ARMOR_SLOTS, ItemContainerContents.fromItems(slots));
        return filled;
    }

    /**
     * Selects the hotbar slot the player had before stepping in. Called once the frame is off them, since while it is on only its racks can be selected.
     */
    public static void restoreSelection(ServerPlayer player)
    {
        var selected = player.getAttached(SELECTED);
        if (selected == null || ArmorWear.wearing(player))
            return;
        player.removeAttached(SELECTED);
        player.getInventory().setSelectedSlot(selected);
        player.connection.send(new ClientboundSetHeldSlotPacket(selected));
    }

    private static NonNullList<ItemStack> slots(ItemStack frame)
    {
        var slots = NonNullList.withSize(ArmorBalance.FRAME_SLOTS, ItemStack.EMPTY);
        frame.getOrDefault(VoidArmorContent.ARMOR_SLOTS, ItemContainerContents.EMPTY).copyInto(slots);
        return slots;
    }

    /**
     * Switches the sight under one of the keys after the racks'.
     */
    public static void useCapability(ServerPlayer player, int index)
    {
        var worn = ArmorWear.worn(player);
        if (worn == null)
            return;
        var capabilities = capabilities(worn.frame());
        if (index >= 0 && index < capabilities.size())
            ArmorWear.toggle(player, capabilities.get(index));
    }

    // Thrown weapons

    /**
     * Marks a weapon a wearer just threw with the rack it left.
     */
    public static void thrown(Entity projectile, Player thrower)
    {
        if (ArmorWear.wearing(thrower))
            projectile.setAttached(THROWN_FROM, thrower.getInventory().getSelectedSlot());
    }

    /**
     * Puts a thrown weapon a wearer picks back up in the rack it left, or the first empty one.
     *
     * @return whether it went in a rack; when not, it goes where it would have
     */
    public static boolean pickUp(Entity projectile, Player player, ItemStack stack)
    {
        var rack = projectile.getAttached(THROWN_FROM);
        if (rack == null || stack.isEmpty() || !ArmorWear.wearing(player))
            return false;
        var inventory = player.getInventory();
        int slot = rack >= 0 && rack < RACKS && inventory.getItem(rack).isEmpty() ? rack : -1;
        for (int i = 0; slot < 0 && i < RACKS; i++)
            if (inventory.getItem(i).isEmpty())
                slot = i;
        if (slot < 0)
            return false;
        inventory.setItem(slot, stack.copyAndClear());
        return true;
    }
}
