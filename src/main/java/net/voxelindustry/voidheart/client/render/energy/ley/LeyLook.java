package net.voxelindustry.voidheart.client.render.energy.ley;

import net.minecraft.util.Mth;

/**
 * Colors and the flow level shared by every ley effect, from docs/plans/energy-looks.md. The drawing helpers they share with the fissure's are in
 * {@link net.voxelindustry.voidheart.client.render.energy.GlowDraw}.
 */
public final class LeyLook
{
    /**
     * An awake Old Anchor.
     */
    static final int TEAL       = 0x36C5B0;
    static final int TEAL_LIGHT = 0x6BDBC0;
    /**
     * A dormant Old Anchor.
     */
    static final int MAUVE      = 0x8A6A94;
    /**
     * Where a weakened ward's motes and ring drift to.
     */
    static final int MAUVE_GREY = 0x8C8292;

    /**
     * The flow at which ley effects show at their brightest, the p90 line from N0.
     */
    public static final float FULL_FLOW = 7;

    private LeyLook()
    {
    }

    /**
     * How bright a flow shows, from 0 to 1. A square root, so weak lines still show and the tides still swell.
     */
    public static float flowLevel(float flow)
    {
        return flow <= 0 ? 0 : Mth.clamp((float) Math.sqrt(flow / FULL_FLOW), 0, 1);
    }

}
