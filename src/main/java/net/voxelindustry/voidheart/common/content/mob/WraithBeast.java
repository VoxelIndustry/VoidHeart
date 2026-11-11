package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.salt.SaltWard;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;
import org.jspecify.annotations.Nullable;

/**
 * A horse of the wraith family that is never solid but to strike. It gallops straight through walls, floors and anything in between, and nothing touches it
 * on the way. Beside its prey it takes shape on open ground, rears for a second, which is the time to step back, and brings its hooves down hard enough to
 * knock a shield out of the hands holding it. It stays solid and open to blows a moment longer, then fades back out. Arrows and other projectiles never land:
 * it phases aside from them, solid or not.
 * <p>
 * Salt holds it material. Out of phase it comes out of a wall rather than into a ward, and once in a ward it walks like any horse, collides, and arrows find
 * it. It stays held until its next strike, then fades out only if no salt is near.
 */
public class WraithBeast extends VoidMonster
{
    public static final float WIDTH = 1.2F;
    public static final float HEIGHT = 1.75F;
    public static final float EYE_HEIGHT = 1.6F;

    public static final int WINDUP_TICKS = 20;
    public static final int RECOVER_TICKS = 30;
    // Time from one strike to the next at the quickest
    public static final int STRIKE_INTERVAL = 60;
    public static final float SHIELD_DISABLE_SECONDS = 5;
    public static final byte DODGE_EVENT = 91;
    public static final int DODGE_TICKS = 10;

    public static final float HEALTH = 70;
    public static final float DAMAGE = 16;

    public enum Phase
    {
        PHANTOM,
        WINDUP,
        RECOVER,
        // Held material by salt, walking after its prey
        HELD;

