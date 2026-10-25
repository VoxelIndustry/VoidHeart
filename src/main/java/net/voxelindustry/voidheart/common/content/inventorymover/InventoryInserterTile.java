package net.voxelindustry.voidheart.common.content.inventorymover;

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
                if (ItemStack.isSameItemSameTags(candidate, toExtract))
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
            if (ItemStack.isSameItemSameTags(candidate, toExtract))
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

        var direction = state.getValue(BlockStateProperties.FACING);

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
