package net.voxelindustry.voidheart.common.ley.anchor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What players and placement changed about Old Anchors: where each was built and which dormant ones were awakened.
 */
public class LeyAnchorState extends SavedData
{
    private record Placement(long node, int y)
    {
        static final Codec<Placement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("node").forGetter(Placement::node),
                Codec.INT.fieldOf("y").forGetter(Placement::y)
        ).apply(instance, Placement::new));
    }

    public static final Codec<LeyAnchorState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Placement.CODEC.listOf().fieldOf("placed").forGetter(state -> state.placedY.entrySet().stream().map(entry -> new Placement(entry.getKey(), entry.getValue())).toList()),
            Codec.LONG.listOf().fieldOf("awakened").forGetter(state -> List.copyOf(state.awakened))
    ).apply(instance, LeyAnchorState::new));

    public static final SavedDataType<LeyAnchorState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MODID, "ley_anchors"),
            LeyAnchorState::new,
            CODEC,
            null);

    private final Map<Long, Integer> placedY = new HashMap<>();
    private final Set<Long> awakened = new HashSet<>();

    public LeyAnchorState()
    {
    }

    private LeyAnchorState(List<Placement> placements, List<Long> awakened)
    {
        placements.forEach(placement -> placedY.put(placement.node(), placement.y()));
        this.awakened.addAll(awakened);
    }

    public OptionalInt placedY(long nodeId)
    {
        var y = placedY.get(nodeId);
        return y == null ? OptionalInt.empty() : OptionalInt.of(y);
    }

    public void markPlaced(long nodeId, int y)
    {
        placedY.put(nodeId, y);
        setDirty();
    }

    public boolean isAwakened(long nodeId)
    {
        return awakened.contains(nodeId);
    }

    public void awaken(long nodeId)
    {
        if (awakened.add(nodeId))
            setDirty();
    }

    public static LeyAnchorState get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
}
