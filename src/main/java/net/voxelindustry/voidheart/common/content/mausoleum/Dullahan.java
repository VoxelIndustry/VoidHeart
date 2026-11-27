package net.voxelindustry.voidheart.common.content.mausoleum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.mob.VoidMonster;
import net.voxelindustry.voidheart.common.content.mob.Wraith;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;
import net.voxelindustry.voidheart.common.world.mausoleum.MausoleumPlacer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static net.voxelindustry.voidheart.common.content.mausoleum.MausoleumBalance.*;

/**
 * A headless rider on a wraith beast, keeping the sarcophagus of its mausoleum. It rides slowly at whoever comes into its hall and reaps the ground
 * before it with long sweeps of its scythe, turning slowly enough that its flank is the place to be. A crown of eight green flames floats where
 * its head would be: each time it loses a ninth of its health one of them leaves as a Maleficent Wisp, the rider is stunned until that wisp is put out or half
 * a minute has passed, wraiths come out of the walls, and nothing harms the rider while a wisp still burns. It dies only once every flame is out.
 */
public class Dullahan extends VoidMonster
{
    public static final float WIDTH = 1.4F;
    public static final float HEIGHT = 2.95F;
    public static final float EYE_HEIGHT = 2.5F;

    /** Where its crown burns, as if set on the head it lacks, in blocks from its feet and forward of its middle, and how wide the ring is. */
    public static final double CROWN_HEIGHT = 2.67;
    public static final double CROWN_FORWARD = 0;
    public static final double CROWN_RADIUS = 0.34;

    public static final int DEATH_TICKS = 50;

    public enum Phase
    {
        IDLE,
        RIDE,
        WINDUP,
        RECOVER,
        STUNNED;

