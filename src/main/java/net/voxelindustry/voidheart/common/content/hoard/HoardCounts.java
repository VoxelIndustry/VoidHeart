package net.voxelindustry.voidheart.common.content.hoard;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a hoard holds: a count for each kind of thing, under one capacity counted in things, whatever their kind. Nothing is ever lost to the
 * capacity: a hoard over it, as when a coffer is broken, keeps all it holds and only refuses more.
 *
 * @param <K> a kind of thing, compared by equality
 */
public final class HoardCounts<K>
{
    private final Map<K, Long> counts = new LinkedHashMap<>();
    private long total;
    private long capacity;

    public long total()
    {
        return total;
    }

    public long capacity()
    {
        return capacity;
    }

    public void setCapacity(long capacity)
    {
        this.capacity = Math.max(0, capacity);
    }

    public long room()
    {
        return Math.max(0, capacity - total);
    }

    public boolean isOverCapacity()
    {
        return total > capacity;
    }

    public long count(K kind)
    {
        return counts.getOrDefault(kind, 0L);
    }

    /**
     * @return every kind held, in the order first put in
     */
    public Map<K, Long> view()
    {
        return Collections.unmodifiableMap(counts);
    }

    /**
     * @return how many were taken in, up to the room left
     */
    public long insert(K kind, long amount)
    {
        var taken = Math.min(Math.max(0, amount), room());
        if (taken > 0)
        {
            counts.merge(kind, taken, Long::sum);
            total += taken;
        }
        return taken;
    }

    /**
     * @return how many were taken out, up to how many are held
     */
    public long extract(K kind, long amount)
    {
        var held = count(kind);
        var taken = Math.min(Math.max(0, amount), held);
        if (taken > 0)
        {
            if (taken == held)
                counts.remove(kind);
            else
                counts.put(kind, held - taken);
            total -= taken;
        }
        return taken;
    }

    /**
     * Puts back what an aborted move had taken, past the capacity if needs be, since it was held before.
     */
    public void restore(K kind, long amount)
    {
        if (amount <= 0)
            return;
        counts.merge(kind, amount, Long::sum);
        total += amount;
    }

    /**
     * Loads what was saved, keeping every count even over the capacity.
     */
    public void load(Map<K, Long> saved, long capacity)
    {
        counts.clear();
        total = 0;
        saved.forEach((kind, count) ->
        {
            if (count > 0)
                restore(kind, count);
        });
        setCapacity(capacity);
    }
}
