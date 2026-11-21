package net.voxelindustry.voidheart.common.content.daedalus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.util.SoulFlames;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusProp;

/**
 * The soul flames of the Daedalus's lights, burning as particles over their wicks and coals. Points are in sixteenths of a block, for a prop facing north.
 */
final class DaedalusFlames
{
    private static final double WICK_TO_FLAME = 2;
    private static final double BED_TOP = 9.5;

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
            SoulFlames.coals(BED_TOP, level, pos, random);
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
        SoulFlames.wicks(wicks, WICK_TO_FLAME, facing, level, pos, random);
    }
}
