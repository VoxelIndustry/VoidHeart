package net.voxelindustry.voidheart.common.content.hoard;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A pocket's essence reserve: one pool per aspect, given room by the storages standing in the pocket. Each storage is a vessel of one or more slots,
 * each slot attuned to an aspect or still free; a free slot takes the first aspect that needs its room. A vessel taken away takes its share of its
 * aspect's pool with it. Nothing is ever lost to the room: a pool over it keeps all it holds and only refuses more.
 *
 * @param <K> an aspect, compared by equality
 */
public final class PocketReserve<K>
{
    /**
     * One slot of a vessel: a storage, or one face of a matrix.
     */
    public record SlotId(long pos, int index)
    {
    }

    /**
     * @param aspect what the slot holds, null while it is free
     */
    public record Slot<K>(@Nullable K aspect, long capacity)
    {
    }

    /**
     * What a slot takes with it, or shows.
     */
    public record Share<K>(@Nullable K aspect, long amount)
    {
    }

    private final Map<SlotId, Slot<K>> slots   = new LinkedHashMap<>();
    private final Map<K, Long>         amounts = new LinkedHashMap<>();

    public long amountOf(K aspect)
    {
        return amounts.getOrDefault(aspect, 0L);
    }

    /**
     * @return the room of the slots attuned to an aspect
     */
    public long capacityOf(K aspect)
    {
        long capacity = 0;
        for (var slot : slots.values())
        {
            if (aspect.equals(slot.aspect()))
                capacity += slot.capacity();
        }
        return capacity;
    }

    public long freeCapacity()
    {
        long capacity = 0;
        for (var slot : slots.values())
        {
            if (slot.aspect() == null)
                capacity += slot.capacity();
        }
        return capacity;
    }

    public long room(K aspect)
    {
        return Math.max(0, capacityOf(aspect) + freeCapacity() - amountOf(aspect));
    }

    /**
     * @return every aspect held, in the order first poured in
     */
    public Map<K, Long> amounts()
    {
        return Collections.unmodifiableMap(amounts);
    }

    public Map<SlotId, Slot<K>> slots()
    {
        return Collections.unmodifiableMap(slots);
    }

    /**
     * @return how much was taken in, up to the room left, attuning free slots as the aspect needs them
     */
    public long insert(K aspect, long amount)
    {
        var taken = Math.min(Math.max(0, amount), room(aspect));
        if (taken <= 0)
            return 0;
        amounts.merge(aspect, taken, Long::sum);
        for (var entry : slots.entrySet())
        {
            if (amountOf(aspect) <= capacityOf(aspect))
                break;
            if (entry.getValue().aspect() == null)
                entry.setValue(new Slot<>(aspect, entry.getValue().capacity()));
        }
        return taken;
    }

    /**
     * @return how much was taken out, up to how much is held
     */
    public long extract(K aspect, long amount)
    {
        var held = amountOf(aspect);
        var taken = Math.min(Math.max(0, amount), held);
        if (taken <= 0)
            return 0;
        if (taken == held)
            amounts.remove(aspect);
        else
            amounts.put(aspect, held - taken);
        return taken;
    }

    /**
     * A vessel's slot joins the reserve, pouring in what it held.
     *
     * @param aspect what it holds, null for a slot never attuned
     */
    public void addSlot(SlotId id, @Nullable K aspect, long capacity, long amount)
    {
        slots.put(id, new Slot<>(aspect, Math.max(0, capacity)));
        if (aspect != null && amount > 0)
            amounts.merge(aspect, amount, Long::sum);
    }

    /**
     * A vessel's slot leaves, taking its share of its aspect's pool: as much of the pool as its room is of the aspect's room, all of what is left up
     * to its room if it is the last slot of that aspect.
     *
     * @return what it takes, nothing for a slot it does not know
     */
    public Share<K> removeSlot(SlotId id)
    {
        var slot = slots.get(id);
        if (slot == null)
            return new Share<>(null, 0);
        var aspect = slot.aspect();
        if (aspect == null)
        {
            slots.remove(id);
            return new Share<>(null, 0);
        }
        var held = amountOf(aspect);
        var capacity = capacityOf(aspect);
        long share = capacity <= slot.capacity()
                ? Math.min(slot.capacity(), held)
                : Math.min(slot.capacity(), held * slot.capacity() / capacity);
        extract(aspect, share);
        slots.remove(id);
        return new Share<>(aspect, share);
    }

    /**
     * @return what a slot shows: its aspect, and the fill of that aspect's pool on its own room
     */
    public Share<K> shown(SlotId id)
    {
        var slot = slots.get(id);
        if (slot == null || slot.aspect() == null)
            return new Share<>(slot == null ? null : slot.aspect(), 0);
        var capacity = capacityOf(slot.aspect());
        if (capacity <= 0)
            return new Share<>(slot.aspect(), 0);
        var fill = Math.min(1, amountOf(slot.aspect()) / (double) capacity);
        return new Share<>(slot.aspect(), Math.round(fill * slot.capacity()));
    }

    /**
     * Replaces everything, for loading.
     */
    public void load(Map<SlotId, Slot<K>> slots, Map<K, Long> amounts)
    {
        this.slots.clear();
        this.slots.putAll(slots);
        this.amounts.clear();
        amounts.forEach((aspect, amount) ->
        {
            if (amount > 0)
                this.amounts.put(aspect, amount);
        });
    }
}
