package net.voxelindustry.voidheart.common.content.beholder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Every beholder of a dimension with its owner, so tables find them without their chunks being searched.
 */
public class BeholderRegistry extends SavedData
{
    private record Entry(BlockPos pos, Optional<UUID> owner)
    {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Entry::owner)
        ).apply(instance, Entry::new));
    }

    public static final Codec<BeholderRegistry> CODEC = Entry.CODEC.listOf()
            .fieldOf("beholders")
            .codec()
            .xmap(BeholderRegistry::new, BeholderRegistry::entries);

    public static final SavedDataType<BeholderRegistry> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MODID, "beholders"),
            BeholderRegistry::new,
            CODEC,
            null);

    private final Map<BlockPos, Optional<UUID>> owners = new HashMap<>();

    public BeholderRegistry()
    {
    }

    private BeholderRegistry(List<Entry> entries)
    {
        entries.forEach(entry -> owners.put(entry.pos(), entry.owner()));
    }

    private List<Entry> entries()
    {
        return owners.entrySet().stream().map(entry -> new Entry(entry.getKey(), entry.getValue())).toList();
    }

    public void put(BlockPos pos, Optional<UUID> owner)
    {
        if (!owner.equals(owners.put(pos.immutable(), owner)))
            setDirty();
    }

    public void remove(BlockPos pos)
    {
        if (owners.remove(pos) != null)
            setDirty();
    }

    public List<BlockPos> ownedBy(Set<Optional<UUID>> wanted)
    {
        var found = new ArrayList<BlockPos>();
        owners.forEach((pos, owner) ->
        {
            if (wanted.contains(owner))
                found.add(pos);
        });
        return found;
    }

    /**
     * Whether another beholder stands in the same chunk, which then has to stay loaded.
     */
    public boolean anyInChunk(int chunkX, int chunkZ)
    {
        return owners.keySet().stream().anyMatch(pos -> SectionPos.blockToSectionCoord(pos.getX()) == chunkX && SectionPos.blockToSectionCoord(pos.getZ()) == chunkZ);
    }

    public static BeholderRegistry get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
}
