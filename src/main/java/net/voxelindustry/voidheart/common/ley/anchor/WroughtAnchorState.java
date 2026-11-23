package net.voxelindustry.voidheart.common.ley.anchor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Every wrought anchor standing in a level, so travel reaches those whose chunks aren't loaded.
 */
public class WroughtAnchorState extends SavedData
{
    public static final Codec<WroughtAnchorState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            WroughtAnchor.CODEC.listOf().fieldOf("anchors").forGetter(state -> List.copyOf(state.anchors.values())),
            Codec.LONG.fieldOf("issued").forGetter(state -> state.issued)
    ).apply(instance, WroughtAnchorState::new));

    public static final SavedDataType<WroughtAnchorState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MODID, "wrought_anchors"),
            WroughtAnchorState::new,
            CODEC,
            null);

    private final Map<Long, WroughtAnchor> anchors = new LinkedHashMap<>();
    private long issued;

    public WroughtAnchorState()
    {
    }

    WroughtAnchorState(List<WroughtAnchor> anchors, long issued)
    {
        // A bad saved id must never meet an Old Anchor's node id or the next one issued
        anchors.stream().filter(anchor -> WroughtAnchor.isWrought(anchor.id())).forEach(anchor -> this.anchors.put(anchor.id(), anchor));
        this.issued = Math.max(issued, this.anchors.keySet().stream().mapToLong(id -> -id).max().orElse(0));
    }

    public WroughtAnchor add(String name, int x, int y, int z)
    {
        var anchor = new WroughtAnchor(-++issued, name, x, y, z);
        anchors.put(anchor.id(), anchor);
        setDirty();
        return anchor;
    }

    public boolean remove(long id)
    {
        if (anchors.remove(id) == null)
            return false;
        setDirty();
        return true;
    }

    /**
     * Replaces an anchor still standing, for a new name.
     */
    public void update(WroughtAnchor anchor)
    {
        if (anchors.replace(anchor.id(), anchor) != null)
            setDirty();
    }

    public Optional<WroughtAnchor> get(long id)
    {
        return Optional.ofNullable(anchors.get(id));
    }

    public Collection<WroughtAnchor> all()
    {
        return Collections.unmodifiableCollection(anchors.values());
    }

    public static WroughtAnchorState get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
}
