package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Drifts after its target slowly enough to be outrun around a corner. Armor does nothing against it, but a raised shield stops it,
 * and a hit sends it back where the attacker is looking.
 */
public class VoidOrb extends Projectile
{
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "void_orb"));

    private static final double SPEED = 0.26;
    private static final double TURN_RATE = 0.09;
    private static final double DEFLECTED_SPEED = 0.7;
    private static final int LIFETIME = 10 * 20;
    private static final float PICK_RADIUS = 0.6F;
    private static final DustParticleOptions TRAIL = new DustParticleOptions(0x36C5B0, 1);

    private @Nullable LivingEntity target;
    private float damage;
    private boolean deflected;

    public VoidOrb(EntityType<? extends VoidOrb> type, Level level)
    {
        super(type, level);
        setNoGravity(true);
    }

    public VoidOrb(Level level, LivingEntity owner, LivingEntity target, float damage)
    {
        this(VoidHeartEntities.VOID_ORB, level);
        setOwner(owner);
        this.target = target;
        this.damage = damage;
        setDeltaMovement(target.getBoundingBox().getCenter().subtract(owner.getEyePosition()).normalize().scale(SPEED));
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
            if (tickCount > LIFETIME)
            {
                fizzle((ServerLevel) level());
                return;
            }
            steer();
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
        if (level().isClientSide() && tickCount % 2 == 0)
            level().addParticle(TRAIL, getX(), getY() + getBbHeight() / 2, getZ(), 0, 0, 0);
    }

    private void steer()
    {
        if (deflected || target == null || !target.isAlive())
            return;

        var wanted = target.getBoundingBox().getCenter().subtract(position()).normalize();
        var heading = getDeltaMovement().normalize().lerp(wanted, TURN_RATE).normalize();
        setDeltaMovement(heading.scale(SPEED));
        needsSync = true;
    }

    @Override
    protected boolean canHitEntity(Entity entity)
    {
        if (!super.canHitEntity(entity))
            return false;
        return deflected ? entity instanceof LivingEntity : entity instanceof Player;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit)
    {
        super.onHitEntity(hit);
        if (!(level() instanceof ServerLevel level) || !(hit.getEntity() instanceof LivingEntity living))
            return;

        // The armor piercing type also passes shields, so a shield gets its say with an ordinary projectile first
        if (living.applyItemBlocking(level, damageSources().thrown(this, getOwner()), damage) > 0)
            return;

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
        level.playSound(null, getX(), getY(), getZ(), VoidHeartSounds.ORB_HIT, SoundSource.HOSTILE, 1, 1);
        level.sendParticles(TRAIL, getX(), getY(), getZ(), 18, 0.25, 0.25, 0.25, 0.08);
        level.sendParticles(ParticleTypes.SQUID_INK, getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.02);
        discard();
    }

    @Override
    public boolean isPickable()
    {
        return true;
    }

    @Override
    public float getPickRadius()
    {
        return PICK_RADIUS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount)
    {
        var attacker = source.getEntity();
        if (attacker == null)
            return false;

        setDeltaMovement(attacker.getLookAngle().scale(DEFLECTED_SPEED));
        setOwner(attacker);
        deflected = true;
        needsSync = true;
        level.playSound(null, getX(), getY(), getZ(), VoidHeartSounds.ORB_DEFLECT, SoundSource.PLAYERS, 1, 1);
        return true;
    }
}
