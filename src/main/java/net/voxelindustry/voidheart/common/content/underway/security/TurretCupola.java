package net.voxelindustry.voidheart.common.content.underway.security;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;

/**
 * The gun in a ceiling cupola. It only works on power: asleep it stays up inside its armoured housing, where nothing harms it. Awake it drops under the ceiling to fire all around, and can be struck while it is down.
 */
public class TurretCupola extends UnderwayGun
{
    public static final float WIDTH = 0.8F;
    public static final float HEIGHT = 1.5F;
    /** How far under its housing block the entity's feet are, so that it covers the housing and the gun hanging under it. */
    public static final double HANG = 0.5;
    /** How far under the housing the barrel hangs once it is down. */
    private static final double MUZZLE_DROP = 0.3;

    public TurretCupola(EntityType<? extends TurretCupola> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, SecurityBalance.CUPOLA_HEALTH).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    public Vec3 muzzle()
    {
        return new Vec3(getX(), getY() + HANG - MUZZLE_DROP, getZ());
    }

    @Override
    protected boolean bears(Vec3 point)
    {
        return point.y < getY() + HANG;
    }

    @Override
    protected boolean isArmoured(DamageSource source)
    {
        return phase() == Phase.FOLDED || phase() == Phase.DEPLOYING;
    }
}
