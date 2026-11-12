package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.oil.OilCoating;
import net.voxelindustry.voidheart.common.content.underway.UnderwayKeyEntity;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Stands still until it sees a player, and from then on nothing makes it forget them. It walks when there is a way and makes one when there is not, one slow
 * step at a time. Its slam takes a whole unboosted health bar through any defense but cannot be hurried, so it can only be walked away from. Magic does nothing
 * to it, which a shell of its own runes shows each time.
 */
public class MalignantBoulder extends VoidMonster
{
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "boulder_slam"));
    public static final TagKey<DamageType> MAGIC = TagKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "magic"));
    public static final float SLAM_DAMAGE = 20;
    public static final int WINDUP_TICKS = 24;
    public static final int RECOVER_TICKS = 16;
    public static final int WARD_TICKS = 15;
    public static final int BREAK_TICKS = 20;

    private static final EntityDataAccessor<Integer> DATA_SLAM = SynchedEntityData.defineId(MalignantBoulder.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BREAKING = SynchedEntityData.defineId(MalignantBoulder.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_WARD_TICK = SynchedEntityData.defineId(MalignantBoulder.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3fc> DATA_WARD_FROM = SynchedEntityData.defineId(MalignantBoulder.class, EntityDataSerializers.VECTOR3);

    private static final int EXPERIENCE = 80;
    private static final double SPEED = 0.18;
    private static final double SIGHT_RANGE = 32;
    private static final double LOCK_RANGE = 96;
    private static final double SLAM_START_REACH = 1.9;
    // Further than the wind-up starts from, so that a half step back during it is not enough
    private static final double SLAM_HIT_REACH = 2.5;
    private static final double SLAM_FRONT = -0.1;
    private static final int AIM_UNTIL_TICK = WINDUP_TICKS - 6;
    private static final int REPATH_TICKS = 20;
    private static final int STUCK_CHECK_TICKS = 40;
    private static final double STUCK_MOVE_SQR = 0.25;
    private static final int STUCK_BREACH_TICKS = 100;
    private static final double LOOK_AHEAD = 0.8;
    private static final int STEP_FOR_HEIGHT = 2;
    private static final int CRACK_ID_BASE = 0x40000000;
    private static final int MAX_CRACKS = 16;

    public MalignantBoulder(EntityType<? extends MalignantBoulder> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
        setPathfindingMalus(PathType.WATER, 4);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(240, SLAM_DAMAGE, SPEED)
                .add(Attributes.ARMOR, 14)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.STEP_HEIGHT, 1.05)
                .add(Attributes.FOLLOW_RANGE, SIGHT_RANGE);
    }

    public int slamTicks()
    {
        return entityData.get(DATA_SLAM);
    }

    public boolean isBreaking()
    {
        return entityData.get(DATA_BREAKING);
    }

    public int wardTick()
    {
        return entityData.get(DATA_WARD_TICK);
    }

    /**
     * @return where the last spell struck the shell, from the feet and along the world's axes
     */
    public Vec3 wardFrom()
    {
        var from = entityData.get(DATA_WARD_FROM);
        return new Vec3(from.x(), from.y(), from.z());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_SLAM, 0);
        builder.define(DATA_BREAKING, false);
        builder.define(DATA_WARD_TICK, Integer.MIN_VALUE / 2);
        builder.define(DATA_WARD_FROM, new Vector3f());
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(1, new SlamGoal());
        goalSelector.addGoal(2, new HuntGoal());
        targetSelector.addGoal(1, new LockOnGoal());
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.MALIGNANT_BOULDER;
    }

    @Override
    public boolean canBreatheUnderwater()
    {
        return true;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect)
    {
        return effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL && super.canBeAffected(effect);
    }

    private boolean withinReach(LivingEntity target, double reach)
    {
        return getBoundingBox().inflate(reach, 0, reach).intersects(target.getBoundingBox());
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source)
    {
        return source.is(DamageTypeTags.IS_FALL) || super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        if (source.is(MAGIC))
        {
            ward(level, source);
            return false;
        }
        return super.hurtServer(level, source, OilCoating.againstConstruct(source, damage));
    }

    private void ward(ServerLevel level, DamageSource source)
    {
        var center = getBoundingBox().getCenter();
        var sourceAt = source.getSourcePosition();
        var direction = sourceAt == null ? getLookAngle() : sourceAt.subtract(center);
        if (direction.lengthSqr() < 1e-6)
            direction = getLookAngle();

        // Pushed out to the side of the hitbox it faces
        double halfWidth = getBbWidth() / 2;
        double halfHeight = getBbHeight() / 2;
        double scale = 1 / Math.max(Math.max(Math.abs(direction.x), Math.abs(direction.z)) / halfWidth, Math.abs(direction.y) / halfHeight);
        var onShell = center.add(direction.scale(scale)).subtract(position());

        entityData.set(DATA_WARD_FROM, new Vector3f((float) onShell.x, (float) onShell.y, (float) onShell.z));
        entityData.set(DATA_WARD_TICK, (int) level.getGameTime());
        playSound(VoidHeartSounds.BOULDER_WARD, 1, 1);
    }

    private void slam(ServerLevel level)
    {
        var look = getLookAngle();
        var flat = new Vec3(look.x, 0, look.z).normalize();
        for (var player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(SLAM_HIT_REACH + 1), EntitySelector.NO_CREATIVE_OR_SPECTATOR))
        {
            if (!withinReach(player, SLAM_HIT_REACH))
                continue;
            var toward = player.position().subtract(position());
            if (new Vec3(toward.x, 0, toward.z).normalize().dot(flat) >= SLAM_FRONT)
                player.hurtServer(level, damageSources().source(DAMAGE, this), SLAM_DAMAGE);
        }
        if (getTarget() instanceof UnderwayKeyEntity key && withinReach(key, SLAM_HIT_REACH)
                && new Vec3(key.getX() - getX(), 0, key.getZ() - getZ()).normalize().dot(flat) >= SLAM_FRONT)
            key.hurtServer(level, damageSources().source(DAMAGE, this), SLAM_DAMAGE);

        playSound(VoidHeartSounds.BOULDER_SLAM, 2, 1);
        var ahead = position().add(flat.scale(getBbWidth() / 2 + 0.8));
        var floor = level.getBlockState(BlockPos.containing(ahead.x, ahead.y - 0.5, ahead.z));
        if (!floor.isAir())
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), ahead.x, ahead.y + 0.1, ahead.z, 40, 0.8, 0.1, 0.8, 0.15);
    }

    private final class LockOnGoal extends NearestAttackableTargetGoal<Player>
    {
        private LockOnGoal()
        {
            super(MalignantBoulder.this, Player.class, true);
        }

        @Override
        public boolean canContinueToUse()
        {
            var target = getTarget();
            return target != null && target.isAlive() && target.level() == level() && distanceTo(target) <= LOCK_RANGE && MalignantBoulder.this.canAttack(target);
        }
    }

    private final class SlamGoal extends Goal
    {
        private SlamGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse()
        {
            var target = getTarget();
            return target != null && target.isAlive() && withinReach(target, SLAM_START_REACH) && getSensing().hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse()
        {
            return slamTicks() > 0;
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void start()
        {
            getNavigation().stop();
            entityData.set(DATA_SLAM, 1);
            playSound(VoidHeartSounds.BOULDER_RAISE, 1.5F, 1);
        }

        @Override
        public void stop()
        {
            entityData.set(DATA_SLAM, 0);
        }

        @Override
        public void tick()
        {
            getNavigation().stop();
            int ticks = slamTicks() + 1;
            var target = getTarget();
            if (target != null && ticks <= AIM_UNTIL_TICK)
                getLookControl().setLookAt(target, 40, 40);

            if (ticks == WINDUP_TICKS)
                slam((ServerLevel) level());
            entityData.set(DATA_SLAM, ticks >= WINDUP_TICKS + RECOVER_TICKS ? 0 : ticks);
        }
    }

    private final class HuntGoal extends Goal
    {
        private final List<BlockPos> breaking = new ArrayList<>();
        private int breakTicks;
        private int repathDelay;
        private boolean breaching;
        private int stuckCheck;
        private int forcedBreach;
        private Vec3 lastPosition = Vec3.ZERO;

        private HuntGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            var target = getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void start()
        {
            repathDelay = 0;
            stuckCheck = STUCK_CHECK_TICKS;
            lastPosition = position();
        }

        @Override
        public void stop()
        {
            getNavigation().stop();
            stopBreaking();
        }

        @Override
        public void tick()
        {
            var target = getTarget();
            if (target == null)
                return;

            getLookControl().setLookAt(target, 20, 20);
            if (forcedBreach > 0)
                forcedBreach--;
            watchForStuck();

            if (--repathDelay <= 0)
            {
                repathDelay = REPATH_TICKS;
                var path = getNavigation().createPath(target, 0);
                boolean griefing = ((ServerLevel) level()).getGameRules().get(GameRules.MOB_GRIEFING);
                breaching = griefing && (forcedBreach > 0 || path == null || !path.canReach());
                if (breaching)
                    getNavigation().stop();
                else
                {
                    stopBreaking();
                    if (path != null)
                        getNavigation().moveTo(path, 1);
                }
            }
            if (breaching)
                breach(target);
        }

        /**
         * A path it can't actually fit along looks fine to the navigator, so going nowhere also counts as being walled in.
         */
        private void watchForStuck()
        {
            if (--stuckCheck > 0)
                return;
            stuckCheck = STUCK_CHECK_TICKS;
            if (!breaching && getNavigation().isInProgress() && position().distanceToSqr(lastPosition) < STUCK_MOVE_SQR)
            {
                forcedBreach = STUCK_BREACH_TICKS;
                repathDelay = 0;
            }
            lastPosition = position();
        }

        private void breach(LivingEntity target)
        {
            var toward = target.position().subtract(position());
            var flat = new Vec3(toward.x, 0, toward.z);
            if (flat.lengthSqr() < 1e-4)
                return;
            flat = flat.normalize();

            int step = toward.y >= STEP_FOR_HEIGHT ? 1 : toward.y <= -STEP_FOR_HEIGHT ? -1 : 0;
            if (tryAdvance(flat, step) || step != 0 && tryAdvance(flat, 0))
                return;

            // Something it may not break stands in the way, so it tries to go around
            var side = new Vec3(-flat.z, 0, flat.x);
            if (!tryAdvance(side, 0))
                tryAdvance(side.reverse(), 0);
        }

        /**
         * @return whether this way is open or being opened
         */
        private boolean tryAdvance(Vec3 direction, int step)
        {
            var box = getBoundingBox().move(direction.x * LOOK_AHEAD, step, direction.z * LOOK_AHEAD);
            var blocks = new ArrayList<BlockPos>();
            for (var pos : BlockPos.betweenClosed(
                    Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                    Mth.floor(box.maxX - 1e-4), Mth.floor(box.maxY - 1e-4), Mth.floor(box.maxZ - 1e-4)))
            {
                var state = level().getBlockState(pos);
                if (state.getCollisionShape(level(), pos).isEmpty())
                    continue;
                if (!canBreak(state, pos))
                    return false;
                blocks.add(pos.immutable());
            }

            if (blocks.isEmpty())
            {
                stopBreaking();
                getMoveControl().setWantedPosition(getX() + direction.x * 2, getY() + step, getZ() + direction.z * 2, 1);
                return true;
            }
            breakAll(blocks);
            return true;
        }

        private boolean canBreak(BlockState state, BlockPos pos)
        {
            return state.getDestroySpeed(level(), pos) >= 0 && !state.is(BlockTags.WITHER_IMMUNE) && !state.hasBlockEntity();
        }

        private void breakAll(List<BlockPos> blocks)
        {
            if (!blocks.equals(breaking))
            {
                stopBreaking();
                breaking.addAll(blocks);
                breakTicks = 0;
            }
            entityData.set(DATA_BREAKING, true);
            breakTicks++;

            var level = (ServerLevel) level();
            int crack = breakTicks * 9 / BREAK_TICKS;
            for (int i = 0; i < Math.min(breaking.size(), MAX_CRACKS); i++)
                level.destroyBlockProgress(crackId(i), breaking.get(i), crack);

            if (breakTicks < BREAK_TICKS)
                return;

            for (var pos : breaking)
                level.destroyBlock(pos, false, MalignantBoulder.this);
            playSound(VoidHeartSounds.BOULDER_BREAK, 1.4F, 1);
            stopBreaking();
        }

        private void stopBreaking()
        {
            if (!breaking.isEmpty())
            {
                var level = (ServerLevel) level();
                for (int i = 0; i < Math.min(breaking.size(), MAX_CRACKS); i++)
                    level.destroyBlockProgress(crackId(i), breaking.get(i), -1);
                breaking.clear();
            }
            breakTicks = 0;
            entityData.set(DATA_BREAKING, false);
        }

        private int crackId(int index)
        {
            return CRACK_ID_BASE | getId() << 4 | index;
        }
    }
}
