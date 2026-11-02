package net.voxelindustry.voidheart.common.curse;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The one part of a curse that really happens, so it runs on the server. Everything is derived from the seed for the client to stay in step.
 */
public final class RaptureLift
{
    public static final int EYES_OPEN_TICKS  = 54;
    public static final int STARE_START_TICK = 68;
    public static final int STARE_END_TICK   = 94;
    public static final int RUMBLE_TICK      = 100;
    public static final int MOUTH_TICK       = 140;
    public static final int MOUTH_OPEN_TICKS = 50;
    public static final int LIFT_TICK        = 160;
    public static final int MOUTH_SNAP_TICKS = 10;
    public static final int EYES_CLOSE_TICKS = 20;
    public static final int RELEASE_TICKS    = 30;

    public static final int MIN_HEIGHT = 6;
    public static final int MAX_HEIGHT = 12;

    // From LivingEntity.travelInAir, at amplifier 0
    private static final double LEVITATION_SPEED = 0.05;
    private static final double LEVITATION_EASE  = 0.2;
    private static final double AIR_DRAG         = 0.98;

    private static final Map<UUID, MobEffectInstance> APPLIED = new HashMap<>();

    private RaptureLift()
    {
    }

    /**
     * How many blocks this curse lifts the player, both ends included.
     */
    public static int height(long seed)
    {
        return RandomSource.create(seed).nextIntBetweenInclusive(MIN_HEIGHT, MAX_HEIGHT);
    }

    /**
     * How long levitation must last to rise this many blocks from standing on the ground. The first tick only cancels the push into the ground.
     */
    public static int liftTicks(int height)
    {
        int ticks = 1;
        double speed = levitated(0);
        double risen = 0;
        while (risen < height)
        {
            risen += speed;
            speed = levitated(speed);
            ticks++;
        }
        return ticks;
    }

    private static double levitated(double speed)
    {
        return (speed + (LEVITATION_SPEED - speed) * LEVITATION_EASE) * AIR_DRAG;
    }

    public static int liftStartTick()
    {
        return LIFT_TICK;
    }

    /**
     * When the height is reached: the player falls, the sky closes.
     */
    public static int endTick(long seed)
    {
        return LIFT_TICK + liftTicks(height(seed));
    }

    public static int totalTicks(long seed)
    {
        return endTick(seed) + RELEASE_TICKS;
    }

    /**
     * Checked every tick rather than applied once, so that milk doesn't get a player out of it and a levitation they already had is left as it is.
     */
    public static void tick(ServerPlayer player, int age, long seed)
    {
        int remaining = endTick(seed) - age;
        if (age < LIFT_TICK || remaining <= 1 || player.hasEffect(MobEffects.LEVITATION))
            return;

        var levitation = new MobEffectInstance(MobEffects.LEVITATION, remaining, 0, false, false, false);
        if (player.addEffect(levitation))
            APPLIED.put(player.getUUID(), levitation);
    }

    /**
     * Another levitation landing on ours merges into the same instance and makes it visible, then it belongs to that source.
     */
    public static void finish(ServerPlayer player)
    {
        var applied = APPLIED.remove(player.getUUID());
        var current = player.getEffect(MobEffects.LEVITATION);
        if (current != null && current == applied && !current.isVisible() && !current.showIcon())
            player.removeEffect(MobEffects.LEVITATION);
    }
}
