package net.voxelindustry.voidheart.common.energy.storage;

import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.Size;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SocketSetTest
{
    @Test
    void largeCoreMovesIntoSmallFrameWhenItsBundlesFit()
    {
        var sockets = new SocketSet();
        sockets.set(2, SocketBundle.LARGE_RUBRUM);
        sockets.set(17, SocketBundle.SMALL_AZURIUM);
        sockets.set(29, SocketBundle.MEDIUM_RUBRUM);

        assertThat(sockets.fits(Size.SMALL)).isTrue();
        sockets.arrangeFor(Size.SMALL);

        assertThat(sockets.get(0)).isEqualTo(SocketBundle.SMALL_AZURIUM);
        assertThat(sockets.get(1)).isEqualTo(SocketBundle.MEDIUM_RUBRUM);
        assertThat(sockets.get(2)).isEqualTo(SocketBundle.LARGE_RUBRUM);
        assertThat(sockets.count()).isEqualTo(3);
        assertThat(sockets.capacity()).isEqualTo(5_000_000);
    }

    @Test
    void tooManyBundlesStayWhereTheyAre()
    {
        var sockets = new SocketSet();
        for (var index = 0; index < 7; index++)
            sockets.set(index, SocketBundle.SMALL_RUBRUM);

        assertThat(sockets.fits(Size.SMALL)).isFalse();
        sockets.arrangeFor(Size.SMALL);
        assertThat(sockets.get(6)).isEqualTo(SocketBundle.SMALL_RUBRUM);
    }

    @Test
    void nearestPicksTheClosestAcceptedSocket()
    {
        // East face center of a small construct is socket 5, at (1, 0, 0)
        assertThat(SocketSet.nearest(Size.SMALL, 1.4, 0.1, 0, index -> true)).isEqualTo(5);
        assertThat(SocketSet.nearest(Size.SMALL, 1.4, 0.1, 0, index -> index != 5)).isNotEqualTo(5);
        assertThat(SocketSet.nearest(Size.SMALL, 0, 0, 0, index -> false)).isEqualTo(-1);
    }

    @Test
    void entriesRoundTrip()
    {
        var sockets = new SocketSet();
        sockets.set(4, SocketBundle.MEDIUM_AZURIUM);
        sockets.set(12, SocketBundle.LARGE_RUBRUM);

        var copy = new SocketSet();
        copy.load(sockets.entries());
        assertThat(copy.entries()).containsExactlyElementsOf(sockets.entries());
        assertThat(new ConstructContents(5, sockets.entries()).capacity()).isEqualTo(4_000_000);
        assertThat(new ConstructContents(5, sockets.entries()).rate()).isEqualTo(160);
    }
}
