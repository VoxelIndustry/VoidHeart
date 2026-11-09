package net.voxelindustry.voidheart.common.energy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoidBufferTest
{
    @Test
    void insertStopsAtCapacityAndRate()
    {
        var buffer = new VoidBuffer(100, 30);

        assertThat(buffer.insert(50, false)).isEqualTo(30);
        assertThat(buffer.insert(50, false)).isEqualTo(30);
        assertThat(buffer.insert(50, false)).isEqualTo(30);
        assertThat(buffer.insert(50, false)).isEqualTo(10);
        assertThat(buffer.insert(50, false)).isZero();
        assertThat(buffer.stored()).isEqualTo(100);
    }

    @Test
    void extractStopsAtStoredAndRate()
    {
        var buffer = new VoidBuffer(100, 30);
        buffer.setStored(40);

        assertThat(buffer.extract(100, false)).isEqualTo(30);
        assertThat(buffer.extract(100, false)).isEqualTo(10);
        assertThat(buffer.extract(100, false)).isZero();
    }

    @Test
    void simulateChangesNothing()
    {
        var buffer = new VoidBuffer(100, 30);
        buffer.setStored(50);

        assertThat(buffer.insert(20, true)).isEqualTo(20);
        assertThat(buffer.extract(20, true)).isEqualTo(20);
        assertThat(buffer.stored()).isEqualTo(50);
    }

    @Test
    void negativeAmountsMoveNothing()
    {
        var buffer = new VoidBuffer(100, 30);
        buffer.setStored(50);

        assertThat(buffer.insert(-20, false)).isZero();
        assertThat(buffer.extract(-20, false)).isZero();
        assertThat(buffer.stored()).isEqualTo(50);
    }

    @Test
    void savedValuesAreClampedToThisBuffer()
    {
        var buffer = new VoidBuffer(100, 30);

        buffer.setStored(1_000);
        assertThat(buffer.stored()).isEqualTo(100);
        buffer.setStored(-5);
        assertThat(buffer.stored()).isZero();
    }
}
