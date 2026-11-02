package net.voxelindustry.voidheart.client.curse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Something creeps up right behind the player and lights its fuse. No blast ever comes, only a laugh from inside their own head,
 * too deep to belong to a witch.
 */
public final class EchoCurse extends ClientCurse
{
    private static final int   STEPS         = 3;
    private static final int   STEP_INTERVAL = 8;
    private static final float STEP_VOLUME   = 0.35F;
    private static final float STEP_FARTHEST = 5;
    private static final float STEP_NEAREST  = 2.5F;

    private static final int   GROWL_TICK   = STEPS * STEP_INTERVAL;
    private static final float GROWL_VOLUME = 0.5F;
    private static final float GROWL_PITCH  = 0.7F;

    private static final int   HISS_TICK     = GROWL_TICK + 6;
    private static final float HISS_DISTANCE = 1.5F;
    // As a creeper plays it when priming
    private static final float HISS_PITCH    = 0.5F;
    // As long as a creeper's fuse, so that the blast is due right when the laugh comes
    private static final int   FUSE_TICKS    = 30;

    private static final int   LAUGH_TICK         = HISS_TICK + FUSE_TICKS;
    // The sound engine doesn't go lower. A slower second voice just behind thickens it into something larger
    private static final float LAUGH_PITCH        = 0.5F;
    private static final float LAUGH_VOLUME       = 1;
    private static final int   LAUGH_ECHO_DELAY   = 3;
    private static final float LAUGH_ECHO_PITCH   = 0.56F;
    private static final float LAUGH_ECHO_VOLUME  = 0.6F;

    public EchoCurse(long seed, int duration)
    {
        super(seed, duration);
    }

    @Override
    protected void tick(Minecraft minecraft, LocalPlayer player)
    {
        if (age < GROWL_TICK && age % STEP_INTERVAL == 0)
        {
            float closing = age / (float) (STEP_INTERVAL * (STEPS - 1));
            var ground = player.level().getBlockState(player.getOnPos());
            playBehind(minecraft, player, ground.getSoundType().getStepSound(), STEP_VOLUME, 1, Mth.lerp(closing, STEP_FARTHEST, STEP_NEAREST));
        }
        if (age == GROWL_TICK)
            playBehind(minecraft, player, SoundEvents.CREEPER_HURT, GROWL_VOLUME, GROWL_PITCH, STEP_NEAREST);
        if (age == HISS_TICK)
            playBehind(minecraft, player, SoundEvents.CREEPER_PRIMED, 1, HISS_PITCH, HISS_DISTANCE);
        if (age == LAUGH_TICK)
            playInsideHead(minecraft, SoundEvents.WITCH_CELEBRATE, LAUGH_VOLUME, LAUGH_PITCH);
        if (age == LAUGH_TICK + LAUGH_ECHO_DELAY)
            playInsideHead(minecraft, SoundEvents.WITCH_CELEBRATE, LAUGH_ECHO_VOLUME, LAUGH_ECHO_PITCH);
    }

    private void playBehind(Minecraft minecraft, LocalPlayer player, SoundEvent sound, float volume, float pitch, float distance)
    {
        var look = player.getLookAngle();
        var flat = new Vec3(look.x, 0, look.z);
        var at = player.position().subtract(flat.lengthSqr() > 1.0E-4 ? flat.normalize().scale(distance) : new Vec3(0, 0, distance));
        minecraft.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.HOSTILE, volume, pitch, random, at.x, at.y, at.z));
    }

    private void playInsideHead(Minecraft minecraft, SoundEvent sound, float volume, float pitch)
    {
        minecraft.getSoundManager().play(new SimpleSoundInstance(sound.location(), SoundSource.HOSTILE, volume, pitch, random,
                false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }
}
