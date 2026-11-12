package net.voxelindustry.voidheart.common.content.underway.security;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * The mortar in a turret well. It only works on power: asleep it lies under the armoured plate of its well, where nothing harms it. Awake and with someone to shoot at, it rises and lobs slow shells, a few in a row. Each shell falls where its target stood as it left, on a circle shown on the ground for as long as the shell flies.
 * <p>
 * Moving on is how a shell is escaped, and it cannot drop one close to itself: it stays up for whoever stands next to it, which is where it is wrecked from. It needs no sight of whoever a searchlight caught.
 */
public class TurretWell extends MountedUnit
{
    public enum Phase
    {
        /** Under its plate, or asleep. */
        DOWN,
        RISING,
        /** Up and looking for someone. */
        UP,
        FIRING,
        COOLING;

        private static final Phase[] VALUES = values();

        static Phase of(int ordinal)
        {
            return VALUES[Math.clamp(ordinal, 0, VALUES.length - 1)];
        }
    }

    public static final float WIDTH = 0.8F;
    public static final float HEIGHT = 1.0F;
    /** How high over the line from its muzzle to its mark a shell climbs. */
    public static final double ARC = 2.2;
    private static final double MUZZLE_HEIGHT = 0.95;
    private static final int FIRST_SHELL_TICKS = 10;
    private static final double SAME_LEVEL = 6;

    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(TurretWell.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3fc> DATA_MARK = SynchedEntityData.defineId(TurretWell.class, EntityDataSerializers.VECTOR3);
    // How many shells left so far, so that two shells on one mark still read as a change
    private static final EntityDataAccessor<Integer> DATA_SHELLS = SynchedEntityData.defineId(TurretWell.class, EntityDataSerializers.INT);

    private int phaseTicks;
    private int idleTicks;
    private int shellsLeft;
    private int impactIn;

    /** Kept by the client for show: how far it has risen, and ticks its last shell has flown for, or -1 with none in the air. */
    public float out;
    public float outO;
    public int flight = -1;

    public TurretWell(EntityType<? extends TurretWell> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, SecurityBalance.WELL_HEALTH).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData)
    {
        super.defineSynchedData(entityData);
        entityData.define(DATA_PHASE, Phase.DOWN.ordinal());
        entityData.define(DATA_MARK, new Vector3f());
        entityData.define(DATA_SHELLS, 0);
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

    /** @return where its last shell falls */
    public Vec3 mark()
    {
        return UnderwayShots.vec(entityData.get(DATA_MARK));
    }

    public int shellsFired()
    {
        return entityData.get(DATA_SHELLS);
    }

    public Vec3 muzzle()
    {
        return position().add(0, MUZZLE_HEIGHT, 0);
    }

    /**
     * @param progress how far through its flight a shell is, from 0 to 1
     * @return where the shell is
     */
    public Vec3 shellAt(float progress)
    {
        return muzzle().lerp(mark(), progress).add(0, 4 * ARC * progress * (1 - progress), 0);
    }

    /** @return the nearest player it sees, or failing that whoever a searchlight holds, who needs not be seen from here */
    private @Nullable Player findSeen(ServerLevel level)
    {
        var seen = UnderwayShots.nearestSeen(level, this, muzzle(), SecurityBalance.WELL_RANGE);
        if (seen != null)
            return seen;
        var spotted = link().spotted(level);
        return spotted != null && spotted.distanceToSqr(this) <= SecurityBalance.WELL_RANGE * SecurityBalance.WELL_RANGE ? spotted : null;
    }

    /** @return whether it can drop a shell on someone: not right beside it, nor on another level */
    private boolean canReach(Player seen)
    {
        return Math.hypot(seen.getX() - getX(), seen.getZ() - getZ()) >= SecurityBalance.WELL_MIN_RANGE && Math.abs(seen.getY() - getY()) <= SAME_LEVEL;
    }

    @Override
    protected void guard(ServerLevel level, boolean awake)
    {
        // A shell already in the air falls whatever became of the mortar's power
        if (impactIn > 0 && --impactIn == 0)
            burst(level, mark());
        var phase = phase();
        if (!awake)
        {
            if (phase != Phase.DOWN)
                setPhase(Phase.DOWN, 0);
            return;
        }
        var seen = phase == Phase.COOLING ? null : findSeen(level);
        // Someone right beside it must not shield someone further off
        var target = seen == null || canReach(seen) ? seen : UnderwayShots.nearestSeen(level, this, muzzle(), SecurityBalance.WELL_RANGE, this::canReach);
        if (seen != null)
            link().raiseAlarm(level);
        switch (phase)
        {
            case DOWN ->
            {
                if (seen == null)
                    break;
                setPhase(Phase.RISING, SecurityBalance.WELL_RISE_TICKS);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_EXTEND, getSoundSource(), 1.0F, 0.6F);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, getSoundSource(), 0.6F, 0.6F);
                UnderwayShots.puff(level, position().add(0, 0.4, 0));
            }
            case RISING ->
            {
                if (--phaseTicks <= 0)
                    setPhase(Phase.UP, 0);
            }
            case UP ->
            {
                if (target != null)
                {
                    idleTicks = 0;
                    shellsLeft = SecurityBalance.WELL_SALVO_SHELLS;
                    setPhase(Phase.FIRING, FIRST_SHELL_TICKS);
                }
                else if (seen != null)
                    idleTicks = 0;
                else if (++idleTicks >= SecurityBalance.WELL_STAND_DOWN_TICKS)
                {
                    idleTicks = 0;
                    setPhase(Phase.DOWN, 0);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_CONTRACT, getSoundSource(), 1.0F, 0.6F);
                }
            }
            case FIRING ->
            {
                if (phaseTicks-- > 0)
                    break;
                if (target != null)
                    launch(level, target);
                phaseTicks = SecurityBalance.WELL_SHELL_EVERY;
                if (target == null || --shellsLeft <= 0)
                    setPhase(Phase.COOLING, SecurityBalance.WELL_COOL_TICKS);
            }
            case COOLING ->
            {
                if (--phaseTicks <= 0)
                    setPhase(Phase.UP, 0);
            }
        }
    }

    private void launch(ServerLevel level, Player target)
    {
        var mark = target.position();
        entityData.set(DATA_MARK, new Vector3f((float) mark.x, (float) mark.y, (float) mark.z));
        entityData.set(DATA_SHELLS, shellsFired() + 1);
        impactIn = SecurityBalance.WELL_FLIGHT_TICKS;
        var muzzle = muzzle();
        UnderwayShots.puff(level, muzzle);
        level.sendParticles(ParticleTypes.CLOUD, muzzle.x, muzzle.y, muzzle.z, 6, 0.1, 0.1, 0.1, 0.05);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, getSoundSource(), 2.0F, 0.5F);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.PISTON_EXTEND, getSoundSource(), 1.5F, 0.5F);
    }

    /** A shell lands: it harms whoever stands in its circle with nothing solid between, most at the middle, and breaks no block. */
    private void burst(ServerLevel level, Vec3 at)
    {
        var middle = at.add(0, 0.5, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, middle.x, middle.y, middle.z, 3, 0.6, 0.2, 0.6, 0);
        level.sendParticles(ParticleTypes.CLOUD, middle.x, middle.y, middle.z, 16, 0.8, 0.2, 0.8, 0.05);
        level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.GENERIC_EXPLODE.value(), getSoundSource(), 2.0F, 0.8F);
        double radius = SecurityBalance.WELL_BLAST_RADIUS;
        for (var player : level.getEntitiesOfClass(Player.class, new AABB(middle, middle).inflate(radius + 1), EntitySelector.NO_CREATIVE_OR_SPECTATOR))
        {
            double away = Math.hypot(player.getX() - at.x, player.getZ() - at.z);
            if (away > radius || Math.abs(player.getY() - at.y) > radius
                    || level.clip(new ClipContext(middle, player.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS)
                continue;
            player.hurtServer(level, damageSources().explosion(this, this), (float) (SecurityBalance.WELL_SHELL_DAMAGE * (1 - 0.6 * away / radius)));
        }
    }

    @Override
    protected boolean isArmoured(DamageSource source)
    {
        return phase() == Phase.DOWN || phase() == Phase.RISING;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        if (DATA_SHELLS.equals(accessor) && level().isClientSide())
            flight = 0;
    }

    @Override
    protected void clientTick()
    {
        outO = out;
        float step = 1F / SecurityBalance.WELL_RISE_TICKS;
        out = Math.clamp(out + (phase() == Phase.DOWN ? -step : step), 0, 1);
        if (flight < 0)
            return;
        var shell = shellAt(flight / (float) SecurityBalance.WELL_FLIGHT_TICKS);
        level().addParticle(ParticleTypes.SMOKE, shell.x, shell.y, shell.z, 0, 0, 0);
        if (++flight > SecurityBalance.WELL_FLIGHT_TICKS)
            flight = -1;
    }
}
