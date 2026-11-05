package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Where a dungeon lies is what defines it: world generation reads the size, theme and difficulty back from the position,
 * so a chunk can generate on any thread, in any order, without saved data. Cells are too far apart for a player in one to load another.
 */
public record DungeonAddress(DungeonSize size, DungeonTheme theme, int difficulty, int index)
{
    public static final int MIN_DIFFICULTY = 1;
    public static final int MAX_DIFFICULTY = 5;
    public static final int BASE_Y = 64;
    public static final int MAX_INDEX = 29_000;

    private static final int SPACING_BITS = 10;
    private static final int SPACING = 1 << SPACING_BITS;
    private static final int RESERVED_THEMES = 64;
    private static final int SIZES = DungeonSize.values().length;

    public DungeonAddress
    {
        if (difficulty < MIN_DIFFICULTY || difficulty > MAX_DIFFICULTY)
            throw new IllegalArgumentException("Difficulty out of range: " + difficulty);
        if (index < 0 || index > MAX_INDEX)
            throw new IllegalArgumentException("Index out of range: " + index);
        if (!theme.comesIn(size))
            throw new IllegalArgumentException("No " + theme.getSerializedName() + " dungeon comes in " + size.getSerializedName());
    }

    public int row()
    {
        return size.ordinal() + SIZES * (theme.id() + RESERVED_THEMES * (difficulty - MIN_DIFFICULTY));
    }

    public BlockPos origin()
    {
        return new BlockPos((index + 1) * SPACING, BASE_Y, (row() + 1) * SPACING);
    }

    public static @Nullable DungeonAddress at(int x, int z)
    {
        int column = (x >> SPACING_BITS) - 1;
        int row = (z >> SPACING_BITS) - 1;
        if (column < 0 || column > MAX_INDEX || row < 0)
            return null;

        var size = DungeonSize.values()[row % SIZES];
        if ((x & SPACING - 1) >= size.edge() || (z & SPACING - 1) >= size.edge())
            return null;

        int themeAndDifficulty = row / SIZES;
        var theme = DungeonTheme.byId(themeAndDifficulty % RESERVED_THEMES);
        int difficulty = themeAndDifficulty / RESERVED_THEMES + MIN_DIFFICULTY;
        if (theme == null || difficulty > MAX_DIFFICULTY || !theme.comesIn(size))
            return null;

        return new DungeonAddress(size, theme, difficulty, column);
    }
}
