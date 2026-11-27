package net.voxelindustry.voidheart.common.world.mausoleum;

import net.minecraft.core.BlockPos;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;

/**
 * Mausoleums lie in the dungeons' dimension, in a row of their own west of every dungeon and south of the sanctums, so nothing else there
 * comes across one. Like a dungeon, where one lies is all world generation needs to know.
 */
public final class MausoleumAddress
{
    public static final int MAX_INDEX = 29_000;

    private static final int SPACING_BITS = 10;
    private static final int SPACING = 1 << SPACING_BITS;
    private static final int ROW = -2;

    private MausoleumAddress()
    {
    }

    public static BlockPos origin(int index)
    {
        if (index < 0 || index > MAX_INDEX)
            throw new IllegalArgumentException("Mausoleum index out of range: " + index);
        return new BlockPos(-(index + 1) * SPACING, DungeonAddress.BASE_Y, ROW * SPACING);
    }

    /** @return the index of the mausoleum whose cell holds a column, or -1 */
    public static int at(int x, int z)
    {
        if (z >> SPACING_BITS != ROW || x >= 0)
            return -1;
        int index = -(x >> SPACING_BITS) - 1;
        return index <= MAX_INDEX ? index : -1;
    }
}
