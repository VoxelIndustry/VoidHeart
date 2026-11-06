package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Clubs what comes close and spears what stays away, with a lance that armor does nothing against. Hurt badly enough it runs out of sight and kneels to heal,
 * which a hit breaks, and it only tries twice.
 */
public class FallenBishop extends VoidMonster
{
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "bishop_lance"));
    public static final float LANCE_DAMAGE = 8;
    public static final int CHARGE_TICKS = 20;
    public static final int LOCK_TICKS = 10;
    public static final int LINGER_TICKS = 8;
    public static final int BLOW_TICKS = 14;
    public static final int BLOW_HIT_TICK = 6;
    public static final int BLOW_COOLDOWN = 10;
    /**
     * How long before its lance can be cast a Bishop counts as ready, which only Insight shows.
     */
    public static final int LANCE_WARN_TICKS = 20;

    private static final EntityDataAccessor<Integer> DATA_LANCE = SynchedEntityData.defineId(FallenBishop.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3fc> DATA_AIM = SynchedEntityData.defineId(FallenBishop.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> DATA_KNEELING = SynchedEntityData.defineId(FallenBishop.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_LANCE_READY = SynchedEntityData.defineId(FallenBishop.class, EntityDataSerializers.BOOLEAN);

    private static final int EXPERIENCE = 60;
    private static final float MELEE_DAMAGE = 16;
    private static final double MELEE_REACH = 0.6;
    private static final double LANCE_RANGE = 24;
    private static final double LANCE_MIN_RANGE = 4;
    private static final int LANCE_INTERVAL = 80;
    private static final double LANCE_HIT_RADIUS = 0.3;
    // Where the orb in the crook is with the crozier raised, from the model at its drawn scale
    private static final double TIP_HEIGHT = 2.36;
    private static final double TIP_FORWARD = 0.85;
    private static final double TIP_RIGHT = 0.59;
    private static final double FLEE_SPEED = 1.35;
    private static final int FLEE_MAX_TICKS = 200;
    private static final int FLEE_REPATH_TICKS = 40;
    private static final int FLEE_CANDIDATES = 5;
    private static final double SAFE_DISTANCE = 8;
    private static final int CHANT_TICKS = 40;
    private static final int HEAL_MOTE_TICKS = 4;
    private static final DustParticleOptions HEAL_MOTE = new DustParticleOptions(0x36C5B0, 1.2F);
    private static final SwingAnimation BLOW = new SwingAnimation(SwingAnimationType.WHACK, BLOW_TICKS);
    private static final float RAISE_PER_TICK = 1F / 6;
    private static final float KNEEL_PER_TICK = 1F / 10;

    private int lanceCooldown = LANCE_INTERVAL / 2;
    private int blowTicks = -1;
    private int withdrawals;
    private boolean interrupted;

    public float raise;
    public float raiseO;
    public float kneel;
    public float kneelO;
    /**
     * Ticks its lance has been ready for, on the client.
     */
    public int readyTicks;

    public FallenBishop(EntityType<? extends FallenBishop> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(120, MELEE_DAMAGE, 0.27)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    public int lanceTicks()
    {
        return entityData.get(DATA_LANCE);
    }

    public boolean isKneeling()
    {
        return entityData.get(DATA_KNEELING);
    }

    /**
     * Shows the lance at a given moment toward a given point, without firing it.
     */
    public void showLance(Vec3 end, int ticks)
    {
        aimAt(end);
        entityData.set(DATA_LANCE, ticks);
    }

    public void setKneeling(boolean kneeling)
    {
        entityData.set(DATA_KNEELING, kneeling);
    }

    /**
     * @return how far into its blow it is, or -1 between blows
     */
    public int blowTicks()
    {
        return blowTicks;
    }

    public int withdrawals()
    {
        return withdrawals;
    }

    public Vec3 lanceEnd()
    {
        var aim = entityData.get(DATA_AIM);
        return new Vec3(aim.x(), aim.y(), aim.z());
    }

    /**
     * Where the gem on top of its crozier is while raised, which the model has to agree with.
     */
    public Vec3 lanceOrigin(float partialTicks)
    {
        float yaw = Mth.rotLerp(partialTicks, yBodyRotO, yBodyRot) * Mth.DEG_TO_RAD;
        var forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        var right = new Vec3(-forward.z, 0, forward.x);
        return getPosition(partialTicks).add(0, TIP_HEIGHT, 0).add(forward.scale(TIP_FORWARD)).add(right.scale(TIP_RIGHT));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_LANCE, 0);
        builder.define(DATA_AIM, new Vector3f());
        builder.define(DATA_KNEELING, false);
        builder.define(DATA_LANCE_READY, false);
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(1, new WithdrawGoal());
        goalSelector.addGoal(2, new LanceGoal());
        goalSelector.addGoal(3, new BlowGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        addIdleGoals(6);
        addPlayerTarget(true);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.FALLEN_BISHOP;
    }

    /**
     * A heavy two handed blow, slower than an arm swing, so that the overhead wind-up can be seen.
     */
    @Override
    public void swingForAttack(InteractionHand hand)
    {
        swing(hand, BLOW);
    }

    @Override
    protected AABB getAttackBoundingBox(double horizontalExpansion)
    {
        return super.getAttackBoundingBox(horizontalExpansion).inflate(MELEE_REACH, 0, MELEE_REACH);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (level().isClientSide())
        {
            raiseO = raise;
            kneelO = kneel;
            raise = Mth.approach(raise, lanceTicks() > 0 ? 1 : 0, RAISE_PER_TICK);
            kneel = Mth.approach(kneel, isKneeling() ? 1 : 0, KNEEL_PER_TICK);
            readyTicks = isLanceReady() ? readyTicks + 1 : 0;
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);

        if (lanceCooldown > 0)
            lanceCooldown--;
        int lance = lanceTicks();
        if (lance > CHARGE_TICKS)
            entityData.set(DATA_LANCE, lance >= CHARGE_TICKS + LINGER_TICKS ? 0 : lance + 1);
        entityData.set(DATA_LANCE_READY, lance == 0 && !isKneeling() && lanceCooldown <= LANCE_WARN_TICKS && canLance(getTarget()));
    }

    /**
     * Whether its lance is about to be cast at what it hunts, synced so the client can warn under Insight.
     */
    public boolean isLanceReady()
    {
        return entityData.get(DATA_LANCE_READY);
    }

    private boolean canLance(@Nullable LivingEntity target)
    {
        if (target == null || !target.isAlive())
            return false;
        double distance = distanceTo(target);
        return distance >= LANCE_MIN_RANGE && distance <= LANCE_RANGE && getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && isKneeling())
            interrupted = true;
        return hurt;
    }

    /**
     * The first block along the way from the crozier toward what it aims at, or the full reach of the lance.
     */
    private Vec3 aimedEnd(Vec3 toward)
    {
        var from = lanceOrigin(1);
        var end = from.add(toward.subtract(from).normalize().scale(LANCE_RANGE));
        return level().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getLocation();
    }

    private void aimAt(Vec3 end)
    {
        entityData.set(DATA_AIM, new Vector3f((float) end.x, (float) end.y, (float) end.z));
    }

    private void fire(ServerLevel level)
    {
        var from = lanceOrigin(1);
        var to = lanceEnd();
        playSound(VoidHeartSounds.BISHOP_LANCE, 1.5F, 1);

        Player struck = null;
        double nearest = Double.MAX_VALUE;
        for (var player : level.getEntitiesOfClass(Player.class, new AABB(from, to).inflate(1), EntitySelector.NO_CREATIVE_OR_SPECTATOR))
        {
            var hit = player.getBoundingBox().inflate(LANCE_HIT_RADIUS).clip(from, to);
            if (hit.isPresent() && hit.get().distanceToSqr(from) < nearest)
            {
                nearest = hit.get().distanceToSqr(from);
                struck = player;
            }
        }
        if (struck == null)
            return;

        // The armor piercing type also passes shields, so a shield gets its say with an ordinary hit first
        if (struck.applyItemBlocking(level, damageSources().mobAttack(this), LANCE_DAMAGE) > 0)
            return;
        struck.hurtServer(level, damageSources().source(DAMAGE, this), LANCE_DAMAGE);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.putInt("Withdrawals", withdrawals);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        withdrawals = input.getIntOr("Withdrawals", 0);
    }

    private final class LanceGoal extends Goal
    {
        private LanceGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            var target = getTarget();
            if (lanceCooldown > 0 || lanceTicks() > 0)
                return false;
            return canLance(target);
        }

        @Override
        public boolean canContinueToUse()
        {
            var target = getTarget();
            return target != null && target.isAlive() && lanceTicks() > 0 && lanceTicks() <= CHARGE_TICKS;
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
            entityData.set(DATA_LANCE, 1);
            aimAt(aimedEnd(getTarget().getBoundingBox().getCenter()));
            playSound(VoidHeartSounds.BISHOP_CHARGE, 1.2F, 1);
        }

        @Override
        public void stop()
        {
            lanceCooldown = LANCE_INTERVAL;
            if (lanceTicks() <= CHARGE_TICKS)
                entityData.set(DATA_LANCE, 0);
        }

        @Override
        public void tick()
        {
            var target = getTarget();
            if (target == null)
                return;

            getNavigation().stop();
            int ticks = lanceTicks() + 1;
            entityData.set(DATA_LANCE, ticks);

            if (ticks <= CHARGE_TICKS - LOCK_TICKS)
            {
                getLookControl().setLookAt(target, 60, 60);
                aimAt(aimedEnd(target.getBoundingBox().getCenter()));
            }
            if (ticks > CHARGE_TICKS)
                fire((ServerLevel) level());
        }
    }

    /**
     * Walks in and clubs, with the damage landing when the crozier comes down rather than as the swing starts, so a step back during the wind-up dodges it.
     */
    private final class BlowGoal extends Goal
    {
        private int cooldown;
        private int repathDelay;

        private BlowGoal()
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
        public boolean canContinueToUse()
        {
            return blowTicks >= 0 || canUse();
        }

        @Override
        public boolean isInterruptable()
        {
            return blowTicks < 0;
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void start()
        {
            setAggressive(true);
            repathDelay = 0;
        }

        @Override
        public void stop()
        {
            setAggressive(false);
            getNavigation().stop();
            blowTicks = -1;
        }

        @Override
        public void tick()
        {
            var target = getTarget();
            if (cooldown > 0)
                cooldown--;

            if (blowTicks >= 0)
            {
                getNavigation().stop();
                if (target != null)
                    getLookControl().setLookAt(target, 30, 30);
                if (++blowTicks == BLOW_HIT_TICK && target != null && isWithinMeleeAttackRange(target) && getSensing().hasLineOfSight(target))
                    doHurtTarget((ServerLevel) level(), target);
                if (blowTicks >= BLOW_TICKS)
                {
                    blowTicks = -1;
                    cooldown = BLOW_COOLDOWN;
                }
                return;
            }

            if (target == null)
                return;
            getLookControl().setLookAt(target, 30, 30);
            if (cooldown == 0 && isWithinMeleeAttackRange(target) && getSensing().hasLineOfSight(target))
            {
                blowTicks = 0;
                getNavigation().stop();
                swingForAttack(InteractionHand.MAIN_HAND);
                return;
            }
            if (--repathDelay <= 0)
            {
                repathDelay = 10;
                getNavigation().moveTo(target, 1);
            }
        }
    }

    private final class WithdrawGoal extends Goal
    {
        private LivingEntity from;
        private int fleeTicks;
        private int repathDelay;
        private boolean finished;

        private WithdrawGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse()
        {
            return getTarget() != null && BishopRetreat.shouldWithdraw(getHealth(), getMaxHealth(), withdrawals);
        }

        @Override
        public boolean canContinueToUse()
        {
            return !finished;
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void start()
        {
            withdrawals++;
            from = getTarget();
            fleeTicks = 0;
            repathDelay = 0;
            finished = false;
            interrupted = false;
        }

        @Override
        public void stop()
        {
            setKneeling(false);
            getNavigation().stop();
        }

        @Override
        public void tick()
        {
            if (isKneeling())
                kneel();
            else
                flee();
        }

        private void flee()
        {
            fleeTicks++;
            boolean safe = !from.isAlive() || distanceTo(from) >= SAFE_DISTANCE && !getSensing().hasLineOfSight(from);
            if (safe || fleeTicks >= FLEE_MAX_TICKS)
            {
                getNavigation().stop();
                setKneeling(true);
                interrupted = false;
                return;
            }

            if (--repathDelay > 0 && getNavigation().isInProgress())
                return;
            repathDelay = FLEE_REPATH_TICKS;

            // The farthest of a few spots away from the threat, kept until reached, so that it runs instead of dithering
            Vec3 best = null;
            double bestDistance = 0;
            for (int attempt = 0; attempt < FLEE_CANDIDATES; attempt++)
            {
                var away = DefaultRandomPos.getPosAway(FallenBishop.this, 16, 7, from.position());
                if (away != null && away.distanceToSqr(from.position()) > bestDistance)
                {
                    best = away;
                    bestDistance = away.distanceToSqr(from.position());
                }
            }
            if (best != null)
                getNavigation().moveTo(best.x, best.y, best.z, FLEE_SPEED);
        }

        private void kneel()
        {
            getNavigation().stop();
            heal(BishopRetreat.HEAL_PER_TICK);

            if (tickCount % HEAL_MOTE_TICKS == 0)
                ((ServerLevel) level()).sendParticles(HEAL_MOTE, getX(), getY() + 1, getZ(), 2, 0.5, 0.6, 0.5, 0.01);
            if (tickCount % CHANT_TICKS == 0)
                playSound(VoidHeartSounds.BISHOP_CHANT, 0.7F, 1);

            if (interrupted || BishopRetreat.isHealed(getHealth(), getMaxHealth()))
                finished = true;
        }
    }
}
