package net.voxelindustry.voidheart.common.content.underway;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.underway.HatchOpening;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * An Underway key at work in a district's hatch. It is a living thing only so that mobs can go for it: it never moves, and the hatch charges through it while a player stays near.
 * The hatch raises a shield over it, see {@link KeyDefence}. While the shield holds, the key is as wide as the shield, so mobs strike the panes from outside; with it down they must walk in to the key itself.
 * While it charges it draws waves of the district's mobs, see {@link HatchWaves}, and keeps turning those sent at it back to itself.
 * Only mobs can harm it. Broken, it is gone with the charge. Once the charge is full the leaves draw back under it and it is gone with them.
 */
public class UnderwayKeyEntity extends Mob
{
    public static final float WIDTH = 0.6F;
    public static final float HEIGHT = 1.2F;
    /** The shield stands on the hatch's coaming and stays under the room's ceiling. */
    public static final float SHIELD_WIDTH = 7;
    public static final float SHIELD_HEIGHT = 2.4F;
    /** Ticks a pane flares for when it takes a hit, on the client. */
    public static final int FLARE_TICKS = 8;

    private static final EntityDimensions SHIELDED = EntityDimensions.fixed(SHIELD_WIDTH, SHIELD_HEIGHT);
    private static final double MESSAGE_RANGE = 48;
    // Degrees a tick the key turns, at no charge and at full charge
    private static final float TURN_SLOW = 2;
    private static final float TURN_FAST = 14;
    private static final float POST_RISE = 1 / 12F;
    private static final float PANE_LIGHT = 1 / 10F;
    private static final float PANE_FADE = 1 / 4F;
    private static final int FLARE_PANE_BITS = 3;

