package net.voxelindustry.voidheart.common.content.apparatus;

/**
 * A larger apparatus holds a wider grid and starts from a stronger shield before any component.
 *
 * @param capacity     shield points, two to a heart
 * @param delayTicks   how long after the last hit the shield waits before it refills
 * @param regenPerSecond points refilled each second once it does
 */
public enum ApparatusTier
{
    LESSER(3, 4, 100, 1),
    GREATER(4, 6, 80, 1.25F),
    SUPREME(5, 8, 60, 1.5F);

    private final int   size;
    private final float capacity;
    private final int   delayTicks;
    private final float regenPerSecond;

    ApparatusTier(int size, float capacity, int delayTicks, float regenPerSecond)
    {
        this.size = size;
        this.capacity = capacity;
        this.delayTicks = delayTicks;
        this.regenPerSecond = regenPerSecond;
    }

    public int size()
    {
        return size;
    }

    public float capacity()
    {
        return capacity;
    }

    public int delayTicks()
    {
        return delayTicks;
    }

    public float regenPerSecond()
    {
        return regenPerSecond;
    }
}
