package net.voxelindustry.voidheart.client.curse;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Rain heard all around the player and nowhere else, its volume following how hard it falls.
 */
final class OmenPatterSound extends AbstractTickableSoundInstance
{
    OmenPatterSound(RandomSource random, float pitch)
    {
        super(SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, random);
        this.pitch = pitch;
        volume = 0;
        looping = true;
        relative = true;
        attenuation = SoundInstance.Attenuation.NONE;
    }

    void setLoudness(float loudness)
    {
        volume = loudness;
    }

    void end()
    {
        stop();
    }

    @Override
    public void tick()
    {
    }

    @Override
    public boolean canStartSilent()
    {
        return true;
    }
}
