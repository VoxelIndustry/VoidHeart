package net.voxelindustry.voidheart.common.content.underway;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.world.underway.HatchOpening;
import org.jspecify.annotations.Nullable;

/**
 * An Underway key at work in a district's hatch. It is a living thing only so that mobs can go for it: it never moves, and the hatch charges through it while a player stays near.
 * Once the charge is full the leaves draw back under it and it is gone with them.
 */
public class UnderwayKeyEntity extends Mob
{
    public static final float WIDTH = 0.6F;
    public static final float HEIGHT = 1.2F;

    // Degrees a tick the key turns, at no charge and at full charge
    private static final float TURN_SLOW = 2;
    private static final float TURN_FAST = 14;

    private static final EntityDataAccessor<Integer> DATA_CHARGE = SynchedEntityData.defineId(UnderwayKeyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_OPENING = SynchedEntityData.defineId(UnderwayKeyEntity.class, EntityDataSerializers.BOOLEAN);

    private final ServerBossEvent bossEvent = new ServerBossEvent(getUUID(), Component.translatable("voidheart.underway_key.charging"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_12);

    private int openingTicks;

    /** Degrees the key has turned in its lock, kept by the client for show. */
    public float turn;
    public float turnO;

    public UnderwayKeyEntity(EntityType<? extends UnderwayKeyEntity> type, Level level)
    {
        super(type, level);
        setNoGravity(true);
        setNoAi(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData)
    {
        super.defineSynchedData(entityData);
        entityData.define(DATA_CHARGE, 0);
        entityData.define(DATA_OPENING, false);
    }

    public int charge()
    {
        return entityData.get(DATA_CHARGE);
    }

    /** Sets the charge, for commands and tests that skip the wait. */
    public void setCharge(int charge)
    {
        entityData.set(DATA_CHARGE, Math.clamp(charge, 0, HatchBalance.CHARGE_TICKS));
    }

    public boolean isOpening()
    {
        return entityData.get(DATA_OPENING);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (level() instanceof ServerLevel level)
            serverTick(level);
        else
        {
            turnO = turn;
            turn += Mth.lerp(charge() / (float) HatchBalance.CHARGE_TICKS, TURN_SLOW, TURN_FAST);
        }
    }

    private void serverTick(ServerLevel level)
    {
        var hatch = UnderwayHatch.at(level, blockPosition().below());
        if (hatch == null || !isOpening() && !hatch.isClosed(level))
        {
            discard();
            return;
        }
        var spot = hatch.keySpot();
        setPos(spot);
        bossEvent.setProgress(charge() / (float) HatchBalance.CHARGE_TICKS);

        if (isOpening())
            open(level, hatch, spot);
        else if (level.getNearestPlayer(this, HatchBalance.PLAYER_RANGE) != null)
        {
            setCharge(charge() + 1);
            if (charge() >= HatchBalance.CHARGE_TICKS)
            {
                entityData.set(DATA_OPENING, true);
                level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.BEACON_POWER_SELECT, getSoundSource(), 1.5F, 0.6F);
            }
        }
    }

    private void open(ServerLevel level, UnderwayHatch hatch, Vec3 spot)
    {
        if (++openingTicks % HatchBalance.LEAF_STAGE_TICKS != 0)
            return;
        int stage = openingTicks / HatchBalance.LEAF_STAGE_TICKS - 1;
        hatch.retreat(level, stage);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.PISTON_CONTRACT, getSoundSource(), 1.5F, 0.5F);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.IRON_DOOR_OPEN, getSoundSource(), 1.5F, 0.5F);
        if (stage >= HatchOpening.STAGES - 1)
        {
            hatch.layStairs(level);
            discard();
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player)
    {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player)
    {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.putInt("Charge", charge());
        output.putBoolean("Opening", isOpening());
        output.putInt("OpeningTicks", openingTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        setCharge(input.getIntOr("Charge", 0));
        entityData.set(DATA_OPENING, input.getBooleanOr("Opening", false));
        openingTicks = input.getIntOr("OpeningTicks", 0);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        return false;
    }

    @Override
    public void knockback(double power, double xd, double zd, DamageSource source, float damage, boolean comesFromEffect)
    {
    }

    @Override
    public boolean isPickable()
    {
        return false;
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
    public void travel(Vec3 input)
    {
    }

    @Override
    public boolean isAffectedByPotions()
    {
        return false;
    }

    @Override
    public boolean canBeLeashed()
    {
        return false;
    }

    @Override
    public void checkDespawn()
    {
    }

    @Override
    public boolean removeWhenFarAway(double distSqr)
    {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean ignorePassenger)
    {
        return false;
    }

    @Override
    public boolean shouldDropExperience()
    {
        return false;
    }

    @Override
    public @Nullable SoundEvent getAmbientSound()
    {
        return null;
    }
}
