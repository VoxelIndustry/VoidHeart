package net.voxelindustry.voidheart.common.content.armillary;

import net.minecraft.util.Mth;

/**
 * What the client shows of an armillary on its dial, eased from what the server tells it every few seconds.
 */
public class ArmillaryDisplay
{
    private static final float FADE_STEP = 0.08F;
    private static final float EASE      = 0.12F;

    private float previousHud;
    private float hud;
    private float previousArc;
    private float arc;

    /**
     * Nothing to read until a bubble has been found, so a new armillary keeps to itself.
     */
    public static boolean isShown(boolean formed, int liveBubbles, ArmillaryChart.Phase phase)
    {
        return formed && (liveBubbles > 0 || phase != ArmillaryChart.Phase.SCANNING);
    }

    /**
     * How much of the dial's ring is lit: a search fills it, a reached bubble holds it full, and an open connection drains it.
     */
    public static float arcOf(ArmillaryChart.Phase phase, int searchTicks, int searchDuration, float connectionLeft)
    {
        return switch (phase)
        {
            case SCANNING -> 0;
            case SEARCHING -> searchDuration <= 0 ? 0 : Mth.clamp(searchTicks / (float) searchDuration, 0, 1);
            case REACHED -> 1;
            case CONNECTED -> Mth.clamp(connectionLeft, 0, 1);
        };
    }

    public void tick(boolean shown, float wantedArc)
    {
        previousHud = hud;
        float wantedHud = shown ? 1 : 0;
        hud += Mth.clamp(wantedHud - hud, -FADE_STEP, FADE_STEP);

        previousArc = arc;
        arc += (wantedArc - arc) * EASE;
    }

    public float hud(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousHud, hud);
    }

    public float arc(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousArc, arc);
    }
}
