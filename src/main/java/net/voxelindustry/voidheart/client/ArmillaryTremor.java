package net.voxelindustry.voidheart.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryPayloads;

/**
 * The ground giving way while a connection collapses: the camera shakes and dust falls from whatever hangs above the player.
 */
public final class ArmillaryTremor
{
    private static final int   HOLD_TICKS    = 30;
    private static final float MAX_DEGREES   = 2.4F;
    private static final float YAW_SPEED     = 0.83F;
    private static final float PITCH_SPEED   = 1.21F;
    private static final float FLUTTER_SPEED = 2.7F;
    private static final float FLUTTER_SHARE = 0.35F;
    private static final int   DUST_MAX      = 24;
    private static final int   DUST_REACH    = 7;
    private static final int   DUST_HEIGHT   = 8;

    private static float strength;
    private static int   ticksLeft;

    private ArmillaryTremor()
    {
    }

    public static void receive(ArmillaryPayloads.Tremor payload)
    {
        strength = payload.strength();
        ticksLeft = HOLD_TICKS;
    }

    public static void clear()
    {
        strength = 0;
        ticksLeft = 0;
    }

    public static void tickClient(Minecraft client)
    {
        if (ticksLeft <= 0)
            return;

        ticksLeft--;
        if (client.level == null || client.player == null || client.isPaused())
            return;

        var random = client.level.getRandom();
        var dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.STONE.defaultBlockState());
        int count = Math.round(DUST_MAX * strength);
        for (int index = 0; index < count; index++)
        {
            var from = client.player.blockPosition().offset(random.nextInt(DUST_REACH * 2 + 1) - DUST_REACH, 0, random.nextInt(DUST_REACH * 2 + 1) - DUST_REACH);
            var ceiling = ceilingAbove(client, from);
            if (ceiling != null)
                client.level.addParticle(dust, ceiling.getX() + random.nextDouble(), ceiling.getY() - 0.05, ceiling.getZ() + random.nextDouble(), 0, 0, 0);
        }
    }

    private static BlockPos ceilingAbove(Minecraft client, BlockPos from)
    {
        for (int up = 1; up <= DUST_HEIGHT; up++)
        {
            var pos = from.above(up);
            if (!client.level.getBlockState(pos).isAir())
                return pos;
        }
        return null;
    }

    public static boolean isShaking()
    {
        return ticksLeft > 0 && VoidHeartConfig.get().armillaryCameraShake;
    }

    public static float yawOffset(float time)
    {
        return offset(time, YAW_SPEED);
    }

    public static float pitchOffset(float time)
    {
        return offset(time, PITCH_SPEED);
    }

    private static float offset(float time, float speed)
    {
        float wave = Mth.sin(time * speed) * (1 - FLUTTER_SHARE) + Mth.sin(time * speed * FLUTTER_SPEED) * FLUTTER_SHARE;
        return wave * strength * MAX_DEGREES * Math.min(1, ticksLeft / (float) HOLD_TICKS * 2);
    }
}
