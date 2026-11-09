package net.voxelindustry.voidheart.common.energy;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.common.energy.creative.CreativeSources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LeyPowerTest
{
    private static final BlockPos STONE = new BlockPos(10, 70, -4);

    private final ServerLevel level = mock(ServerLevel.class);

    @BeforeAll
    static void bootstrap()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void reset()
    {
        LeyPower.install(LeyPowerProvider.NONE);
        VoidSeep.install(VoidSeepProvider.NONE);
        CreativeSources.clear();
    }

    @Test
    void defaultSupplyMeetsNoNeed()
    {
        assertThat(LeyPower.supply(level, STONE, 1)).isZero();
    }

    @Test
    void needingNothingIsAlwaysMet()
    {
        assertThat(LeyPower.supply(level, STONE, 0)).isEqualTo(1);
        assertThat(LeyPower.supply(level, STONE, -2)).isEqualTo(1);
    }

    @Test
    void creativeStoneMeetsAnyNeedNearIt()
    {
        CreativeSources.stones(level).add(STONE, FoundationBalance.CREATIVE_LEY_STONE_RADIUS);

        assertThat(LeyPower.supply(level, STONE.above(FoundationBalance.CREATIVE_LEY_STONE_RADIUS), 1000)).isEqualTo(1);
        assertThat(LeyPower.supply(level, STONE.above(FoundationBalance.CREATIVE_LEY_STONE_RADIUS + 1), 1000)).isZero();
    }

    @Test
    void installedSupplyIsKeptBetweenZeroAndOne()
    {
        LeyPower.install((level, pos, need) -> pos.getX() > 0 ? 3 : pos.getX() < 0 ? -1 : Float.NaN);

        assertThat(LeyPower.supply(level, new BlockPos(1, 0, 0), 1)).isEqualTo(1);
        assertThat(LeyPower.supply(level, new BlockPos(-1, 0, 0), 1)).isZero();
        assertThat(LeyPower.supply(level, BlockPos.ZERO, 1)).isZero();
    }

    @Test
    void installedSupplyPassesThroughInsideRange()
    {
        LeyPower.install((level, pos, need) -> (float) (0.5 / need));

        assertThat(LeyPower.supply(level, STONE, 2)).isEqualTo(0.25F);
    }

    @Test
    void seepIsAbsentByDefaultAndKeptBetweenZeroAndOne()
    {
        assertThat(VoidSeep.strengthAt(level, STONE)).isZero();

        VoidSeep.install((level, pos) -> pos.getY() / 10F);
        assertThat(VoidSeep.strengthAt(level, new BlockPos(0, 5, 0))).isEqualTo(0.5F);
        assertThat(VoidSeep.strengthAt(level, new BlockPos(0, 50, 0))).isEqualTo(1);
        assertThat(VoidSeep.strengthAt(level, new BlockPos(0, -5, 0))).isZero();
    }
}
