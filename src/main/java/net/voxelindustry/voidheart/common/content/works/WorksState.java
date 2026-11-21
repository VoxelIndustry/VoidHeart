package net.voxelindustry.voidheart.common.content.works;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Every owner's queue of orders, kept with the overworld's data. Changes go through {@link #edit}, which marks it to be saved.
 */
public class WorksState extends SavedData
{
    public static final Codec<WorksState> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, WorksQueue.CODEC)
            .fieldOf("queues")
            .codec()
            .xmap(WorksState::new, state -> state.queues);

    public static final SavedDataType<WorksState> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(MODID, "works"), WorksState::new, CODEC, null);

    private final Map<UUID, WorksQueue> queues = new HashMap<>();

    public WorksState()
    {
    }

    private WorksState(Map<UUID, WorksQueue> queues)
    {
        this.queues.putAll(queues);
    }

    public static WorksState get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public WorksQueue of(UUID owner)
    {
        return queues.computeIfAbsent(owner, id -> new WorksQueue());
    }

    /**
     * @return the queue, marked to be saved
     */
    public WorksQueue edit(UUID owner)
    {
        setDirty();
        return of(owner);
    }
}
