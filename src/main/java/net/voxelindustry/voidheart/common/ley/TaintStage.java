package net.voxelindustry.voidheart.common.ley;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.voxelindustry.voidheart.common.energy.FoundationBalance;

/**
 * How far the void has worked into a node, ordered from clean to breached.
 */
public enum TaintStage implements StringRepresentable
{
    CLEAN,
    TAINTED,
    FESTERING,
    BREACHED;

    public static final Codec<TaintStage> CODEC = StringRepresentable.fromEnum(TaintStage::values);

    /**
     * The default thresholds from {@link FoundationBalance}. A flow provider may draw its stages from other numbers.
     */
    public static TaintStage of(float taint)
    {
        if (taint >= FoundationBalance.BREACHED_FROM)
            return BREACHED;
        if (taint >= FoundationBalance.FESTERING_FROM)
            return FESTERING;
        if (taint >= FoundationBalance.TAINTED_FROM)
            return TAINTED;
        return CLEAN;
    }

    public boolean isAtLeast(TaintStage other)
    {
        return compareTo(other) >= 0;
    }

    @Override
    public String getSerializedName()
    {
        return name().toLowerCase();
    }
}
