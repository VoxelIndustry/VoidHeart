package net.voxelindustry.voidheart.common.content.apparatus;

import lombok.Getter;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusGrid.Placement;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartMenus;
import org.jspecify.annotations.Nullable;

/**
 * The grid of an apparatus beside the player's inventory. Components go from the cursor into the grid and come back onto it, through menu buttons that carry
 * the cell, so the server checks every edit against the stack it holds. The slot the apparatus lies in is locked while its grid is open.
 */
public class ApparatusMenu extends AbstractContainerMenu
{
    public static final int INVENTORY_X = 44;
    public static final int INVENTORY_Y = 144;
    private static final int HOTBAR_GAP = 4;

    private static final int PLACE             = 0;
    private static final int TAKE_TO_CURSOR    = 1;
    private static final int TAKE_TO_INVENTORY = 2;

    private static final int MAIN_SLOTS = 27;
    private static final int ALL_SLOTS  = 36;

    @Getter
    private final ApparatusHolder holder;
    private final Player          player;

    public ApparatusMenu(int containerId, Inventory inventory, ApparatusHolder holder)
    {
        super(VoidHeartMenus.APPARATUS, containerId);
        this.holder = holder;
        this.player = inventory.player;

        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                addSlot(slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
        for (int column = 0; column < 9; column++)
            addSlot(slot(inventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 3 * 18 + HOTBAR_GAP));
    }

    private Slot slot(Inventory inventory, int index, int x, int y)
    {
        if (index != holder.inventorySlot())
            return new Slot(inventory, index, x, y);
        return new Slot(inventory, index, x, y)
        {
            @Override
            public boolean mayPickup(Player player)
            {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack)
            {
                return false;
            }
        };
    }

    public ItemStack apparatus()
    {
        return holder.get(player);
    }

    public int size()
    {
        return apparatus().getItem() instanceof ShieldingApparatusItem item ? item.tier().size() : 0;
    }

    public ApparatusGrid grid()
    {
        return ShieldingApparatus.grid(apparatus());
    }

    public static int placeButton(int x, int y, int rotation)
    {
        return button(PLACE, x, y, rotation);
    }

    public static int takeButton(int x, int y, boolean toInventory)
    {
        return button(toInventory ? TAKE_TO_INVENTORY : TAKE_TO_CURSOR, x, y, 0);
    }

    private static int button(int action, int x, int y, int rotation)
    {
        return action | (rotation & 3) << 2 | (x & 15) << 4 | (y & 15) << 8;
    }

    @Override
    public boolean clickMenuButton(Player player, int id)
    {
        int action = id & 3;
        int rotation = id >> 2 & 3;
        int x = id >> 4 & 15;
        int y = id >> 8 & 15;
        return switch (action)
        {
            case PLACE -> place(player, x, y, rotation);
            case TAKE_TO_CURSOR, TAKE_TO_INVENTORY -> take(player, x, y, action == TAKE_TO_INVENTORY);
            default -> false;
        };
    }

    /**
     * Sets one of the components on the cursor with its top left corner on the cell.
     */
    private boolean place(Player player, int x, int y, int rotation)
    {
        var carried = getCarried();
        if (!(carried.getItem() instanceof ApparatusComponentItem item) || !set(item.component(), x, y, rotation))
            return false;
        carried.shrink(1);
        sound(player, true);
        return true;
    }

    private boolean set(ApparatusComponent component, int x, int y, int rotation)
    {
        var apparatus = apparatus();
        int size = size();
        var grid = ShieldingApparatus.grid(apparatus);
        if (size == 0 || !grid.canPlace(size, component, x, y, rotation))
            return false;
        apparatus.set(VoidHeartDataComponents.APPARATUS_GRID, grid.with(new Placement(component, x, y, rotation)));
        return true;
    }

    /**
     * Takes back the piece covering the cell, onto the cursor when it is free or holds the same component, otherwise into the inventory.
     */
    private boolean take(Player player, int x, int y, boolean toInventory)
    {
        var apparatus = apparatus();
        if (size() == 0)
            return false;
        var grid = ShieldingApparatus.grid(apparatus);
        var placement = grid.at(x, y);
        if (placement == null)
            return false;

        var returned = new ItemStack(VoidHeartItems.APPARATUS_COMPONENTS.get(placement.component()));
        var carried = getCarried();
        if (!toInventory && carried.isEmpty())
            setCarried(returned);
        else if (!toInventory && ItemStack.isSameItemSameComponents(carried, returned) && carried.getCount() < carried.getMaxStackSize())
            carried.grow(1);
        else if (!moveItemStackTo(returned, 0, ALL_SLOTS, false))
            return false;

        apparatus.set(VoidHeartDataComponents.APPARATUS_GRID, grid.without(placement));
        sound(player, false);
        return true;
    }

    /**
     * The first spot a component fits, row by row and turning it if it must, or null when the grid has no room for it.
     */
    public @Nullable Placement firstFit(ApparatusComponent component)
    {
        int size = size();
        var grid = grid();
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                for (int rotation = 0; rotation < 4; rotation++)
                    if (grid.canPlace(size, component, x, y, rotation))
                        return new Placement(component, x, y, rotation);
        return null;
    }

    private void sound(Player player, boolean placed)
    {
        if (!player.level().isClientSide())
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), placed ? SoundEvents.AMETHYST_BLOCK_PLACE : SoundEvents.AMETHYST_BLOCK_HIT,
                    SoundSource.PLAYERS, 0.6F, placed ? 1.2F : 0.8F);
    }

    /**
     * A component shift clicked goes into the first spot it fits, one at a time. Anything else moves between the inventory and the hotbar.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        var slot = slots.get(index);
        if (!slot.hasItem())
            return ItemStack.EMPTY;
        var stack = slot.getItem();

        if (stack.getItem() instanceof ApparatusComponentItem item)
        {
            var fit = firstFit(item.component());
            if (fit != null && set(item.component(), fit.x(), fit.y(), fit.rotation()))
            {
                stack.shrink(1);
                slot.setChanged();
                sound(player, true);
                return ItemStack.EMPTY;
            }
        }

        var original = stack.copy();
        boolean moved = index < MAIN_SLOTS ? moveItemStackTo(stack, MAIN_SLOTS, ALL_SLOTS, false) : moveItemStackTo(stack, 0, MAIN_SLOTS, false);
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

    /**
     * Keeps the number keys and the offhand key from swapping the apparatus out of the slot it lies in.
     */
    @Override
    public void clicked(int slotIndex, int button, ContainerInput input, Player player)
    {
        if (input == ContainerInput.SWAP && holder.inventorySlot() >= 0 && button == holder.inventorySlot())
            return;
        super.clicked(slotIndex, button, input, player);
    }

    @Override
    public boolean stillValid(Player player)
    {
        return player.isAlive() && apparatus().getItem() instanceof ShieldingApparatusItem;
    }
}
