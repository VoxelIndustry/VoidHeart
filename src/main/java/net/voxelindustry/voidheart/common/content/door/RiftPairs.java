package net.voxelindustry.voidheart.common.content.door;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.voxelindustry.voidheart.VoidHeart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Open rifts: each pocket void door a Rift Key opened, with the ephemeral entry standing for it in the world. A door has at most one entry at a time, both stored by their lower half.
 */
public class RiftPairs extends SavedData
{
    public record Pair(BlockPos door, GlobalPos entry)
    {
        static final Codec<Pair> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("door").forGetter(Pair::door),
                GlobalPos.CODEC.fieldOf("entry").forGetter(Pair::entry)
        ).apply(instance, Pair::new));
    }

    public static final Codec<RiftPairs> CODEC = Pair.CODEC.listOf()
            .fieldOf("pairs")
            .codec()
            .xmap(RiftPairs::new, RiftPairs::pairs);

    public static final SavedDataType<RiftPairs> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MODID, "rift_pairs"),
            RiftPairs::new,
            CODEC,
            null);

    private final Map<BlockPos, GlobalPos> entryByDoor = new HashMap<>();
    private final Map<GlobalPos, BlockPos> doorByEntry = new HashMap<>();

    public RiftPairs()
    {
    }

    private RiftPairs(List<Pair> pairs)
    {
        pairs.forEach(pair -> put(pair.door(), pair.entry()));
    }

    public List<Pair> pairs()
    {
        return entryByDoor.entrySet().stream().map(entry -> new Pair(entry.getKey(), entry.getValue())).toList();
    }

    private void put(BlockPos door, GlobalPos entry)
    {
        entryByDoor.put(door, entry);
        doorByEntry.put(entry, door);
    }

    /**
     * @return the entry the door had before, which the caller must collapse
     */
    public Optional<GlobalPos> open(BlockPos door, GlobalPos entry)
    {
        var previous = close(door);
        var previousDoor = doorByEntry.remove(entry);
        if (previousDoor != null)
            entryByDoor.remove(previousDoor);

        put(door.immutable(), entry);
        setDirty();
        return previous;
    }

    /**
     * @return the entry the door had, which the caller must collapse
     */
    public Optional<GlobalPos> close(BlockPos door)
    {
        var entry = entryByDoor.remove(door);
        if (entry == null)
            return Optional.empty();

        doorByEntry.remove(entry);
        setDirty();
        return Optional.of(entry);
    }

    public Optional<GlobalPos> entryOf(BlockPos door)
    {
        return Optional.ofNullable(entryByDoor.get(door));
    }

    public Optional<BlockPos> doorOf(GlobalPos entry)
    {
        return Optional.ofNullable(doorByEntry.get(entry));
    }

    public boolean isEmpty()
    {
        return entryByDoor.isEmpty();
    }

    public static RiftPairs get(MinecraftServer server)
    {
        return server.getLevel(VoidHeart.VOID_WORLD_KEY).getDataStorage().computeIfAbsent(TYPE);
    }
}
