package net.voxelindustry.voidheart.common.content.underway.security;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * An armoured wagon on the forts' narrow track, a ram plate at each end. It never leaves its track. When it sees someone standing on its track, it whistles and builds steam for a moment, then charges along the track, runs over whoever is still on it, overshoots, and brakes.
 * <p>
 * Stepping off the track is all it takes to be missed. Its plates turn every blow from along the track, so it is wrecked from the side, as it passes or while it stands braking.
 */
public class RamCart extends SecurityMob
{
    public enum Phase
    {
        PARKED,
        WIND_UP,
        CHARGE,
        BRAKE;

        private static final Phase[] VALUES = values();

        static Phase of(int ordinal)
        {
            return VALUES[Math.clamp(ordinal, 0, VALUES.length - 1)];
        }
    }

    public static final float WIDTH = 1.1F;
    public static final float HEIGHT = 1.2F;
    public static final int WAKE_TICKS = 20;

    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(RamCart.class, EntityDataSerializers.INT);
    private static final int LOOK_EVERY = 5;
    private static final double MIN_RUN = 2;
    private static final double START_SPEED = 0.15;
    private static final double THROW = 1.6;
    private static final float COS_PLATE = Mth.cos(SecurityBalance.CART_PLATE_ARC * Mth.DEG_TO_RAD);

    private int phaseTicks;
    private int way;
    private double speed;
    private double left;
    private final Set<UUID> struck = new HashSet<>();

    /** Kept by the client for show: how far its wheels have turned, in radians. */
    public float roll;
    public float rollO;

