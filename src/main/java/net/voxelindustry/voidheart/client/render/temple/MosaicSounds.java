package net.voxelindustry.voidheart.client.render.temple;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * What the mosaic sounds like as it wakes, heard by the player staring at it alone: a wet click of eyes opening when it notices them, slow wet breaths while
 * it stirs, stone grinding as its tesserae come loose, wet slithering as its tendrils reach, and a sucking retreat as it withdraws. All of it is the game's own
 * sounds pitched low.
 */
public final class MosaicSounds
{
    private static final float NOTICE_VOLUME = 0.7F;
    private static final float NOTICE_PITCH = 0.55F;
    /** One breath for each turn of the flesh's breathing. */
    private static final float BREATH_VOLUME = 0.55F;
    private static final float BREATH_PITCH = 0.45F;
    /** While its tesserae come loose, a grind every this many ticks or so. */
    private static final int GRIND_TICKS = 9;
    private static final float GRIND_VOLUME = 0.35F;
    private static final float GRIND_PITCH = 0.5F;
    private static final int SLITHER_TICKS = 23;
    private static final float SLITHER_VOLUME = 0.5F;
    private static final float SLITHER_PITCH = 0.55F;
    private static final int WITHDRAW_TICKS = 6;
    private static final float WITHDRAW_VOLUME = 0.4F;
    private static final float WITHDRAW_PITCH = 0.5F;
    /** How much each sound's pitch wanders either way. */
    private static final float PITCH_WANDER = 0.08F;

    private boolean noticed;
    private float lastBreath;
    private int previousStare;
    private int played;

    public void tick(ClientLevel level, MosaicWall wall, MosaicWake wake)
    {
        var random = level.getRandom();
        int stare = wake.stareTicks();
        if (wake.noticed() && !noticed)
            play(level, wall, SoundEvents.SCULK_CLICKING, NOTICE_VOLUME, NOTICE_PITCH, random);
        noticed = wake.noticed();

        float stir = wake.stir(1);
        float reach = wake.reach(1);
        float time = level.getGameTime();
        // A breath as the flesh's swell passes its lowest, in step with how it moves
        float breath = (float) Math.sin(time * MosaicPicture.BREATH_RATE);
        if (stir > 0 && lastBreath < 0 && breath >= 0)
            play(level, wall, SoundEvents.SLIME_SQUISH, BREATH_VOLUME * stir, BREATH_PITCH, random);
        lastBreath = breath;

        boolean coming = stare > previousStare;
        if (coming && reach > 0 && reach < 1 && random.nextInt(GRIND_TICKS) == 0)
        {
            play(level, wall, SoundEvents.GRINDSTONE_USE, GRIND_VOLUME, GRIND_PITCH, random);
            play(level, wall, SoundEvents.DEEPSLATE_TILES_BREAK, GRIND_VOLUME, GRIND_PITCH, random);
        }
        if (reach > 0.3F && random.nextInt(SLITHER_TICKS) == 0)
            play(level, wall, SoundEvents.HONEY_BLOCK_SLIDE, SLITHER_VOLUME * reach, SLITHER_PITCH, random);
        if (!coming && stare < previousStare && (stir > 0 || reach > 0) && random.nextInt(WITHDRAW_TICKS) == 0)
            play(level, wall, SoundEvents.MUD_BREAK, WITHDRAW_VOLUME, WITHDRAW_PITCH, random);
        previousStare = stare;
    }

    public void forget()
    {
        noticed = false;
        previousStare = 0;
    }

    /** How many sounds it has made, for game tests. */
    public int played()
    {
        return played;
    }

    private void play(ClientLevel level, MosaicWall wall, SoundEvent sound, float volume, float pitch, RandomSource random)
    {
        played++;
        // From the flesh, a little out of the wall
        var at = wall.toWorld(MosaicArt.WIDTH / 2D, 24, 8);
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.AMBIENT, volume, pitch + (random.nextFloat() * 2 - 1) * PITCH_WANDER, false);
    }
}