        public boolean isSolid()
        {
            return this != PHANTOM;
        }
    }

    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(WraithBeast.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_WARDED = SynchedEntityData.defineId(WraithBeast.class, EntityDataSerializers.BOOLEAN);

    private static final int EXPERIENCE = 25;
    private static final double SENSE_RANGE = 32;
    private static final double GALLOP_SPEED = 0.36;
    private static final double CLIMB_SPEED = 0.2;
    private static final double WANDER_SPEED = 0.1;
    private static final int WANDER_MIN_TICKS = 80;
    private static final int WANDER_SPREAD_TICKS = 80;
    private static final int WANDER_RANGE = 8;
    private static final int WANDER_TRIES = 5;
    // How far from its prey it takes shape, center to center, and how far its hooves still reach when they come down
    private static final double STRIKE_SPOT = 1.9;
    private static final double MANIFEST_RANGE = 2.8;
    private static final double REACH = 3.1;
    private static final double REACH_UP = 2.2;
    private static final int MANIFEST_SPOTS = 8;
    private static final int MANIFEST_CHECK_TICKS = 4;
    private static final double DODGE_SENSE = 3;
    private static final int DODGE_COOLDOWN = 6;
    private static final int THROUGH_WALL_PARTICLE_TICKS = 4;
    private static final int SALT_CRACKLE_TICKS = 20;
    private static final double KNOCKBACK_RESISTANCE = 0.8;
    private static final double ATTACK_KNOCKBACK = 1.2;
    private static final double HELD_WALK_SPEED = 1.1;
    // Shrinks a swept box so a face lying exactly on a block edge doesn't count the block beyond it
    private static final double EDGE_SLACK = 1e-4;
    private static final int REPATH_TICKS = 10;

    private int phaseTicks;
    private int strikeCooldown;
    private int dodgeCooldown;
    private int saltCrackleDelay;
    private int wanderDelay;
    private @Nullable Vec3 wanderTo;

    // Client side only: when the phase last changed and when it last phased aside, on the client's count
    public int phaseChangedTick = -100;
    public boolean wasSolid;
    public int dodgedTick = -100;
    public boolean dodgeLeft;

    public WraithBeast(EntityType<? extends WraithBeast> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
        setPhase(Phase.PHANTOM);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(HEALTH, DAMAGE, 0.3)
                .add(Attributes.FOLLOW_RANGE, SENSE_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
                .add(Attributes.ATTACK_KNOCKBACK, ATTACK_KNOCKBACK);
    }

    public Phase phase()
    {
        return Phase.values()[Mth.clamp(entityData.get(DATA_PHASE), 0, Phase.values().length - 1)];
    }

    public boolean isSolid()
    {
        return phase().isSolid();
    }

    /**
     * @return whether salt lies near enough to hold it material and keep it from slipping aside
     */
    public boolean isWarded()
    {
        return entityData.get(DATA_WARDED);
    }

    /**
     * Holds it solid where it stands, for galleries and tests that look at it with no AI running.
     */
    public void showSolid()
    {
        setPhase(Phase.RECOVER);
    }

    private void setPhase(Phase phase)
    {
        entityData.set(DATA_PHASE, phase.ordinal());
        noPhysics = !phase.isSolid();
        setNoGravity(!phase.isSolid());
        fallDistance = 0;
        phaseTicks = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, Phase.PHANTOM.ordinal());
        builder.define(DATA_WARDED, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        if (DATA_PHASE.equals(accessor))
        {
            wasSolid = !noPhysics;
            noPhysics = !isSolid();
            phaseChangedTick = tickCount;
        }
    }

    @Override
    protected void registerGoals()
    {
        targetSelector.addGoal(1, new SenseThroughWallsGoal());
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.WRAITH_BEAST;
    }

    @Override
    public void travel(Vec3 input)
    {
        // Moved by hand while out of phase, walks while salt holds it, and stands still to strike but for falling
        if (phase() == Phase.HELD)
            super.travel(input);
        else if (isSolid())
            super.travel(Vec3.ZERO);
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);
        phaseTicks++;
        if (strikeCooldown > 0)
            strikeCooldown--;
        if (dodgeCooldown > 0)
            dodgeCooldown--;
        if (saltCrackleDelay > 0)
            saltCrackleDelay--;

        boolean warded = SaltWard.isWardedBelow(level, blockPosition());
        entityData.set(DATA_WARDED, warded);
        if (warded && tickCount % SALT_CRACKLE_TICKS == 0)
            crackle(level, SaltWard.wardBelow(level, blockPosition()));

        var target = validTarget();
        switch (phase())
        {
            case PHANTOM ->
            {
                if (warded && level.noCollision(this, getBoundingBox()))
                    hold(level);
                else
                    phantomStep(level, target);
            }
            case WINDUP -> windupStep(level, target);
            case RECOVER -> recoverStep(level, target, warded);
            case HELD -> heldStep(level, target, warded);
        }
        if (!warded)
            sidestepProjectiles(level);
    }

    private @Nullable LivingEntity validTarget()
    {
        var target = getTarget();
        if (target == null || !target.isAlive() || target instanceof Player player && (player.isCreative() || player.isSpectator()))
            return null;
        return target;
    }

    private void phantomStep(ServerLevel level, @Nullable LivingEntity target)
    {
        if (target == null)
        {
            wander(level);
            return;
        }
        wanderTo = null;

        if (strikeCooldown == 0 && horizontalDistanceTo(target.position()) < MANIFEST_RANGE && Math.abs(target.getY() - getY()) < REACH_UP
            && tickCount % MANIFEST_CHECK_TICKS == 0)
        {
            var spot = manifestSpot(target);
            if (spot != null)
            {
                manifest(level, spot, target);
                return;
            }
        }

        var away = position().subtract(target.position()).multiply(1, 0, 1);
        var side = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
        var goal = target.position().add(side.scale(STRIKE_SPOT));
        glideTowards(level, goal, GALLOP_SPEED);
        face(target.position().subtract(position()));

        if (tickCount % THROUGH_WALL_PARTICLE_TICKS == 0 && !level.noCollision(this, getBoundingBox()))
        {
            var center = getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 2, WIDTH / 2, HEIGHT / 3, WIDTH / 2, 0.01);
        }
    }

