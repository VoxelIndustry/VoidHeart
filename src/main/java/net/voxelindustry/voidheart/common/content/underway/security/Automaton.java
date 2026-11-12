package net.voxelindustry.voidheart.common.content.underway.security;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;

/**
 * A walking machine of the Underway: riveted copper and brass around a boiler, with one eye of amber glass. Asleep it stands cold at its post, and nothing harms it. Woken, it takes a moment to build pressure, venting steam from its back, before its eye opens.
 * <p>
 * It carries a piston lance: from a distance it throws bolts with it, showing the line of each before it fires, and once its target is close it charges and thrusts. It opens the fort's doors, and walks back to its post when it has lost whoever it hunted.
 */
public class Automaton extends SecurityMob
{
    public static final float WIDTH = 0.6F;
    public static final float HEIGHT = 1.95F;
    /** How long it builds pressure between waking and moving. */
    public static final int WAKE_TICKS = 24;

    private static final double MUZZLE_HEIGHT = 1.35;
    private static final double MUZZLE_OUT = 1.3;
    /** Its lance lies along its right arm, this far from its middle. */
    private static final double MUZZLE_RIGHT = 0.5;

    /** Kept by the client for show: how far its lance is raised. */
    public float raise;
    public float raiseO;

    public Automaton(EntityType<? extends Automaton> type, Level level)
    {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation ground)
            ground.setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, SecurityBalance.AUTOMATON_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, SecurityBalance.AUTOMATON_LANCE_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, SecurityBalance.AUTOMATON_SPEED)
                .add(Attributes.ARMOR, SecurityBalance.AUTOMATON_ARMOUR)
                .add(Attributes.ARMOR_TOUGHNESS, 4)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, SecurityBalance.AUTOMATON_SIGHT);
    }

    @Override
    public int wakeTicks()
    {
        return WAKE_TICKS;
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(2, new BoltGoal(SecurityBalance.AUTOMATON_CHARGE_RANGE, SecurityBalance.AUTOMATON_SHOT_RANGE, SecurityBalance.AUTOMATON_AIM_TICKS, SecurityBalance.AUTOMATON_LOCK_TICKS,
                1, 1, SecurityBalance.AUTOMATON_RELOAD_TICKS, SecurityBalance.AUTOMATON_SHOT_DAMAGE));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(5, new ReturnToPostGoal());
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public boolean isAimLocked()
    {
        return aimTicks() > SecurityBalance.AUTOMATON_AIM_TICKS - SecurityBalance.AUTOMATON_LOCK_TICKS;
    }

    /** @return where its bolts leave from: the point of its lance, held level at its shoulder */
    @Override
    public Vec3 muzzle()
    {
        var forward = Vec3.directionFromRotation(0, yBodyRot);
        return new Vec3(getX() + forward.x * MUZZLE_OUT - forward.z * MUZZLE_RIGHT, getY() + MUZZLE_HEIGHT, getZ() + forward.z * MUZZLE_OUT + forward.x * MUZZLE_RIGHT);
    }

    @Override
    protected void clientTick()
    {
        raiseO = raise;
        raise = Math.clamp(raise + (aimTicks() > 0 ? 0.25F : -0.15F), 0, 1);
    }
}
