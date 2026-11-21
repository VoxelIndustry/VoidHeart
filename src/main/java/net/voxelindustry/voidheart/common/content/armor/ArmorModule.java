package net.voxelindustry.voidheart.common.content.armor;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.voxelindustry.voidheart.common.content.apparatus.ComponentShape;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * A piece set in one of the frame's grids. Batteries fit any grid, so every cell given to one is a cell taken from a capability.
 *
 * @param restsOn the module this one must touch along an edge to be set, and to work
 * @param unique  whether a frame takes only one
 */
public enum ArmorModule implements StringRepresentable
{
    BATTERY_CELL(ComponentShape.of("##", "##"), EnumSet.allOf(ArmorGridKind.class), null, false, 0xD4415E),
    NIGHT_VISION(ComponentShape.of("##"), EnumSet.of(ArmorGridKind.HELM), null, true, 0x36C5B0),
    SPECTRAL_LENS(ComponentShape.of("##", "#."), EnumSet.of(ArmorGridKind.HELM), "night_vision", true, 0xD6C9F5),
    // Stacks: each one fitted adds its boost again, as far as the limbs have room
    EXOSKELETON_POWER(ComponentShape.of("##", "##", "##"), EnumSet.of(ArmorGridKind.LIMBS), null, false, 0xE8C46A);

    public static final Codec<ArmorModule> CODEC = StringRepresentable.fromEnum(ArmorModule::values);

    private final ComponentShape     shape;
    private final Set<ArmorGridKind> grids;
    private final @Nullable String   restsOn;
    private final boolean            unique;
    private final int                rgb;

    ArmorModule(ComponentShape shape, Set<ArmorGridKind> grids, @Nullable String restsOn, boolean unique, int rgb)
    {
        this.shape = shape;
        this.grids = grids;
        this.restsOn = restsOn;
        this.unique = unique;
        this.rgb = rgb;
    }

    public ComponentShape shape()
    {
        return shape;
    }

    public boolean fits(ArmorGridKind grid)
    {
        return grids.contains(grid);
    }

    public Set<ArmorGridKind> grids()
    {
        return grids;
    }

    /**
     * The module this one has to touch, named rather than held so the enum can refer forward and back.
     */
    public @Nullable ArmorModule restsOn()
    {
        return restsOn == null ? null : valueOf(restsOn.toUpperCase(Locale.ROOT));
    }

    public boolean unique()
    {
        return unique;
    }

    /**
     * The colour of what the piece gives, shared by its pieces in the grid and its tooltip.
     */
    public int rgb()
    {
        return rgb;
    }

    public String itemName()
    {
        return "armor_" + getSerializedName();
    }

    @Override
    public String getSerializedName()
    {
        return name().toLowerCase(Locale.ROOT);
    }
}
