package net.voxelindustry.voidheart.common.world.dungeon;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.util.RandomSource;

public record DungeonBlueprint(DungeonSize size, DungeonTheme theme, int difficulty, DungeonLayout layout, long seed)
{
    // Harder dungeons tend to be buried, without either layout being certain
    private static final double[] BURIED_CHANCE = { 0.05, 0.25, 0.5, 0.75, 0.95 };

    public static DungeonBlueprint of(long worldSeed, DungeonAddress address)
    {
        // Dungeons lie close together in position, and the first roll of nearly equal seeds is nearly equal
        long seed = HashCommon.murmurHash3(worldSeed * 31 + address.origin().asLong());
        var layout = RandomSource.create(seed).nextDouble() < BURIED_CHANCE[address.difficulty() - DungeonAddress.MIN_DIFFICULTY]
                     ? DungeonLayout.BURIED
                     : DungeonLayout.FLOATING;
        return new DungeonBlueprint(address.size(), address.theme(), address.difficulty(), layout, seed);
    }

    public boolean isBuried()
    {
        return layout == DungeonLayout.BURIED;
    }
}
