package net.voxelindustry.voidheart.common.content.daedalus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusProp;

/**
 * The soul flames of the Daedalus's lights, burning as particles over their wicks and coals. Points are in sixteenths of a block, for a prop facing north.
 */
final class DaedalusFlames
{
    private static final double PIXELS = 16;
    private static final double WICK_TO_FLAME = 2;
    private static final float SMOKE_CHANCE = 0.1F;
    private static final double BED_FROM = 4;
    private static final double BED_SPAN = 8;
    private static final double BED_TOP = 9.5;
    private static final int BRAZIER_FLAMES = 2;
    private static final double BRAZIER_RISE = 0.008;

    private static final double[][] CANDLES = { { 5, 5, 5 }, { 10, 3, 4.5 }, { 7, 6, 9.5 }, { 11, 4, 9 } };
    private static final double[][] CANDELABRUM = { { 4, 14, 8 }, { 8, 15, 8 }, { 12, 14, 8 } };
    private static final double[][] CHANDELIER = { { 3, 8, 3 }, { 13, 8, 3 }, { 3, 8, 13 }, { 13, 8, 13 } };
    private static final double[][] SCONCE = { { 8, 12, 11.5 } };
    private static final double[][] UNLIT = {};

    private DaedalusFlames()
    {
    }

    static void burn(DaedalusProp prop, Direction facing, Level level, BlockPos pos, RandomSource random)
    {
        if (prop == DaedalusProp.BRAZIER)
        {
            burnCoals(level, pos, random);
            return;
        }
        var wicks = switch (prop)
        {
            case CANDLES -> CANDLES;
            case CANDELABRUM -> CANDELABRUM;
            case CHANDELIER -> CHANDELIER;
            case SCONCE -> SCONCE;
            default -> UNLIT;
        };
        for (double[] wick : wicks)
        {
            double x = pos.getX() + turnedX(wick[0], wick[2], facing) / PIXELS;
            double y = pos.getY() + (wick[1] + WICK_TO_FLAME) / PIXELS;
            double z = pos.getZ() + turnedZ(wick[0], wick[2], facing) / PIXELS;
            level.addParticle(VoidHeart.SMALL_SOUL_FLAME_PARTICLE, x, y, z, 0, 0, 0);
            if (random.nextFloat() < SMOKE_CHANCE)
                level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
        }
    }

    private static void burnCoals(Level level, BlockPos pos, RandomSource random)
    {
        double y = pos.getY() + BED_TOP / PIXELS;
        for (int flame = 0; flame <= BRAZIER_FLAMES; flame++)
        {
            double x = pos.getX() + (BED_FROM + random.nextDouble() * BED_SPAN) / PIXELS;
            double z = pos.getZ() + (BED_FROM + random.nextDouble() * BED_SPAN) / PIXELS;
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
