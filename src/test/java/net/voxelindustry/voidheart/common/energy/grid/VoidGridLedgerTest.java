package net.voxelindustry.voidheart.common.energy.grid;

import net.minecraft.core.BlockPos;
import net.voxelindustry.voidheart.common.energy.IVoidHolder;
import net.voxelindustry.voidheart.common.energy.VoidBuffer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VoidGridLedgerTest
{
    private static final int Y = 64;

    /**
     * Small pylons 10 blocks apart along x, each field 6 wide, so a chain links and no field covers its neighbour's machines.
     */
    private static GridLayout chain(int count)
    {
        var pylons = new java.util.ArrayList<GridLayout.Pylon>();
        for (int i = 0; i < count; i++)
            pylons.add(new GridLayout.Pylon(new BlockPos(i * 10, Y, 0), new BlockPos(i * 10, Y + 1, 0), VoidGridBalance.SMALL_RADIUS, VoidGridBalance.SMALL_CAP));
        return GridLayout.link(pylons, (a, b) -> true);
    }

    private static BlockPos near(int pylon)
    {
        return new BlockPos(pylon * 10 + 2, Y, 1);
    }

    private static VoidGridLedger ledger(GridLayout layout, Map<BlockPos, IVoidHolder> holders)
    {
        var ledger = new VoidGridLedger();
        var cover = new HashMap<BlockPos, int[]>();
        holders.keySet().forEach(pos -> cover.put(pos, layout.covering(pos)));
        ledger.setLayout(layout, cover, holders::get);
        return ledger;
    }

    @Test
    void aSourceFeedsAMachineThreePylonsAwayEveryTick()
    {
        var ledger = ledger(chain(3), Map.of());
        var source = near(0);
        var machine = near(2);

        long drawn = 0;
        long offered = 0;
        for (int tick = 1; tick <= 40; tick++)
        {
            ledger.startTick(tick);
            offered += ledger.offer(source, 30);
            drawn += ledger.draw(machine, 30);
        }

        assertThat(drawn).isEqualTo(30 * 40);
        assertThat(offered).isEqualTo(drawn);
    }

    @Test
    void theOrderMachinesAndSourcesTickInOnlyCostsTheFirstTick()
    {
        var ledger = ledger(chain(3), Map.of());
        var source = near(0);
        var machine = near(2);

        long drawn = 0;
        for (int tick = 1; tick <= 40; tick++)
        {
            ledger.startTick(tick);
            drawn += ledger.draw(machine, 30);
            ledger.offer(source, 30);
        }

        assertThat(drawn).isEqualTo(30 * 39);
        // Nothing is lost: the last tick's offer waits for the machine
        assertThat(ledger.transitAt(source) + ledger.readyAt(machine)).isEqualTo(30);
    }

    @Test
    void theSmallestPylonOnThePathCapsTheChain()
    {
        var ledger = ledger(chain(3), Map.of());
        long drawn = 0;
        for (int tick = 1; tick <= 20; tick++)
        {
            ledger.startTick(tick);
            ledger.offer(near(0), 500);
            drawn = ledger.draw(near(2), 500);
        }
        assertThat(drawn).isEqualTo(VoidGridBalance.SMALL_CAP);
    }

    @Test
    void aSourceRunsOnlyWhileSomethingTakesItsEnergy()
    {
        var ledger = ledger(chain(2), Map.of());
        long offered = 0;
        for (int tick = 1; tick <= 50; tick++)
        {
            ledger.startTick(tick);
            offered += ledger.offer(near(0), 30);
        }
        // It fills what its one pylon passes in a tick, then waits
        assertThat(offered).isEqualTo(VoidGridBalance.SMALL_CAP);
        assertThat(ledger.offer(near(0), 30)).isZero();
    }

    @Test
    void anOfferOutsideEveryFieldIsRefused()
    {
        var ledger = ledger(chain(2), Map.of());
        ledger.startTick(1);
        assertThat(ledger.offer(new BlockPos(500, Y, 500), 30)).isZero();
        assertThat(ledger.draw(new BlockPos(500, Y, 500), 30)).isZero();
    }

    @Test
    void holdersChargeFromSurplusAndCarryMachinesWhenSourcesStop()
    {
        var storage = new VoidBuffer(10_000, 64);
        var layout = chain(2);
        var ledger = ledger(layout, Map.of(near(1), storage));

        for (int tick = 1; tick <= 20; tick++)
        {
            ledger.startTick(tick);
            ledger.offer(near(0), 50);
        }
        assertThat(storage.stored()).isEqualTo(19 * 50);

        long drawn = 0;
        for (int tick = 21; tick <= 30; tick++)
        {
            ledger.startTick(tick);
            drawn += ledger.draw(near(1), 40);
        }
        // The last offer reaches the holder first, which leaves the pylon 30 to pass that tick; then the holder carries the machine
        assertThat(drawn).isEqualTo(30 + 9 * 40);
        assertThat(storage.stored()).isEqualTo(20 * 50 - drawn);
    }

    @Test
    void surplusTransitChargesAHolderRatherThanDrainingIt()
    {
        var storage = new VoidBuffer(10_000, 64);
        storage.setStored(5_000);
        var ledger = ledger(chain(1), Map.of(near(0).east(), storage));

        for (int tick = 1; tick <= 20; tick++)
        {
            ledger.startTick(tick);
            ledger.offer(near(0), 40);
            ledger.draw(near(0), 30);
        }
        // The 10 a tick left over charges it instead
        assertThat(storage.stored()).isGreaterThan(5_000);
    }

    @Test
    void anEstablishedDrawKeepsItsPowerWhenANewMachineStarts()
    {
        var ledger = ledger(chain(1), Map.of());
        var steady = near(0);
        var brew = near(0).north();

        for (int tick = 1; tick <= 20; tick++)
        {
            ledger.startTick(tick);
            ledger.offer(new BlockPos(1, Y, 0), 80);
            ledger.draw(steady, 64);
        }

        long steadyGot = 0;
        long brewGot = 0;
        for (int tick = 21; tick <= 60; tick++)
        {
            ledger.startTick(tick);
            // The new machine ticks first every time
            brewGot += ledger.draw(brew, 80);
            steadyGot += ledger.draw(steady, 64);
            ledger.offer(new BlockPos(1, Y, 0), 80);
        }

        assertThat(steadyGot).isEqualTo(64 * 40);
        assertThat(brewGot).isLessThanOrEqualTo(16 * 40);
    }

    @Test
    void energyWaitingForAMachineThatStoppedGoesBackIntoTheGrid()
    {
        var storage = new VoidBuffer(10_000, 200);
        var ledger = ledger(chain(1), Map.of(near(0).east(), storage));
        var machine = near(0);
        var source = new BlockPos(1, Y, 0);

        // Asked once, delivered ahead at the next tick, then never drawn
        ledger.startTick(1);
        ledger.draw(machine, 50);
        ledger.offer(source, 50);
        ledger.startTick(2);
        assertThat(ledger.readyAt(machine)).isEqualTo(50);

        for (int tick = 3; tick < 3 + VoidGridBalance.READY_EXPIRY_TICKS + 5; tick++)
            ledger.startTick(tick);

        assertThat(ledger.readyAt(machine)).isZero();
        assertThat(storage.stored()).isEqualTo(50);
    }

    @Test
    void availableCountsWhatWaitsAndWhatCouldBeRouted()
    {
        var storage = new VoidBuffer(10_000, 64);
        storage.setStored(5_000);
        var ledger = ledger(chain(2), Map.of(near(0).east(), storage));

        ledger.startTick(1);
        ledger.offer(near(0), 30);
        assertThat(ledger.available(near(1))).isEqualTo(VoidGridBalance.SMALL_CAP);
        assertThat(ledger.draw(near(1), 30)).isEqualTo(30);
        assertThat(ledger.available(near(1))).isEqualTo(VoidGridBalance.SMALL_CAP - 30);
        assertThat(ledger.available(new BlockPos(500, Y, 0))).isZero();
    }

    @Test
    void displayAveragesThroughputArcsAndThreads()
    {
        var layout = chain(3);
        var ledger = ledger(layout, Map.of());
        ledger.takeDisplay();
        for (int tick = 1; tick <= 10; tick++)
        {
            ledger.startTick(tick);
            ledger.offer(near(0), 40);
            ledger.draw(near(2), 40);
        }
        List<VoidGridLedger.PylonDisplay> display = ledger.takeDisplay();

        assertThat(display.get(1).throughput()).isEqualTo(40);
        var first = layout.pylons().get(0).pos();
        var last = layout.pylons().get(2).pos();
        var middle = layout.pylons().get(1).pos();
        assertThat(display.get(1).arcs()).containsEntry(last, 40L).containsEntry(first, -40L);
        assertThat(display.get(0).arcs()).containsEntry(middle, 40L);
        assertThat(display.get(2).fed()).containsEntry(near(2), 40L);
        assertThat(display.get(0).fed()).isEmpty();
    }
}
