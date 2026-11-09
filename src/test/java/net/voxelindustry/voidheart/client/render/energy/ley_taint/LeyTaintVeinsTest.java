package net.voxelindustry.voidheart.client.render.energy.ley_taint;

import net.voxelindustry.voidheart.common.ley.TaintStage;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaintPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeyTaintVeinsTest
{
    static final long NODE = 0x1234_5678L;
    static final int X = 100, Z = -40;
    /**
     * Upstream lines come in from the west and north, downstream lines leave to the east and south.
     */
    static final List<LeyTaintPayload.Spoke> SPOKES = List.of(
            new LeyTaintPayload.Spoke(X - 300, Z, false),
            new LeyTaintPayload.Spoke(X, Z - 250, false),
            new LeyTaintPayload.Spoke(X + 280, Z + 10, true),
            new LeyTaintPayload.Spoke(X - 20, Z + 260, true));

    @Test
    void theSameNodeAlwaysGrowsTheSameVeins()
    {
        var first = LeyTaintVeins.generate(NODE, X, Z, SPOKES);
        var again = LeyTaintVeins.generate(NODE, X, Z, List.of(SPOKES.get(2), SPOKES.get(0), SPOKES.get(3), SPOKES.get(1)));

        assertThat(again).isEqualTo(first);
        assertThat(LeyTaintVeins.generate(NODE + 1, X, Z, SPOKES)).isNotEqualTo(first);
    }

    @Test
    void trunksReachAsFarAsABreachedNodeShows()
    {
        var pieces = LeyTaintVeins.generate(NODE, X, Z, SPOKES);
        double farthest = pieces.stream().filter(piece -> piece.level() == 0).mapToDouble(LeyTaintVeins.Piece::distance).max().orElse(0);

        assertThat(farthest).isGreaterThanOrEqualTo(LeyTaintLook.BREACHED_REACH_END);
    }

    @Test
    void trunksWanderOnlyABlockOrTwoFromTheirLine()
    {
        var pieces = LeyTaintVeins.generate(NODE, X, Z, List.of(new LeyTaintPayload.Spoke(X + 300, Z, false)));

        for (var piece : pieces)
        {
            if (piece.level() == 0)
                assertThat(Math.abs(piece.z1() - (Z + 0.5))).as("trunk offset at %.1f", piece.x1() - X).isLessThan(3);
        }
    }

    @Test
    void trunksFollowTheirLinesDirection()
    {
        var pieces = LeyTaintVeins.generate(NODE, X, Z, List.of(new LeyTaintPayload.Spoke(X, Z + 300, false)));

        var farTrunk = pieces.stream().filter(piece -> piece.level() == 0 && piece.distance() > 20).toList();
        assertThat(farTrunk).isNotEmpty().allSatisfy(piece -> assertThat(piece.z1()).isGreaterThan(Z + 15));
    }

    @Test
    void downstreamLinesGetThinnerSparserVeins()
    {
        var upstream = LeyTaintVeins.generate(NODE, X, Z, List.of(new LeyTaintPayload.Spoke(X + 300, Z, false)));
        var downstream = LeyTaintVeins.generate(NODE, X, Z, List.of(new LeyTaintPayload.Spoke(X + 300, Z, true)));

        assertThat(trunkPieces(downstream)).isLessThan(trunkPieces(upstream));
        float upWidth = (float) upstream.stream().filter(piece -> piece.level() == 0).mapToDouble(LeyTaintVeins.Piece::width).max().orElse(0);
        float downWidth = (float) downstream.stream().filter(piece -> piece.level() == 0).mapToDouble(LeyTaintVeins.Piece::width).max().orElse(0);
        assertThat(downWidth).isLessThan(upWidth);
    }

    @Test
    void aCleanNodeShowsNothingAndEachStageReachesFarther()
    {
        assertThat(LeyTaintLook.shape(10).isNone()).isTrue();
        var tainted = LeyTaintLook.shape(30);
        var festering = LeyTaintLook.shape(60);
        var breached = LeyTaintLook.shape(90);

        assertThat(tainted.reach()).isBetween(3F, 4.5F);
        assertThat(festering.reach()).isBetween(10F, 16F);
        assertThat(breached.reach()).isBetween(24F, 32F);
        assertThat(tainted.width()).isLessThan(festering.width());
        assertThat(festering.width()).isLessThan(breached.width());
        assertThat(tainted.branches()).isZero();
        assertThat(tainted.dark()).isZero();
        assertThat(festering.dark()).isPositive();
        assertThat(festering.breach()).isZero();
        assertThat(breached.breach()).isEqualTo(1);
    }

    @Test
    void oculusTonesDarkenByStage()
    {
        assertThat(LeyTaintLook.oculusStrength(TaintStage.CLEAN)).isZero();
        assertThat(LeyTaintLook.oculusTone(TaintStage.TAINTED)).isEqualTo(LeyTaintLook.LEY_MAUVE);
        assertThat(LeyTaintLook.oculusTone(TaintStage.BREACHED)).isEqualTo(LeyTaintLook.BRUISE);
        assertThat(LeyTaintLook.oculusStrength(TaintStage.BREACHED)).isGreaterThan(LeyTaintLook.oculusStrength(TaintStage.TAINTED));
    }

    private static long trunkPieces(List<LeyTaintVeins.Piece> pieces)
    {
        return pieces.stream().filter(piece -> piece.level() == 0).count();
    }
}
