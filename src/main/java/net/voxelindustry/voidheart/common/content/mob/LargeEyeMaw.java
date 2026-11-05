package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Never bites. It hangs back from its prey and keeps opening up to spit out a brood of small maws, which outlive it.
 */
public class LargeEyeMaw extends FloatingEye
{
    public static final int SPIT_INTERVAL = 12 * 20;
    public static final int SPIT_COUNT = 4;
    public static final int MAX_ALIVE = 8;
    public static final int OPEN_TICKS = 16;

    private static final int EXPERIENCE = 20;
    private static final float HEALTH = 60;
    private static final double SPEED = 0.12;
    private static final int SPIT_SPACING = 4;
    private static final int CLOSE_DELAY = 8;
    private static final int RETRY_TICKS = 40;
    private static final int FIRST_SPIT = 40;
    private static final double KEEP_MIN = 8;
    private static final double KEEP_MAX = 14;
    private static final double IDLE_SHARE = 0.5;
    private static final double TURN_RATE = 0.06;
    private static final double WALL_REACH = 1.2;
    private static final double SPIT_SPEED = 0.55;
    private static final double SPIT_SPREAD = 0.3;

    private final List<UUID> spat = new ArrayList<>();
    private int spitCooldown = FIRST_SPIT;
    private int spitStep = -1;
    private int spitsLeft;

    public LargeEyeMaw(EntityType<? extends LargeEyeMaw> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return eyeAttributes(HEALTH, 0, SPEED);
    }

    public int aliveSpat(ServerLevel level)
    {
        spat.removeIf(id ->
        {
            var entity = level.getEntity(id);
            return entity == null || !entity.isAlive();
        });
        return spat.size();
    }

    @Override
    protected void registerGoals()
    {
        addPlayerTarget(false);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.LARGE_EYE_MAW;
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);

        var target = getTarget();
        if (target != null && !stillHunted(target))
        {
            setTarget(null);
            target = null;
        }

        if (spitCooldown > 0)
            spitCooldown--;
        tickSpit(level, target);
        drift(target);
    }

    private void drift(LivingEntity target)
    {
        if (target == null)
        {
            steer(EyeFlock.velocity(wander(), Vec3.ZERO, wallPush(WALL_REACH), flightSpeed() * IDLE_SHARE), TURN_RATE);
            faceAlong(getDeltaMovement());
            return;
        }

        faceTarget(target);
        if (spitStep >= 0)
        {
            steer(Vec3.ZERO, TURN_RATE);
            return;
        }

        double distance = distanceTo(target);
        var wish = Vec3.ZERO;
        if (distance < KEEP_MIN)
            wish = position().subtract(target.position()).normalize();
        else if (distance > KEEP_MAX || !getSensing().hasLineOfSight(target))
            wish = headingTo(target, target.getEyePosition());
        steer(EyeFlock.velocity(wish, Vec3.ZERO, wallPush(WALL_REACH), flightSpeed()), TURN_RATE);
    }

    private void tickSpit(ServerLevel level, LivingEntity target)
    {
        if (spitStep >= 0)
        {
            spitStep++;
            if (spitsLeft > 0 && spitStep >= OPEN_TICKS && (spitStep - OPEN_TICKS) % SPIT_SPACING == 0)
            {
                spit(level, target);
                spitsLeft--;
            }
            if (spitsLeft == 0 && spitStep >= OPEN_TICKS + SPIT_COUNT * SPIT_SPACING + CLOSE_DELAY)
            {
                setJawsOpen(false);
                spitStep = -1;
            }
            return;
        }

        if (spitCooldown > 0 || target == null || !getSensing().hasLineOfSight(target))
            return;

        int room = Math.min(SPIT_COUNT, MAX_ALIVE - aliveSpat(level));
        if (room <= 0)
        {
            spitCooldown = RETRY_TICKS;
            return;
        }

        spitsLeft = room;
        spitStep = 0;
        spitCooldown = SPIT_INTERVAL;
        setJawsOpen(true);
        playSound(VoidHeartSounds.LARGE_EYE_MAW_OPEN, 2, 1);
    }

    private void spit(ServerLevel level, LivingEntity target)
    {
        var maw = VoidHeartEntities.EYE_MAW.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (maw == null)
            return;

        var look = getLookAngle();
        var center = getBoundingBox().getCenter();
        var mouth = center.add(look.scale(getBbWidth() / 2 + maw.getBbWidth()));
        maw.snapTo(mouth.x, mouth.y - maw.getBbHeight() / 2, mouth.z, getYRot(), getXRot());
        if (!level.noCollision(maw))
            maw.snapTo(center.x, center.y - maw.getBbHeight() / 2, center.z, getYRot(), getXRot());

        maw.setSpat();
        maw.setTarget(target);
        maw.setDeltaMovement(look.scale(SPIT_SPEED).add(
                (random.nextDouble() - 0.5) * SPIT_SPREAD, (random.nextDouble() - 0.5) * SPIT_SPREAD, (random.nextDouble() - 0.5) * SPIT_SPREAD));
        level.addFreshEntity(maw);
        spat.add(maw.getUUID());

        playSound(VoidHeartSounds.LARGE_EYE_MAW_SPIT, 1.5F, 0.9F + random.nextFloat() * 0.3F);
        level.sendParticles(ParticleTypes.SQUID_INK, mouth.x, mouth.y, mouth.z, 6, 0.2, 0.2, 0.2, 0.03);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.store("SpatMaws", UUIDUtil.CODEC.listOf(), spat);
        output.putInt("SpitCooldown", spitCooldown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        spat.clear();
        input.read("SpatMaws", UUIDUtil.CODEC.listOf()).ifPresent(spat::addAll);
        spitCooldown = input.getIntOr("SpitCooldown", FIRST_SPIT);
    }
}
