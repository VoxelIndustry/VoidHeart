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
 * Links between a door of any dimension and a void door of the pocket dimension, both stored by their lower half.
 */
public class VoidDoorLinks extends SavedData
{
    private record Link(GlobalPos source, BlockPos voidDoor)
    {
        static final Codec<Link> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                GlobalPos.CODEC.fieldOf("source").forGetter(Link::source),
                BlockPos.CODEC.fieldOf("void_door").forGetter(Link::voidDoor)
        ).apply(instance, Link::new));
    }

    public static final Codec<VoidDoorLinks> CODEC = Link.CODEC.listOf()
            .fieldOf("links")
            .codec()
            .xmap(VoidDoorLinks::new, VoidDoorLinks::links);

    public static final SavedDataType<VoidDoorLinks> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MODID, "door_links"),
            VoidDoorLinks::new,
            CODEC,
            null);

    private final Map<BlockPos, GlobalPos> sourceByVoidDoor = new HashMap<>();
    private final Map<GlobalPos, BlockPos> voidDoorBySource = new HashMap<>();

    public VoidDoorLinks()
    {
    }

    private VoidDoorLinks(List<Link> links)
    {
        links.forEach(link -> put(link.source(), link.voidDoor()));
    }

    private List<Link> links()
    {
        return sourceByVoidDoor.entrySet().stream().map(entry -> new Link(entry.getValue(), entry.getKey())).toList();
    }

    private void put(GlobalPos source, BlockPos voidDoor)
    {
        sourceByVoidDoor.put(voidDoor, source);
        voidDoorBySource.put(source, voidDoor);
    }

    /**
     * Each door has a single link, linking either side again replaces its previous link.
     */
    public void link(GlobalPos source, BlockPos voidDoor)
    {
        unlinkVoidDoor(voidDoor);
        var previousVoidDoor = voidDoorBySource.remove(source);
        if (previousVoidDoor != null)
            sourceByVoidDoor.remove(previousVoidDoor);

        put(source, voidDoor);
        setDirty();
    }

    public void unlinkVoidDoor(BlockPos voidDoor)
    {
        var source = sourceByVoidDoor.remove(voidDoor);
        if (source == null)
            return;

        voidDoorBySource.remove(source);
        setDirty();
    }

    public Optional<GlobalPos> getSource(BlockPos voidDoor)
    {
        return Optional.ofNullable(sourceByVoidDoor.get(voidDoor));
    }

    public Optional<BlockPos> getVoidDoor(GlobalPos source)
    {
        return Optional.ofNullable(voidDoorBySource.get(source));
    }

    public boolean isEmpty()
    {
        return sourceByVoidDoor.isEmpty();
    }

    public static VoidDoorLinks get(MinecraftServer server)
    {
        return server.getLevel(VoidHeart.VOID_WORLD_KEY).getDataStorage().computeIfAbsent(TYPE);
    }
}
