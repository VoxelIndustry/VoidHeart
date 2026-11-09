package net.voxelindustry.voidheart.common.energy.storage;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * A crystal bundle set in a construct socket: rubrum adds capacity, azurium adds charge and discharge rate.
 */
public enum SocketBundle implements StringRepresentable
{
    SMALL_RUBRUM(true, RubrumBalance.SMALL_RUBRUM_CAPACITY),
    MEDIUM_RUBRUM(true, RubrumBalance.MEDIUM_RUBRUM_CAPACITY),
    LARGE_RUBRUM(true, RubrumBalance.LARGE_RUBRUM_CAPACITY),
    SMALL_AZURIUM(false, RubrumBalance.SMALL_AZURIUM_RATE),
    MEDIUM_AZURIUM(false, RubrumBalance.MEDIUM_AZURIUM_RATE),
    LARGE_AZURIUM(false, RubrumBalance.LARGE_AZURIUM_RATE);

    public static final Codec<SocketBundle> CODEC = StringRepresentable.fromEnum(SocketBundle::values);

    private final boolean rubrum;
    private final long    value;

    SocketBundle(boolean rubrum, long value)
    {
        this.rubrum = rubrum;
        this.value = value;
    }

    public boolean isRubrum()
    {
        return rubrum;
    }

    public long capacity()
    {
        return rubrum ? value : 0;
    }

    public long rate()
    {
        return rubrum ? 0 : value;
    }

    /**
     * 0 for small, 1 for medium, 2 for large.
     */
    public int size()
    {
        return ordinal() % 3;
    }

    @Override
    public String getSerializedName()
    {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static long capacityOf(Iterable<SocketBundle> bundles)
    {
        long capacity = 0;
        for (var bundle : bundles)
        {
            if (bundle != null)
                capacity += bundle.capacity();
        }
        return capacity;
    }

    public static long rateOf(Iterable<SocketBundle> bundles)
    {
        long rate = 0;
        for (var bundle : bundles)
        {
            if (bundle != null)
                rate += bundle.rate();
        }
        return rate;
    }
}
