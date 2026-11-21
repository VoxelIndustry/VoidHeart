package net.voxelindustry.voidheart.common.content.works;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Every owner's works, kept with the overworld's data: the queue of orders, the plan drawn for each, and the levels to keep in the hoard. Changes go
 * through {@link #edit}, which marks it to be saved.
 */
public class WorksState extends SavedData
{
    /**
     * @param keepJobs the order placed for each kept item, so a level is not ordered again while one is running
     */
    public record Owner(WorksQueue queue, Map<Integer, WorksGraph> graphs, Map<ItemVariant, Long> keeps, Map<ItemVariant, Integer> keepJobs)
    {
        private record Keep(ItemVariant item, long level, int job)
        {
            static final Codec<Keep> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ItemVariant.CODEC.fieldOf("item").forGetter(Keep::item),
                    Codec.LONG.fieldOf("level").forGetter(Keep::level),
                    Codec.INT.optionalFieldOf("job", 0).forGetter(Keep::job)
            ).apply(instance, Keep::new));
        }

        static final Codec<Owner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                WorksQueue.CODEC.optionalFieldOf("queue", new WorksQueue()).forGetter(Owner::queue),
                Codec.unboundedMap(Codec.STRING.xmap(Integer::parseInt, String::valueOf), WorksGraph.CODEC).optionalFieldOf("graphs", Map.of()).forGetter(Owner::graphs),
                Keep.CODEC.listOf().optionalFieldOf("keeps", List.of()).forGetter(Owner::keepList)
        ).apply(instance, Owner::of));

        static Owner empty()
        {
            return new Owner(new WorksQueue(), new HashMap<>(), new LinkedHashMap<>(), new HashMap<>());
        }

        private static Owner of(WorksQueue queue, Map<Integer, WorksGraph> graphs, List<Keep> keeps)
        {
            var owner = new Owner(queue, new HashMap<>(graphs), new LinkedHashMap<>(), new HashMap<>());
            for (var keep : keeps)
            {
                owner.keeps.put(keep.item(), keep.level());
                if (keep.job() != 0)
                    owner.keepJobs.put(keep.item(), keep.job());
            }
            return owner;
        }

        private List<Keep> keepList()
        {
            return keeps.entrySet().stream().map(entry -> new Keep(entry.getKey(), entry.getValue(), keepJobs.getOrDefault(entry.getKey(), 0))).toList();
        }

        /**
         * Forgets the graphs of orders that are done or cancelled.
         */
        void tidy()
        {
            var live = queue.jobs().stream().map(WorksQueue.Job::id).toList();
            graphs.keySet().retainAll(live);
            keepJobs.values().removeIf(job -> !live.contains(job));
        }
    }

    public static final Codec<WorksState> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Owner.CODEC)
            .fieldOf("owners")
            .codec()
            .xmap(WorksState::new, state -> state.owners);

    public static final SavedDataType<WorksState> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(MODID, "works"), WorksState::new, CODEC, null);

    private final Map<UUID, Owner> owners = new HashMap<>();

    public WorksState()
    {
    }

    private WorksState(Map<UUID, Owner> owners)
    {
        this.owners.putAll(owners);
    }

    public static WorksState get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Map<UUID, Owner> owners()
    {
        return owners;
    }

    public Owner owner(UUID owner)
    {
        return owners.computeIfAbsent(owner, id -> Owner.empty());
    }

    public WorksQueue of(UUID owner)
    {
        return owner(owner).queue();
    }

    /**
     * @return the queue, marked to be saved
     */
    public WorksQueue edit(UUID owner)
    {
        setDirty();
        var works = owner(owner);
        works.tidy();
        return works.queue();
    }
}
