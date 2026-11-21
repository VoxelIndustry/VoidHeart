package net.voxelindustry.voidheart.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.VoidHeart;

/**
 * Soul flames burning as particles over the wicks of a prop's candles or the coals of its brazier, as the lights of the Daedalus and of a temple's cult
 * do. Points are in sixteenths of a block, for a prop facing north.
 */
public final class SoulFlames
{
    private static final double PIXELS         = 16;
    private static final float  SMOKE_CHANCE   = 0.1F;
    private static final double COALS_FROM     = 4;
    private static final double COALS_SPAN     = 8;
    private static final int    BRAZIER_FLAMES = 2;
    private static final double BRAZIER_RISE   = 0.008;

    private SoulFlames()
    {
    }

    /**
     * @param wicks       the tip of each wick, as x, y, z
     * @param wickToFlame how far above its wick each flame stands
     */
    public static void wicks(double[][] wicks, double wickToFlame, Direction facing, Level level, BlockPos pos, RandomSource random)
    {
        for (double[] wick : wicks)
        {
            double x = pos.getX() + turnedX(wick[0], wick[2], facing) / PIXELS;
            double y = pos.getY() + (wick[1] + wickToFlame) / PIXELS;
            double z = pos.getZ() + turnedZ(wick[0], wick[2], facing) / PIXELS;
            level.addParticle(VoidHeart.SMALL_SOUL_FLAME_PARTICLE, x, y, z, 0, 0, 0);
            if (random.nextFloat() < SMOKE_CHANCE)
                level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
        }
    }

    /**
     * @param top the height of the bed of coals
     */
    public static void coals(double top, Level level, BlockPos pos, RandomSource random)
    {
        double y = pos.getY() + top / PIXELS;
        for (int flame = 0; flame <= BRAZIER_FLAMES; flame++)
        {
            double x = pos.getX() + (COALS_FROM + random.nextDouble() * COALS_SPAN) / PIXELS;
            double z = pos.getZ() + (COALS_FROM + random.nextDouble() * COALS_SPAN) / PIXELS;
            var type = flame < BRAZIER_FLAMES ? ParticleTypes.SOUL_FIRE_FLAME : VoidHeart.SMALL_SOUL_FLAME_PARTICLE;
            level.addParticle(type, x, y, z, 0, BRAZIER_RISE, 0);
        }
        if (random.nextFloat() < SMOKE_CHANCE)
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, y, pos.getZ() + 0.5, 0, BRAZIER_RISE, 0);
    }

    /** Blockstates turn a prop clockwise seen from above, a quarter turn from north for each step to east, south and west. */
    private static double turnedX(double x, double z, Direction facing)
    {
        return switch (facing)
        {
            case EAST -> PIXELS - z;
            case SOUTH -> PIXELS - x;
            case WEST -> z;
            default -> x;
        };
    }

    private static double turnedZ(double x, double z, Direction facing)
    {
        return switch (facing)
        {
            case EAST -> x;
            case SOUTH -> PIXELS - z;
            case WEST -> PIXELS - x;
            default -> z;
        };
    }
}
