package net.voxelindustry.voidheart.client.render.temple;

import net.minecraft.util.Mth;

/**
 * What the mosaic's moving parts have travelled, added up tick by tick at the pace its waking allows, so they slow and stop with it rather than jumping
 * whenever the pace changes: how far each wheel has turned within the wall and tumbled out of it, and how far the brood has dripped.
 * <p>
 * As the mosaic calms, each wheel's angles are drawn back to the nearest that looks as it did at rest, a share of the way each tick, so the wheels settle onto
 * their painting without ever turning back through the way they came.
 */
public final class MosaicMotion
{
    /** Radians a tick the wheels turn at full stir, and tumble at full reach. */
    static final float SPIN_RATE = 0.03F;
    static final float TUMBLE_RATE = 0.035F;
    /** Pixels a tick the brood drips at full stir. */
    static final float DRIP_RATE = 0.06F;
    /** The share of the way back to rest an angle goes each tick once the mosaic is wholly calm. */
    static final float SETTLE = 0.2F;

    private float[] spin = new float[0];
    private float[] previousSpin = new float[0];
    private float[] tumble = new float[0];
    private float[] previousTumble = new float[0];
    private float drip;
    private float previousDrip;

    public void tick(MosaicWake wake, MosaicArt art)
    {
        var wheels = art.wheels();
        if (spin.length != wheels.size())
        {
            spin = new float[wheels.size()];
            previousSpin = new float[wheels.size()];
            tumble = new float[wheels.size()];
            previousTumble = new float[wheels.size()];
        }
        float stir = wake.stir(1);
        float reach = wake.reach(1);
        for (int k = 0; k < wheels.size(); k++)
        {
            previousSpin[k] = spin[k];
            previousTumble[k] = tumble[k];
            // A wheel looks the same turned by the gap between its eyes, and tumbled by a whole turn
            spin[k] = settle(spin[k] + SPIN_RATE * MosaicWheels.spinPace(k) * stir, (float) (Math.PI * 2 / wheels.get(k).eyes()), stir);
            tumble[k] = settle(tumble[k] + TUMBLE_RATE * MosaicWheels.tumblePace(k) * reach, (float) (Math.PI * 2), reach);
        }
        previousDrip = drip;
        drip += DRIP_RATE * stir;
    }

    /** Draws an angle toward the nearest one that looks as at rest, the harder the calmer the mosaic. */
    static float settle(float angle, float period, float pace)
    {
        float rest = Math.round(angle / period) * period;
        return angle - (angle - rest) * SETTLE * (1 - pace);
    }

    public void forget()
    {
        spin = previousSpin = tumble = previousTumble = new float[0];
        drip = previousDrip = 0;
    }

    /** @return each wheel's turn within the wall, in radians */
    public float[] spin(float partialTicks)
    {
        return lerp(partialTicks, previousSpin, spin);
    }

    /** @return each wheel's tumble about its own axis, in radians */
    public float[] tumble(float partialTicks)
    {
        return lerp(partialTicks, previousTumble, tumble);
    }

    public float drip(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousDrip, drip);
    }

    private static float[] lerp(float partialTicks, float[] from, float[] to)
    {
        var at = new float[to.length];
        for (int i = 0; i < to.length; i++)
            at[i] = Mth.lerp(partialTicks, from[i], to[i]);
        return at;
    }
}
