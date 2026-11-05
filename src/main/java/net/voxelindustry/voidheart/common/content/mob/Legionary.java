package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Always comes in twos, one taking each side of its target. Whichever outlasts the other burns hotter for it, moving and striking faster for the rest of its time.
 */
public class Legionary extends Automaton
{
    public static final int ATTACK_INTERVAL = 30;
    public static final int FRENZY_ATTACK_INTERVAL = 23;
    public static final float SHIELD_DISABLE_SECONDS = 5;

    private static final EntityDataAccessor<Boolean> DATA_FRENZIED = SynchedEntityData.defineId(Legionary.class, EntityDataSerializers.BOOLEAN);

    private static final Identifier FRENZY_MODIFIER = Identifier.fromNamespaceAndPath(MODID, "legionary_frenzy");
    // Walking speed grows with the square of the attribute, so this is what a third faster takes
    private static final double FRENZY_SPEED_BONUS = 0.14;

    private static final int EXPERIENCE = 30;
    private static final double SPEED = 0.29;
    private static final double AXE_REACH = 0.5;
    private static final double PARTNER_RANGE = 32;
    private static final int PARTNER_SPAWN_DISTANCE = 2;
    private static final int REPATH_TICKS = 10;
    private static final int FRENZY_SPARK_TICKS = 4;

    private @Nullable UUID partner;
    private int flankSide = 1;

    public Legionary(EntityType<? extends Legionary> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(70, 18, SPEED)
                .add(Attributes.ARMOR, 14)
                .add(Attributes.ARMOR_TOUGHNESS, 4)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    public boolean isFrenzied()
    {
        return entityData.get(DATA_FRENZIED);
    }

    public @Nullable UUID partner()
    {
        return partner;
    }

    public int attackInterval()
    {
        return isFrenzied() ? FRENZY_ATTACK_INTERVAL : ATTACK_INTERVAL;
    }

    public void pairWith(Legionary other)
    {
        partner = other.getUUID();
        other.partner = getUUID();
        flankSide = 1;
        other.flankSide = -1;
    }

    private @Nullable Legionary partnerEntity()
    {
        if (partner == null || !(level() instanceof ServerLevel level))
            return null;
        return level.getEntity(partner) instanceof Legionary other && other.isAlive() ? other : null;
    }

    public void enterFrenzy()
    {
        if (isFrenzied())
            return;

        entityData.set(DATA_FRENZIED, true);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(FRENZY_MODIFIER))
            speed.addPermanentModifier(new AttributeModifier(FRENZY_MODIFIER, FRENZY_SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));

        playSound(VoidHeartSounds.LEGIONARY_FRENZY, 1.5F, 1);
        if (level() instanceof ServerLevel level)
        {
            var center = getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 30, 0.4, 0.7, 0.4, 0.04);
        }
    }

    /**
     * Brings the other half of the pair with it, unless it is that other half.
     */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData)
    {
        var result = super.finalizeSpawn(level, difficulty, reason, groupData);
        if (partner == null)
            spawnPartner(level, reason);
        return result;
    }

    private void spawnPartner(ServerLevelAccessor level, EntitySpawnReason reason)
    {
        var other = VoidHeartEntities.LEGIONARY.create(level.getLevel(), reason);
        if (other == null)
            return;

        other.snapTo(partnerSpot(level, other), getYRot(), getXRot());
        pairWith(other);
        level.addFreshEntityWithPassengers(other);
    }

    private Vec3 partnerSpot(ServerLevelAccessor level, Legionary other)
    {
        for (var direction : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
        {
            var spot = position().add(direction[0] * PARTNER_SPAWN_DISTANCE, 0, direction[1] * PARTNER_SPAWN_DISTANCE);
            other.setPos(spot);
            if (level.noCollision(other))
                return spot;
        }
        return position();
    }

    @Override
    public void tick()
    {
        super.tick();
        if (level().isClientSide() && isFrenzied() && tickCount % FRENZY_SPARK_TICKS == 0)
            level().addParticle(ParticleTypes.SMALL_FLAME, getRandomX(0.5), getY() + 1.2 + random.nextDouble() * 0.6, getRandomZ(0.5), 0, 0.02, 0);
    }

    @Override
    public void die(DamageSource source)
    {
        var other = partnerEntity();
        super.die(source);
        if (other != null)
            other.enterFrenzy();
    }

    @Override
    public void setTarget(@Nullable LivingEntity target)
    {
        super.setTarget(target);
        if (target == null)
            return;

        var other = partnerEntity();
        if (other != null && other.getTarget() == null && other.distanceTo(this) <= PARTNER_RANGE)
            other.setTarget(target);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_FRENZIED, false);
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(3, new FlankGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        addIdleGoals(6);
        addPlayerTarget(true);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.LEGIONARY;
    }

    @Override
    public float getSecondsToDisableBlocking()
    {
        return SHIELD_DISABLE_SECONDS;
    }

    @Override
    protected AABB getAttackBoundingBox(double horizontalExpansion)
    {
        return super.getAttackBoundingBox(horizontalExpansion).inflate(AXE_REACH, 0, AXE_REACH);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        if (partner != null)
            output.store("Partner", UUIDUtil.CODEC, partner);
        output.putInt("FlankSide", flankSide);
        output.putBoolean("Frenzied", isFrenzied());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        partner = input.read("Partner", UUIDUtil.CODEC).orElse(null);
        flankSide = input.getIntOr("FlankSide", 1);
        entityData.set(DATA_FRENZIED, input.getBooleanOr("Frenzied", false));
    }

    private final class FlankGoal extends Goal
    {
        private int attackDelay;
        private int repathDelay;
        private boolean engaged;
        private int flankTicks;

        private FlankGoal()
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
            setAggressive(true);
            repathDelay = 0;
            engaged = false;
            flankTicks = 0;
        }

        @Override
        public void stop()
        {
            setAggressive(false);
            getNavigation().stop();
        }

        @Override
        public void tick()
        {
            var target = getTarget();
            if (target == null)
                return;

            getLookControl().setLookAt(target, 30, 30);
            if (attackDelay > 0)
                attackDelay--;
            flankTicks = engaged ? 0 : flankTicks + 1;

            if (--repathDelay <= 0)
            {
                repathDelay = REPATH_TICKS;
                var spot = LegionFlank.spot(target.position(), target.getViewVector(1), flankSide);
                engaged = partnerEntity() == null || flankTicks >= LegionFlank.FLANK_PATIENCE_TICKS || LegionFlank.shouldEngage(target.position(), position(), spot, engaged);
                if (engaged)
                    getNavigation().moveTo(target, 1);
                else
                    getNavigation().moveTo(spot.x, spot.y, spot.z, 1);
            }

            if (attackDelay == 0 && isWithinMeleeAttackRange(target) && getSensing().hasLineOfSight(target))
            {
                attackDelay = attackInterval();
                swingForAttack(InteractionHand.MAIN_HAND);
                playSound(VoidHeartSounds.LEGIONARY_CHOP, 1, 1);
                doHurtTarget((ServerLevel) level(), target);
            }
        }
    }
}
