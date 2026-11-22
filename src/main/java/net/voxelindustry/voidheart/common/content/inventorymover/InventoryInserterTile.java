package net.voxelindustry.voidheart.common.content.inventorymover;

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
        var besideTile = getLevel().getBlockEntity(worldPosition.relative(direction));
        if (besideTile instanceof SingleStackInsertable insertable)
            return insertable;
        return null;
    }

    private SingleStackExtractable tryGetExtractable(Direction direction)
    {
        var besideTile = getLevel().getBlockEntity(worldPosition.relative(direction));
        if (besideTile instanceof SingleStackExtractable extractable)
            return extractable;
        return null;
    }

    private Container tryGetInventory(Direction direction)
    {
        var upsideTile = getLevel().getBlockEntity(worldPosition.relative(direction));
        if (upsideTile instanceof Container inventory)
            return inventory;
        return null;
    }

    private ItemStack getExtractionCandidate(Collection<ItemStack> alreadyTriedStacks, Container inventory, Direction direction)
    {
        if (inventory instanceof WorldlyContainer sidedInventory)
        {
            var slots = sidedInventory.getSlotsForFace(direction);
            for (var slot : slots)
            {
                var candidate = inventory.getItem(slot);
                if (!candidate.isEmpty() && !ItemUtils.containsStack(alreadyTriedStacks, candidate))
                {
                    var copy = candidate.copyWithCount(1);
                    if (sidedInventory.canTakeItemThroughFace(slot, copy, direction))
                        return copy;
                }
            }
            return ItemStack.EMPTY;
        }

        var slots = IntStream.range(0, inventory.getContainerSize()).toArray();

        for (var slot : slots)
        {
            var candidate = inventory.getItem(slot);
            if (!candidate.isEmpty() && !ItemUtils.containsStack(alreadyTriedStacks, candidate))
                return candidate.copyWithCount(1);
        }

        return ItemStack.EMPTY;
    }

    private boolean tryInsert(Container inventory, ItemStack stack, Direction direction)
    {
        if (inventory instanceof WorldlyContainer sidedInventory)
        {
            var slots = sidedInventory.getSlotsForFace(direction);
            for (var slot : slots)
            {
                if (!sidedInventory.canPlaceItemThroughFace(slot, stack, direction))
                    continue;

                if (insert(inventory, stack, slot)) return true;
            }
            return false;
        }

        var slots = IntStream.range(0, inventory.getContainerSize()).toArray();

        for (var slot : slots)
        {
            if (insert(inventory, stack, slot)) return true;
        }

        return false;
    }

    private boolean insert(Container inventory, ItemStack stack, int slot)
    {
        var candidate = inventory.getItem(slot);
        if (candidate.isEmpty())
        {
            inventory.setItem(slot, stack);
            return true;
        }

        if (ItemUtils.canMerge(candidate, stack))
        {
            ItemUtils.mergeStacks(candidate, stack, true);
            return true;
        }
        return false;
    }

    private void doExtract(Container inventory, ItemStack toExtract, Direction direction)
    {
        if (inventory instanceof WorldlyContainer sidedInventory)
        {
            var slots = sidedInventory.getSlotsForFace(direction);
            for (var slot : slots)
            {
                var candidate = inventory.getItem(slot);
                if (ItemStack.isSameItemSameComponents(candidate, toExtract))
                {
                    var copy = candidate.copyWithCount(1);
                    if (sidedInventory.canTakeItemThroughFace(slot, copy, direction))
                    {
                        candidate.shrink(1);
                        return;
                    }
                }
            }
            return;
        }

        var slots = IntStream.range(0, inventory.getContainerSize()).toArray();

        for (var slot : slots)
        {
            var candidate = inventory.getItem(slot);
            if (ItemStack.isSameItemSameComponents(candidate, toExtract))
            {
                candidate.shrink(1);
                return;
            }
        }
    }

    public static void tick(Level world, BlockPos pos, BlockState state, InventoryInserterTile inserter)
    {
        if (world.isClientSide())
            return;

        if (world.getGameTime() % 20 != 0)
            return;

        transfer(world, pos, state, inserter);
    }

    /**
     * Moves what one tick of the inserter moves, whatever the tick; public for tests.
     */
    public static void transfer(Level world, BlockPos pos, BlockState state, InventoryInserterTile inserter)
    {
        var direction = state.getValue(BlockStateProperties.FACING);

        var insertInventory = inserter.tryGetInventory(direction);
        var extractInventory = inserter.tryGetInventory(direction.getOpposite());

        // A side that is neither an inventory nor a pillar, such as a Fold, is reached through the transfer API
        var insertsSomewhere = insertInventory != null || inserter.tryGetInsertable(direction) != null;
        var extractsSomewhere = extractInventory != null || inserter.tryGetExtractable(direction.getOpposite()) != null;
        if (!insertsSomewhere || !extractsSomewhere)
        {
            var from = ItemStorage.SIDED.find(world, pos.relative(direction.getOpposite()), direction);
            var to = ItemStorage.SIDED.find(world, pos.relative(direction), direction.getOpposite());
            if (from != null && to != null)
                StorageUtil.move(from, to, variant -> true, 1, null);
            return;
        }

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
                if (extractable.getStack().isEmpty() || !extractable.canExtract())
                    return;

                var extractionCandidate = extractable.getStack().copyWithCount(1);

                // A forge output can hold two items, so only the one moved is taken off
                if (inserter.tryInsert(insertInventory, extractionCandidate, direction.getOpposite()))
                {
                    var remaining = extractable.getStack().copy();
                    remaining.shrink(1);
                    extractable.setStack(remaining.isEmpty() ? ItemStack.EMPTY : remaining);
                    extractable.onExtracted();
                }
            }
            return;
        }

        transferLoop(inserter, extractInventory, insertInventory, direction);
    }

    private static void transferLoop(InventoryInserterTile inserter, Container extractInventory, Container insertInventory, Direction direction)
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
