package net.voxelindustry.voidheart.common.content.sanctum;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.content.sanctum.SanctumBalance.*;

/**
 * A lob of bile from the Vile Flesh Mass, falling in an arc. Where it lands it leaves a pool that eats at whoever stands in it.
 */
public class BileGlob extends Projectile
{
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "bile"));

    private static final double GRAVITY = 0.04;
    private static final int MAX_TICKS = 120;

    public BileGlob(EntityType<? extends BileGlob> type, Level level)
    {
        super(type, level);
    }

    public BileGlob(Level level, LivingEntity owner, Vec3 from, Vec3 to)
    {
        this(VoidHeartEntities.BILE_GLOB, level);
        setOwner(owner);
        setPos(from);
        setDeltaMovement(launch(from, to));
    }

    /**
     * @return a velocity that lands on a spot at the glob's speed, taking the higher arc when the spot is out of a flat throw's reach
     */
    static Vec3 launch(Vec3 from, Vec3 to)
    {
        double dx = to.x - from.x, dz = to.z - from.z, dy = to.y - from.y;
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1e-3)
            return new Vec3(0, BILE_SPEED, 0);
        // Ticks in the air for a throw at this speed along the ground, then the climb that brings it down on the spot then
        double ticks = Math.max(8, flat / (BILE_SPEED * 0.7));
        double vy = dy / ticks + GRAVITY * ticks / 2;
        return new Vec3(dx / ticks, vy, dz / ticks);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!level().isClientSide())
        {
            if (tickCount > MAX_TICKS)
            {
                discard();
                return;
            }
            var hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS)
            {
                hitTargetOrDeflectSelf(hit);
                if (isRemoved())
                    return;
            }
        }
        var motion = getDeltaMovement();
        setPos(position().add(motion));
        setDeltaMovement(motion.x * 0.99, motion.y - GRAVITY, motion.z * 0.99);
        if (level().isClientSide())
            level().addParticle(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 0, 0, 0);
    }

    @Override
    protected boolean canHitEntity(Entity entity)
    {
        return entity instanceof Player && super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result)
    {
        super.onHitEntity(result);
        if (level() instanceof ServerLevel level)
        {
            result.getEntity().hurtServer(level, damageSources().source(DAMAGE, this, getOwner()), BILE_HIT_DAMAGE);
            splash(level, result.getEntity().position());
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result)
    {
        super.onHitBlock(result);
        if (level() instanceof ServerLevel level)
        {
            var at = result.getLocation();
            // A wall or ceiling it struck drips it to the floor below
            if (result.getDirection() != Direction.UP)
                at = at.add(result.getDirection().getStepX() * 0.3, 0, result.getDirection().getStepZ() * 0.3);
            splash(level, at);
        }
    }

    private void splash(ServerLevel level, Vec3 at)
    {
        level.playSound(null, at.x, at.y, at.z, VoidHeartSounds.BILE_SPLASH, SoundSource.HOSTILE, 1.2F, 0.8F + random.nextFloat() * 0.3F);
        BilePool.spill(level, getOwner() instanceof LivingEntity owner ? owner : null, at);
        discard();
    }
}
