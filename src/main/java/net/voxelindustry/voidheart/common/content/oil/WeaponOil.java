package net.voxelindustry.voidheart.common.content.oil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

/**
 * The coating on a weapon and the hits it has left.
 */
public record WeaponOil(OilType type, int charges)
{
    public static final Codec<WeaponOil> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            OilType.CODEC.fieldOf("type").forGetter(WeaponOil::type),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("charges").forGetter(WeaponOil::charges)
    ).apply(instance, WeaponOil::new));

    public static WeaponOil fresh(OilType type)
    {
        return new WeaponOil(type, type.charges());
    }

    /**
     * @return the coating after one more landed hit, or null once it is used up
     */
    @Nullable
    public WeaponOil spend()
    {
        return charges <= 1 ? null : new WeaponOil(type, charges - 1);
    }
}
