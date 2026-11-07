package net.voxelindustry.voidheart.common.content.oil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

/**
 * The coating on a weapon and the hits it has left, out of the hits it was laid for. A strong coat comes from an empowered brew, and a wearing one
 * from a seething steel flask.
 */
public record WeaponOil(OilType type, int charges, int full, boolean strong, boolean wearing)
{
    public static final Codec<WeaponOil> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            OilType.CODEC.fieldOf("type").forGetter(WeaponOil::type),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("charges").forGetter(WeaponOil::charges),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("full", 0).forGetter(WeaponOil::full),
            Codec.BOOL.optionalFieldOf("strong", false).forGetter(WeaponOil::strong),
            Codec.BOOL.optionalFieldOf("wearing", false).forGetter(WeaponOil::wearing)
    ).apply(instance, WeaponOil::new));

    public WeaponOil(OilType type, int charges)
    {
        this(type, charges, 0, false, false);
    }

    public static WeaponOil fresh(OilType type)
    {
        return fresh(type, Brew.PLAIN);
    }

    /**
     * A new coat from a flask brewed this way.
     */
    public static WeaponOil fresh(OilType type, Brew brew)
    {
        int charges = brew.charges(type.charges());
        return new WeaponOil(type, charges, charges, brew.empowered(), brew.in(SinVessel.SEETHING_STEEL));
    }

    /**
     * @return the hits the coat was laid for, which older coats didn't record
     */
    public int capacity()
    {
        return full > 0 ? full : type.charges();
    }

    /**
     * @return the coating after one more landed hit, or null once it is used up
     */
    @Nullable
    public WeaponOil spend()
    {
        return charges <= 1 ? null : new WeaponOil(type, charges - 1, full, strong, wearing);
    }
}
