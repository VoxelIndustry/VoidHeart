package net.voxelindustry.voidheart.common.content.crystal;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The three crystals a cradle grows. Their blocks and items are in {@link CrystalBundles}.
 */
public enum CrystalKind implements StringRepresentable
{
    AZURIUM("azurium"),
    RUBRUM("rubrum"),
    MEMORIAM("memoriam");

    public static final Codec<CrystalKind> CODEC = StringRepresentable.fromEnum(CrystalKind::values);

    private final String name;

    CrystalKind(String name)
    {
        this.name = name;
    }

    @Override
    public String getSerializedName()
    {
        return name;
    }
}
