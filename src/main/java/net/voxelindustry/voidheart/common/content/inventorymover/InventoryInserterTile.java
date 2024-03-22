package net.voxelindustry.voidheart.common.content.inventorymover;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.voxelindustry.steamlayer.common.utils.ItemUtils;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.IntStream;

public class InventoryInserterTile extends BlockEntity
{
    public InventoryInserterTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.INVENTORY_INSERTER, pos, state);
    }

    private SingleStackInsertable tryGetInsertable(Direction direction)
    {
        var besideTile = getWorld().getBlockEntity(pos.offset(direction));
        if (besideTile instanceof SingleStackInsertable insertable)
            return insertable;
        return null;
    }

    private SingleStackExtractable tryGetExtractable(Direction direction)
    {
        var besideTile = getWorld().getBlockEntity(pos.offset(direction));
        if (besideTile instanceof SingleStackExtractable extractable)
            return extractable;
        return null;
    }

    private Inventory tryGetInventory(Direction direction)
    {
        var upsideTile = getWorld().getBlockEntity(pos.offset(direction));
        if (upsideTile instanceof Inventory inventory)
            return inventory;
        return null;
    }

    private ItemStack getExtractionCandidate(Collection<ItemStack> alreadyTriedStacks, Inventory inventory, Direction direction)
    {
        if (inventory instanceof SidedInventory sidedInventory)
        {
            var slots = sidedInventory.getAvailableSlots(direction);
            for (var slot : slots)
            {
                var candidate = inventory.getStack(slot);
                if (!candidate.isEmpty() && !ItemUtils.containsStack(alreadyTriedStacks, candidate))
                {
                    var copy = candidate.copyWithCount(1);
                    if (sidedInventory.canExtract(slot, copy, direction))
                        return copy;
                }
            }
            return ItemStack.EMPTY;
        }

        var slots = IntStream.range(0, inventory.size()).toArray();

        for (var slot : slots)
        {
            var candidate = inventory.getStack(slot);
            if (!candidate.isEmpty() && !ItemUtils.containsStack(alreadyTriedStacks, candidate))
                return candidate.copyWithCount(1);
        }

        return ItemStack.EMPTY;
    }

    private boolean tryInsert(Inventory inventory, ItemStack stack, Direction direction)
    {
        if (inventory instanceof SidedInventory sidedInventory)
        {
            var slots = sidedInventory.getAvailableSlots(direction);
            for (var slot : slots)
            {
                if (!sidedInventory.canInsert(slot, stack, direction))
                    continue;

                if (insert(inventory, stack, slot)) return true;
            }
            return false;
        }

        var slots = IntStream.range(0, inventory.size()).toArray();

        for (var slot : slots)
        {
            if (insert(inventory, stack, slot)) return true;
        }

        return false;
    }

    private boolean insert(Inventory inventory, ItemStack stack, int slot)
    {
        var candidate = inventory.getStack(slot);
        if (candidate.isEmpty())
        {
            inventory.setStack(slot, stack);
            return true;
        }

        if (ItemUtils.canMerge(candidate, stack))
        {
            ItemUtils.mergeStacks(candidate, stack, true);
            return true;
        }
        return false;
    }

    private void doExtract(Inventory inventory, ItemStack toExtract, Direction direction)
    {
        if (inventory instanceof SidedInventory sidedInventory)
        {
            var slots = sidedInventory.getAvailableSlots(direction);
            for (var slot : slots)
            {
                var candidate = inventory.getStack(slot);
                if (ItemStack.canCombine(candidate, toExtract))
                {
                    var copy = candidate.copyWithCount(1);
                    if (sidedInventory.canExtract(slot, copy, direction))
                    {
                        candidate.decrement(1);
                        return;
                    }
                }
            }
            return;
        }

        var slots = IntStream.range(0, inventory.size()).toArray();

        for (var slot : slots)
        {
            var candidate = inventory.getStack(slot);
            if (ItemStack.canCombine(candidate, toExtract))
            {
                candidate.decrement(1);
                return;
            }
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, InventoryInserterTile inserter)
    {
        if (world.isClient())
            return;

        if (world.getTime() % 20 != 0)
            return;

        var direction = state.get(Properties.FACING);

        var insertInventory = inserter.tryGetInventory(direction);
        var extractInventory = inserter.tryGetInventory(direction.getOpposite());

        if (insertInventory == null || extractInventory == null)
        {
            var insertable = inserter.tryGetInsertable(direction);
            if (extractInventory != null && insertable != null)
            {
                if (!insertable.getStack().isEmpty())
                    return;

                var extractionCandidate = inserter.getExtractionCandidate(Collections.emptyList(), extractInventory, direction);

                if (!extractionCandidate.isEmpty())
                {
                    insertable.setStack(extractionCandidate);
                    inserter.doExtract(extractInventory, extractionCandidate, direction);
                }
            }

            var extractable = inserter.tryGetExtractable(direction.getOpposite());
            if (insertInventory != null && extractable != null)
            {
                if (extractable.getStack().isEmpty())
                    return;

                var extractionCandidate = extractable.getStack().copyWithCount(1);

                if (!extractionCandidate.isEmpty())
                {
                    if (inserter.tryInsert(insertInventory, extractionCandidate.copy(), direction.getOpposite()))
                        extractable.setStack(ItemStack.EMPTY);
                }
            }
            return;
        }

        transferLoop(inserter, extractInventory, insertInventory, direction);
    }

    private static void transferLoop(InventoryInserterTile inserter, Inventory extractInventory, Inventory insertInventory, Direction direction)
    {
        var triedExtractionCandidates = new ArrayList<ItemStack>();

        while (true)
        {
            var extractionCandidate = inserter.getExtractionCandidate(triedExtractionCandidates, extractInventory, direction);

            if (extractionCandidate.isEmpty())
                break;

            if (inserter.tryInsert(insertInventory, extractionCandidate.copy(), direction.getOpposite()))
            {
                inserter.doExtract(extractInventory, extractionCandidate, direction);
                break;
            }
            else
                triedExtractionCandidates.add(extractionCandidate);
        }
    }
}
