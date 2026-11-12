package net.voxelindustry.voidheart.common.content.underway.security;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;

/**
 * A searchlight standing in a hall of the Underway. It only burns on power. Its narrow beam sweeps far across the hall, and whoever it catches is known to the whole section for a while: its machines come for them without having seen them, and its mortars drop shells on them behind cover.
 * <p>
 * The beam shows where it looks, so the way past is in its shadow, or through its glass.
 */
public class Searchlight extends SentinelEye
{
    public static final float WIDTH = 0.6F;
    public static final float HEIGHT = 0.9F;
    /** Its lamp stands this high over the floor, and its glass this far in front of the middle of its block. */
    private static final double LENS_HEIGHT = 0.625;
    private static final double LENS_OUT = 0.375 + 0.004;
    /** It looks level, so its beam covers a standing body all the way down a hall. */
    private static final float PITCH = 0;

    public Searchlight(EntityType<? extends Searchlight> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, SecurityBalance.SEARCHLIGHT_HEALTH).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    public double range()
    {
        return SecurityBalance.SEARCHLIGHT_RANGE;
    }

    @Override
    public float cone()
    {
        return SecurityBalance.SEARCHLIGHT_CONE;
    }

    @Override
    protected float sweepHalf()
    {
        return SecurityBalance.SEARCHLIGHT_SWEEP;
    }

    @Override
    protected int sweepTicks()
    {
        return SecurityBalance.SEARCHLIGHT_SWEEP_TICKS;
    }

    @Override
    protected float pitch()
    {
        return PITCH;
    }

    @Override
    public int noticeTicks()
    {
        return SecurityBalance.SEARCHLIGHT_NOTICE_TICKS;
    }

    @Override
    protected boolean watches(boolean awake)
    {
        return awake;
    }

    @Override
    public Vec3 lens()
    {
        return position().add(0, LENS_HEIGHT, 0).add(facing().scale(LENS_OUT));
    }

    @Override
    protected void report(ServerLevel level, Player seen, boolean first)
    {
        if (first)
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), getSoundSource(), 2.5F, 1.4F);
        link().spot(level, seen);
    }
}
