package net.voxelindustry.voidheart.common.content.hoard;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HoardCountsTest
{
    private static HoardCounts<String> hoard(long capacity)
    {
        var hoard = new HoardCounts<String>();
        hoard.setCapacity(capacity);
        return hoard;
    }

    @Test
    void insert_givenRoom_thenTakesAll()
    {
        var hoard = hoard(100);

        assertThat(hoard.insert("iron", 40)).isEqualTo(40);
        assertThat(hoard.insert("gold", 10)).isEqualTo(10);

        assertThat(hoard.count("iron")).isEqualTo(40);
        assertThat(hoard.total()).isEqualTo(50);
        assertThat(hoard.room()).isEqualTo(50);
    }

    @Test
    void insert_givenLittleRoom_thenTakesWhatFitsWhateverTheKind()
    {
        var hoard = hoard(10);
        hoard.insert("iron", 7);

        assertThat(hoard.insert("gold", 5)).isEqualTo(3);
        assertThat(hoard.insert("gold", 5)).isZero();
        assertThat(hoard.total()).isEqualTo(10);
    }

    @Test
    void extract_givenMoreThanHeld_thenTakesWhatIsThereAndForgetsTheKind()
    {
        var hoard = hoard(100);
        hoard.insert("iron", 5);

        assertThat(hoard.extract("iron", 9)).isEqualTo(5);
        assertThat(hoard.view()).doesNotContainKey("iron");
        assertThat(hoard.extract("gold", 1)).isZero();
    }

    @Test
    void capacity_givenLoweredUnderWhatIsHeld_thenKeepsEverythingAndRefusesMore()
    {
        var hoard = hoard(100);
        hoard.insert("iron", 80);

        hoard.setCapacity(30);

        assertThat(hoard.isOverCapacity()).isTrue();
        assertThat(hoard.count("iron")).isEqualTo(80);
        assertThat(hoard.insert("iron", 1)).isZero();
        assertThat(hoard.extract("iron", 60)).isEqualTo(60);
        assertThat(hoard.isOverCapacity()).isFalse();
        assertThat(hoard.insert("iron", 20)).isEqualTo(10);
    }

    @Test
    void restore_givenAnAbortedExtractWhileFull_thenPutsItBackPastTheCapacity()
    {
        var hoard = hoard(10);
        hoard.insert("iron", 10);
        hoard.extract("iron", 4);
        hoard.insert("gold", 4);

        hoard.restore("iron", 4);

        assertThat(hoard.count("iron")).isEqualTo(10);
        assertThat(hoard.total()).isEqualTo(14);
    }

    @Test
    void load_givenSavedCountsOverCapacity_thenKeepsThemAll()
    {
        var hoard = new HoardCounts<String>();

        hoard.load(Map.of("iron", 50L, "gold", 0L), 20);

        assertThat(hoard.count("iron")).isEqualTo(50);
        assertThat(hoard.view()).containsOnlyKeys("iron");
        assertThat(hoard.isOverCapacity()).isTrue();
    }
}
