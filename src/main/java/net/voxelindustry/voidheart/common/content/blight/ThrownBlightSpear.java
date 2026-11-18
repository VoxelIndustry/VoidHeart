package net.voxelindustry.voidheart.common.content.blight;

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
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The spear blight in flight. Where it lands it opens a hole of darkness, see {@link SpearHole}, which draws mobs in and crushes them, then it flies back
 * to its thrower. Its kills are its thrower's. It never pulls or crushes players, its thrower's pets or raised wraiths, and what is tagged
 * {@link #UNPULLABLE} is crushed but not drawn in.
 */
public class ThrownBlightSpear extends AbstractArrow
{
    public static final ResourceKey<DamageType> CRUSH       = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "abyss_crush"));
    /** What resists the hole's pull, bosses above all. */
    public static final TagKey<EntityType<?>>   UNPULLABLE  = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, "unpullable"));

    /** The game time its hole opened at, or -1 while it hasn't. */
    private static final EntityDataAccessor<Long>     DATA_HOLE_AT = SynchedEntityData.defineId(ThrownBlightSpear.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Vector3fc> DATA_CENTER = SynchedEntityData.defineId(ThrownBlightSpear.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean>  DATA_RETURN  = SynchedEntityData.defineId(ThrownBlightSpear.class, EntityDataSerializers.BOOLEAN);

    /** Close enough to its thrower to be caught. */
    private static final double CATCH_REACH = 1.5;

    private int flight;

    public ThrownBlightSpear(EntityType<? extends ThrownBlightSpear> type, Level level)
    {
        super(type, level);
    }

    public ThrownBlightSpear(Level level, LivingEntity owner, ItemStack stack)
    {
        super(VoidHeartEntities.THROWN_BLIGHT_SPEAR, owner, level, stack, null);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_HOLE_AT, -1L);
        builder.define(DATA_CENTER, new Vector3f());
        builder.define(DATA_RETURN, false);
    }

    /** Ticks since its hole opened, by the world's clock, or -1 before it has. */
    public float holeAge(float partialTicks)
    {
        long at = entityData.get(DATA_HOLE_AT);
        return at < 0 ? -1 : Math.max(0, level().getGameTime() - at + partialTicks);
    }

    public boolean hasOpened()
    {
        return entityData.get(DATA_HOLE_AT) >= 0;
    }

    public boolean isHoleOpen()
    {
        return hasOpened() && !SpearHole.isClosed((int) holeAge(0));
    }

    public boolean isReturning()
    {
        return entityData.get(DATA_RETURN);
    }

    public Vec3 holeCenter()
    {
        var center = entityData.get(DATA_CENTER);
        return new Vec3(center.x(), center.y(), center.z());
    }

    @Override
    public void tick()
    {
        if (isHoleOpen())
        {
            // It hangs where it struck while the hole lasts
            setDeltaMovement(Vec3.ZERO);
            if (level() instanceof ServerLevel level)
                draw(level, (int) holeAge(0));
            super.tick();
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (hasOpened() && !isReturning() && !level().isClientSide())
            beginReturn();
        if (isReturning())
        {
            if (level() instanceof ServerLevel level)
                flyHome(level);
            super.tick();
            return;
        }

        super.tick();
        if (level().isClientSide() || hasOpened())
            return;
        // Thrown at nothing, or out of the world, it opens wherever it is
        if (++flight >= BlightBalance.SPEAR_FLIGHT_TICKS || getY() < level().getMinY())
            open(position());
    }

    @Override
    protected void onHitEntity(EntityHitResult result)
    {
        var target = result.getEntity();
        var owner = getOwner();
        var source = damageSources().trident(this, owner == null ? this : owner);
        if (!(target instanceof Player) && level() instanceof ServerLevel)
            target.hurtOrSimulate(source, BlightBalance.SPEAR_THROW_DAMAGE);
        playSound(SoundEvents.TRIDENT_HIT, 1, 1);
        // Into the middle of what it struck, so that is the first thing in the dark
        open(target.getBoundingBox().getCenter());
    }

    @Override
    protected void onHitBlock(BlockHitResult result)
    {
        super.onHitBlock(result);
        if (hasOpened())
            return;
        var normal = Vec3.atLowerCornerOf(result.getDirection().getUnitVec3i());
        open(result.getLocation().add(normal.scale(BlightBalance.HOLE_STANDOFF)));
    }

    private void open(Vec3 center)
    {
        if (level().isClientSide() || hasOpened())
            return;
        entityData.set(DATA_CENTER, new Vector3f((float) center.x, (float) center.y, (float) center.z));
        entityData.set(DATA_HOLE_AT, level().getGameTime());
        setDeltaMovement(Vec3.ZERO);
        setNoPhysics(true);
        level().playSound(null, center.x, center.y, center.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.2F, 0.5F);
    }

    /** Draws in what it may and crushes what is inside, on the crushing ticks. */
    private void draw(ServerLevel level, int age)
    {
        var center = holeCenter();
        var owner = getOwner();
        boolean crush = SpearHole.crushesAt(age);
        boolean crushed = false;
        for (var living : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(BlightBalance.HOLE_PULL_RANGE + 2)))
        {
            if (isSpared(living, owner))
                continue;
            var box = living.getBoundingBox();
            if (!living.getType().builtInRegistryHolder().is(UNPULLABLE))
            {
                living.setDeltaMovement(SpearHole.pulled(center.subtract(box.getCenter()), living.getDeltaMovement()));
                living.needsSync = true;
                living.resetFallDistance();
            }
            if (crush && SpearHole.isInside(Math.sqrt(box.distanceToSqr(center))))
            {
                living.hurtServer(level, damageSources().source(CRUSH, this, owner == null ? this : owner), BlightBalance.HOLE_CRUSH_DAMAGE);
                crushed = true;
            }
        }
        if (crushed)
            level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.5F);
    }

    /** Players, its thrower and what belongs to them are never drawn in. */
    private static boolean isSpared(LivingEntity living, @Nullable Entity owner)
    {
        if (!living.isAlive() || living.isSpectator() || living instanceof Player || living == owner || living instanceof ArmorStand || living instanceof HarvestWraith)
            return true;
        return owner != null && living instanceof OwnableEntity ownable && ownable.getOwner() == owner;
    }

    private void beginReturn()
    {
        entityData.set(DATA_RETURN, true);
        setNoPhysics(true);
        setInGround(false);
        playSound(SoundEvents.TRIDENT_RETURN, 2, 0.7F);
    }

    private void flyHome(ServerLevel level)
    {
        var owner = getOwner();
        if (!(owner instanceof LivingEntity living) || !living.isAlive() || owner.level() != level || owner instanceof ServerPlayer player && player.isSpectator())
        {
            // No one to go back to: it falls where it is for whoever finds it
            if (pickup == Pickup.ALLOWED)
                spawnAtLocation(level, getPickupItem(), 0.1F);
            discard();
            return;
        }
        var toward = owner.getEyePosition().subtract(position());
        if (toward.length() < CATCH_REACH)
        {
            if (!(owner instanceof Player player) || pickup != Pickup.ALLOWED || player.getInventory().add(getPickupItem()))
            {
                discard();
                return;
            }
            spawnAtLocation(level, getPickupItem(), 0.1F);
            discard();
            return;
        }
        setDeltaMovement(toward.normalize().scale(Math.min(toward.length(), BlightBalance.SPEAR_RETURN_SPEED)));
    }

    @Override
    protected @Nullable EntityHitResult findHitEntity(Vec3 from, Vec3 to)
    {
        return hasOpened() ? null : super.findHitEntity(from, to);
    }

    @Override
    protected boolean tryPickup(Player player)
    {
        // Only caught on its way back, from {@link #flyHome}
        return false;
    }

    @Override
    protected void tickDespawn()
    {
        // It always opens, then goes home or falls as an item, so it never lies about to despawn
    }

    @Override
    protected ItemStack getDefaultPickupItem()
    {
        return new ItemStack(VoidHeartItems.VOID_BLIGHT_SPEAR);
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent()
    {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    protected float getWaterInertia()
    {
        return 0.99F;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance)
    {
        return true;
    }

    /** The mobs it is drawing in, for game tests. */
    public List<LivingEntity> caught()
    {
        var center = holeCenter();
        var owner = getOwner();
        return level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(BlightBalance.HOLE_PULL_RANGE), living -> !isSpared(living, owner));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.putLong("HoleAt", entityData.get(DATA_HOLE_AT));
        var center = entityData.get(DATA_CENTER);
        output.putFloat("HoleX", center.x());
        output.putFloat("HoleY", center.y());
        output.putFloat("HoleZ", center.z());
        output.putBoolean("Returning", isReturning());
        output.putInt("Flight", flight);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        entityData.set(DATA_HOLE_AT, input.getLongOr("HoleAt", -1L));
        entityData.set(DATA_CENTER, new Vector3f(input.getFloatOr("HoleX", (float) getX()), input.getFloatOr("HoleY", (float) getY()), input.getFloatOr("HoleZ", (float) getZ())));
        entityData.set(DATA_RETURN, input.getBooleanOr("Returning", false));
        flight = input.getIntOr("Flight", 0);
        // A hole saved long ago has closed by now, and one that was open goes on where it was
        if (hasOpened())
            setNoPhysics(true);
    }
}
