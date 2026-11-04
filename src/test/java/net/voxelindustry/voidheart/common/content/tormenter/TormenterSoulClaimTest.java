package net.voxelindustry.voidheart.common.content.tormenter;

import net.voxelindustry.voidheart.common.content.tormenter.TormenterSoulClaim.Held;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TormenterSoulClaimTest
{
    private static final Held<String> NOTHING = null;

    private static List<Held<String>> hotbar(Held<String>... slots)
    {
        return Arrays.asList(slots);
    }

    @Test
    void noTormenterTakesNothing()
    {
        assertThat(TormenterSoulClaim.claimingSlot(hotbar(NOTHING, NOTHING), "zombie")).isEqualTo(TormenterSoulClaim.NONE);
    }

    @Test
    void firstEmptyTakesTheSoulWhenSeveralCould()
    {
        assertThat(TormenterSoulClaim.claimingSlot(hotbar(NOTHING, Held.empty(), Held.empty()), "zombie")).isEqualTo(1);
    }

    @Test
    void oneAlreadyFeedingOnThisMobGoesBeforeAnEarlierEmptyOne()
    {
        assertThat(TormenterSoulClaim.claimingSlot(hotbar(Held.empty(), new Held<>("zombie", 3)), "zombie")).isEqualTo(1);
    }

    @Test
    void oneFeedingOnAnotherMobRefuses()
    {
        assertThat(TormenterSoulClaim.claimingSlot(hotbar(new Held<>("skeleton", 3)), "zombie")).isEqualTo(TormenterSoulClaim.NONE);
        assertThat(TormenterSoulClaim.claimingSlot(hotbar(new Held<>("skeleton", 3), Held.empty()), "zombie")).isEqualTo(1);
    }

    @Test
    void aSatedOneTakesNoMore()
    {
        var sated = new Held<>("zombie", TormenterSouls.REQUIRED);

        assertThat(TormenterSoulClaim.claimingSlot(hotbar(sated), "zombie")).isEqualTo(TormenterSoulClaim.NONE);
        assertThat(TormenterSoulClaim.claimingSlot(hotbar(sated, new Held<>("zombie", 15)), "zombie")).isEqualTo(1);
    }
}
