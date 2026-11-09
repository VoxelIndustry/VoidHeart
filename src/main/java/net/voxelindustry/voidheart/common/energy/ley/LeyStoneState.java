package net.voxelindustry.voidheart.common.energy.ley;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Every standing stone in a level, saved, so a spur still runs from its line to a base while the chunks between them are unloaded.
 */
public class LeyStoneState extends SavedData
{
    public static final Codec<LeyStoneState> CODEC = BlockPos.CODEC.listOf()
            .fieldOf("stones")
            .codec()
            .xmap(LeyStoneState::new, LeyStoneState::list);

    public static final SavedDataType<LeyStoneState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MODID, "ley_stones"),
            LeyStoneState::new,
            CODEC,
            null);

    private final Set<BlockPos> stones = new HashSet<>();

    public LeyStoneState()
    {
    }

    private LeyStoneState(List<BlockPos> stones)
    {
        stones.forEach(stone -> this.stones.add(stone.immutable()));
    }

    public static LeyStoneState get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean add(BlockPos stone)
    {
        boolean added = stones.add(stone.immutable());
        if (added)
            setDirty();
        return added;
    }

    public boolean remove(BlockPos stone)
    {
        boolean removed = stones.remove(stone);
        if (removed)
            setDirty();
        return removed;
    }

    public Set<BlockPos> stones()
    {
        return Collections.unmodifiableSet(stones);
    }

    private List<BlockPos> list()
    {
        var list = new ArrayList<>(stones);
        list.sort(null);
        return list;
    }
}
