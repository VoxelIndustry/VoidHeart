package net.voxelindustry.voidheart.common.content.oil;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation;

import java.util.Locale;

/**
 * What a weapon is coated with, and which kind of damage its hits count as for those adapting to them.
 */
public enum OilType implements StringRepresentable
{
    SEARING(DragoonAdaptation.Kind.FIRE, 20, 0xFF8A3D),
    CONCUSSIVE(DragoonAdaptation.Kind.BLUNT, 20, 0xC2A272),
    ARCANE(DragoonAdaptation.Kind.MAGIC, 20, 0x5E8BFF),
    KERNACIAN(DragoonAdaptation.Kind.KERNACIAN, 20, 0x9B5CF6);

    public static final Codec<OilType> CODEC = StringRepresentable.fromEnum(OilType::values);

    private final DragoonAdaptation.Kind kind;
    private final int                    charges;
    private final int                    color;

    OilType(DragoonAdaptation.Kind kind, int charges, int color)
    {
        this.kind = kind;
        this.charges = charges;
        this.color = color;
    }

    public DragoonAdaptation.Kind kind()
    {
        return kind;
    }

    /**
     * Hits a fresh coating lasts.
     */
    public int charges()
    {
        return charges;
    }

    public int color()
    {
        return color;
    }

    public String nameKey()
    {
        return "voidheart.oil." + getSerializedName();
    }

    @Override
    public String getSerializedName()
    {
        return name().toLowerCase(Locale.ROOT);
    }
}
