package net.voxelindustry.voidheart.common.energy;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.common.energy.creative.CreativeSources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VoidEnergyTest
{
    private static final BlockPos WELL = new BlockPos(0, 64, 0);

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
        VoidEnergy.install(VoidNetworkProvider.NONE);
        CreativeSources.clear();
    }

    @Test
    void defaultNetworkGivesAndTakesNothing()
    {
        assertThat(VoidEnergy.provider()).isSameAs(VoidNetworkProvider.NONE);
        assertThat(VoidEnergy.available(level, WELL)).isZero();
        assertThat(VoidEnergy.draw(level, WELL, 100)).isZero();
        assertThat(VoidEnergy.offer(level, WELL, 100)).isZero();
    }

    @Test
    void creativeWellIsEndlessAndTakesAnyOfferInsideItsField()
    {
        CreativeSources.wells(level).add(WELL, FoundationBalance.CREATIVE_WELL_RADIUS);
        var inside = WELL.offset(FoundationBalance.CREATIVE_WELL_RADIUS, 0, 0);

        assertThat(VoidEnergy.available(level, inside)).isEqualTo(Long.MAX_VALUE);
        assertThat(VoidEnergy.draw(level, inside, 1_000_000)).isEqualTo(1_000_000);
        assertThat(VoidEnergy.draw(level, inside, Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
        assertThat(VoidEnergy.offer(level, inside, 5_000)).isEqualTo(5_000);
    }

    @Test
    void creativeWellDoesNothingOutsideItsField()
    {
        CreativeSources.wells(level).add(WELL, FoundationBalance.CREATIVE_WELL_RADIUS);
        var outside = WELL.offset(FoundationBalance.CREATIVE_WELL_RADIUS + 1, 0, 0);

        assertThat(VoidEnergy.available(level, outside)).isZero();
        assertThat(VoidEnergy.draw(level, outside, 100)).isZero();
        assertThat(VoidEnergy.offer(level, outside, 100)).isZero();
    }

    @Test
    void wellsBelongToTheirOwnLevel()
    {
        CreativeSources.wells(level).add(WELL, FoundationBalance.CREATIVE_WELL_RADIUS);
        var other = mock(ServerLevel.class);

        assertThat(VoidEnergy.draw(other, WELL, 100)).isZero();
    }

    @Test
    void brokenWellStopsGiving()
    {
        CreativeSources.wells(level).add(WELL, FoundationBalance.CREATIVE_WELL_RADIUS);
        CreativeSources.wells(level).remove(WELL);

        assertThat(VoidEnergy.draw(level, WELL, 100)).isZero();
    }

    @Test
    void nothingMovesForZeroOrNegativeAmounts()
    {
        CreativeSources.wells(level).add(WELL, FoundationBalance.CREATIVE_WELL_RADIUS);

        assertThat(VoidEnergy.draw(level, WELL, 0)).isZero();
        assertThat(VoidEnergy.draw(level, WELL, -50)).isZero();
        assertThat(VoidEnergy.offer(level, WELL, -50)).isZero();
    }

    @Test
    void installedNetworkAnswersOutsideWellsAndIsClampedToTheAmount()
    {
        var calls = new ArrayList<String>();
        VoidEnergy.install(new VoidNetworkProvider()
        {
            @Override
            public long available(ServerLevel level, BlockPos pos)
            {
                return -10;
            }

            @Override
            public long draw(ServerLevel level, BlockPos pos, long amount)
            {
                calls.add("draw " + amount);
                return amount * 2;
            }

            @Override
            public long offer(ServerLevel level, BlockPos pos, long amount)
            {
                calls.add("offer " + amount);
                return -amount;
            }
        });

        assertThat(VoidEnergy.available(level, WELL)).isZero();
        assertThat(VoidEnergy.draw(level, WELL, 40)).isEqualTo(40);
        assertThat(VoidEnergy.offer(level, WELL, 40)).isZero();
        assertThat(calls).containsExactly("draw 40", "offer 40");
    }

    @Test
    void wellAnswersBeforeTheInstalledNetwork()
    {
        var calls = new ArrayList<String>();
        VoidEnergy.install(recording(calls));
        CreativeSources.wells(level).add(WELL, FoundationBalance.CREATIVE_WELL_RADIUS);

        VoidEnergy.draw(level, WELL, 10);
        VoidEnergy.offer(level, WELL, 10);
        assertThat(calls).isEmpty();
    }

    private static VoidNetworkProvider recording(List<String> calls)
    {
        return new VoidNetworkProvider()
        {
            @Override
            public long available(ServerLevel level, BlockPos pos)
            {
                calls.add("available");
                return 0;
            }

            @Override
            public long draw(ServerLevel level, BlockPos pos, long amount)
            {
                calls.add("draw");
                return 0;
            }

            @Override
            public long offer(ServerLevel level, BlockPos pos, long amount)
            {
                calls.add("offer");
                return 0;
            }
        };
    }
}
