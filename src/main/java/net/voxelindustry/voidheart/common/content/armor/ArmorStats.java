package net.voxelindustry.voidheart.common.content.armor;

/**
 * What a frame gives with its working modules in place.
 */
public record ArmorStats(int batteries, boolean nightVision, boolean spectralLens, int exoskeletons)
{
    public boolean exoskeleton()
    {
        return exoskeletons > 0;
    }

    public long capacity()
    {
        return ArmorBalance.FRAME_CAPACITY + batteries * ArmorBalance.BATTERY_CAPACITY;
    }

    /**
     * What a fight costs a tick on average, every module working.
     */
    public long fightDrain()
    {
        long drain = ArmorBalance.FIGHT_DRAIN;
        drain += exoskeletons * ArmorBalance.EXOSKELETON_FIGHT_DRAIN;
        if (nightVision)
            drain += ArmorBalance.NIGHT_VISION_DRAIN;
        if (spectralLens)
            drain += ArmorBalance.SPECTRAL_FIGHT_DRAIN;
        return drain;
    }

    /**
     * How many minutes of fighting a full charge lasts, rounded down to the half minute.
     */
    public float sortieMinutes()
    {
        float minutes = capacity() / (float) fightDrain() / 1200F;
        return (float) Math.floor(minutes * 2) / 2;
    }

    public float speedBonus()
    {
        return ArmorBalance.FRAME_SPEED + exoskeletons * ArmorBalance.EXOSKELETON_SPEED;
    }

    public float jumpBlocks()
    {
        return exoskeletons == 0 ? ArmorBalance.FRAME_JUMP : ArmorBalance.EXOSKELETON_JUMP + (exoskeletons - 1) * ArmorBalance.EXTRA_EXOSKELETON_JUMP;
    }

    public float hitBonus()
    {
        return exoskeletons * ArmorBalance.EXOSKELETON_HIT;
    }
}
