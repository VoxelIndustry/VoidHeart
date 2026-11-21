package net.voxelindustry.voidheart.common.content.underway;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.util.SoulFlames;
import net.voxelindustry.voidheart.common.world.underway.UnderwayProp;

/**
 * The soul flames a temple's cult still keeps in a sealed fort, burning as particles over the wicks of its candles and the coals of its braziers, the same as the Daedalus's above. Points are in sixteenths of a block, for a prop facing north.
 */
final class TempleFlames
{
    private static final double WICK_TO_FLAME = 1;
    private static final double COALS_TOP = 12.5;
    /** The top of each wick of {@link UnderwayProp#VOTIVE_CANDLES_LIT}, where its flame stands. */
    private static final double[][] WICKS = { { 4.5, 8, 4.5 }, { 9.5, 5, 3.5 }, { 7.5, 11, 9.5 }, { 12.5, 7, 10.5 }, { 3.5, 4, 12.5 } };

    private TempleFlames()
    {
    }

    static void burn(UnderwayProp prop, Direction facing, Level level, BlockPos pos, RandomSource random)
    {
        if (prop == UnderwayProp.BRAZIER_LIT)
            SoulFlames.coals(COALS_TOP, level, pos, random);
        else if (prop == UnderwayProp.VOTIVE_CANDLES_LIT)
            SoulFlames.wicks(WICKS, WICK_TO_FLAME, facing, level, pos, random);
    }
}
