package net.voxelindustry.voidheart.client.render.temple;

import net.minecraft.util.Mth;

/**
 * How far a temple's mosaic has woken under one player's stare. Each tick spent looking at it adds one; each tick looking away takes {@value #WITHDRAW} back, so it
 * withdraws far faster than it came. Once it has noticed the player it keeps that until they leave, and its eyes follow them all the while.
 */
public final class MosaicWake
{
    /** Ticks of staring before its eyes turn to the player. */
    public static final int NOTICE = 60;
    /** It stirs within the wall between these. */
    public static final int STIR_START = 140;
    public static final int STIR_FULL = 220;
    /** It comes out of the wall between these. */
    public static final int REACH_START = 300;
    public static final int REACH_FULL = 400;
    /** Staring adds no more past this, so looking away starts the withdrawal soon even after a long stare. */
    public static final int MOST = 440;
    public static final int WITHDRAW = 8;

    private int stare;
    private int previous;
    private boolean noticed;
    private int sinceNotice;

    public void tick(boolean staring)
    {
        previous = stare;
        stare = staring ? Math.min(MOST, stare + 1) : Math.max(0, stare - WITHDRAW);
        if (stare >= NOTICE)
            noticed = true;
        if (noticed)
            sinceNotice++;
    }

    /** Lets it all go, as when the player leaves the temple. */
    public void forget()
    {
        stare = 0;
        previous = 0;
        noticed = false;
        sinceNotice = 0;
    }

    public float stare(float partialTicks)
    {
        return Mth.lerp(partialTicks, previous, stare);
    }

    public int stareTicks()
    {
        return stare;
    }

    /** @return from 0 to 1, how far it moves within the wall */
    public float stir(float partialTicks)
    {
        return ease(stare(partialTicks), STIR_START, STIR_FULL);
    }

    /** @return from 0 to 1, how far it has come out of the wall */
    public float reach(float partialTicks)
    {
        return ease(stare(partialTicks), REACH_START, REACH_FULL);
    }

    public boolean noticed()
    {
        return noticed;
    }

    /** Ticks since it noticed the player, or 0 when it has not. */
    public int sinceNotice()
    {
        return sinceNotice;
    }

    /** Whether there is anything of it to draw. */
    public boolean active()
    {
        return noticed || stare > 0 || previous > 0;
    }

    static float ease(float value, float from, float to)
    {
        float t = Mth.clamp((value - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
