package net.voxelindustry.voidheart.common.content.mausoleum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.mob.VoidMonster;
import net.voxelindustry.voidheart.common.content.mob.Wraith;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.UUID;

import static net.voxelindustry.voidheart.common.content.mausoleum.MausoleumBalance.*;

/**
 * A flame of the Dullahan's crown turned loose, a green fire of fury. It rises from the crown, then circles its prey fast, gathers itself, and rams
 * straight through where they stood, burning and setting them alight with green fire. It is easily put out, if it can be hit between two charges.
 */
public class MaleficentWisp extends VoidMonster
{
    public static final float SIZE = 0.7F;

    public enum State
    {
        BIRTH,
        ORBIT,
        WINDUP,
        CHARGE;

        private static final State[] VALUES = values();
    }

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(MaleficentWisp.class, EntityDataSerializers.INT);

    private static final int EXPERIENCE = 6;
    private static final double BIRTH_RISE = 0.05;
    private static final double ORBIT_PULL = 0.22;
    private static final double ORBIT_MAX_SPEED = 0.55;
    private static final double HOVER_DRAG = 0.6;
    private static final double BOB = 0.35;
    /** How far below its prey's eyes it aims, so a charge runs through the body. */
    private static final double AIM_DROP = 0.6;
    private static final double HIT_SLACK = 0.25;
    private static final double AFTER_CHARGE_DRAG = 0.25;

    private int stateTicks;
    private int orbitTicks = WISP_ORBIT_MIN;
    private double orbitAngle;
    private Vec3 chargeDirection = Vec3.ZERO;
    private boolean struck;
    private @Nullable UUID owner;

    /** Client only: when it last changed state, by its own tick count. */
    public int stateChangedTick = -100;

    public MaleficentWisp(EntityType<? extends MaleficentWisp> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(WISP_HEALTH, WISP_RAM_DAMAGE, 0.3).add(Attributes.FOLLOW_RANGE, WISP_RANGE);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.MALEFICENT_WISP;
    }

    @Override
    protected void registerGoals()
    {
    }

    /** Sets it rising out of the crown of the rider that gave it off, already after a prey. */
    void born(Dullahan rider, @Nullable LivingEntity prey)
    {
        owner = rider.getUUID();
        orbitAngle = random.nextDouble() * Mth.TWO_PI;
        setTarget(prey);
        setState(State.BIRTH);
    }

    public State state()
    {
        return State.VALUES[Mth.clamp(entityData.get(DATA_STATE), 0, State.VALUES.length - 1)];
    }

