package net.voxelindustry.voidheart.common.content.oil;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * A flask of sin metal set in the athanor in place of a glass bottle. Each changes what is brewed in it, and each asks something back.
 */
public enum SinVessel implements StringRepresentable
{
    /**
     * Stronger, but drinking it drives out every other brew.
     */
    ARROGANT_IRON(1, 1, 1, 1, true, false),
    /**
     * Lasts twice as long, while hunger gnaws.
     */
    RAVENOUS_GOLD(2, 1, 0, 1, true, false),
    /**
     * Half again as many hits, but the weapon wears twice as fast.
     */
    SEETHING_STEEL(1, 1.5F, 0, 1, false, true),
    /**
     * Two brews from one, each thinner.
     */
    COVETOUS_COPPER(0.6F, 0.6F, 0, 2, true, true),
    /**
     * Lasts three times as long, while it weighs down the step.
     */
    LISTLESS_LEAD(3, 1, 0, 1, true, false);

    public static final Codec<SinVessel> CODEC = StringRepresentable.fromEnum(SinVessel::values);

    private final float   durationScale;
    private final float   chargeScale;
    private final int     potency;
    private final int     yield;
    private final boolean elixirs;
    private final boolean oils;

    SinVessel(float durationScale, float chargeScale, int potency, int yield, boolean elixirs, boolean oils)
    {
        this.durationScale = durationScale;
        this.chargeScale = chargeScale;
        this.potency = potency;
        this.yield = yield;
        this.elixirs = elixirs;
        this.oils = oils;
    }

    public float durationScale()
    {
        return durationScale;
    }

    public float chargeScale()
    {
        return chargeScale;
    }

    /**
     * Levels it adds to a drunk brew's effect.
     */
    public int potency()
    {
        return potency;
    }

    /**
     * Brews one flask of it gives.
     */
    public int yield()
    {
        return yield;
    }

    public boolean holdsElixirs()
    {
        return elixirs;
    }

    public boolean holdsOils()
    {
        return oils;
    }

    public String flaskName()
    {
        return getSerializedName() + "_flask";
    }

    public String traitKey()
    {
        return "voidheart.brew.vessel." + getSerializedName();
    }

    @Override
    public String getSerializedName()
    {
        return name().toLowerCase(Locale.ROOT);
    }
}
