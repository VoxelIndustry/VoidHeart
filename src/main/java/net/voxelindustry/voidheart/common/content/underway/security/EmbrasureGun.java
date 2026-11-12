package net.voxelindustry.voidheart.common.content.underway.security;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;
import net.voxelindustry.voidheart.common.content.underway.UnderwayEmbrasureBlock;

/**
 * The gun behind a guard room's firing slit. It runs on the emergency circuit and covers the stretch of gallery in front of its slit, within a narrow arc. Nothing reaches it through the armour: it has to be wrecked from inside its guard room.
 */
public class EmbrasureGun extends UnderwayGun
{
    public static final float WIDTH = 0.8F;
    public static final float HEIGHT = 0.9F;
    /** From the middle of the gun's block to the outer face of the slit in front of it. */
    private static final double TO_SLIT_FACE = 1.52;
    /** The middle of the slit over the foot of its block. */
    private static final double SLIT_HEIGHT = (UnderwayEmbrasureBlock.SLIT_BOTTOM + UnderwayEmbrasureBlock.SLIT_TOP) / 32.0;
    private static final double COS_ARC = Math.cos(Math.toRadians(SecurityBalance.EMBRASURE_ARC));
    /** How far in front of the gun a blow must come from to count as coming through the armour. */
    private static final double FRONT = 0.6;

    public EmbrasureGun(EntityType<? extends EmbrasureGun> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, SecurityBalance.EMBRASURE_HEALTH).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    public Vec3 muzzle()
    {
        var facing = facing();
        return new Vec3(getX() + facing.x * TO_SLIT_FACE, getBlockY() + SLIT_HEIGHT, getZ() + facing.z * TO_SLIT_FACE);
    }

    @Override
    protected boolean bears(Vec3 point)
    {
        var to = point.subtract(muzzle());
        double flat = Math.hypot(to.x, to.z);
        var facing = facing();
        return flat > 0.01 && (to.x * facing.x + to.z * facing.z) / flat >= COS_ARC && Math.abs(to.y) <= flat;
    }

    @Override
    protected boolean folds()
    {
        return false;
    }

    @Override
    protected boolean isArmoured(DamageSource source)
    {
        var from = source.getSourcePosition();
        return from == null || from.subtract(position()).dot(facing()) > FRONT;
    }
}
