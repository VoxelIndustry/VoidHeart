package net.voxelindustry.voidheart.common.content.hoard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PocketReserveTest
{
    private static final PocketReserve.SlotId FIRST  = new PocketReserve.SlotId(1, 0);
    private static final PocketReserve.SlotId SECOND = new PocketReserve.SlotId(2, 0);
    private static final PocketReserve.SlotId FREE   = new PocketReserve.SlotId(3, 0);

    @Test
    void addSlot_givenAFilledVessel_thenPoursItInAndGivesItsRoom()
    {
        var reserve = new PocketReserve<String>();

        reserve.addSlot(FIRST, "fiery", 32_000, 10_000);

        assertThat(reserve.amountOf("fiery")).isEqualTo(10_000);
        assertThat(reserve.capacityOf("fiery")).isEqualTo(32_000);
        assertThat(reserve.room("fiery")).isEqualTo(22_000);
        assertThat(reserve.room("stillness")).isZero();
    }

    @Test
    void insert_givenMoreThanTheAttunedRoom_thenAttunesAFreeSlot()
    {
        var reserve = new PocketReserve<String>();
        reserve.addSlot(FIRST, "fiery", 1_000, 1_000);
        reserve.addSlot(FREE, null, 1_000, 0);

        var taken = reserve.insert("fiery", 1_500);

        assertThat(taken).isEqualTo(1_000);
        assertThat(reserve.slots().get(FREE).aspect()).isEqualTo("fiery");
        assertThat(reserve.room("stillness")).isZero();
    }

    @Test
    void insert_givenAnAspectNothingHolds_thenFillsAFreeSlotOnly()
    {
        var reserve = new PocketReserve<String>();
        reserve.addSlot(FIRST, "fiery", 1_000, 0);
        reserve.addSlot(FREE, null, 500, 0);

        assertThat(reserve.insert("stillness", 800)).isEqualTo(500);
        assertThat(reserve.slots().get(FIRST).aspect()).isEqualTo("fiery");
    }

    @Test
    void removeSlot_givenTwoVesselsOfAnAspect_thenTakesItsShareByRoom()
    {
        var reserve = new PocketReserve<String>();
        reserve.addSlot(FIRST, "fiery", 32_000, 16_000);
        reserve.addSlot(SECOND, "fiery", 32_000, 0);

        var share = reserve.removeSlot(SECOND);

        assertThat(share).isEqualTo(new PocketReserve.Share<>("fiery", 8_000L));
        assertThat(reserve.amountOf("fiery")).isEqualTo(8_000);
    }

    @Test
    void removeSlot_givenTheLastVesselOverItsRoom_thenTakesItsRoomAndKeepsTheRest()
    {
        var reserve = new PocketReserve<String>();
        reserve.addSlot(FIRST, "fiery", 1_000, 1_500);

        var share = reserve.removeSlot(FIRST);

        assertThat(share.amount()).isEqualTo(1_000);
        // What no vessel can hold any more stays, refusing more until there is room again
        assertThat(reserve.amountOf("fiery")).isEqualTo(500);
        assertThat(reserve.room("fiery")).isZero();
    }

    @Test
    void shown_givenAQuarterFullAspect_thenEachVesselShowsAQuarterOfItsRoom()
    {
        var reserve = new PocketReserve<String>();
        reserve.addSlot(FIRST, "fiery", 32_000, 16_000);
        reserve.addSlot(SECOND, "fiery", 32_000, 16_000);
        reserve.extract("fiery", 16_000);

        assertThat(reserve.shown(FIRST)).isEqualTo(new PocketReserve.Share<>("fiery", 8_000L));
        assertThat(reserve.shown(SECOND)).isEqualTo(new PocketReserve.Share<>("fiery", 8_000L));
    }

    @Test
    void restore_givenNoRoom_thenKeepsItAllAndRefusesMore()
    {
        var reserve = new PocketReserve<String>();
        reserve.addSlot(FIRST, "fiery", 1_000, 1_000);

        reserve.restore("fiery", 500);

        assertThat(reserve.amountOf("fiery")).isEqualTo(1_500);
        assertThat(reserve.insert("fiery", 10)).isZero();
    }

    @Test
    void extract_givenMoreThanHeld_thenTakesWhatThereIs()
    {
        var reserve = new PocketReserve<String>();
        reserve.addSlot(FIRST, "fiery", 1_000, 300);

        assertThat(reserve.extract("fiery", 500)).isEqualTo(300);
        assertThat(reserve.amounts()).isEmpty();
    }

    @Test
    void toll_givenOrdinaryEssence_thenOneEnergyPerFive()
    {
        assertThat(HoardBalance.toll(false, 250)).isEqualTo(50);
        assertThat(HoardBalance.toll(false, 3)).isEqualTo(1);
        assertThat(HoardBalance.affordableEssence(false, 50)).isEqualTo(250);
    }

    @Test
    void toll_givenContravoid_thenTenTimesAsMuch()
    {
        assertThat(HoardBalance.toll(true, 250)).isEqualTo(500);
        assertThat(HoardBalance.affordableEssence(true, 500)).isEqualTo(250);
        assertThat(HoardBalance.toll(true, 5) / HoardBalance.toll(false, 5)).isEqualTo(10);
    }
}
