package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Flies straight and dies out soon after, so stepping aside or backing away both work. Armor and shields take it as they do an arrow.
 */
public class ArcaneBolt extends Projectile
{
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "arcane_bolt"));
    public static final double SPEED = 0.55;
    public static final double MAX_TRAVEL = 8;

    private static final DustParticleOptions TRAIL = new DustParticleOptions(0x39E05A, 0.8F);

    private float damage;

    public ArcaneBolt(EntityType<? extends ArcaneBolt> type, Level level)
    {
        super(type, level);
        setNoGravity(true);
    }

    public ArcaneBolt(Level level, LivingEntity owner, Vec3 from, Vec3 direction, float damage)
    {
        this(VoidHeartEntities.ARCANE_BOLT, level);
        setOwner(owner);
        this.damage = damage;
        setPos(from.x, from.y - getBbHeight() / 2, from.z);
        setDeltaMovement(direction.scale(SPEED));
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
            if (tickCount * SPEED >= MAX_TRAVEL)
            {
                fizzle((ServerLevel) level());
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

        setPos(position().add(getDeltaMovement()));
        if (level().isClientSide())
            level().addParticle(TRAIL, getX(), getY() + getBbHeight() / 2, getZ(), 0, 0, 0);
    }

    @Override
    protected boolean canHitEntity(Entity entity)
    {
        return super.canHitEntity(entity) && entity instanceof Player;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit)
    {
        super.onHitEntity(hit);
        if (level() instanceof ServerLevel level && hit.getEntity() instanceof LivingEntity living)
            living.hurtServer(level, damageSources().source(DAMAGE, this, getOwner()), damage);
    }

    @Override
    protected void onHit(HitResult hit)
    {
        super.onHit(hit);
        if (level() instanceof ServerLevel level)
            fizzle(level);
    }

    private void fizzle(ServerLevel level)
    {
        level.playSound(null, getX(), getY(), getZ(), VoidHeartSounds.ARCANE_BOLT_HIT, SoundSource.HOSTILE, 0.8F, 1);
        level.sendParticles(TRAIL, getX(), getY(), getZ(), 12, 0.15, 0.15, 0.15, 0.05);
        discard();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount)
    {
        return false;
    }
}