    private static final EntityDataAccessor<Integer> DATA_CHARGE = SynchedEntityData.defineId(UnderwayKeyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_OPENING = SynchedEntityData.defineId(UnderwayKeyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_PANES = SynchedEntityData.defineId(UnderwayKeyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_KEY_HITS = SynchedEntityData.defineId(UnderwayKeyEntity.class, EntityDataSerializers.INT);
    // The pane last hit in the low bits and a count of hits above, so two hits on one pane still read as a change
    private static final EntityDataAccessor<Integer> DATA_FLARE = SynchedEntityData.defineId(UnderwayKeyEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent = new ServerBossEvent(getUUID(), Component.translatable("voidheart.underway_key.charging"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_12);

    private KeyDefence defence = new KeyDefence();
    private int openingTicks;
    private int lastHitTick = -HatchBalance.HIT_INTERVAL_TICKS;
    private int wavesSent;
    // The wave mobs sent at the key, kept so they can be turned back to it
    private final List<UUID> breakers = new ArrayList<>();

    /** Kept by the client for show: degrees the key has turned, how far the posts have risen, how lit each pane is and how long it still flares. */
    public float turn;
    public float turnO;
    public float rise;
    public float riseO;
    public final float[] paneLight = new float[KeyDefence.PANES];
    public final float[] paneLightO = new float[KeyDefence.PANES];
    public final int[] paneFlare = new int[KeyDefence.PANES];

    public UnderwayKeyEntity(EntityType<? extends UnderwayKeyEntity> type, Level level)
    {
        super(type, level);
        setNoGravity(true);
        setNoAi(true);
        setPersistenceRequired();
        noPhysics = true;
        refreshDimensions();
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
        entityData.define(DATA_PANES, KeyDefence.ALL_PANES);
        entityData.define(DATA_KEY_HITS, HatchBalance.KEY_HITS);
        entityData.define(DATA_FLARE, 0);
    }

    public int charge()
    {
        return entityData.get(DATA_CHARGE);
    }

    private void setCharge(int charge)
    {
        entityData.set(DATA_CHARGE, Math.clamp(charge, 0, HatchBalance.CHARGE_TICKS));
    }

    /** Sets the charge and passes over the waves due before it, for commands and tests that skip the wait. */
    public void skipTo(int charge)
    {
        setCharge(charge);
        wavesSent = HatchWaves.due(charge());
    }

    public int wavesSent()
    {
        return wavesSent;
    }

    /** Brings the next wave now, whatever the charge. */
    public void callWave()
    {
        var level = (ServerLevel) level();
        int wave = wavesSent++;
        if (level.getDifficulty() == Difficulty.PEACEFUL)
            return;
        var district = DistrictPos.at(getBlockX(), getBlockZ());
        var arrivals = HatchWaveSpawner.spawn(level, this, HatchWaves.compose(district, wave, new SplittableRandom(level.getRandom().nextLong())));
        for (var arrival : arrivals)
        {
            if (HatchWaves.goesForKey(arrival.kind()))
            {
                breakers.add(arrival.mob().getUUID());
                arrival.mob().setTarget(this);
            }
        }
        if (!arrivals.isEmpty())
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAID_HORN.value(), getSoundSource(), 8, 0.7F);
    }

    /** Turns back to the key the mobs sent at it, unless a player has just hurt them: a defender can draw one off for a while by striking it. */
    private void steer(ServerLevel level)
    {
        for (var iterator = breakers.iterator(); iterator.hasNext(); )
        {
            if (!(level.getEntity(iterator.next()) instanceof Mob mob) || !mob.isAlive())
            {
                iterator.remove();
                continue;
            }
            boolean distracted = mob.getLastHurtByMob() instanceof Player && mob.tickCount - mob.getLastHurtByMobTimestamp() < HatchBalance.DISTRACTION_TICKS;
            if (mob.getTarget() != this && !distracted)
                mob.setTarget(this);
        }
    }

    public boolean isOpening()
    {
        return entityData.get(DATA_OPENING);
    }

    /** @return a bit for each pane of the shield still lit */
    public int panes()
    {
        return entityData.get(DATA_PANES);
    }

    public boolean isShielded()
    {
        return panes() != 0;
    }

    /** @return the hits the bare key can still take */
    public int keyHits()
    {
        return entityData.get(DATA_KEY_HITS);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        if (DATA_PANES.equals(accessor) || DATA_OPENING.equals(accessor))
            refreshDimensions();
        else if (DATA_FLARE.equals(accessor) && level().isClientSide())
            paneFlare[entityData.get(DATA_FLARE) & (1 << FLARE_PANE_BITS) - 1] = FLARE_TICKS;
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose)
    {
        return isShielded() && !isOpening() ? SHIELDED : super.getDefaultDimensions(pose);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (level() instanceof ServerLevel level)
            serverTick(level);
        else
            clientTick();
    }

    private void clientTick()
    {
        turnO = turn;
        turn += Mth.lerp(charge() / (float) HatchBalance.CHARGE_TICKS, TURN_SLOW, TURN_FAST);
        riseO = rise;
        boolean up = isShielded() && !isOpening();
        rise = Mth.clamp(rise + (up ? POST_RISE : -POST_RISE), 0, 1);
        for (int pane = 0; pane < KeyDefence.PANES; pane++)
        {
            boolean lit = up && rise == 1 && (panes() & 1 << pane) != 0;
            paneLightO[pane] = paneLight[pane];
            paneLight[pane] = Mth.clamp(paneLight[pane] + (lit ? PANE_LIGHT : -PANE_FADE), 0, 1);
            if (paneFlare[pane] > 0)
                paneFlare[pane]--;
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
        bossEvent.setColor(isShielded() ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);

        if (isOpening())
        {
            open(level, hatch, spot);
            return;
        }
        if (defence.tick())
        {
            syncDefence();
            level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.BEACON_ACTIVATE, getSoundSource(), 1, 1.5F);
        }
        if (level.getNearestPlayer(this, HatchBalance.PLAYER_RANGE) == null)
            return;
        setCharge(charge() + 1);
        if (wavesSent < HatchWaves.due(charge()))
            callWave();
        if (tickCount % HatchBalance.STEER_INTERVAL_TICKS == 0)
            steer(level);
        if (charge() >= HatchBalance.CHARGE_TICKS)
        {
            entityData.set(DATA_OPENING, true);
            level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.BEACON_POWER_SELECT, getSoundSource(), 1.5F, 0.6F);
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

    private void syncDefence()
    {
        entityData.set(DATA_PANES, defence.panes());
        entityData.set(DATA_KEY_HITS, defence.keyHits());
    }

    /**
     * Only the blows of hostile mobs count, one hit each whatever their strength, and never two within {@link HatchBalance#HIT_INTERVAL_TICKS}.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        if (isOpening() || !(source.getEntity() instanceof Enemy) || tickCount - lastHitTick < HatchBalance.HIT_INTERVAL_TICKS)
            return false;
        lastHitTick = tickCount;
        var from = source.getEntity().position();
        double dx = from.x - getX();
        double dz = from.z - getZ();
        int pane = defence.paneFor(dx, dz);
        var outcome = defence.hit(dx, dz);
        syncDefence();
        if (pane >= 0)
            entityData.set(DATA_FLARE, pane | (entityData.get(DATA_FLARE) >> FLARE_PANE_BITS) + 1 << FLARE_PANE_BITS);

        double reach = pane >= 0 ? SHIELD_WIDTH / 2 : WIDTH / 2;
        double length = Math.max(Math.hypot(dx, dz), 0.01);
        double x = getX() + dx / length * reach;
        double z = getZ() + dz / length * reach;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, getY() + 1, z, 12, 0.3, 0.4, 0.3, 0.2);
        switch (outcome)
        {
            case ABSORBED -> level.playSound(null, x, getY(), z, SoundEvents.AMETHYST_BLOCK_RESONATE, getSoundSource(), 1.5F, 0.6F);
            case SHIELD_BROKEN ->
            {
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, getSoundSource(), 1.5F, 0.6F);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, getSoundSource(), 1.5F, 1.2F);
            }
            case KEY_HIT -> level.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, getSoundSource(), 1.2F, 1.3F);
            case KEY_DESTROYED -> shatter(level);
        }
        return true;
    }

    private void shatter(ServerLevel level)
    {
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, getSoundSource(), 1.2F, 0.5F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, getSoundSource(), 1.5F, 0.6F);
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.8, getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.8, getZ(), 40, 0.3, 0.4, 0.3, 0.6);
        for (var player : level.players())
        {
            if (player.distanceToSqr(this) < MESSAGE_RANGE * MESSAGE_RANGE)
                player.sendOverlayMessage(Component.translatable("voidheart.underway_key.broken"));
        }
        discard();
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
        output.putInt("Panes", defence.panes());
        output.putInt("KeyHits", defence.keyHits());
        output.putInt("Lull", defence.lull());
        output.putInt("Waves", wavesSent);
        output.store("Breakers", UUIDUtil.CODEC.listOf(), breakers);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        setCharge(input.getIntOr("Charge", 0));
        entityData.set(DATA_OPENING, input.getBooleanOr("Opening", false));
        openingTicks = input.getIntOr("OpeningTicks", 0);
        defence = new KeyDefence(input.getIntOr("Panes", KeyDefence.ALL_PANES), Math.max(1, input.getIntOr("KeyHits", HatchBalance.KEY_HITS)), input.getIntOr("Lull", 0));
        syncDefence();
        wavesSent = input.getIntOr("Waves", HatchWaves.due(charge()));
        breakers.clear();
        breakers.addAll(input.read("Breakers", UUIDUtil.CODEC.listOf()).orElse(List.of()));
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
