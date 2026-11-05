package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

public enum DungeonTheme implements StringRepresentable
{
    CATHEDRAL(0, "cathedral"),
    FOUNDRY(1, "foundry"),
    PALACE(2, "palace"),
    TEMPLE(3, "temple"),
    FOREST(4, "forest"),
    MYCOSIS(5, "mycosis"),
    DEPTHS(6, "depths");

    private final int id;
    private final String name;

    // The id is part of where a dungeon lies in the world, so it can never change once released
    DungeonTheme(int id, String name)
    {
        this.id = id;
        this.name = name;
    }

    public int id()
    {
        return id;
    }

    public static @Nullable DungeonTheme byId(int id)
    {
        for (var theme : values())
        {
            if (theme.id == id)
                return theme;
        }
        return null;
    }

    @Override
    public String getSerializedName()
    {
        return name;
    }
}