        private static final Phase[] VALUES = values();
    }

    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(Dullahan.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RELEASED = SynchedEntityData.defineId(Dullahan.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SWING_LEFT = SynchedEntityData.defineId(Dullahan.class, EntityDataSerializers.BOOLEAN);

    private static final int EXPERIENCE = 120;
    private static final int WISP_CHECK_TICKS = 5;
    /** How long a wisp may be out of sight while someone is near before it counts as put out, as when it was lost to an unloaded chunk. */
    private static final int WISP_LOST_TICKS = 100;
    private static final double WISP_CHECK_RANGE = 32;
    private static final double POST_SLACK = 1.5;
    private static final double RETURN_SPEED = 0.8;
    private static final double RIDE_SPEED = 1;
    /** It stops a little short of its prey, so its sweep lands with the scythe rather than its mount's chest. */
    private static final double RIDE_STOP = 3.2;
    private static final float SWEEP_START_ANGLE = 50;
    private static final int GLANCE_SOUND_TICKS = 8;

    private final ServerBossEvent bossEvent = new ServerBossEvent(getUUID(), getDisplayName(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);

    private int phaseTicks;
    private int sweepCooldown;
    private int stunTicks;
    private int glanceDelay;
    private @Nullable BlockPos post;
    private float postYaw;
    private final List<UUID> wisps = new ArrayList<>();
    private final Map<UUID, Integer> lostWisps = new HashMap<>();

    /** Client only: when it last changed phase, by its own tick count. */
    public int phaseChangedTick = -100;

    public Dullahan(EntityType<? extends Dullahan> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
        bossEvent.setVisible(false);
        moveControl = new SlowTurning(this);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(RIDER_HEALTH, SWEEP_DAMAGE, RIDER_SPEED)
                .add(Attributes.ARMOR, RIDER_ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, RIDER_TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.STEP_HEIGHT, RIDER_STEP)
                .add(Attributes.FOLLOW_RANGE, RIDER_RANGE);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.DULLAHAN;
    }

    @Override
    protected void registerGoals()
    {
    }

    public Phase phase()
    {
        return Phase.VALUES[Mth.clamp(entityData.get(DATA_PHASE), 0, Phase.VALUES.length - 1)];
    }

    private void setPhase(Phase phase)
    {
        entityData.set(DATA_PHASE, phase.ordinal());
        phaseTicks = 0;
    }

    /** @return how many flames have left its crown */
    public int released()
    {
        return entityData.get(DATA_RELEASED);
    }

    public boolean swingsLeft()
    {
        return entityData.get(DATA_SWING_LEFT);
    }

    public boolean isAwake()
    {
        return phase() != Phase.IDLE;
    }

    public boolean wispsBurning()
    {
        return !wisps.isEmpty();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, Phase.IDLE.ordinal());
        builder.define(DATA_RELEASED, 0);
        builder.define(DATA_SWING_LEFT, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        if (DATA_PHASE.equals(accessor) && level().isClientSide())
            phaseChangedTick = tickCount;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data)
    {
        post = blockPosition();
        postYaw = getYRot();
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public void push(Entity entity)
    {
    }

    @Override
    public boolean isPushedByFluid()
    {
        return false;
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);
        if (post == null)
        {
            post = blockPosition();
            postYaw = getYRot();
        }
        bossEvent.setProgress(getHealth() / getMaxHealth());
        if (glanceDelay > 0)
            glanceDelay--;
        if (sweepCooldown > 0)
            sweepCooldown--;
        if (tickCount % WISP_CHECK_TICKS == 0)
            checkWisps(level);

        var target = pickTarget(level);
        setTarget(target);
        if (target == null && phase() != Phase.STUNNED)
        {
            if (isAwake() && level.getNearestPlayer(this, RIDER_RANGE) == null)
                setPhase(Phase.IDLE);
            if (!isAwake())
            {
                bossEvent.setVisible(false);
                keepPost();
                return;
            }
        }
        if (target != null && !isAwake())
        {
            setPhase(Phase.RIDE);
            playSound(VoidHeartSounds.DULLAHAN.ambient(), 2, 0.6F);
        }
        bossEvent.setVisible(true);
        phaseTicks++;

        switch (phase())
        {
            case STUNNED -> stunned();
            case WINDUP -> windUp(level, target);
            case RECOVER -> recover();
            default -> ride(target);
        }
    }

    private @Nullable Player pickTarget(ServerLevel level)
    {
        double range = isAwake() ? RIDER_RANGE : RIDER_WAKE_RANGE;
        return level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(range), EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(Entity::isAlive)).stream()
                .filter(player -> distanceTo(player) <= range && canAttack(player))
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    /** Asleep, it rides back to its post before the sarcophagus and faces the way in again. */
    private void keepPost()
    {
        if (post == null)
            return;
        if (post.distToCenterSqr(getX(), post.getY() + 0.5, getZ()) > POST_SLACK * POST_SLACK)
        {
            if (getNavigation().isDone())
                getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, RETURN_SPEED);
            return;
        }
        getNavigation().stop();
        turnTowards(postYaw, RIDER_TURN);
    }

    private void ride(@Nullable LivingEntity target)
    {
        if (target == null)
            return;
        double distance = horizontalDistanceTo(target);
        if (distance > RIDE_STOP)
        {
            if (getNavigation().isDone() || tickCount % 10 == 0)
                getNavigation().moveTo(target, RIDE_SPEED);
        }
        else
        {
            getNavigation().stop();
            turnTowards(yawTo(target), RIDER_TURN);
        }
        if (sweepCooldown == 0 && distance <= SWEEP_START && Math.abs(Mth.wrapDegrees(yawTo(target) - getYRot())) < SWEEP_START_ANGLE)
        {
            getNavigation().stop();
            entityData.set(DATA_SWING_LEFT, !swingsLeft());
            setPhase(Phase.WINDUP);
            playSound(VoidHeartSounds.DULLAHAN_WINDUP, 1.5F, 0.8F);
        }
    }

    private void windUp(ServerLevel level, @Nullable LivingEntity target)
    {
        getNavigation().stop();
        if (target != null)
            turnTowards(yawTo(target), RIDER_TURN_WINDUP);
        if (phaseTicks >= SWEEP_WINDUP)
        {
            sweep(level);
            setPhase(Phase.RECOVER);
        }
    }

    private void recover()
    {
        getNavigation().stop();
        if (phaseTicks >= SWEEP_RECOVER)
        {
            sweepCooldown = SWEEP_COOLDOWN;
            setPhase(Phase.RIDE);
        }
    }

    private void stunned()
    {
        getNavigation().stop();
        if (--stunTicks <= 0 || !wispsBurning())
        {
            stunTicks = 0;
            setPhase(Phase.RIDE);
            playSound(VoidHeartSounds.DULLAHAN.ambient(), 2, 0.5F);
        }
    }

    /** The scythe reaps everything on the ground in the arc before it. */
    private void sweep(ServerLevel level)
    {
        float yaw = getYRot();
        var look = Vec3.directionFromRotation(0, yaw);
        playSound(VoidHeartSounds.DULLAHAN_SWEEP, 2, 0.7F);
        for (var victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SWEEP_REACH, 1, SWEEP_REACH), this::mayReap))
        {
            var to = victim.position().subtract(position()).multiply(1, 0, 1);
            double reach = to.length() - victim.getBbWidth() / 2;
            if (reach > SWEEP_REACH || victim.getY() > getY() + SWEEP_REACH_UP)
                continue;
            if (to.lengthSqr() > 1 && Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(look), -1, 1))) > SWEEP_ARC / 2)
                continue;
            var source = damageSources().mobAttack(this);
            if (victim.hurtServer(level, source, SWEEP_DAMAGE))
                victim.knockback(SWEEP_KNOCKBACK, getX() - victim.getX(), getZ() - victim.getZ(), source, SWEEP_DAMAGE);
        }
        for (int step = -3; step <= 3; step++)
        {
            var arc = Vec3.directionFromRotation(0, yaw + step * SWEEP_ARC / 7).scale(SWEEP_REACH * 0.7);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + arc.x, getY() + 1.1, getZ() + arc.z, 1, 0, 0, 0, 0);
        }
    }

    private boolean mayReap(LivingEntity victim)
    {
        if (victim == this || !victim.isAlive() || victim instanceof Wraith || victim instanceof MaleficentWisp)
            return false;
        return !(victim instanceof Player player) || !player.isCreative() && !player.isSpectator();
    }

    private double horizontalDistanceTo(Entity entity)
    {
        return Math.sqrt(Mth.square(entity.getX() - getX()) + Mth.square(entity.getZ() - getZ()));
    }

    private float yawTo(Entity entity)
    {
        return (float) (Mth.atan2(entity.getZ() - getZ(), entity.getX() - getX()) * Mth.RAD_TO_DEG) - 90;
    }

    private void turnTowards(float yaw, float most)
    {
        float turned = Mth.approachDegrees(getYRot(), yaw, most);
        setYRot(turned);
        setYHeadRot(turned);
        setYBodyRot(turned);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !DullahanCrown.vulnerable(wispsBurning()))
        {
            glance(level);
            if (source.getEntity() instanceof Player player && !isAwake() && !player.isCreative())
                setPhase(Phase.RIDE);
            return false;
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && !isAwake())
            setPhase(Phase.RIDE);
        return hurt;
    }

    /** A blow while a wisp burns flares off the crown without touching it. */
    private void glance(ServerLevel level)
    {
        level.sendParticles(VoidHeart.FUNERARY_FLAME_PARTICLE, getX(), getY() + CROWN_HEIGHT - 0.6, getZ(), 6, 0.4, 0.4, 0.4, 0.04);
        if (glanceDelay == 0)
        {
            playSound(VoidHeartSounds.DULLAHAN_GLANCE, 1, 0.7F);
            glanceDelay = GLANCE_SOUND_TICKS;
        }
    }

    @Override
    protected void actuallyHurt(ServerLevel level, DamageSource source, float damage)
    {
        super.actuallyHurt(level, source, damage);
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || released() >= DullahanCrown.FLAMES)
            return;
        float floor = DullahanCrown.threshold(released()) * getMaxHealth();
        if (getHealth() <= floor)
        {
            setHealth(floor);
            release(level);
        }
    }

    /** One flame leaves the crown as a wisp, the rider is stunned, and wraiths wake in the burial chambers. */
    public void release(ServerLevel level)
    {
        int released = released();
        if (released >= DullahanCrown.FLAMES)
            return;
        entityData.set(DATA_RELEASED, released + 1);

        var wisp = VoidHeartEntities.MALEFICENT_WISP.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (wisp != null)
        {
            var at = crownFlame(released);
            wisp.snapTo(at.x, at.y - MaleficentWisp.SIZE / 2, at.z, getYRot(), 0);
            wisp.setPersistenceRequired();
            wisp.born(this, getTarget());
            level.addFreshEntity(wisp);
            wisps.add(wisp.getUUID());
        }
        getNavigation().stop();
        setPhase(Phase.STUNNED);
        stunTicks = STUN_TICKS;
        level.playSound(null, getX(), getY() + CROWN_HEIGHT, getZ(), VoidHeartSounds.DULLAHAN_RELEASE, SoundSource.HOSTILE, 2, 0.6F);
        var crown = crownCenter();
        level.sendParticles(VoidHeart.FUNERARY_FLAME_PARTICLE, crown.x, crown.y, crown.z, 30, 0.3, 0.3, 0.3, 0.08);
        wave(level, DullahanCrown.wave(released + 1));
    }

    /** Wraiths come into the burial chambers off the hall, as far apart as there are chambers, and cross the walls to whoever fights here. */
    private void wave(ServerLevel level, int count)
    {
        int index = Mausoleums.indexAt(blockPosition());
        if (index < 0 || level.dimension() != VoidDungeons.WORLD_KEY)
            return;
        var chambers = new ArrayList<>(MausoleumPlacer.plan(level.getSeed(), index).chambers());
        Collections.shuffle(chambers, new Random(getRandom().nextLong()));
        for (int i = 0; i < count && !chambers.isEmpty(); i++)
        {
            var chamber = chambers.get(i % chambers.size());
            var wraith = VoidHeartEntities.WRAITH.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (wraith == null)
                continue;
            var pos = Mausoleums.local(index, chamber.x(), chamber.y(), chamber.z());
            double spread = i >= chambers.size() ? 0.6 : 0;
            wraith.snapTo(pos.getX() + 0.5 + spread, pos.getY(), pos.getZ() + 0.5, getRandom().nextFloat() * 360, 0);
            wraith.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
            wraith.setPersistenceRequired();
            wraith.setTarget(getTarget());
            level.addFreshEntity(wraith);
            level.sendParticles(ParticleTypes.SQUID_INK, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 12, 0.4, 0.6, 0.4, 0.02);
        }
    }

    private void checkWisps(ServerLevel level)
    {
        if (wisps.isEmpty())
            return;
        boolean watched = level.getNearestPlayer(this, WISP_CHECK_RANGE) != null;
        var iterator = wisps.iterator();
        while (iterator.hasNext())
        {
            var id = iterator.next();
            var entity = level.getEntity(id);
            if (entity != null)
            {
                lostWisps.remove(id);
                if (!entity.isAlive())
                    iterator.remove();
                continue;
            }
            if (!watched)
                continue;
            int lost = lostWisps.merge(id, WISP_CHECK_TICKS, Integer::sum);
            if (lost >= WISP_LOST_TICKS)
            {
                lostWisps.remove(id);
                iterator.remove();
            }
        }
    }

    /** Called by a wisp of its own as it goes out. */
    void wispOut(UUID wisp)
    {
        wisps.remove(wisp);
        lostWisps.remove(wisp);
    }

    /** Puts out every wisp it gave off, for a review starting the fight again. */
    public void putOutWisps(ServerLevel level)
    {
        for (var id : List.copyOf(wisps))
        {
            var entity = level.getEntity(id);
            if (entity != null)
                entity.discard();
        }
        wisps.clear();
        lostWisps.clear();
    }

    public Vec3 crownCenter()
    {
        var forward = Vec3.directionFromRotation(0, yBodyRot).scale(CROWN_FORWARD);
        return position().add(forward.x, CROWN_HEIGHT, forward.z);
    }

    /** Where the flame of a place in the ring burns, the first at its front and the rest round it clockwise. */
    public Vec3 crownFlame(int flame)
    {
        double angle = Math.toRadians(yBodyRot) + flame * Mth.TWO_PI / DullahanCrown.FLAMES;
        return crownCenter().add(-Math.sin(angle) * CROWN_RADIUS, 0, Math.cos(angle) * CROWN_RADIUS);
    }

    @Override
    public void die(DamageSource source)
    {
        super.die(source);
        if (level() instanceof ServerLevel level)
        {
            int index = Mausoleums.indexAt(blockPosition());
            if (index >= 0)
                Mausoleums.state(level.getServer()).markSlain(index);
            bossEvent.setVisible(false);
        }
    }

    /** Its last embers gutter out as it sinks, and the sarcophagus cracks once it is gone. */
    @Override
    protected void tickDeath()
    {
        deathTime++;
        if (!(level() instanceof ServerLevel level))
            return;
        if (deathTime % 3 == 0)
            level.sendParticles(VoidHeart.FUNERARY_FLAME_PARTICLE, getX(), getY() + getBbHeight() * 0.5, getZ(), 6, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.02);
        if (deathTime >= DEATH_TICKS && !isRemoved())
        {
            int index = Mausoleums.indexAt(blockPosition());
            if (index >= 0 && level.dimension() == VoidDungeons.WORLD_KEY)
                Sarcophagi.open(level, index);
            level.broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
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

    public boolean isBossBarShownTo(ServerPlayer player)
    {
        return bossEvent.isVisible() && bossEvent.getPlayers().contains(player);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.putInt("Phase", phase().ordinal());
        output.putInt("Released", released());
        output.putInt("Stun", stunTicks);
        output.store("Wisps", UUIDUtil.CODEC.listOf(), List.copyOf(wisps));
        if (post != null)
        {
            output.store("Post", BlockPos.CODEC, post);
            output.putFloat("PostYaw", postYaw);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        entityData.set(DATA_PHASE, Mth.clamp(input.getIntOr("Phase", 0), 0, Phase.VALUES.length - 1));
        entityData.set(DATA_RELEASED, Mth.clamp(input.getIntOr("Released", 0), 0, DullahanCrown.FLAMES));
        stunTicks = input.getIntOr("Stun", 0);
        wisps.clear();
        wisps.addAll(input.read("Wisps", UUIDUtil.CODEC.listOf()).orElse(List.of()));
        post = input.read("Post", BlockPos.CODEC).orElse(null);
        postYaw = input.getFloatOr("PostYaw", getYRot());
        if (hasCustomName())
            bossEvent.setName(getDisplayName());
    }

    /** Turns no faster than a rider reining in a heavy mount, so its flank stays open while it comes round. */
    private static final class SlowTurning extends MoveControl
    {
        private SlowTurning(Dullahan rider)
        {
            super(rider);
        }

        @Override
        protected float rotlerp(float from, float to, float most)
        {
            return super.rotlerp(from, to, Math.min(most, RIDER_TURN));
        }
    }
}
