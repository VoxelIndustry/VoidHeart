package net.voxelindustry.voidheart.client.render.temple;

import net.minecraft.util.Mth;

/**
 * What the mosaic's moving parts have travelled, added up tick by tick at the pace its waking allows, so they slow and stop with it rather than jumping
 * whenever the pace changes: how far its wheels have turned within the wall and tumbled out of it, and how far its brood has dripped.
 */
public final class MosaicMotion
{
    /** Radians a tick the wheels turn at full stir, and tumble at full reach. */
    static final float SPIN_RATE = 0.03F;
    static final float TUMBLE_RATE = 0.035F;
    /** Pixels a tick the brood drips at full stir. */
    static final float DRIP_RATE = 0.06F;

    private float spin;
    private float previousSpin;
    private float tumble;
    private float previousTumble;
    private float drip;
    private float previousDrip;

    public void tick(MosaicWake wake)
    {
        previousSpin = spin;
        previousTumble = tumble;
        previousDrip = drip;
        float stir = wake.stir(1);
        spin += SPIN_RATE * stir;
        tumble += TUMBLE_RATE * wake.reach(1);
        drip += DRIP_RATE * stir;
    }

    public void forget()
    {
        spin = previousSpin = tumble = previousTumble = drip = previousDrip = 0;
    }

    public float spin(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousSpin, spin);
    }

    public float tumble(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousTumble, tumble);
    }

    public float drip(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousDrip, drip);
    }
}
