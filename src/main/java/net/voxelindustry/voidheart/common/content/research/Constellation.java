package net.voxelindustry.voidheart.common.content.research;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * What a research is about: making and gathering, useful devices, the repository itself, or going elsewhere.
 */
public enum Constellation implements StringRepresentable
{
    RITES,
    ARTIFICES,
    ARCHIVE,
    TRAVEL;

    public static final Codec<Constellation> CODEC = StringRepresentable.fromEnum(Constellation::values);

    @Override
    public String getSerializedName()
    {
        return name().toLowerCase(Locale.ROOT);
    }

    public String nameKey()
    {
        return "voidheart.research.constellation." + getSerializedName();
    }
}
