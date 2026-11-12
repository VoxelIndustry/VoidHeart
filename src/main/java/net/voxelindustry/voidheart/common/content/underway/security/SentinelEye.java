package net.voxelindustry.voidheart.common.content.underway.security;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;
import org.jspecify.annotations.Nullable;

/**
 * A sentinel eye on a wall of the Underway. It runs on the emergency circuit, so it watches whether its section has power or not: it sweeps a cone from side to side, stops on whoever enters it, and reports whoever stays in it long enough, which sounds the section's alarm.
 * <p>
 * The cone shows where it looks, so it can be walked around, and the eye is frail enough to be broken first. It does not see the unseen.
 */
public class SentinelEye extends MountedUnit
{
    public static final float WIDTH = 0.65F;
    public static final float HEIGHT = 0.65F;
    /** How far under the level it looks, in degrees: it hangs over head height. */
    public static final float PITCH = 18;

    private static final EntityDataAccessor<Integer> DATA_NOTICE = SynchedEntityData.defineId(SentinelEye.class, EntityDataSerializers.INT);
    // Ticks its sweep has been held for in all, which only changes while it has someone in its cone
    private static final EntityDataAccessor<Integer> DATA_HELD = SynchedEntityData.defineId(SentinelEye.class, EntityDataSerializers.INT);
    /** From the middle of its block to its glass, which is set a sixteenth back, less a hair so the lit eye never shares its plane. */
    private static final double LENS_OUT = -0.0625 + 0.004;
    private static final int FORGET_PER_TICK = 2;

    public SentinelEye(EntityType<? extends SentinelEye> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, SecurityBalance.EYE_HEALTH).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData)
    {
        super.defineSynchedData(entityData);
        entityData.define(DATA_NOTICE, 0);
        entityData.define(DATA_HELD, 0);
    }

    public double range()
    {
        return SecurityBalance.EYE_RANGE;
    }

    /** @return half the width of its cone, in degrees */
    public float cone()
    {
        return SecurityBalance.EYE_CONE;
    }

    protected float sweepHalf()
    {
        return SecurityBalance.EYE_SWEEP;
    }

    protected int sweepTicks()
    {
        return SecurityBalance.EYE_SWEEP_TICKS;
    }

    protected float pitch()
    {
        return PITCH;
    }

    /** @return how long someone may stand in its cone before it reports them */
    public int noticeTicks()
    {
        return SecurityBalance.EYE_NOTICE_TICKS;
    }

    /** @return whether it looks at all, given whether its section has woken it. An eye always does. */
    protected boolean watches(boolean awake)
    {
        return true;
    }

    /** @return how long it has had someone in its cone, up to {@link #noticeTicks()} */
    public int notice()
    {
        return entityData.get(DATA_NOTICE);
    }

    public boolean hasNoticed()
    {
        return notice() >= noticeTicks();
    }

    /**
     * @return how far it has swung from the way it faces, in degrees. Both sides read it from the world's clock, less the time it spent held on someone, so nothing is sent while it only sweeps.
     */
    public float sweep(float partialTicks)
    {
        boolean held = notice() > 0;
        float clock = level().getGameTime() - entityData.get(DATA_HELD) + getId() * 37L % sweepTicks() + (held ? 0 : partialTicks);
        return sweepHalf() * Mth.sin(clock / sweepTicks() * Mth.TWO_PI);
    }

    /** @return the way it looks right now */
    public Vec3 gaze(float partialTicks)
    {
        return Vec3.directionFromRotation(pitch(), getYRot() + sweep(partialTicks));
    }

    public Vec3 lens()
    {
        return getBoundingBox().getCenter().add(facing().scale(LENS_OUT));
    }

    private @Nullable Player firstSeen(ServerLevel level)
    {
        var lens = lens();
        var gaze = gaze(0);
        double cosCone = Math.cos(Math.toRadians(cone()));
        for (var player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(range()), EntitySelector.NO_CREATIVE_OR_SPECTATOR))
        {
            var to = player.getBoundingBox().getCenter().subtract(lens);
            double distance = to.length();
            if (distance > range() || distance < 0.01 || player.isInvisible() || to.dot(gaze) / distance < cosCone)
                continue;
            if (UnderwayShots.isClear(level, this, lens, player))
                return player;
        }
        return null;
    }

    @Override
    protected void guard(ServerLevel level, boolean awake)
    {
        var seen = watches(awake) ? firstSeen(level) : null;
        boolean noticed = hasNoticed();
        int notice = seen != null ? Math.min(notice() + 1, noticeTicks()) : Math.max(0, notice() - FORGET_PER_TICK);
        entityData.set(DATA_NOTICE, notice);
        // It stops on whoever it makes out, so the only way out of its cone is to walk out of it
        if (notice > 0)
            entityData.set(DATA_HELD, entityData.get(DATA_HELD) + 1);
        if (seen != null && notice >= noticeTicks())
            report(level, seen, !noticed);
    }

    /**
     * Tells its section about someone it has had in its cone long enough, every tick they stay there.
     *
     * @param first whether it only just made them out
     */
    protected void report(ServerLevel level, Player seen, boolean first)
    {
        if (first)
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), getSoundSource(), 2.0F, 0.5F);
        link().raiseAlarm(level);
    }
}
