package net.voxelindustry.voidheart.common.content.crystal;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.util.StringRepresentable;

import java.util.EnumSet;
import java.util.Set;

import static net.voxelindustry.voidheart.common.content.crystal.CrystalKind.AZURIUM;
import static net.voxelindustry.voidheart.common.content.crystal.CrystalKind.MEMORIAM;
import static net.voxelindustry.voidheart.common.content.crystal.CrystalKind.RUBRUM;

/**
 * How a crystal grew. {@link #NONE} is a crystal as the world made it, {@link #PLAIN} one regrown in a cradle without a habit, and the rest are habits,
 * each taken only by the crystals whose nature fits it: azurium carries, rubrum holds, memoriam remembers.
 */
public enum CrystalHabit implements StringRepresentable
{
    NONE("none", ChatFormatting.GRAY, Set.of()),
    PLAIN("plain", ChatFormatting.GRAY, Set.of()),
    CLEAR("clear", ChatFormatting.AQUA, EnumSet.of(AZURIUM)),
    DEEP("deep", ChatFormatting.DARK_PURPLE, EnumSet.of(AZURIUM, MEMORIAM)),
    FOULED("fouled", ChatFormatting.DARK_GREEN, EnumSet.of(AZURIUM, RUBRUM, MEMORIAM)),
    SCOURED("scoured", ChatFormatting.WHITE, EnumSet.of(AZURIUM, RUBRUM, MEMORIAM)),
    SEARING("searing", ChatFormatting.GOLD, EnumSet.of(AZURIUM, RUBRUM)),
    STILL("still", ChatFormatting.DARK_AQUA, EnumSet.of(AZURIUM, RUBRUM)),
    WRUNG("wrung", ChatFormatting.RED, EnumSet.of(RUBRUM, MEMORIAM));

    public static final Codec<CrystalHabit> CODEC = StringRepresentable.fromEnum(CrystalHabit::values);

    private final String          name;
    private final ChatFormatting  color;
    private final Set<CrystalKind> takenBy;

    CrystalHabit(String name, ChatFormatting color, Set<CrystalKind> takenBy)
    {
        this.name = name;
        this.color = color;
        this.takenBy = takenBy;
    }

    /**
     * Whether this is a habit at all, rather than a crystal grown without one.
     */
    public boolean isHabit()
    {
        return this != NONE && this != PLAIN;
    }

    public boolean takenBy(CrystalKind kind)
    {
        return takenBy.contains(kind);
    }

    public ChatFormatting color()
    {
        return color;
    }

    public String translationKey()
    {
        return "voidheart.habit." + name;
    }

    @Override
    public String getSerializedName()
    {
        return name;
    }
}
