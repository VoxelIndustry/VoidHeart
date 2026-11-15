package net.voxelindustry.voidheart.common.world.sanctum;

import net.minecraft.core.BlockPos;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;

/**
 * Sanctums lie in the dungeons' dimension, in one row west of every dungeon, so nothing that hands out dungeons can come across one. Like a
 * dungeon, where one lies is all world generation needs to know.
 */
public final class SanctumAddress
{
    public static final int MAX_INDEX = 29_000;

    private static final int SPACING_BITS = 10;
    private static final int SPACING = 1 << SPACING_BITS;
    private static final int ROW = -1;

    private SanctumAddress()
    {
    }

    public static BlockPos origin(int index)
    {
        if (index < 0 || index > MAX_INDEX)
            throw new IllegalArgumentException("Sanctum index out of range: " + index);
        return new BlockPos(-(index + 1) * SPACING, DungeonAddress.BASE_Y, ROW * SPACING);
    }

    /** @return the index of the sanctum whose cell holds a column, or -1 */
    public static int at(int x, int z)
    {
        if (z >> SPACING_BITS != ROW || x >= 0)
            return -1;
        int index = -(x >> SPACING_BITS) - 1;
        return index <= MAX_INDEX ? index : -1;
    }
}
