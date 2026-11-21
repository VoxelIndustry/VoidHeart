package net.voxelindustry.voidheart.common.content.hoard;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Every pocket's hoard, by the UUID of the pocket's owner. Kept with the overworld's data rather than in blocks, so a Fold anywhere reaches it while
 * the pocket itself stays unloaded.
 */
public class PocketHoard extends SavedData
{
    private record Item(ItemVariant variant, long count)
    {
        static final Codec<Item> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemVariant.CODEC.fieldOf("variant").forGetter(Item::variant),
                Codec.LONG.fieldOf("count").forGetter(Item::count)
        ).apply(instance, Item::new));
    }

    private record Coffer(BlockPos pos, long capacity)
    {
        static final Codec<Coffer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Coffer::pos),
                Codec.LONG.fieldOf("capacity").forGetter(Coffer::capacity)
        ).apply(instance, Coffer::new));
    }

    private record Vessel(long pos, int index, Optional<EssenceType> aspect, long capacity)
    {
        static final Codec<Vessel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("pos").forGetter(Vessel::pos),
                Codec.INT.optionalFieldOf("index", 0).forGetter(Vessel::index),
                EssenceRegistry.ASPECT_CODEC.optionalFieldOf("aspect").forGetter(Vessel::aspect),
                Codec.LONG.fieldOf("capacity").forGetter(Vessel::capacity)
        ).apply(instance, Vessel::new));
    }

    private record Essence(EssenceType aspect, long amount)
    {
        static final Codec<Essence> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EssenceRegistry.ASPECT_CODEC.fieldOf("aspect").forGetter(Essence::aspect),
                Codec.LONG.fieldOf("amount").forGetter(Essence::amount)
        ).apply(instance, Essence::new));
    }

    private record Saved(List<Item> items, List<Coffer> coffers, List<Vessel> vessels, List<Essence> essence)
    {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Item.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Saved::items),
                Coffer.CODEC.listOf().optionalFieldOf("coffers", List.of()).forGetter(Saved::coffers),
                Vessel.CODEC.listOf().optionalFieldOf("vessels", List.of()).forGetter(Saved::vessels),
                Essence.CODEC.listOf().optionalFieldOf("essence", List.of()).forGetter(Saved::essence)
        ).apply(instance, Saved::new));
    }

    public static final Codec<PocketHoard> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Saved.CODEC)
            .fieldOf("hoards")
            .codec()
            .xmap(PocketHoard::new, PocketHoard::save);

    public static final SavedDataType<PocketHoard> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MODID, "pocket_hoard"),
            PocketHoard::new,
            CODEC,
            null);

    private final Map<UUID, Hoard> hoards = new HashMap<>();

    public PocketHoard()
    {
    }

    private PocketHoard(Map<UUID, Saved> saved)
    {
        saved.forEach((owner, data) ->
        {
            var hoard = of(owner);
            data.coffers().forEach(coffer -> hoard.coffers.put(coffer.pos(), coffer.capacity()));
            var items = new LinkedHashMap<ItemVariant, Long>();
            data.items().forEach(item -> items.merge(item.variant(), item.count(), Long::sum));
            hoard.counts.load(items, hoard.cofferCapacity());

            var vessels = new LinkedHashMap<PocketReserve.SlotId, PocketReserve.Slot<EssenceType>>();
            data.vessels().forEach(vessel -> vessels.put(new PocketReserve.SlotId(vessel.pos(), vessel.index()),
                    new PocketReserve.Slot<>(vessel.aspect().orElse(null), vessel.capacity())));
            var essence = new LinkedHashMap<EssenceType, Long>();
            data.essence().forEach(held -> essence.merge(held.aspect(), held.amount(), Long::sum));
            hoard.reserve.load(vessels, essence);
        });
    }

    private Map<UUID, Saved> save()
    {
        var saved = new HashMap<UUID, Saved>();
        hoards.forEach((owner, hoard) ->
        {
            var items = hoard.counts.view().entrySet().stream().map(entry -> new Item(entry.getKey(), entry.getValue())).toList();
            var coffers = hoard.coffers.entrySet().stream().map(entry -> new Coffer(entry.getKey(), entry.getValue())).toList();
            var vessels = hoard.reserve.slots().entrySet().stream().map(entry -> new Vessel(entry.getKey().pos(), entry.getKey().index(),
                    Optional.ofNullable(entry.getValue().aspect()), entry.getValue().capacity())).toList();
            var essence = hoard.reserve.amounts().entrySet().stream().map(entry -> new Essence(entry.getKey(), entry.getValue())).toList();
            saved.put(owner, new Saved(items, coffers, vessels, essence));
        });
        return saved;
    }

    public static PocketHoard get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Hoard of(UUID owner)
    {
        return hoards.computeIfAbsent(owner, id -> new Hoard());
    }

    /**
     * One pocket's hoard and its essence reserve. Each change counts up its version, so a screen showing it knows when to send it again.
     */
    public class Hoard
    {
        private final HoardCounts<ItemVariant> counts = new HoardCounts<>();
        private final Map<BlockPos, Long> coffers = new HashMap<>();
        private final PocketReserve<EssenceType> reserve = new PocketReserve<>();
        private long version;

        private long cofferCapacity()
        {
            return coffers.values().stream().mapToLong(Long::longValue).sum();
        }

        private void changed()
        {
            version++;
            setDirty();
        }

        public long version()
        {
            return version;
        }

        public long count(ItemVariant variant)
        {
            return counts.count(variant);
        }

        public long total()
        {
            return counts.total();
        }

        public long capacity()
        {
            return counts.capacity();
        }

        public long room()
        {
            return counts.room();
        }

        public Map<ItemVariant, Long> view()
        {
            return counts.view();
        }

        public long insert(ItemVariant variant, long amount)
        {
            if (variant.isBlank())
                return 0;
            var taken = counts.insert(variant, amount);
            if (taken > 0)
                changed();
            return taken;
        }

        public long extract(ItemVariant variant, long amount)
        {
            var taken = counts.extract(variant, amount);
            if (taken > 0)
                changed();
            return taken;
        }

        /**
         * Puts back what an aborted move had taken out, even past the capacity.
         */
        void restore(ItemVariant variant, long amount)
        {
            counts.restore(variant, amount);
            changed();
        }

        public int cofferCount()
        {
            return coffers.size();
        }

        /**
         * @param capacity what the coffer adds, or 0 to forget it
         */
        public void setCoffer(BlockPos pos, long capacity)
        {
            if (capacity <= 0)
                coffers.remove(pos.immutable());
            else
                coffers.put(pos.immutable(), capacity);
            counts.setCapacity(cofferCapacity());
            changed();
        }

        /**
         * The essence reserve, read only: change it through the methods below so it is saved.
         */
        public PocketReserve<EssenceType> reserve()
        {
            return reserve;
        }

        public long insertEssence(EssenceType aspect, long amount)
        {
            var taken = reserve.insert(aspect, amount);
            if (taken > 0)
                changed();
            return taken;
        }

        public long extractEssence(EssenceType aspect, long amount)
        {
            var taken = reserve.extract(aspect, amount);
            if (taken > 0)
                changed();
            return taken;
        }

        public void addVessel(PocketReserve.SlotId id, EssenceType aspect, long capacity, long amount)
        {
            reserve.addSlot(id, aspect == EssenceRegistry.EMPTY ? null : aspect, capacity, amount);
            changed();
        }

        public PocketReserve.Share<EssenceType> removeVessel(PocketReserve.SlotId id)
        {
            var share = reserve.removeSlot(id);
            changed();
            return share;
        }

        /**
         * Empties the hoard, for review commands.
         */
        public void clear()
        {
            counts.load(Map.of(), counts.capacity());
            changed();
        }
    }
}
