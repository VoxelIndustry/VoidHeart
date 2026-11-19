package net.voxelindustry.voidheart.common.content.blight;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EclipseLinkTest
{
    @Test
    void struck_holdsEachToTheOneBeforeAndLetsTheOldestGo()
    {
        var link = EclipseLink.NONE.struck(1, 0).struck(2, 5).struck(3, 10);

        assertThat(link.members()).containsExactly(1, 2, 3);
        assertThat(link.struck(4, 12).members()).containsExactly(2, 3, 4);
        assertThat(link.until()).isEqualTo(10 + BlightBalance.LINK_TICKS);
    }

    @Test
    void struck_onAMemberOnlyKeepsItTogether()
    {
        var link = EclipseLink.NONE.struck(1, 0).struck(2, 5).struck(1, 40);

        assertThat(link.members()).containsExactly(1, 2);
        assertThat(link.until()).isEqualTo(40 + BlightBalance.LINK_TICKS);
    }

    @Test
    void holds_needsTwoAndComesApartInTime()
    {
        var one = EclipseLink.NONE.struck(1, 0);
        var two = one.struck(2, 0);

        assertThat(one.holds(1)).isFalse();
        assertThat(two.holds(BlightBalance.LINK_TICKS - 1)).isTrue();
        assertThat(two.holds(BlightBalance.LINK_TICKS)).isFalse();
        assertThat(two.struck(3, BlightBalance.LINK_TICKS + 5).members()).containsExactly(3);
    }

    @Test
    void othersThan_sharesOutToTheRest()
    {
        var link = EclipseLink.NONE.struck(1, 0).struck(2, 0).struck(3, 0);

        assertThat(link.othersThan(2)).containsExactly(1, 3);
        assertThat(EclipseLink.shareOf(8)).isEqualTo(8 * BlightBalance.LINK_SHARE);
        assertThat(link.without(2).members()).containsExactly(1, 3);
    }

    @Test
    void shared_keepsOnlyTheLastFew()
    {
        var link = EclipseLink.NONE.struck(1, 0).struck(2, 0);
        for (int i = 0; i < 10; i++)
            link = link.shared(1, i);

        assertThat(link.shares()).hasSizeLessThanOrEqualTo(6);
        assertThat(link.shares().getLast().at()).isEqualTo(9);
    }

    @Test
    void isStretched_whenAnyTwoStandTooFarApart()
    {
        var near = List.of(Vec3.ZERO, new Vec3(BlightBalance.LINK_BREAK - 1, 0, 0), new Vec3(0, 0, 5));
        var far = List.of(Vec3.ZERO, new Vec3(5, 0, 0), new Vec3(BlightBalance.LINK_BREAK + 6, 0, 0));

        assertThat(EclipseLink.isStretched(near)).isFalse();
        assertThat(EclipseLink.isStretched(far)).isTrue();
    }
}
