package net.voxelindustry.voidheart.common.world.pocket;

import net.minecraft.core.Direction;

// Grows from the rock on the side opposite to its facing. Coordinates are relative to the heart.
public record PocketCrystal(int x, int y, int z, Type type, Size size, Direction facing)
{
    public enum Type
    {
        AZURIUM,
        RUBRUM
    }

    public enum Size
    {
        SMALL,
        MEDIUM,
        LARGE
    }
}
