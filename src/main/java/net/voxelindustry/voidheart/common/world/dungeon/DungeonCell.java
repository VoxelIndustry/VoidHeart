package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.Direction;

/**
 * Packs a material with its orientation. Flipped means upper half, upside down or hanging, depending on the material.
 */
public final class DungeonCell
{
    private static final int MATERIAL_MASK = 0xFF;
    private static final int FACING_SHIFT = 8;
    private static final int FACING_MASK = 0x7;
    private static final int FLIPPED = 1 << 11;
    private static final DungeonMaterial[] MATERIALS = DungeonMaterial.values();
    private static final Direction[] DIRECTIONS = Direction.values();

    private DungeonCell()
    {
    }

    public static short of(DungeonMaterial material)
    {
        return (short) material.ordinal();
    }

    public static short of(DungeonMaterial material, Direction facing)
    {
        return of(material, facing, false);
    }

    public static short of(DungeonMaterial material, Direction facing, boolean flipped)
    {
        return (short) (material.ordinal() | facing.ordinal() << FACING_SHIFT | (flipped ? FLIPPED : 0));
    }

    public static DungeonMaterial material(short cell)
    {
        return MATERIALS[cell & MATERIAL_MASK];
    }

    public static Direction facing(short cell)
    {
        return DIRECTIONS[cell >> FACING_SHIFT & FACING_MASK];
    }

    public static boolean flipped(short cell)
    {
        return (cell & FLIPPED) != 0;
    }

    public static short upsideDown(short cell)
    {
        return of(material(cell), facing(cell), !flipped(cell));
    }
}
