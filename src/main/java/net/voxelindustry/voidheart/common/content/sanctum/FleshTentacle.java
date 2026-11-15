package net.voxelindustry.voidheart.common.content.sanctum;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.common.content.sanctum.SanctumBalance.*;

/**
 * A tentacle of the Vile Flesh Mass. One kind bursts up through the floor where the flesh swelled for a moment, and sinks back. The other
 * reaches from the mass to someone, wraps round them and draws them in to its maw, unless it is cut first: it stands where it holds them, so a
 * blow at the tentacle lands on it.
 */
public class FleshTentacle extends Entity
{
    private static final EntityDataAccessor<Boolean> DATA_GRAB = SynchedEntityData.defineId(FleshTentacle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(FleshTentacle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VICTIM = SynchedEntityData.defineId(FleshTentacle.class, EntityDataSerializers.INT);

    /** Ticks an eruption stands up before sinking, then takes to sink. */
    public static final int RISE_TICKS = 4, HOLD_TICKS = 12, SINK_TICKS = 10;
    public static final int ERUPT_TICKS = ERUPT_WARN_TICKS + RISE_TICKS + HOLD_TICKS + SINK_TICKS;
    public static final double ERUPT_HEIGHT = 3.4;

    private float health = GRAB_TENTACLE_HEALTH;

    public FleshTentacle(EntityType<? extends FleshTentacle> type, Level level)
    {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static void erupt(ServerLevel level, VileFleshMass mass, Vec3 at)
    {
        var tentacle = new FleshTentacle(VoidHeartEntities.FLESH_TENTACLE, level);
        tentacle.entityData.set(DATA_OWNER, mass.getId());
        tentacle.setPos(at);
        level.addFreshEntity(tentacle);
        level.playSound(null, at.x, at.y, at.z, VoidHeartSounds.TENTACLE_WARN, SoundSource.HOSTILE, 1, 0.8F + level.getRandom().nextFloat() * 0.3F);
    }

    public static void grab(ServerLevel level, VileFleshMass mass, LivingEntity victim)
    {
        var tentacle = new FleshTentacle(VoidHeartEntities.FLESH_TENTACLE, level);
        tentacle.entityData.set(DATA_GRAB, true);
        tentacle.entityData.set(DATA_OWNER, mass.getId());
        tentacle.entityData.set(DATA_VICTIM, victim.getId());
        tentacle.setPos(mass.mouth());
        level.addFreshEntity(tentacle);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        builder.define(DATA_GRAB, false);
        builder.define(DATA_OWNER, -1);
        builder.define(DATA_VICTIM, -1);
    }

    public boolean isGrab()
    {
        return entityData.get(DATA_GRAB);
    }

    public boolean isGrabOf(VileFleshMass mass)
    {
        return isGrab() && entityData.get(DATA_OWNER) == mass.getId();
    }

    public @Nullable VileFleshMass owner()
    {
        return level().getEntity(entityData.get(DATA_OWNER)) instanceof VileFleshMass mass ? mass : null;
    }

    public @Nullable LivingEntity victim()
    {
        return level().getEntity(entityData.get(DATA_VICTIM)) instanceof LivingEntity living ? living : null;
    }

    /** @return how far an eruption stands out of the floor at an age, 0 to 1 */
    public static float risen(float age)
    {
        float since = age - ERUPT_WARN_TICKS;
        if (since < 0)
            return 0;
        if (since < RISE_TICKS)
            return since / RISE_TICKS;
        if (since < RISE_TICKS + HOLD_TICKS)
            return 1;
        return Math.max(0, 1 - (since - RISE_TICKS - HOLD_TICKS) / SINK_TICKS);
    }

    /** @return how far a grab has reached its victim at an age, 0 to 1 */
    public static float reached(float age)
    {
        return Math.min(1, age / GRAB_REACH_TICKS);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!(level() instanceof ServerLevel level))
            return;
        if (isGrab())
            tickGrab(level);
        else
            tickEruption(level);
    }

    private void tickEruption(ServerLevel level)
    {
        if (tickCount < ERUPT_WARN_TICKS && tickCount % 4 == 0)
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, VoidHeartBlocks.VILE_FLESH.defaultBlockState()), getX(), getY() + 0.05, getZ(), 4, 0.35, 0, 0.35, 0.05);
        if (tickCount == ERUPT_WARN_TICKS)
        {
            level.playSound(null, getX(), getY(), getZ(), VoidHeartSounds.TENTACLE_ERUPT, SoundSource.HOSTILE, 1.5F, 0.7F + random.nextFloat() * 0.2F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, VoidHeartBlocks.VILE_FLESH.defaultBlockState()), getX(), getY() + 0.2, getZ(), 30, 0.4, 0.3, 0.4, 0.2);
            var mass = owner();
            var source = mass == null ? damageSources().generic() : damageSources().source(VileFleshMass.ERUPTION, mass);
            var box = new AABB(getX() - ERUPT_RADIUS, getY() - 0.5, getZ() - ERUPT_RADIUS, getX() + ERUPT_RADIUS, getY() + ERUPT_HEIGHT, getZ() + ERUPT_RADIUS);
            for (var player : level.getEntitiesOfClass(Player.class, box, EntitySelector.NO_CREATIVE_OR_SPECTATOR))
            {
                double dx = player.getX() - getX(), dz = player.getZ() - getZ();
                if (dx * dx + dz * dz > (ERUPT_RADIUS + player.getBbWidth() / 2) * (ERUPT_RADIUS + player.getBbWidth() / 2))
                    continue;
                if (player.hurtServer(level, source, ERUPT_DAMAGE))
                {
                    player.setDeltaMovement(player.getDeltaMovement().x, ERUPT_LIFT, player.getDeltaMovement().z);
                    SanctumPush.sync(player);
                }
            }
        }
        if (tickCount >= ERUPT_TICKS)
            discard();
    }

    private void tickGrab(ServerLevel level)
    {
        var mass = owner();
        var victim = victim();
        if (mass == null || !mass.isAlive() || victim == null || !victim.isAlive() || tickCount > GRAB_MAX_TICKS
                || (victim instanceof Player player && (player.isCreative() || player.isSpectator())) || mass.edgeDistance(victim) > GRAB_RANGE + 6)
        {
            release(level);
            return;
        }
        // It holds round their middle, and stands there to be struck
        setPos(victim.getX(), victim.getY() + victim.getBbHeight() * 0.45, victim.getZ());
        if (tickCount < GRAB_REACH_TICKS)
            return;
        if (tickCount == GRAB_REACH_TICKS)
            level.playSound(null, getX(), getY(), getZ(), VoidHeartSounds.TENTACLE_WRAP, SoundSource.HOSTILE, 1.2F, 0.8F);
        if (mass.edgeDistance(victim) <= BITE_REACH)
        {
            mass.bite(level, victim);
            discard();
            return;
        }
        var pull = new Vec3(mass.getX() - victim.getX(), 0, mass.getZ() - victim.getZ()).normalize().scale(GRAB_PULL);
        victim.setDeltaMovement(pull.x, Math.max(victim.getDeltaMovement().y, victim.onGround() ? 0.05 : -0.08), pull.z);
        SanctumPush.sync(victim);
    }

    private void release(ServerLevel level)
    {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, VoidHeartBlocks.VILE_FLESH.defaultBlockState()), getX(), getY(), getZ(), 20, 0.3, 0.3, 0.3, 0.1);
        discard();
    }

    /** A grab is cut loose by blows; an eruption shrugs them off. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        // Whoever is held can strike it too: it wraps round them, so their blows land on it wherever they aim
        if (!isGrab() || isRemoved() || tickCount < GRAB_REACH_TICKS || source.getEntity() instanceof VileFleshMass)
            return false;
        health -= damage;
        level.playSound(null, getX(), getY(), getZ(), VoidHeartSounds.TENTACLE_HURT, SoundSource.HOSTILE, 1, 0.9F + random.nextFloat() * 0.2F);
        if (health <= 0)
        {
            level.playSound(null, getX(), getY(), getZ(), VoidHeartSounds.TENTACLE_SEVER, SoundSource.HOSTILE, 1.4F, 0.8F);
            release(level);
        }
        return true;
    }

    @Override
    public boolean isPickable()
    {
        return isGrab() && !isRemoved();
    }

    @Override
    public boolean isAttackable()
    {
        return isGrab();
    }

    @Override
    public boolean shouldBeSaved()
    {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
    }
}
