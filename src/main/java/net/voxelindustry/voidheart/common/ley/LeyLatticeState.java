package net.voxelindustry.voidheart.common.ley;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Regions frozen the first time they were computed, so later rule or structure changes never reroute explored land.
 */
public class LeyLatticeState extends SavedData implements LeyLattice.RegionStore
{
    public static final Codec<LeyLatticeState> CODEC = LeyRegion.CODEC.listOf()
            .fieldOf("regions")
            .codec()
            .xmap(LeyLatticeState::new, state -> List.copyOf(state.regionsByKey.values()));

    public static final SavedDataType<LeyLatticeState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MODID, "ley_lattice"),
            LeyLatticeState::new,
            CODEC,
            null);

    private final Map<Long, LeyRegion> regionsByKey = new HashMap<>();

    public LeyLatticeState()
    {
    }

    private LeyLatticeState(List<LeyRegion> regions)
    {
        regions.forEach(region -> regionsByKey.put(region.key(), region));
    }

    @Override
    public LeyRegion get(int regionX, int regionZ)
    {
        return regionsByKey.get(LeyRegion.key(regionX, regionZ));
    }

    @Override
    public void put(LeyRegion region)
    {
        regionsByKey.put(region.key(), region);
        setDirty();
    }

    public static LeyLatticeState get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
}