    public RamCart(EntityType<? extends RamCart> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, SecurityBalance.CART_HEALTH)
                .add(Attributes.ARMOR, SecurityBalance.CART_ARMOUR)
                .add(Attributes.ARMOR_TOUGHNESS, 4)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.FOLLOW_RANGE, SecurityBalance.CART_SIGHT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData)
    {
        super.defineSynchedData(entityData);
        entityData.define(DATA_PHASE, Phase.PARKED.ordinal());
    }

    @Override
    public int wakeTicks()
    {
        return WAKE_TICKS;
    }

    @Override
    protected boolean thinks()
    {
        return false;
    }

    @Override
    protected boolean hunts()
    {
        return false;
    }

    public Phase phase()
    {
        return Phase.of(entityData.get(DATA_PHASE));
    }

    private void setPhase(Phase phase, int ticks)
    {
        entityData.set(DATA_PHASE, phase.ordinal());
        phaseTicks = ticks;
    }

    /** @return the way its track runs, as a step of one block: it stands along the way it was posted */
    public Vec3 track()
    {
        return Vec3.directionFromRotation(0, Math.round(getYRot() / 90) * 90F);
    }

    @Override
    protected void serverTick(ServerLevel level)
    {
        if (!isUnderPressure() || !isAlive())
            return;
        var track = track();
        switch (phase())
        {
            case PARKED ->
            {
                if (tickCount % LOOK_EVERY != 0)
                    break;
                var target = inTheWay(level, track);
                if (target == null)
                    break;
                double along = target.position().subtract(position()).dot(track);
                way = along > 0 ? 1 : -1;
                left = Math.abs(along) + SecurityBalance.CART_OVERSHOOT;
                setPhase(Phase.WIND_UP, SecurityBalance.CART_WIND_UP_TICKS);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), getSoundSource(), 3.0F, 1.8F);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, getSoundSource(), 1.0F, 0.5F);
                link().raiseAlarm(level);
            }
            case WIND_UP ->
            {
                if (phaseTicks % 3 == 0)
                    steam(level, 3);
                if (--phaseTicks > 0)
                    break;
                speed = START_SPEED;
                struck.clear();
                setPhase(Phase.CHARGE, 0);
            }
            case CHARGE -> charge(level, track.scale(way));
            case BRAKE ->
            {
                if (--phaseTicks <= 0)
                    setPhase(Phase.PARKED, 0);
            }
        }
    }

    private void charge(ServerLevel level, Vec3 heading)
    {
        speed = Math.min(SecurityBalance.CART_TOP_SPEED, speed + SecurityBalance.CART_SPEED_UP);
        // It stops where its track does
        var ahead = BlockPos.containing(getX() + heading.x * (WIDTH / 2 + speed), getY() + 0.05, getZ() + heading.z * (WIDTH / 2 + speed));
        if (!isTrack(level.getBlockState(ahead)))
        {
            brake(level, true);
            return;
        }
        var before = position();
        move(MoverType.SELF, new Vec3(heading.x * speed, -0.1, heading.z * speed));
        double moved = Math.hypot(getX() - before.x, getZ() - before.z);
        if (tickCount % 3 == 0)
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.MINECART_RIDING, getSoundSource(), 0.8F, 1.2F);
        for (var player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(0.25, 0, 0.25), EntitySelector.NO_CREATIVE_OR_SPECTATOR))
        {
            if (!struck.add(player.getUUID()))
                continue;
            var source = damageSources().mobAttack(this);
            // Thrown across the track to the side they stand on, or to one side from its very middle, so they land clear of it
            double side = (player.getX() - getX()) * heading.z - (player.getZ() - getZ()) * heading.x >= 0 ? 1 : -1;
            if (player.hurtServer(level, source, SecurityBalance.CART_RAM_DAMAGE))
                player.knockback(THROW, -heading.z * side, heading.x * side, source, SecurityBalance.CART_RAM_DAMAGE);
        }
        left -= moved;
        if (moved < speed * 0.5)
            brake(level, true);
        else if (left <= 0)
            brake(level, false);
    }

    private void brake(ServerLevel level, boolean hard)
    {
        setPhase(Phase.BRAKE, SecurityBalance.CART_BRAKE_TICKS);
        steam(level, 6);
        level.playSound(null, getX(), getY(), getZ(), hard ? SoundEvents.ANVIL_LAND : SoundEvents.PISTON_CONTRACT, getSoundSource(), 1.0F, 0.6F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, getSoundSource(), 1.0F, 0.7F);
    }

    private static boolean isTrack(BlockState state)
    {
        return state.is(VoidHeartBlocks.UNDERWAY_FEEDER_RAIL);
    }

    /** @return the nearest player it can see standing on its track, far enough for a run at them */
    private @Nullable Player inTheWay(ServerLevel level, Vec3 track)
    {
        if (level.getDifficulty() == Difficulty.PEACEFUL)
            return null;
        var from = getBoundingBox().getCenter();
        Player nearest = null;
        double best = SecurityBalance.CART_SIGHT;
        for (var player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(SecurityBalance.CART_SIGHT, 1.5, SecurityBalance.CART_SIGHT), EntitySelector.NO_CREATIVE_OR_SPECTATOR))
        {
            var to = player.position().subtract(position());
            double along = Math.abs(to.dot(track));
            double aside = Math.abs(to.x * track.z - to.z * track.x);
            if (along < MIN_RUN || along >= best || aside > SecurityBalance.CART_TRACK_WIDTH || player.isInvisible() || !UnderwayShots.isClear(level, this, from, player))
                continue;
            best = along;
            nearest = player;
        }
        return nearest;
    }

    @Override
    protected boolean isArmoured(DamageSource source)
    {
        var from = source.getSourcePosition();
        if (from == null)
            return false;
        var way = new Vec3(from.x - getX(), 0, from.z - getZ());
        return way.lengthSqr() > 1.0E-4 && Math.abs(way.normalize().dot(track())) >= COS_PLATE;
    }

    @Override
    protected Vec3 vent()
    {
        return position().add(0, HEIGHT + 0.2, 0);
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    protected void pushEntities()
    {
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block)
    {
    }

    @Override
    protected void clientTick()
    {
        rollO = roll;
        roll += (float) (Math.hypot(getX() - xo, getZ() - zo) / 0.16);
    }
}
