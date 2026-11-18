package net.voxelindustry.voidheart.common.content.armor;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatusItem;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids.Placement;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.content.armor.VoidArmorLayout.*;

/**
 * The docked frame's three grids, four racks and apparatus socket beside the player's inventory. Modules go from the cursor into a grid and come back
 * onto it through menu buttons carrying the grid and the cell, so the server checks every edit against the frame it holds. Racks and the socket are
 * ordinary slots over the frame's own container.
 */
public class VoidArmorMenu extends AbstractContainerMenu
{
    private static final int PLACE             = 0;
    private static final int TAKE_TO_CURSOR    = 1;
    private static final int TAKE_TO_INVENTORY = 2;

    public static final int MAIN_SLOTS  = 27;
    public static final int ALL_SLOTS   = 36;
    public static final int FIRST_RACK  = ALL_SLOTS;
    public static final int SOCKET      = FIRST_RACK + ArmorBalance.RACKS;

    private static final Identifier SOCKET_ICON = Identifier.fromNamespaceAndPath(MODID, "container/slots/shielding_apparatus");

    @Getter
    private final BlockPos                    pos;
    private final Player                      player;
    private final @Nullable VoidArmorBaseTile base;

    public VoidArmorMenu(int containerId, Inventory inventory, BlockPos pos)
    {
        super(VoidArmorContent.VOID_ARMOR_MENU, containerId);
        this.pos = pos;
        this.player = inventory.player;
        this.base = inventory.player.level().getBlockEntity(pos) instanceof VoidArmorBaseTile tile ? tile : null;
        Container frameSlots = base != null ? base.slots() : new SimpleContainer(ArmorBalance.FRAME_SLOTS);

        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * SLOT, INVENTORY_Y + row * SLOT));
        for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column, INVENTORY_X + column * SLOT, INVENTORY_Y + 3 * SLOT + HOTBAR_GAP));

        for (int rack = 0; rack < ArmorBalance.RACKS; rack++)
            addSlot(new FrameSlot(frameSlots, rack, RACK_X + rack * SLOT, RACK_Y));
        addSlot(new FrameSlot(frameSlots, ArmorBalance.SOCKET_SLOT, socketX(), socketY()));
    }

    private static class FrameSlot extends Slot
    {
        FrameSlot(Container container, int index, int x, int y)
        {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack)
        {
            return VoidArmorBaseTile.mayHold(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize()
        {
            return 1;
        }

        @Override
        public @Nullable Identifier getNoItemIcon()
        {
            return getContainerSlot() == ArmorBalance.SOCKET_SLOT ? SOCKET_ICON : null;
        }
    }

    public ItemStack frame()
    {
        return base == null ? ItemStack.EMPTY : base.frame();
    }

    public ArmorGrids grids()
    {
        return base == null ? ArmorGrids.EMPTY : base.grids();
    }

    public long charge()
    {
        return base == null ? 0 : base.charge();
    }

    public static int placeButton(ArmorGridKind kind, int x, int y, int rotation)
    {
        return button(PLACE, kind, x, y, rotation);
    }

    public static int takeButton(ArmorGridKind kind, int x, int y, boolean toInventory)
    {
        return button(toInventory ? TAKE_TO_INVENTORY : TAKE_TO_CURSOR, kind, x, y, 0);
    }

    private static int button(int action, ArmorGridKind kind, int x, int y, int rotation)
    {
        return action | (rotation & 3) << 2 | (x & 15) << 4 | (y & 15) << 8 | kind.ordinal() << 12;
    }

    @Override
    public boolean clickMenuButton(Player player, int id)
    {
        int action = id & 3;
        int rotation = id >> 2 & 3;
        int x = id >> 4 & 15;
        int y = id >> 8 & 15;
        int grid = id >> 12;
        if (grid >= ArmorGridKind.values().length)
            return false;
        var kind = ArmorGridKind.values()[grid];
        boolean accepted = switch (action)
        {
            case PLACE -> place(player, kind, x, y, rotation);
            case TAKE_TO_CURSOR, TAKE_TO_INVENTORY -> take(player, kind, x, y, action == TAKE_TO_INVENTORY);
            default -> false;
        };
        // The client already made the edit it asked for. Refused here, nothing changed to sync, so it would keep it: send back the cursor, the slots and
        // the frame as the server holds them
        if (!accepted && player instanceof ServerPlayer && base != null)
        {
            sendAllDataToRemote();
            base.sync();
        }
        return accepted;
    }

    private boolean place(Player player, ArmorGridKind kind, int x, int y, int rotation)
    {
        var carried = getCarried();
        if (!(carried.getItem() instanceof ArmorModuleItem item) || !set(kind, item.module(), x, y, rotation))
            return false;
        carried.shrink(1);
        sound(player, true);
        return true;
    }

    private boolean set(ArmorGridKind kind, ArmorModule module, int x, int y, int rotation)
    {
        if (base == null || !base.hasFrame())
            return false;
        var grids = base.grids();
        if (!grids.canPlace(kind, module, x, y, rotation))
            return false;
        base.setGrids(grids.with(kind, new Placement(module, x, y, rotation)));
        return true;
    }

    /**
     * Takes back the piece covering the cell, onto the cursor when it is free or holds the same module, otherwise into the inventory.
     */
    private boolean take(Player player, ArmorGridKind kind, int x, int y, boolean toInventory)
    {
        if (base == null || !base.hasFrame())
            return false;
        var grids = base.grids();
        var placement = grids.at(kind, x, y);
        if (placement == null)
            return false;

        var returned = new ItemStack(VoidArmorContent.MODULES.get(placement.module()));
        var carried = getCarried();
        if (!toInventory && carried.isEmpty())
            setCarried(returned);
        else if (!toInventory && ItemStack.isSameItemSameComponents(carried, returned) && carried.getCount() < carried.getMaxStackSize())
            carried.grow(1);
        else if (!moveItemStackTo(returned, 0, ALL_SLOTS, false))
            return false;

        base.setGrids(grids.without(kind, placement));
        sound(player, false);
        return true;
    }

    /**
     * The grids a module is tried in when shift clicked: its own, or for a battery the core, then the limbs, then the helm.
     */
    private static List<ArmorGridKind> order(ArmorModule module)
    {
        if (module.grids().size() == ArmorGridKind.values().length)
            return List.of(ArmorGridKind.CORE, ArmorGridKind.LIMBS, ArmorGridKind.HELM);
        return List.copyOf(module.grids());
    }

    /**
     * The first spot a module fits, grid by grid and row by row, turning it if it must, or null when the frame has no room for it.
     */
    public @Nullable FirstFit firstFit(ArmorModule module)
    {
        var grids = grids();
        for (var kind : order(module))
            for (int y = 0; y < kind.height(); y++)
                for (int x = 0; x < kind.width(); x++)
                    for (int rotation = 0; rotation < 4; rotation++)
                        if (grids.canPlace(kind, module, x, y, rotation))
                            return new FirstFit(kind, x, y, rotation);
        return null;
    }

    public record FirstFit(ArmorGridKind kind, int x, int y, int rotation)
    {
    }

    private void sound(Player player, boolean placed)
    {
        if (!player.level().isClientSide())
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), placed ? SoundEvents.ARMOR_EQUIP_NETHERITE.value() : SoundEvents.CHAIN_HIT,
                    SoundSource.PLAYERS, 0.6F, placed ? 1.3F : 0.9F);
    }

    /**
     * A module shift clicked goes into the first spot it fits, a weapon into a free rack and an apparatus into the socket. Anything in the frame comes back
     * into the inventory, and the rest moves between the inventory and the hotbar.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        var slot = slots.get(index);
        if (!slot.hasItem())
            return ItemStack.EMPTY;
        var stack = slot.getItem();

        if (index < ALL_SLOTS && stack.getItem() instanceof ArmorModuleItem item)
        {
            var fit = firstFit(item.module());
            if (fit != null && set(fit.kind(), item.module(), fit.x(), fit.y(), fit.rotation()))
            {
                stack.shrink(1);
                slot.setChanged();
                sound(player, true);
                return ItemStack.EMPTY;
            }
            return ItemStack.EMPTY;
        }

        var original = stack.copy();
        boolean moved;
        if (index >= ALL_SLOTS)
            moved = moveItemStackTo(stack, 0, ALL_SLOTS, true);
        else if (stack.getItem() instanceof ShieldingApparatusItem)
            moved = moveItemStackTo(stack, SOCKET, SOCKET + 1, false);
        else if (VoidArmorBaseTile.mayHold(0, stack))
            moved = moveItemStackTo(stack, FIRST_RACK, SOCKET, false);
        else
            moved = index < MAIN_SLOTS ? moveItemStackTo(stack, MAIN_SLOTS, ALL_SLOTS, false) : moveItemStackTo(stack, 0, MAIN_SLOTS, false);
        if (!moved)
            return ItemStack.EMPTY;
        if (stack.isEmpty())
            slot.setByPlayer(ItemStack.EMPTY);
        else
            slot.setChanged();
        if (stack.getCount() == original.getCount())
            return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return base != null && !base.isRemoved() && base.hasFrame() && player.isAlive() && Container.stillValidBlockEntity(base, player);
    }
}