    private void wander(ServerLevel level)
    {
        if (wanderTo == null)
        {
            if (--wanderDelay > 0)
                return;
            wanderDelay = WANDER_MIN_TICKS + random.nextInt(WANDER_SPREAD_TICKS);
            for (int i = 0; i < WANDER_TRIES && wanderTo == null; i++)
            {
                var spot = position().add(random.nextInt(WANDER_RANGE * 2 + 1) - WANDER_RANGE, 0, random.nextInt(WANDER_RANGE * 2 + 1) - WANDER_RANGE);
                if (standsAt(spot))
                    wanderTo = spot;
            }
            return;
        }
        if (position().distanceToSqr(wanderTo) < WANDER_SPEED * WANDER_SPEED || !glideTowards(level, wanderTo, WANDER_SPEED))
        {
            wanderTo = null;
            return;
        }
        face(wanderTo.subtract(position()));
    }

    /**
     * @return whether it moved at all
     */
    private boolean glideTowards(ServerLevel level, Vec3 goal, double speed)
    {
        var delta = goal.subtract(position());
        var flat = delta.multiply(1, 0, 1);
        double flatLength = flat.length();
        var step = flatLength > speed ? flat.scale(speed / flatLength) : flat;
        step = step.add(0, Mth.clamp(delta.y, -CLIMB_SPEED, CLIMB_SPEED), 0);

        for (var tried : new Vec3[] { step, new Vec3(step.x, step.y, 0), new Vec3(0, step.y, step.z), new Vec3(0, step.y, 0) })
        {
            if (tried.lengthSqr() < 1e-6)
                continue;
            var next = position().add(tried);
            if (mayPass(level, next))
            {
                setPos(next);
                return true;
            }
        }
        return false;
    }

    /**
     * Out of phase it passes anything breakable, but never the pocket's wall, bedrock and the like. It only steps into a ward where it stands in the open,
     * since there it turns material.
     */
    private boolean mayPass(ServerLevel level, Vec3 spot)
    {
        if (!mayGoTo(spot))
            return false;
        // Every block its middle passes from here to there, its full height up, so a step past a corner can't skip one. Its flanks may brush a wall
        // while out of phase, as any wraith's do
        if (!isCrossable(level, new AABB(position(), spot).expandTowards(0, HEIGHT, 0)))
            return false;
        var feet = BlockPos.containing(spot);
        var salt = SaltWard.wardBelow(level, feet);
        if (salt != null && !level.noCollision(this, aabbAt(spot)))
        {
            crackle(level, salt);
            return false;
        }
        return true;
    }

    private static boolean isUncrossable(Level level, BlockPos pos)
    {
        var state = level.getBlockState(pos);
        return state.is(VoidHeartBlocks.POCKET_WALL) || state.getDestroySpeed(level, pos) < 0;
    }

    /**
     * @return whether its whole body, going straight from where it is to the spot, passes nothing it may never cross. Checks every block under the box
     * spanning both ends, which holds the swept body and only rejects more than needed around the end of a wall
     */
    private boolean hasCrossableRoute(Vec3 to)
    {
        return isCrossable(level(), getBoundingBox().minmax(aabbAt(to)));
    }

