package net.voxelindustry.voidheart.client.render.sin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.voxelindustry.voidheart.common.content.sin.Sin;
import net.voxelindustry.voidheart.common.content.sin.SinStageCrossedPayload;

/**
 * When the player's proficiency in a sin passes a stage, the inlay of every weapon of that sin they carry flares white-hot for a moment, with a short sound and no message.
 */
public final class SinFlare
{
    public static final int   FLARE_TICKS  = 20;
    private static final float SOUND_VOLUME = 0.8F;
    private static final float SOUND_PITCH  = 0.6F;

    private static Sin  flaring;
    private static long flaringUntil;

    private SinFlare()
    {
    }

    public static void register()
    {
        ClientPlayNetworking.registerGlobalReceiver(SinStageCrossedPayload.TYPE, (payload, context) -> start(payload.sin()));
    }

    public static void start(Sin sin)
    {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null)
            return;

        flaring = sin;
        flaringUntil = minecraft.level.getGameTime() + FLARE_TICKS;
        minecraft.level.playLocalSound(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(),
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, SOUND_VOLUME, SOUND_PITCH, false);
    }

    public static boolean isFlaring(Sin sin)
    {
        var level = Minecraft.getInstance().level;
        return sin != null && sin == flaring && level != null && level.getGameTime() < flaringUntil;
    }
}
