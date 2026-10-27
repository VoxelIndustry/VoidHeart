package net.voxelindustry.voidheart.common.ley;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Ordered by rank: a kind always outranks the ones declared before it.
 */
public enum LeyNodeKind implements StringRepresentable
{
    WILD(0F, 1F),
    MINOR_ANCHOR(0.4F, 0.55F),
    ANCHOR(0.6F, 0.75F),
    MAJOR_ANCHOR(0.95F, 1F);

    public static final Codec<LeyNodeKind> CODEC = StringRepresentable.fromEnum(LeyNodeKind::values);

    private final float minWeight;
    private final float maxWeight;

    LeyNodeKind(float minWeight, float maxWeight)
    {
        this.minWeight = minWeight;
        this.maxWeight = maxWeight;
    }

    public float weight(float roll)
    {
        if (this == WILD)
            return roll * roll * roll;
        return minWeight + (maxWeight - minWeight) * roll;
    }

    public boolean isAnchor()
    {
        return this != WILD;
    }

    @Override
    public String getSerializedName()
    {
        return name().toLowerCase();
    }
}