    private static boolean isCrossable(Level level, AABB area)
    {
        var box = area.deflate(EDGE_SLACK);
        for (var pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ)))
        {
            if (isUncrossable(level, pos))
                return false;
        }
        return true;
    }

    private void crackle(ServerLevel level, @Nullable BlockPos salt)
    {
        if (salt == null || saltCrackleDelay > 0)
            return;
        SaltWard.crackle(level, salt);
        saltCrackleDelay = SALT_CRACKLE_TICKS;
    }

    private void face(Vec3 heading)
    {
        if (heading.horizontalDistanceSqr() < 1e-4)
            return;
        float yaw = (float) (Mth.atan2(heading.z, heading.x) * Mth.RAD_TO_DEG) - 90;
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
    }

    private double horizontalDistanceTo(Vec3 spot)
    {
        return Math.sqrt(position().subtract(spot).horizontalDistanceSqr());
    }

    /**
     * The nearest open spot on solid ground beside its prey, in reach and in sight of it, out of any ward.
     */
    private @Nullable Vec3 manifestSpot(LivingEntity target)
    {
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        var here = new Vec3(getX(), Math.floor(target.getY() + 0.5), getZ());
        var candidates = new Vec3[MANIFEST_SPOTS * 3 + 1];
        candidates[0] = horizontalDistanceTo(target.position()) < REACH ? here : null;
        for (int i = 0; i < MANIFEST_SPOTS; i++)
        {
            double angle = i * Mth.TWO_PI / MANIFEST_SPOTS;
            for (int dy = -1; dy <= 1; dy++)
            {
                candidates[1 + i * 3 + dy + 1] = new Vec3(target.getX() + Math.cos(angle) * STRIKE_SPOT, Math.floor(target.getY() + 0.5) + dy,
                        target.getZ() + Math.sin(angle) * STRIKE_SPOT);
            }
        }
        for (var spot : candidates)
        {
            if (spot == null)
                continue;
            double distance = spot.distanceToSqr(position());
            // Taking shape is a short jump, so it must not carry it past a wall its gallop could not cross
            if (distance < bestDistance && standsAt(spot) && canStrikeFrom(spot, target) && hasCrossableRoute(spot))
            {
                best = spot;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean standsAt(Vec3 spot)
    {
        var level = level();
        var feet = BlockPos.containing(spot);
        var below = feet.below();
        return level.noCollision(this, aabbAt(spot)) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty() && mayGoTo(spot);
    }

    private boolean canStrikeFrom(Vec3 spot, LivingEntity target)
    {
        if (Math.abs(target.getY() - spot.y) > REACH_UP || Math.sqrt(spot.subtract(target.position()).horizontalDistanceSqr()) > REACH)
            return false;
        var eye = spot.add(0, EYE_HEIGHT, 0);
        return level().clip(new ClipContext(eye, target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    private AABB aabbAt(Vec3 spot)
    {
        return getDimensions(getPose()).makeBoundingBox(spot);
    }

    private void manifest(ServerLevel level, Vec3 spot, LivingEntity target)
    {
        setPos(spot);
        setDeltaMovement(Vec3.ZERO);
        setPhase(Phase.WINDUP);
        face(target.position().subtract(spot));
        playSound(VoidHeartSounds.WRAITH_BEAST_MANIFEST, 1, 1);
        playSound(VoidHeartSounds.WRAITH_BEAST_REAR, 1.2F, 1);
        level.sendParticles(ParticleTypes.SQUID_INK, spot.x, spot.y + HEIGHT / 2, spot.z, 20, WIDTH / 2, HEIGHT / 3, WIDTH / 2, 0.02);
    }

    private void windupStep(ServerLevel level, @Nullable LivingEntity target)
    {
        if (target != null)
            face(target.position().subtract(position()));
        if (phaseTicks < WINDUP_TICKS)
            return;

        strike(level, target);
        setPhase(Phase.RECOVER);
    }

    private void strike(ServerLevel level, @Nullable LivingEntity target)
    {
        playSound(VoidHeartSounds.WRAITH_BEAST_STRIKE, 1.2F, 1);
        var hooves = position().add(getLookAngle().multiply(1, 0, 1).normalize().scale(WIDTH * 0.8));
        level.sendParticles(ParticleTypes.SQUID_INK, hooves.x, hooves.y + 0.1, hooves.z, 14, 0.5, 0.05, 0.5, 0.06);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, hooves.x, hooves.y + 0.6, hooves.z, 1, 0, 0, 0, 0);
        strikeCooldown = STRIKE_INTERVAL - WINDUP_TICKS;
        if (target != null && inReach(target) && getSensing().hasLineOfSight(target))
            doHurtTarget(level, target);
    }

    public boolean inReach(LivingEntity target)
    {
        return Math.sqrt(position().subtract(target.position()).horizontalDistanceSqr()) <= REACH && Math.abs(target.getY() - getY()) <= REACH_UP;
    }

    private void recoverStep(ServerLevel level, @Nullable LivingEntity target, boolean warded)
    {
        if (target != null)
            face(target.position().subtract(position()));
        if (phaseTicks < RECOVER_TICKS)
            return;

        if (warded)
            setPhase(Phase.HELD);
        else
            fade(level);
    }

    private void hold(ServerLevel level)
    {
        setPhase(Phase.HELD);
        playSound(VoidHeartSounds.WRAITH_BEAST_MANIFEST, 1, 1);
        crackle(level, SaltWard.wardBelow(level, blockPosition()));
        var center = getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 20, WIDTH / 2, HEIGHT / 3, WIDTH / 2, 0.02);
    }

    /**
     * Walks after its prey and strikes from where it stands once in reach. Out of the ward it fades as soon as it has nothing to strike.
     */
    private void heldStep(ServerLevel level, @Nullable LivingEntity target, boolean warded)
    {
        if (target == null)
        {
            getNavigation().stop();
            if (!warded)
                fade(level);
            return;
        }

        getLookControl().setLookAt(target);
        if (strikeCooldown == 0 && inReach(target) && getSensing().hasLineOfSight(target))
        {
            getNavigation().stop();
            setPhase(Phase.WINDUP);
            face(target.position().subtract(position()));
            playSound(VoidHeartSounds.WRAITH_BEAST_REAR, 1.2F, 1);
            return;
        }
        if (phaseTicks % REPATH_TICKS == 1)
            getNavigation().moveTo(target, HELD_WALK_SPEED);
    }

    private void fade(ServerLevel level)
    {
        getNavigation().stop();
        setPhase(Phase.PHANTOM);
        playSound(VoidHeartSounds.WRAITH_BEAST_FADE, 1, 1);
        var center = getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 10, WIDTH / 2, HEIGHT / 3, WIDTH / 2, 0.01);
    }

    /**
     * Projectiles pass through it anyway, but it shows them doing so by slipping aside as they come.
     */
    private void sidestepProjectiles(ServerLevel level)
    {
        if (dodgeCooldown > 0)
            return;
        var center = getBoundingBox().getCenter();
        var near = level.getEntitiesOfClass(Projectile.class, getBoundingBox().inflate(DODGE_SENSE), projectile ->
        {
            var motion = projectile.getDeltaMovement();
            return motion.lengthSqr() > 0.01 && motion.dot(center.subtract(projectile.position())) > 0 && projectile.getOwner() != this;
        });
        if (near.isEmpty())
            return;
        dodgeCooldown = DODGE_COOLDOWN;
        level.broadcastEntityEvent(this, DODGE_EVENT);
        playSound(VoidHeartSounds.WRAITH_BEAST_DODGE, 0.8F, 1);
    }

    @Override
    public void handleEntityEvent(byte event)
    {
        if (event == DODGE_EVENT)
        {
            dodgedTick = tickCount;
            dodgeLeft = random.nextBoolean();
            return;
        }
        super.handleEntityEvent(event);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        if (source.is(DamageTypeTags.IS_PROJECTILE) && !isWarded() || !isSolid() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            return false;
        return super.hurtServer(level, source, damage);
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source)
    {
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || super.isInvulnerableTo(level, source);
    }

    @Override
    public float getSecondsToDisableBlocking()
    {
        return SHIELD_DISABLE_SECONDS;
    }

    @Override
    public boolean canBeHitByProjectile()
    {
        return isWarded() && isSolid() && super.canBeHitByProjectile();
    }

    @Override
    public boolean isPickable()
    {
        return isSolid() && super.isPickable();
    }

    @Override
    public boolean isPushable()
    {
        return isSolid() && super.isPushable();
    }

    @Override
    protected void doPush(Entity entity)
    {
        if (isSolid())
            super.doPush(entity);
    }

    /**
     * Galloping through anything, it would otherwise wander into a portal and leave the dungeon it guards.
     */
    @Override
    public boolean canUsePortal(boolean ignorePassenger)
    {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater()
    {
        return true;
    }

    private final class SenseThroughWallsGoal extends NearestAttackableTargetGoal<Player>
    {
        private SenseThroughWallsGoal()
        {
            super(WraithBeast.this, Player.class, false);
            targetConditions.ignoreLineOfSight();
        }
    }
}
