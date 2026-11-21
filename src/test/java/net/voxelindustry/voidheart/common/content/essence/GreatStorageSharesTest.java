package net.voxelindustry.voidheart.common.content.essence;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GreatStorageSharesTest
{
    private static final String NONE = "none";

    @Test
    void blocksHoldingNothingHaveNoSay()
    {
        var aspects = List.of("fiery", NONE, "stillness", "fiery");
        assertThat(GreatStorageShares.commonAspect(aspects, new int[] { 10, 0, 0, 5 }, NONE)).isEqualTo("fiery");
    }

    @Test
    void twoAspectsRefuseToJoin()
    {
        var aspects = List.of("fiery", "stillness");
        assertThat(GreatStorageShares.commonAspect(aspects, new int[] { 10, 3 }, NONE)).isNull();
    }

    @Test
    void emptyBlocksJoinUnattuned()
    {
        var aspects = List.of("fiery", NONE);
        assertThat(GreatStorageShares.commonAspect(aspects, new int[] { 0, 0 }, NONE)).isEqualTo(NONE);
    }

    @Test
    void spreadKeepsEveryDrop()
    {
        var shares = GreatStorageShares.spread(19, 8);
        assertThat(Arrays.stream(shares).sum()).isEqualTo(19);
        assertThat(shares).containsExactly(3, 3, 3, 2, 2, 2, 2, 2);
    }

    @Test
    void fillRaisesTheEmptiestFirst()
    {
        var added = GreatStorageShares.fill(new int[] { 10, 0, 4, 10 }, 100, 12);
        assertThat(added).containsExactly(0, 8, 4, 0);
    }

    @Test
    void fillKeepsSharesLevel()
    {
        var added = GreatStorageShares.fill(new int[8], 100, 21);
        assertThat(Arrays.stream(added).sum()).isEqualTo(21);
        assertThat(Arrays.stream(added).max().getAsInt() - Arrays.stream(added).min().getAsInt()).isLessThanOrEqualTo(1);
    }

    @Test
    void fillStopsAtCapacity()
    {
        assertThat(GreatStorageShares.fill(new int[] { 95, 100, 90 }, 100, 50)).containsExactly(5, 0, 10);
    }

    @Test
    void drainLowersTheFullestFirst()
    {
        var taken = GreatStorageShares.drain(new int[] { 10, 2, 7 }, 6);
        assertThat(taken).containsExactly(5, 0, 1);
    }

    @Test
    void drainStopsWhenEmpty()
    {
        assertThat(GreatStorageShares.drain(new int[] { 3, 1 }, 50)).containsExactly(3, 1);
    }

    @Test
    void fillAndDrainHandleAFullCube()
    {
        var capacity = GreatRavenousStorageTile.SHARE_CAPACITY;
        var added = GreatStorageShares.fill(new int[8], capacity, Integer.MAX_VALUE);
        assertThat(added).containsOnly(capacity);
        assertThat(GreatStorageShares.drain(added, GreatRavenousStorageTile.CAPACITY)).containsOnly(capacity);
    }

    @Test
    void holds128StoragesWorth()
    {
        assertThat(GreatRavenousStorageTile.CAPACITY).isEqualTo(128 * RavenousStorageTile.CAPACITY);
        assertThat(GreatRavenousStorageTile.SHARE_CAPACITY * GreatStorageLayout.BLOCKS).isEqualTo(GreatRavenousStorageTile.CAPACITY);
    }
}