    private void setState(State state)
    {
        entityData.set(DATA_STATE, state.ordinal());
        stateTicks = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, State.ORBIT.ordinal());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        if (DATA_STATE.equals(accessor) && level().isClientSide())
            stateChangedTick = tickCount;
    }

    @Override
    public void travel(Vec3 input)
    {
        move(MoverType.SELF, getDeltaMovement());
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos)
    {
    }

    @Override
    public boolean isPushable()
    {
        return state() != State.CHARGE && super.isPushable();
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);
        stateTicks++;
        var target = getTarget();
        if (target == null || !target.isAlive() || !canAttack(target) || target instanceof Player player && (player.isCreative() || player.isSpectator()))
            setTarget(target = pickTarget(level));

        switch (state())
        {
            case BIRTH -> rise();
            case ORBIT -> orbit(target);
            case WINDUP -> windUp(target);
            case CHARGE -> charge(level);
        }
    }

    private @Nullable Player pickTarget(ServerLevel level)
    {
        return level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(WISP_RANGE), EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(Entity::isAlive)).stream()
                .filter(this::canAttack)
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private void rise()
    {
        setDeltaMovement(0, BIRTH_RISE, 0);
        if (stateTicks >= WISP_BIRTH_TICKS)
        {
            orbitTicks = WISP_ORBIT_MIN + random.nextInt(WISP_ORBIT_SPREAD);
            setState(State.ORBIT);
        }
    }

    private void orbit(@Nullable LivingEntity target)
    {
        if (target == null)
        {
            setDeltaMovement(getDeltaMovement().scale(HOVER_DRAG));
            return;
        }
        orbitAngle += WISP_ORBIT_SPEED;
        double bob = Mth.sin(tickCount * 0.15F) * BOB;
        var goal = target.position().add(Math.cos(orbitAngle) * WISP_ORBIT_RADIUS, WISP_ORBIT_HEIGHT + bob, Math.sin(orbitAngle) * WISP_ORBIT_RADIUS);
        var pull = goal.subtract(position()).scale(ORBIT_PULL);
        if (pull.length() > ORBIT_MAX_SPEED)
            pull = pull.normalize().scale(ORBIT_MAX_SPEED);
        setDeltaMovement(pull);
        if (stateTicks >= orbitTicks && hasLineOfSight(target))
        {
            setState(State.WINDUP);
            playSound(VoidHeartSounds.WISP_WINDUP, 1.2F, 1.2F);
        }
    }

    private void windUp(@Nullable LivingEntity target)
    {
        setDeltaMovement(getDeltaMovement().scale(HOVER_DRAG));
        if (target == null)
        {
            setState(State.ORBIT);
            return;
        }
        if (stateTicks >= WISP_WINDUP)
        {
            var aim = target.getEyePosition().subtract(0, AIM_DROP, 0);
            var from = position().add(0, SIZE / 2, 0);
            chargeDirection = aim.subtract(from).normalize();
            struck = false;
            setState(State.CHARGE);
            playSound(VoidHeartSounds.WISP_CHARGE, 1.5F, 1.1F);
        }
    }

    private void charge(ServerLevel level)
    {
        setDeltaMovement(chargeDirection.scale(WISP_CHARGE_SPEED));
        if (!struck)
        {
            for (var victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(HIT_SLACK), this::mayRam))
            {
                if (victim.hurtServer(level, damageSources().mobAttack(this), WISP_RAM_DAMAGE))
                    FuneraryFire.ignite(victim, WISP_FIRE_TICKS);
                struck = true;
            }
        }
        if (stateTicks >= WISP_CHARGE_TICKS || horizontalCollision || verticalCollision)
        {
            setDeltaMovement(getDeltaMovement().scale(AFTER_CHARGE_DRAG));
            orbitTicks = WISP_ORBIT_MIN + random.nextInt(WISP_ORBIT_SPREAD);
            setState(State.ORBIT);
        }
    }

    private boolean mayRam(LivingEntity victim)
    {
        if (victim == this || !victim.isAlive() || victim instanceof Dullahan || victim instanceof MaleficentWisp
            || victim instanceof Wraith)
            return false;
        return !(victim instanceof Player player) || !player.isCreative() && !player.isSpectator();
    }

    @Override
    public void tick()
    {
        super.tick();
        if (level().isClientSide())
            emitFlames();
    }

    /** It sheds green flames as it goes, a trail of them while it charges. */
    private void emitFlames()
    {
        int flames = state() == State.CHARGE ? 3 : state() == State.WINDUP ? 2 : 1;
        for (int i = 0; i < flames; i++)
        {
            double x = getX() + (random.nextDouble() - 0.5) * SIZE * 0.8;
            double y = getY() + SIZE * 0.5 + (random.nextDouble() - 0.5) * SIZE * 0.8;
            double z = getZ() + (random.nextDouble() - 0.5) * SIZE * 0.8;
            level().addParticle(VoidHeart.FUNERARY_FLAME_PARTICLE, x, y, z, 0, 0.02, 0);
        }
    }

    @Override
    public void die(DamageSource source)
    {
        super.die(source);
        if (level() instanceof ServerLevel level)
        {
            if (owner != null && level.getEntity(owner) instanceof Dullahan rider)
                rider.wispOut(getUUID());
            level.sendParticles(VoidHeart.FUNERARY_FLAME_PARTICLE, getX(), getY() + SIZE / 2, getZ(), 20, 0.25, 0.25, 0.25, 0.06);
        }
    }

    /** A flame put out is gone at once. */
    @Override
    protected void tickDeath()
    {
        if (!level().isClientSide() && !isRemoved())
        {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        if (owner != null)
            output.store("Owner", UUIDUtil.CODEC, owner);
        output.putInt("State", state().ordinal());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        var state = State.VALUES[Mth.clamp(input.getIntOr("State", State.ORBIT.ordinal()), 0, State.VALUES.length - 1)];
        // A charge cut short by a reload has lost its heading
        entityData.set(DATA_STATE, (state == State.CHARGE || state == State.WINDUP ? State.ORBIT : state).ordinal());
        setNoGravity(true);
    }
}
