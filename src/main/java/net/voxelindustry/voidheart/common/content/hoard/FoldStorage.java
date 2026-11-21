package net.voxelindustry.voidheart.common.content.hoard;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A Fold seen by hoppers, pipes and inserters: it takes anything into its owner's hoard, and gives back only the item it was shown. Moves go into the
 * hoard as they happen and are undone if their transaction aborts. The void energy they cost is drawn once the outermost transaction commits, so a
 * simulated move costs nothing.
 */
final class FoldStorage extends SnapshotParticipant<Integer> implements Storage<ItemVariant>
{
    private record Move(ItemVariant variant, long amount)
    {
    }

    /**
     * Folds with moves still open. What they will pay counts against every Fold's field until their transaction ends, so two Folds moving items in
     * one transaction can't both promise the same energy.
     */
    private static final java.util.Set<FoldStorage> OPEN = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private final FoldTile fold;
    /** Moves made in the transactions still open, in order, negative when taken out. */
    private final List<Move> journal = new ArrayList<>();

    FoldStorage(FoldTile fold)
    {
        this.fold = fold;
    }

    private long moved()
    {
        return journal.stream().mapToLong(move -> Math.abs(move.amount())).sum();
    }

    private static long pending()
    {
        return OPEN.stream().mapToLong(FoldStorage::moved).sum();
    }

    /**
     * @return how many more items the energy around the Fold pays for this tick, less what every Fold has moved in transactions still open
     */
    private long affordable(long wanted)
    {
        return Math.min(wanted, Math.max(0, fold.affordableItems() - pending()));
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction)
    {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        var hoard = fold.hoard();
        if (hoard == null)
            return 0;

        var amount = Math.min(affordable(maxAmount), hoard.room());
        if (amount <= 0)
            return 0;

        updateSnapshots(transaction);
        var taken = hoard.insert(resource, amount);
        if (taken > 0)
        {
            journal.add(new Move(resource, taken));
            OPEN.add(this);
        }
        return taken;
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction)
    {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        var hoard = fold.hoard();
        if (hoard == null || !resource.equals(fold.getAttuned()))
            return 0;

        var amount = affordable(maxAmount);
        if (amount <= 0)
            return 0;

        updateSnapshots(transaction);
        var taken = hoard.extract(resource, amount);
        if (taken > 0)
        {
            journal.add(new Move(resource, -taken));
            OPEN.add(this);
        }
        return taken;
    }

    @Override
    public boolean supportsExtraction()
    {
        return fold.getAttuned() != null;
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator()
    {
        var attuned = fold.getAttuned();
        if (attuned == null || fold.hoard() == null)
            return Collections.emptyIterator();
        return List.<StorageView<ItemVariant>>of(new AttunedView(attuned)).iterator();
    }

    @Override
    protected Integer createSnapshot()
    {
        return journal.size();
    }

    @Override
    protected void readSnapshot(Integer size)
    {
        var hoard = fold.hoard();
        while (journal.size() > size)
        {
            var move = journal.removeLast();
            if (hoard == null)
                continue;
            if (move.amount() > 0)
                hoard.extract(move.variant(), move.amount());
            else
                hoard.restore(move.variant(), -move.amount());
        }
        if (journal.isEmpty())
            OPEN.remove(this);
    }

    @Override
    protected void onFinalCommit()
    {
        fold.pay(moved());
        journal.clear();
        OPEN.remove(this);
    }

    /**
     * The one item an attuned Fold gives back, as much of it as the hoard holds.
     */
    private final class AttunedView implements StorageView<ItemVariant>
    {
        private final ItemVariant variant;

        private AttunedView(ItemVariant variant)
        {
            this.variant = variant;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction)
        {
            return FoldStorage.this.extract(resource, maxAmount, transaction);
        }

        @Override
        public boolean isResourceBlank()
        {
            return getAmount() == 0;
        }

        @Override
        public ItemVariant getResource()
        {
            return variant;
        }

        @Override
        public long getAmount()
        {
            var hoard = fold.hoard();
            return hoard == null ? 0 : hoard.count(variant);
        }

        @Override
        public long getCapacity()
        {
            var hoard = fold.hoard();
            return hoard == null ? 0 : hoard.count(variant) + hoard.room();
        }
    }
}
