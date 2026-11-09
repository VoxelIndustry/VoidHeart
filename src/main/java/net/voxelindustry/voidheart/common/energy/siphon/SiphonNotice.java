package net.voxelindustry.voidheart.common.energy.siphon;

/**
 * What the Daedalus has noticed of a siphon. Every bit of energy it draws adds to the notice, up to a cap, and the notice is spent as waves of mobs once a
 * player comes near. Nothing comes while nobody is there, so a siphon left alone draws in peace and the bridge is crowded on the next visit. Pure, so it
 * is unit tested.
 */
public final class SiphonNotice
{
    private final long perMob;
    private final int  cap;
    private final int  waveSize;
    private final int  interval;

    private long banked;
    private long nextWaveAt = Long.MIN_VALUE;

    public SiphonNotice()
    {
        this(DeepSiphonBalance.NOTICE_PER_MOB, DeepSiphonBalance.NOTICE_CAP, DeepSiphonBalance.WAVE_SIZE, DeepSiphonBalance.WAVE_INTERVAL);
    }

    public SiphonNotice(long perMob, int cap, int waveSize, int interval)
    {
        this.perMob = perMob;
        this.cap = cap;
        this.waveSize = waveSize;
        this.interval = interval;
    }

    /**
     * Adds what was drawn. The notice stops growing at the cap.
     */
    public void add(long drawn)
    {
        if (drawn > 0)
            banked = Math.min(banked + drawn, perMob * cap);
    }

    /**
     * Mobs the notice would bring, whole ones only.
     */
    public int mobs()
    {
        return (int) (banked / perMob);
    }

    /**
     * How far the notice is toward its next mob, from 0 to 1. Always 0 at the cap.
     */
    public float towardNext()
    {
        if (mobs() >= cap)
            return 0;
        return (banked % perMob) / (float) perMob;
    }

    /**
     * How many mobs come now. The first wave comes as soon as a player is near, the next ones an interval apart while there is notice left.
     * Call {@link #spend} with how many actually came.
     */
    public int wave(long now, boolean playerNear)
    {
        if (!playerNear || now < nextWaveAt)
            return 0;
        int count = Math.min(waveSize, mobs());
        if (count > 0)
            nextWaveAt = now + interval;
        return count;
    }

    public void spend(int mobs)
    {
        banked = Math.max(0, banked - mobs * perMob);
    }

    public long banked()
    {
        return banked;
    }

    /**
     * Restores the notice from a save or a command, clamped to the cap.
     */
    public void setBanked(long banked)
    {
        this.banked = Math.clamp(banked, 0, perMob * cap);
    }
}
