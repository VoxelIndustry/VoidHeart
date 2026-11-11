package net.voxelindustry.voidheart.common.content.apparatus;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * A piece set in an apparatus grid. Larger pieces give more for the space they take but are harder to fit beside others.
 *
 * @param capacity   shield points added
 * @param regen      added to the refill rate, 0.3 being 30% faster
 * @param quickening taken off the wait before refilling, 0.15 being 15% shorter, multiplied with the others
 */
public enum ApparatusComponent implements StringRepresentable
{
    CELL(ComponentShape.of("#"), 1, 0, 0, null, null),
    BANK(ComponentShape.of("###"), 3.5F, 0, 0, null, null),
    RESERVOIR(ComponentShape.of("##", "##"), 5, 0, 0, null, null),
    RESONATOR(ComponentShape.of("##", "#."), 0, 0.3F, 0, null, null),
    CHOIR(ComponentShape.of("###", ".#."), 0, 0.6F, 0, null, null),
    QUICKENER(ComponentShape.of("##"), 0, 0, 0.15F, null, null),
    HASTENER(ComponentShape.of(".##", "##."), 0, 0, 0.3F, null, null),
    EMBER_WARD(Shapes.WARD, 0, 0, 0, WardKind.FIRE, null),
    ARROW_WARD(Shapes.WARD, 0, 0, 0, WardKind.PROJECTILE, null),
    BLAST_WARD(Shapes.WARD, 0, 0, 0, WardKind.BLAST, null),
    ARCANE_WARD(Shapes.WARD, 0, 0, 0, WardKind.ARCANE, null),
    PULSE_CORE(Shapes.CORE, 0, 0, 0, null, BreakEffect.PULSE),
    BLINK_CORE(Shapes.CORE, 0, 0, 0, null, BreakEffect.BLINK),
    STASIS_CORE(Shapes.CORE, 0, 0, 0, null, BreakEffect.STASIS),
    MENDING_CORE(Shapes.CORE, 0, 0, 0, null, BreakEffect.MEND);

    public static final Codec<ApparatusComponent> CODEC = StringRepresentable.fromEnum(ApparatusComponent::values);

    private final ComponentShape        shape;
    private final float                 capacity;
    private final float                 regen;
    private final float                 quickening;
    private final @Nullable WardKind    ward;
    private final @Nullable BreakEffect breakEffect;

    ApparatusComponent(ComponentShape shape, float capacity, float regen, float quickening, @Nullable WardKind ward, @Nullable BreakEffect breakEffect)
    {
        this.shape = shape;
        this.capacity = capacity;
        this.regen = regen;
        this.quickening = quickening;
        this.ward = ward;
        this.breakEffect = breakEffect;
    }

    public ComponentShape shape()
    {
        return shape;
    }

    public float capacity()
    {
        return capacity;
    }

    public float regen()
    {
        return regen;
    }

    public float quickening()
    {
        return quickening;
    }

    public @Nullable WardKind ward()
    {
        return ward;
    }

    public @Nullable BreakEffect breakEffect()
    {
        return breakEffect;
    }

    public String itemName()
    {
        return "apparatus_" + getSerializedName();
    }

    @Override
    public String getSerializedName()
    {
        return name().toLowerCase(Locale.ROOT);
    }

    private static final class Shapes
    {
        static final ComponentShape WARD = ComponentShape.of("#.", "#.", "##");
        static final ComponentShape CORE = ComponentShape.of("##.", ".##");
    }
}
