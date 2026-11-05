package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;

import java.util.List;

/**
 * Hunts in a flock that circles its prey. One at a time they split open, hang still for a moment and throw themselves at it in a straight line.
 */
public class EyeMaw extends FloatingEye
{
    public static final double LUNGE_RANGE = 10;
    public static final int OPEN_TICKS = 10;
    public static final int MAX_LUNGING = 2;

    public enum Phase
    {
        SWARM,
        OPENING,
        DASH,
        RECOIL
    }

    private static final int EXPERIENCE = 5;
    private static final float HEALTH = 10;
    private static final float BITE = 5;
    private static final double SPEED = 0.32;
    private static final int DASH_TICKS = 14;
    private static final double DASH_SHARE = 2.7;
    private static final int RECOIL_TICKS = 12;
    private static final int MIN_COOLDOWN = 50;
    private static final int COOLDOWN_SPREAD = 50;
    private static final int FIRST_COOLDOWN = 20;
    private static final double NEIGHBOUR_RADIUS = 10;
    private static final int NEIGHBOUR_INTERVAL = 4;
    private static final double SWARM_RADIUS = 5.5;
    private static final double RADIUS_PULL = 1.4;
    private static final double IDLE_SHARE = 0.4;
    private static final double WANDER_WEIGHT = 0.5;
    private static final double HOVER_TURN = 0.3;
    private static final double TURN_RATE = 0.16;
    private static final double BITE_REACH = 0.35;
    private static final double WALL_REACH = 1.4;

    private List<EyeMaw> neighbours = List.of();
    private Phase phase = Phase.SWARM;
    private int phaseTicks;
    private int cooldown;
    private Vec3 dash = Vec3.ZERO;
    private boolean spat;

    public EyeMaw(EntityType<? extends EyeMaw> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
        cooldown = FIRST_COOLDOWN + random.nextInt(COOLDOWN_SPREAD);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return eyeAttributes(HEALTH, BITE, SPEED);
    }

    public Phase phase()
    {
        return phase;
    }

    public boolean isLunging()
    {
        return phase == Phase.OPENING || phase == Phase.DASH;
    }

    public boolean wasSpat()
    {
        return spat;
    }

    public void setSpat()
    {
        spat = true;
    }

    @Override
    protected void registerGoals()
    {
        addPlayerTarget(false);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.EYE_MAW;
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);

        if ((tickCount + getId()) % NEIGHBOUR_INTERVAL == 0)
            neighbours = level.getEntitiesOfClass(EyeMaw.class, getBoundingBox().inflate(NEIGHBOUR_RADIUS), other -> other != this && other.isAlive());

        var target = hunted();
        phaseTicks++;
        switch (phase)
        {
            case SWARM -> swarm(target);
            case OPENING -> open(target);
            case DASH -> dash(level, target);
            case RECOIL -> recoil(target);
        }
    }

    private LivingEntity hunted()
    {
        var target = getTarget();
        if (target != null && !stillHunted(target))
        {
            setTarget(null);
            target = null;
        }
        if (target != null)
            return target;

        for (var neighbour : neighbours)
        {
            var shared = neighbour.getTarget();
            if (shared != null && neighbour.seesPrey() && stillHunted(shared))
            {
                setTarget(shared);
                return shared;
            }
        }
        return null;
    }

    private void swarm(LivingEntity target)
    {
        var members = neighbours.stream().filter(EyeMaw::isAlive).map(other -> new EyeFlock.Member(other.position(), other.getDeltaMovement())).toList();
        var flock = EyeFlock.pull(position(), members);
        var walls = wallPush(WALL_REACH);

        if (target == null)
        {
            steer(EyeFlock.velocity(wander().scale(WANDER_WEIGHT), flock, walls, flightSpeed() * IDLE_SHARE), TURN_RATE);
            faceAlong(getDeltaMovement());
            return;
        }

        steer(EyeFlock.velocity(circle(target), flock, walls, flightSpeed()), TURN_RATE);
        faceTarget(target);
        if (--cooldown <= 0 && canLunge(target))
            enter(Phase.OPENING);
    }

    /**
     * Around the target when it can be seen. Unseen, only the first of the flock looks for a way and the others stay with it.
     */
    private Vec3 circle(LivingEntity target)
    {
        var center = target.getEyePosition();
        if (!getSensing().hasLineOfSight(target))
        {
            var leader = neighbours.stream().filter(other -> other.isAlive() && other.getId() < getId()).findFirst();
            return leader.isPresent() ? leader.get().position().subtract(position()).normalize() : headingTo(target, center);
        }

        var out = position().subtract(center);
        double distance = out.length();
        if (distance < 1e-3)
            return new Vec3(0, 1, 0);

        var radial = out.scale(1 / distance);
        var around = radial.cross(new Vec3(0, 1, 0));
        var tangent = around.lengthSqr() > 1e-4 ? around.normalize() : new Vec3(1, 0, 0);
        return tangent.subtract(radial.scale((distance - SWARM_RADIUS) / SWARM_RADIUS * RADIUS_PULL));
    }

    private boolean canLunge(LivingEntity target)
    {
        return distanceTo(target) <= LUNGE_RANGE && getSensing().hasLineOfSight(target) && neighbours.stream().filter(EyeMaw::isLunging).count() < MAX_LUNGING;
    }

    private void open(LivingEntity target)
    {
        if (target == null || !getSensing().hasLineOfSight(target))
        {
            enter(Phase.RECOIL);
            return;
        }

        if (phaseTicks == 1)
        {
            setJawsOpen(true);
            playSound(VoidHeartSounds.EYE_MAW_OPEN, 1, 0.9F + random.nextFloat() * 0.3F);
        }
        steer(Vec3.ZERO, HOVER_TURN);
        faceTarget(target);
        if (phaseTicks >= OPEN_TICKS)
        {
            dash = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize().scale(flightSpeed() * DASH_SHARE);
            playSound(VoidHeartSounds.EYE_MAW_LUNGE, 1, 0.9F + random.nextFloat() * 0.3F);
            enter(Phase.DASH);
        }
    }

    private void dash(ServerLevel level, LivingEntity target)
    {
        setDeltaMovement(dash);
        faceAlong(dash);
        if (target != null && getBoundingBox().inflate(BITE_REACH).intersects(target.getBoundingBox()))
        {
            doHurtTarget(level, target);
            playSound(VoidHeartSounds.EYE_MAW_BITE, 1, 1);
            enter(Phase.RECOIL);
        }
        else if (phaseTicks >= DASH_TICKS || horizontalCollision || verticalCollision)
            enter(Phase.RECOIL);
    }

    private void recoil(LivingEntity target)
    {
        setJawsOpen(false);
        var away = target != null ? position().subtract(target.position()).normalize().add(0, 0.5, 0) : Vec3.ZERO;
        steer(EyeFlock.velocity(away, Vec3.ZERO, wallPush(WALL_REACH), flightSpeed()), TURN_RATE);
        if (phaseTicks >= RECOIL_TICKS)
        {
            cooldown = MIN_COOLDOWN + random.nextInt(COOLDOWN_SPREAD);
            enter(Phase.SWARM);
        }
    }

    private void enter(Phase next)
    {
        phase = next;
        phaseTicks = 0;
    }

    @Override
    protected boolean shouldDropLoot(ServerLevel level)
    {
        return !spat && super.shouldDropLoot(level);
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level)
    {
        return spat ? 0 : super.getBaseExperienceReward(level);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.putBoolean("Spat", spat);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        spat = input.getBooleanOr("Spat", false);
    }
}
