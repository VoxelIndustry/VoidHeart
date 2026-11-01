package net.voxelindustry.voidheart.common.content.shouldereye;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.function.Predicate;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A floating eye perched above the left shoulder of its owner.
 * It burns with a green beam the mobs hunting its owner and the players who hurt them, and otherwise keeps staring at the other players around.
 * <p>
 * The server moves it with its owner and decides when it fires. The client decides where it looks, as that is only for show.
 */
public class ShoulderEyeEntity extends Mob
{
    public static final float MAX_HEALTH = 12;
    public static final float SIZE       = 0.375F;

    public static final int CHARGE_TICKS = 12;
    public static final int LINGER_TICKS = 4;

    public static final ResourceKey<DamageType> BEAM_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "shoulder_eye_beam"));

    private static final int COOLDOWN_TICKS   = 24;
    private static final int SCAN_INTERVAL    = 4;
    private static final int HEAL_DELAY       = 10 * 20;
    private static final int HEAL_INTERVAL    = 5 * 20;
    private static final double SHOULDER_SIDE = 0.55;
    private static final double SHOULDER_BACK = 0.1;
    private static final double WATCH_RANGE   = 24;
    private static final int BLINK_TICKS      = 4;

    private static final EntityDataAccessor<Integer> DATA_OWNER_ID    = SynchedEntityData.defineId(ShoulderEyeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PERCHED     = SynchedEntityData.defineId(ShoulderEyeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BEAM_TARGET = SynchedEntityData.defineId(ShoulderEyeEntity.class, EntityDataSerializers.INT);

    /**
     * Set by the client so its own eye does not catch its crosshair, unless sneaking to take it back.
     */
    public static Predicate<ShoulderEyeEntity> hiddenFromLocalPick = eye -> false;

    private @Nullable ServerPlayer owner;
    private int beamTicks;
    private int cooldown;
    private int lastCombatTick = -HEAL_DELAY;
    private int returnTicks;

    public float lookYaw;
    public float lookYawO;
    public float lookPitch;
    public float lookPitchO;
    public int   blinkTicks;
    public int   clientBeamTicks;
    private int  clientBeamTarget = -1;
    private int  watchedPlayer    = -1;
    private int  nextWatchSwitch;
    private int  nextBlink        = 40;

    public ShoulderEyeEntity(EntityType<? extends ShoulderEyeEntity> type, Level level)
    {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData)
    {
        super.defineSynchedData(entityData);
        entityData.define(DATA_OWNER_ID, -1);
        entityData.define(DATA_PERCHED, true);
        entityData.define(DATA_BEAM_TARGET, -1);
    }

    public void bindTo(ServerPlayer owner, float health)
    {
        this.owner = owner;
        entityData.set(DATA_OWNER_ID, owner.getId());
        setHealth(health);
    }

    public @Nullable ServerPlayer getOwner()
    {
        return owner;
    }

    public int getOwnerId()
    {
        return entityData.get(DATA_OWNER_ID);
    }

    public @Nullable LivingEntity getOwnerOnClient()
    {
        return level().getEntity(getOwnerId()) instanceof LivingEntity living ? living : null;
    }

    public boolean isPerched()
    {
        return entityData.get(DATA_PERCHED);
    }

    public void setPerched(boolean perched)
    {
        entityData.set(DATA_PERCHED, perched);
        returnTicks = 0;
    }

    public @Nullable LivingEntity getBeamTarget()
    {
        int id = entityData.get(DATA_BEAM_TARGET);
        return id != -1 && level().getEntity(id) instanceof LivingEntity living ? living : null;
    }

    /**
     * Where the eye sits, by its feet, for an owner at the given partial tick.
     */
    public static Vec3 shoulderPosition(LivingEntity owner, float partialTick)
    {
        var pos = owner.getPosition(partialTick);
        float yaw = owner.getPreciseBodyRotation(partialTick) * Mth.DEG_TO_RAD;
        return new Vec3(
                pos.x + Mth.cos(yaw) * SHOULDER_SIDE + Mth.sin(yaw) * SHOULDER_BACK,
                pos.y + owner.getBbHeight() - 0.05 - SIZE / 2,
                pos.z + Mth.sin(yaw) * SHOULDER_SIDE - Mth.cos(yaw) * SHOULDER_BACK);
    }

    @Override
    public void tick()
    {
        noPhysics = true;
        super.tick();

        if (level() instanceof ServerLevel serverLevel)
            serverTick(serverLevel);
        else
            clientTick();
    }

    private void serverTick(ServerLevel level)
    {
        if (owner == null || owner.isRemoved() || owner.level() != level)
        {
            discard();
            return;
        }
        if (isDeadOrDying())
            return;

        followOwner();
        tickBeam(level);

        if (tickCount - lastCombatTick > HEAL_DELAY && tickCount % HEAL_INTERVAL == 0 && getHealth() < getMaxHealth())
            heal(1);
        ShoulderEyes.onEyeHealthChanged(owner, getHealth());
    }

    private void followOwner()
    {
        var shoulder = shoulderPosition(owner, 1);
        if (isPerched())
            setPos(shoulder);
        else
        {
            returnTicks++;
            var toward = shoulder.subtract(position());
            double distance = toward.length();
            double speed = Math.min(0.08 + returnTicks * 0.02, 0.8);

            if (distance > 48 || distance <= speed + 0.05)
            {
                setPos(shoulder);
                setPerched(true);
                playSound(SoundEvents.ENDER_EYE_LAUNCH, 0.5F, 1.6F);
            }
            else
                setPos(position().add(toward.scale(speed / distance)));
        }

        var beamTarget = getBeamTarget();
        float yaw = beamTarget != null ? yawToward(beamTarget.getBoundingBox().getCenter()) : owner.getYHeadRot();
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    private float yawToward(Vec3 point)
    {
        return (float) (Mth.atan2(point.z - getZ(), point.x - getX()) * Mth.RAD_TO_DEG) - 90;
    }

    private void tickBeam(ServerLevel level)
    {
        var target = getBeamTarget();
        if (target != null)
        {
            lastCombatTick = tickCount;
            if (!canKeepFiringAt(target))
            {
                stopBeam(COOLDOWN_TICKS / 2);
                return;
            }

            beamTicks++;
            if (beamTicks == CHARGE_TICKS)
                fire(level, target);
            else if (beamTicks >= CHARGE_TICKS + LINGER_TICKS)
                stopBeam(COOLDOWN_TICKS);
            return;
        }

        if (cooldown > 0)
        {
            cooldown--;
            return;
        }
        if ((tickCount + getId()) % SCAN_INTERVAL != 0)
            return;

        var enemy = findEnemy(level);
        if (enemy != null)
        {
            entityData.set(DATA_BEAM_TARGET, enemy.getId());
            beamTicks = 0;
            playSound(SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.8F);
        }
    }

    private void stopBeam(int cooldown)
    {
        entityData.set(DATA_BEAM_TARGET, -1);
        beamTicks = 0;
        this.cooldown = cooldown;
    }

    private void fire(ServerLevel level, LivingEntity target)
    {
        target.hurtServer(level, damageSources().source(BEAM_DAMAGE, this, owner), VoidHeartConfig.get().shoulderEyeBeamDamage);

        var center = target.getBoundingBox().getCenter();
        level.sendParticles(new DustParticleOptions(0x6CFF5A, 1.2F), center.x, center.y, center.z, 12, 0.25, 0.25, 0.25, 0);
        playSound(SoundEvents.BLAZE_SHOOT, 0.5F, 1.7F);
    }

    private boolean canKeepFiringAt(LivingEntity target)
    {
        double range = VoidHeartConfig.get().shoulderEyeRange + 4;
        return target.isAlive() && !target.isRemoved() && target.level() == level()
                && target.distanceToSqr(this) <= range * range && hasLineOfSight(target);
    }

    private @Nullable LivingEntity findEnemy(ServerLevel level)
    {
        double range = VoidHeartConfig.get().shoulderEyeRange;
        return level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(range), this::isEnemy).stream()
                .filter(enemy -> enemy.distanceToSqr(owner) <= range * range && hasLineOfSight(enemy))
                .min(Comparator.comparingDouble(enemy -> enemy.distanceToSqr(this)))
                .orElse(null);
    }

    /**
     * Mobs hunting the owner, and players who recently hurt them.
     */
    private boolean isEnemy(LivingEntity entity)
    {
        if (entity == owner || !entity.isAlive() || entity instanceof ShoulderEyeEntity)
            return false;
        if (entity instanceof Mob mob)
            return mob.getTarget() == owner;
        return entity instanceof Player player && !player.isCreative() && !player.isSpectator() && ShoulderEyes.isAggressor(owner, player);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        if (owner != null && source.getEntity() == owner)
            return false;
        return super.hurtServer(level, source, damage);
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source)
    {
        return super.isInvulnerableTo(level, source)
                || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.FALL)
                || source.is(DamageTypes.CRAMMING)
                || source.is(DamageTypes.FLY_INTO_WALL);
    }

    @Override
    public void die(DamageSource source)
    {
        super.die(source);
        entityData.set(DATA_BEAM_TARGET, -1);

        if (level() instanceof ServerLevel level)
        {
            var center = getBoundingBox().getCenter();
            level.sendParticles(new DustParticleOptions(0x2A6B3A, 1.0F), center.x, center.y, center.z, 16, 0.15, 0.15, 0.15, 0);
            level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 6, 0.1, 0.1, 0.1, 0.02);
            if (owner != null)
                ShoulderEyes.onEyeDeath(owner);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (player.getId() != getOwnerId() || !player.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty() || isDeadOrDying())
            return InteractionResult.PASS;

        if (player instanceof ServerPlayer serverPlayer)
        {
            ShoulderEyes.unbind(serverPlayer);
            serverPlayer.getInventory().placeItemBackInInventory(new ItemStack(VoidHeartItems.SHOULDER_EYE), Prediction.SERVER_ONLY);
            playSound(SoundEvents.ENDER_EYE_DEATH, 0.8F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }

    private void clientTick()
    {
        var ownerEntity = getOwnerOnClient();
        if (ownerEntity != null && isPerched() && isAlive())
        {
            getInterpolation().cancel();
            setPos(shoulderPosition(ownerEntity, 1));
        }

        int beamTarget = entityData.get(DATA_BEAM_TARGET);
        if (beamTarget != clientBeamTarget)
        {
            clientBeamTarget = beamTarget;
            clientBeamTicks = 0;
        }
        else if (beamTarget != -1)
            clientBeamTicks++;

        tickLook(ownerEntity);

        if (blinkTicks > 0)
            blinkTicks--;
        else if (tickCount >= nextBlink)
        {
            blinkTicks = BLINK_TICKS;
            nextBlink = tickCount + 50 + random.nextInt(150);
        }

        var target = getBeamTarget();
        if (target != null && isAlive())
            spawnBeamParticles(target);
    }

    /**
     * Stares at its target while firing, at other players most of the time otherwise, and wanders around where its owner looks when alone.
     */
    private void tickLook(@Nullable LivingEntity ownerEntity)
    {
        lookYawO = lookYaw;
        lookPitchO = lookPitch;

        Entity focus = getBeamTarget();
        if (focus == null)
            focus = pickWatchedPlayer(ownerEntity);

        float wantedYaw;
        float wantedPitch;
        float ease;
        if (focus != null)
        {
            var toward = (focus == getBeamTarget() ? focus.getBoundingBox().getCenter() : focus.getEyePosition()).subtract(getEyePosition());
            wantedYaw = (float) (Mth.atan2(toward.z, toward.x) * Mth.RAD_TO_DEG) - 90;
            wantedPitch = (float) -(Mth.atan2(toward.y, toward.horizontalDistance()) * Mth.RAD_TO_DEG);
            ease = 0.45F;
        }
        else
        {
            float baseYaw = ownerEntity != null ? ownerEntity.getYHeadRot() : lookYaw;
            float basePitch = ownerEntity != null ? ownerEntity.getXRot() * 0.5F : 0;
            wantedYaw = baseYaw + Mth.sin(tickCount * 0.031F) * 35 + Mth.sin(tickCount * 0.013F + 1.7F) * 20;
            wantedPitch = basePitch + Mth.sin(tickCount * 0.047F) * 12;
            ease = 0.12F;
        }

        lookYaw += Mth.wrapDegrees(wantedYaw - lookYaw) * ease;
        lookPitch += (Mth.clamp(wantedPitch, -80, 80) - lookPitch) * ease;
    }

    private @Nullable Player pickWatchedPlayer(@Nullable LivingEntity ownerEntity)
    {
        var watched = level().getEntity(watchedPlayer) instanceof Player player && isWatchable(player, ownerEntity) ? player : null;
        if (watched != null && tickCount < nextWatchSwitch)
            return watched;

        var candidates = level().players().stream().filter(player -> isWatchable(player, ownerEntity)).toList();
        if (candidates.isEmpty())
        {
            watchedPlayer = -1;
            return null;
        }

        // Every now and then it glances away for a moment, but always comes back to them
        if (watched != null && random.nextFloat() < 0.15F)
        {
            watchedPlayer = -1;
            nextWatchSwitch = tickCount + 15 + random.nextInt(20);
            return null;
        }

        var current = watched;
        var others = candidates.size() > 1 ? candidates.stream().filter(player -> player != current).toList() : candidates;
        var next = others.get(random.nextInt(others.size()));
        watchedPlayer = next.getId();
        nextWatchSwitch = tickCount + 60 + random.nextInt(100);
        return next;
    }

    private boolean isWatchable(Player player, @Nullable LivingEntity ownerEntity)
    {
        return player != ownerEntity && player.isAlive() && !player.isSpectator() && !player.isInvisible() && player.distanceToSqr(this) <= WATCH_RANGE * WATCH_RANGE;
    }

    private void spawnBeamParticles(LivingEntity target)
    {
        var start = getBoundingBox().getCenter();
        var end = target.getBoundingBox().getCenter();
        int count = clientBeamTicks >= CHARGE_TICKS ? 4 : 2;
        for (int i = 0; i < count; i++)
            level().addParticle(VoidHeart.EYE_BEAM_PARTICLE, start.x, start.y, start.z, end.x, end.y, end.z);
    }

    @Override
    public boolean isPickable()
    {
        return super.isPickable() && !(level().isClientSide() && hiddenFromLocalPick.test(this));
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
    public boolean shouldBeSaved()
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
    protected @Nullable SoundEvent getHurtSound(DamageSource source)
    {
        return SoundEvents.SLIME_HURT_SMALL;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound()
    {
        return SoundEvents.SLIME_DEATH_SMALL;
    }
}
