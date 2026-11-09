package net.voxelindustry.voidheart.client.render.energy.reactor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorTile;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The warning heard over a reactor's countdown: a wavering tone rising in pitch over a low rumble, both swelling as the countdown runs and easing off
 * with the looks after a recovery or shutdown. Each sound follows its reactor and stops itself once the reactor is gone.
 */
final class ReactorWarningSound extends AbstractTickableSoundInstance
{
    private static final float TONE_PITCH_START = 0.6F;
    private static final float TONE_PITCH_END   = 1.8F;
    private static final float TONE_WAVER       = 0.05F;
    private static final float TONE_VOLUME      = 0.35F;
    private static final float TONE_SWELL       = 0.9F;
    private static final float RUMBLE_PITCH     = 0.5F;
    private static final float RUMBLE_VOLUME    = 0.4F;
    private static final float RUMBLE_SWELL     = 0.8F;

    private static final Map<ContravoidReactorTile, ReactorWarningSound[]> PLAYING = new WeakHashMap<>();

    private final ContravoidReactorTile reactor;
    private final boolean               rumble;

    private ReactorWarningSound(ContravoidReactorTile reactor, SoundEvent event, boolean rumble)
    {
        super(event, SoundSource.BLOCKS, RandomSource.create());
        this.reactor = reactor;
        this.rumble = rumble;
        looping = true;
        delay = 0;
        volume = 0.01F;
        var pos = reactor.getBlockPos();
        x = pos.getX() + 0.5;
        y = pos.getY() + 0.5;
        z = pos.getZ() + 0.5;
    }

    /**
     * Called on each reactor's client tick: starts the pair of sounds once its warning shows.
     */
    static void follow(ContravoidReactorTile reactor)
    {
        if (reactor.animation().warnStrength(0) <= 0)
            return;
        // The sound manager can stop a pair without a last tick, on a resource reload or leaving the world, so a pair still listed may be gone
        var manager = Minecraft.getInstance().getSoundManager();
        PLAYING.values().removeIf(pair -> !manager.isActive(pair[0]) && !manager.isActive(pair[1]));
        if (PLAYING.containsKey(reactor))
            return;
        var sounds = new ReactorWarningSound[]{
                new ReactorWarningSound(reactor, SoundEvents.BEACON_AMBIENT, false),
                new ReactorWarningSound(reactor, SoundEvents.PORTAL_AMBIENT, true)};
        PLAYING.put(reactor, sounds);
        for (var sound : sounds)
            Minecraft.getInstance().getSoundManager().play(sound);
    }

    @Override
    public void tick()
    {
        var level = Minecraft.getInstance().level;
        var animation = reactor.animation();
        var strength = animation.warnStrength(0);
        if (reactor.isRemoved() || reactor.getLevel() != level || strength <= 0)
        {
            PLAYING.remove(reactor);
            stop();
            return;
        }

        var warning = animation.warning(0);
        var time = animation.time(0);
        if (rumble)
        {
            pitch = RUMBLE_PITCH;
            volume = strength * (RUMBLE_VOLUME + RUMBLE_SWELL * warning);
        }
        else
        {
            pitch = Mth.lerp(warning, TONE_PITCH_START, TONE_PITCH_END) * (1 + TONE_WAVER * Mth.sin(time * 0.9F));
            volume = strength * (TONE_VOLUME + TONE_SWELL * warning);
        }
    }

    @Override
    public boolean canStartSilent()
    {
        return true;
    }
}
