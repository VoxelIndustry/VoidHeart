package net.voxelindustry.voidheart.common.energy.furnace;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;

/**
 * The furnace's input and output beside the player's inventory, with how far the item cooking has got, its pace, the Fiery held and its power.
 */
public class VoidFurnaceMenu extends AbstractContainerMenu
{
    public static final int INPUT_X     = 62;
    public static final int OUTPUT_X    = 134;
    public static final int SLOTS_Y     = 44;
    public static final int INVENTORY_X = 26;
    public static final int INVENTORY_Y = 108;
    public static final int HOTBAR_GAP  = 4;
    public static final int SLOT        = 18;

    private static final int FURNACE_SLOTS = 2;
    private static final int PLAYER_SLOTS  = 36;

    @Getter
    private final BlockPos      pos;
    private final Container     furnace;
    private final ContainerData data;
    private final Level         level;

    public VoidFurnaceMenu(int containerId, Inventory inventory, BlockPos pos)
    {
        super(VoidFurnaceContent.VOID_FURNACE_MENU, containerId);
        this.pos = pos;
        this.level = inventory.player.level();
        var tile = !level.isClientSide() && level.getBlockEntity(pos) instanceof VoidFurnaceTile found ? found : null;
        this.furnace = tile != null ? tile : new SimpleContainer(FURNACE_SLOTS);
        this.data = tile != null ? tile.data() : new SimpleContainerData(VoidFurnaceTile.DATA_COUNT);

        addSlot(new Slot(furnace, VoidFurnaceTile.INPUT, INPUT_X, SLOTS_Y)
        {
            @Override
            public boolean mayPlace(ItemStack stack)
            {
                return smeltable(stack);
            }
        });
        addSlot(new ResultSlot(inventory.player, furnace, VoidFurnaceTile.OUTPUT, OUTPUT_X, SLOTS_Y));

        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * SLOT, INVENTORY_Y + row * SLOT));
        for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column, INVENTORY_X + column * SLOT, INVENTORY_Y + 3 * SLOT + HOTBAR_GAP));

        addDataSlots(data);
    }

    private boolean smeltable(ItemStack stack)
    {
        return level.recipeAccess().propertySet(RecipePropertySet.FURNACE_INPUT).test(stack);
    }

    /**
     * Taking a result gives the player what a furnace would: the item's crafted triggers and the experience the furnace held.
     */
    private static class ResultSlot extends Slot
    {
        private final Player player;
        private       int    removeCount;

        ResultSlot(Player player, Container container, int slot, int x, int y)
        {
            super(container, slot, x, y);
            this.player = player;
        }

        @Override
        public boolean mayPlace(ItemStack stack)
        {
            return false;
        }

        @Override
        public ItemStack remove(int amount)
        {
            if (hasItem())
                removeCount += Math.min(amount, getItem().getCount());
            return super.remove(amount);
        }

        @Override
        public void onTake(Player player, ItemStack carried)
        {
            checkTakeAchievements(carried);
            super.onTake(player, carried);
        }

        @Override
        protected void onQuickCraft(ItemStack picked, int count)
        {
            removeCount += count;
            checkTakeAchievements(picked);
        }

        @Override
        protected void checkTakeAchievements(ItemStack carried)
        {
            carried.onCraftedBy(player, removeCount);
            if (player instanceof ServerPlayer serverPlayer && container instanceof VoidFurnaceTile tile)
                tile.awardExperience(serverPlayer);
            removeCount = 0;
        }
    }

    /**
     * @return from 0 when cold to 1 at full heat
     */
    public double heat()
    {
        return data.get(VoidFurnaceTile.DATA_HEAT) / 1000D;
    }

    /**
     * @return times a vanilla furnace's pace the item cooking runs at
     */
    public double pace()
    {
        return VoidFurnaceBalance.pace(heat(), fast());
    }

    public int fiery()
    {
        return data.get(VoidFurnaceTile.DATA_FIERY);
    }

    public MachinePower power()
    {
        return MachinePower.byOrdinal(data.get(VoidFurnaceTile.DATA_POWER));
    }

    public boolean fast()
    {
        return data.get(VoidFurnaceTile.DATA_STATE) == 2;
    }

    /**
     * @return whether an item cooks, false while what lies in the input can't be smelted or its result has no room
     */
    public boolean cooking()
    {
        return data.get(VoidFurnaceTile.DATA_STATE) != 0;
    }

    /**
     * @return how far the item cooking has got, from 0 to 1
     */
    public float progress()
    {
        return slots.getFirst().getItem().isEmpty() ? 0 : Math.clamp(data.get(VoidFurnaceTile.DATA_COOKED) / 1000F, 0, 1);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        var slot = slots.get(index);
        if (!slot.hasItem())
            return ItemStack.EMPTY;
        var stack = slot.getItem();
        var copy = stack.copy();
        if (index == VoidFurnaceTile.OUTPUT)
        {
            if (!moveItemStackTo(stack, FURNACE_SLOTS, FURNACE_SLOTS + PLAYER_SLOTS, true))
                return ItemStack.EMPTY;
            slot.onQuickCraft(stack, copy);
        }
        else if (index == VoidFurnaceTile.INPUT)
        {
            if (!moveItemStackTo(stack, FURNACE_SLOTS, FURNACE_SLOTS + PLAYER_SLOTS, false))
                return ItemStack.EMPTY;
        }
        else if (smeltable(stack))
        {
            if (!moveItemStackTo(stack, VoidFurnaceTile.INPUT, VoidFurnaceTile.INPUT + 1, false))
                return ItemStack.EMPTY;
        }
        else if (index < FURNACE_SLOTS + 27)
        {
            if (!moveItemStackTo(stack, FURNACE_SLOTS + 27, FURNACE_SLOTS + PLAYER_SLOTS, false))
                return ItemStack.EMPTY;
        }
        else if (!moveItemStackTo(stack, FURNACE_SLOTS, FURNACE_SLOTS + 27, false))
            return ItemStack.EMPTY;

        if (stack.isEmpty())
            slot.setByPlayer(ItemStack.EMPTY);
        else
            slot.setChanged();
        if (stack.getCount() == copy.getCount())
            return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return furnace.stillValid(player);
    }
}
