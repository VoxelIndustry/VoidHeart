package net.voxelindustry.voidheart.common.energy.storage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConstructChargeTest
{
    private long tick;

    private ConstructCharge charge(long capacity, long rate)
    {
        var charge = new ConstructCharge(() -> tick);
        charge.setSockets(capacity, rate);
        charge.setActive(true);
        return charge;
    }

    @Test
    void socketsSetCapacityAndRate()
    {
        // N0's starter: 3 small rubrum and 1 small azurium
        var starter = List.of(SocketBundle.SMALL_RUBRUM, SocketBundle.SMALL_RUBRUM, SocketBundle.SMALL_RUBRUM, SocketBundle.SMALL_AZURIUM);
        assertThat(SocketBundle.capacityOf(starter)).isEqualTo(750_000);
        assertThat(SocketBundle.rateOf(starter)).isEqualTo(64);

        // N0's advanced: 8 medium rubrum and 4 medium azurium
        var sockets = new SocketSet();
        for (var index = 0; index < 8; index++)
            sockets.set(index, SocketBundle.MEDIUM_RUBRUM);
        for (var index = 8; index < 12; index++)
            sockets.set(index, SocketBundle.MEDIUM_AZURIUM);
        assertThat(sockets.capacity()).isEqualTo(8_000_000);
        assertThat(sockets.rate()).isEqualTo(640);

        assertThat(SocketBundle.LARGE_RUBRUM.capacity()).isEqualTo(4_000_000);
        assertThat(SocketBundle.LARGE_RUBRUM.rate()).isZero();
        assertThat(SocketBundle.LARGE_AZURIUM.rate()).isEqualTo(320);
        assertThat(SocketBundle.LARGE_AZURIUM.capacity()).isZero();
    }

    @Test
    void rateCapsEachTickAcrossCalls()
    {
        var charge = charge(1000, 64);

        assertThat(charge.insert(40, false)).isEqualTo(40);
        assertThat(charge.insert(40, false)).isEqualTo(24);
        assertThat(charge.insert(40, false)).isZero();

        tick++;
        assertThat(charge.insert(100, false)).isEqualTo(64);
        assertThat(charge.extract(100, false)).isEqualTo(64);
        assertThat(charge.extract(100, false)).isZero();
        assertThat(charge.stored()).isEqualTo(64);
    }

    @Test
    void capacityAndStoredBound()
    {
        var charge = charge(100, 64);
        for (var step = 0; step < 3; step++)
        {
            charge.insert(64, false);
            tick++;
        }
        assertThat(charge.stored()).isEqualTo(100);
        assertThat(charge.insert(10, true)).isZero();
        assertThat(charge.fill()).isEqualTo(1F);
    }

    @Test
    void noAzuriumOrUnformedMovesNothing()
    {
        var noRate = charge(1000, 0);
        assertThat(noRate.insert(10, false)).isZero();

        var unformed = charge(1000, 64);
        unformed.setStored(500);
        unformed.setActive(false);
        assertThat(unformed.insert(10, false)).isZero();
        assertThat(unformed.extract(10, false)).isZero();
        assertThat(unformed.maxRate()).isZero();
        assertThat(unformed.stored()).isEqualTo(500);
    }

    @Test
    void simulateChangesNothing()
    {
        var charge = charge(1000, 64);
        charge.setStored(500);
        assertThat(charge.insert(30, true)).isEqualTo(30);
        assertThat(charge.extract(30, true)).isEqualTo(30);
        assertThat(charge.stored()).isEqualTo(500);
        assertThat(charge.insert(64, false)).isEqualTo(64);
    }

    @Test
    void rubrumLeavesOnlyWhenTheRestHoldsTheCharge()
    {
        var charge = charge(750_000, 64);
        charge.setStored(600_000);
        assertThat(charge.canLoseCapacity(250_000)).isFalse();
        charge.setStored(500_000);
        assertThat(charge.canLoseCapacity(250_000)).isTrue();
    }

    @Test
    void netFlowReadsTheLastWholeTick()
    {
        var charge = charge(10_000, 64);
        charge.insert(50, false);
        charge.extract(20, false);
        assertThat(charge.lastNetFlow()).isZero();

        tick++;
        assertThat(charge.lastNetFlow()).isEqualTo(30);

        tick += 5;
        assertThat(charge.lastNetFlow()).isZero();
    }
}
