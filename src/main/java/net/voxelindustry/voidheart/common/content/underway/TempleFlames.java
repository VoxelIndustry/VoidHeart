package net.voxelindustry.voidheart.common.content.underway;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.world.underway.UnderwayProp;

/**
 * The soul flames a temple's cult still keeps in a sealed fort, burning as particles over the wicks of its candles and the coals of its braziers, the same as the Daedalus's above. Points are in sixteenths of a block, for a prop facing north.
 */
final class TempleFlames
{
    private static final double PIXELS = 16;
    private static final double WICK_TO_FLAME = 1;
    private static final float SMOKE_CHANCE = 0.1F;
    private static final double COALS_FROM = 4;
    private static final double COALS_SPAN = 8;
    private static final double COALS_TOP = 12.5;
    private static final int BRAZIER_FLAMES = 2;
    private static final double BRAZIER_RISE = 0.008;
    /** The top of each wick of {@link UnderwayProp#VOTIVE_CANDLES_LIT}, where its flame stands. */
    private static final double[][] WICKS = { { 4.5, 8, 4.5 }, { 9.5, 5, 3.5 }, { 7.5, 11, 9.5 }, { 12.5, 7, 10.5 }, { 3.5, 4, 12.5 } };

    private TempleFlames()
    {
    }

    static void burn(UnderwayProp prop, Direction facing, Level level, BlockPos pos, RandomSource random)
    {
        if (prop == UnderwayProp.BRAZIER_LIT)
            burnCoals(level, pos, random);
        else if (prop == UnderwayProp.VOTIVE_CANDLES_LIT)
            burnWicks(facing, level, pos, random);
    }

    private static void burnWicks(Direction facing, Level level, BlockPos pos, RandomSource random)
    {
        for (double[] wick : WICKS)
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
        double y = pos.getY() + COALS_TOP / PIXELS;
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
