package net.voxelindustry.voidheart.common.content.beam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BeamResolverTest
{
    private static final EssenceType FIERY = new EssenceType(Identifier.fromNamespaceAndPath("voidheart", "fiery"), "x", null, null);
    private static final EssenceType STILLNESS = new EssenceType(Identifier.fromNamespaceAndPath("voidheart", "stillness"), "x", null, null);

    private static final BlockPos FORGE = new BlockPos(0, 64, 0);

    private FakeView view;

    @BeforeEach
    void setUp()
    {
        view = new FakeView();
    }

    @Test
    void resolve_givenItemPillarOnTwoPairTrack_thenUnattunedTwo()
    {
        view.forge(FORGE);
        view.track(FORGE, Direction.NORTH, 2);
        var pillar = view.emitter(FORGE, Direction.NORTH, 7, BeamNodeKind.PILLAR);

        var resolution = BeamResolver.resolve(view, FORGE);

        assertThat(resolution.error()).isEmpty();
        assertThat(resolution.result()).isEqualTo(BeamComposition.unattuned(2));
        assertThat(resolution.itemSource()).contains(pillar);
        assertThat(resolution.totalPairs()).isEqualTo(2);
        assertThat(resolution.segments()).hasSize(1);
        assertThat(resolution.collectorContributions()).isEmpty();
    }

    @Test
    void resolve_givenFocusWithPillarAndCollectorTracks_thenSumsAttunedBeams()
    {
        var focus = FORGE.north();
        view.forge(FORGE);
        view.track(FORGE, Direction.NORTH, 0);
        view.focus(focus, Direction.SOUTH);

        view.track(focus, Direction.NORTH, 2);
        view.emitter(focus, Direction.NORTH, 7, BeamNodeKind.PILLAR);
        var fieryCollector = view.collectorMid(focus, Direction.NORTH, 0, FIERY);

        view.track(focus, Direction.EAST, 2);
        var stillnessCollector = view.collectorEmitter(focus, Direction.EAST, 7, STILLNESS);

        var resolution = BeamResolver.resolve(view, FORGE);

        assertThat(resolution.error()).isEmpty();
        assertThat(resolution.result().amount(FIERY)).isGreaterThanOrEqualTo(2);
        assertThat(resolution.result().amount(STILLNESS)).isGreaterThanOrEqualTo(2);
        assertThat(resolution.result().unattuned()).isZero();
        assertThat(resolution.collectorContributions()).containsOnlyKeys(fieryCollector, stillnessCollector);
        assertThat(resolution.totalPairs()).isEqualTo(4);
    }

    @Test
    void resolve_givenFocusWithThreeInputs_thenMergesAllBeams()
    {
        var focus = FORGE.north();
        view.forge(FORGE);
        view.track(FORGE, Direction.NORTH, 0);
        view.focus(focus, Direction.SOUTH);

        view.track(focus, Direction.NORTH, 1);
        view.emitter(focus, Direction.NORTH, 4, BeamNodeKind.PILLAR);

        view.track(focus, Direction.EAST, 1);
        view.collectorEmitter(focus, Direction.EAST, 4, FIERY);

        view.track(focus, Direction.WEST, 1);
        view.collectorEmitter(focus, Direction.WEST, 4, STILLNESS);

        var resolution = BeamResolver.resolve(view, FORGE);

        assertThat(resolution.error()).isEmpty();
        assertThat(resolution.result().unattuned()).isEqualTo(1);
        assertThat(resolution.result().amount(FIERY)).isEqualTo(1);
        assertThat(resolution.result().amount(STILLNESS)).isEqualTo(1);
        assertThat(resolution.collectorContributions()).hasSize(2);
    }

    @Test
    void resolve_givenTwoMidTrackCollectors_thenNearestToEndpointWins()
    {
        view.forge(FORGE);
        view.track(FORGE, Direction.NORTH, 3);
        view.emitter(FORGE, Direction.NORTH, 10, BeamNodeKind.PILLAR);
        var nearest = view.collectorMid(FORGE, Direction.NORTH, 0, FIERY);
        view.collectorMid(FORGE, Direction.NORTH, 2, STILLNESS);

        var resolution = BeamResolver.resolve(view, FORGE);

        assertThat(resolution.error()).isEmpty();
        assertThat(resolution.result()).isEqualTo(BeamComposition.of(FIERY, 3));
        assertThat(resolution.collectorContributions()).containsOnlyKeys(nearest);
        assertThat(resolution.collectorContributions().get(nearest)).isEqualTo(3);
        assertThat(resolution.segments()).hasSize(2);
        assertThat(resolution.segments().get(1).composition()).isEqualTo(BeamComposition.of(FIERY, 3));
    }

    @Test
    void resolve_givenExperienceSkullsOnPairs_thenListsThemWithoutRetuning()
    {
        var focus = FORGE.north();
        view.forge(FORGE);
        view.track(FORGE, Direction.NORTH, 0);
        view.focus(focus, Direction.SOUTH);

        view.track(focus, Direction.NORTH, 2);
        view.emitter(focus, Direction.NORTH, 7, BeamNodeKind.PILLAR);
        var first = view.skullMid(focus, Direction.NORTH, 1);

        view.track(focus, Direction.EAST, 1);
        view.collectorEmitter(focus, Direction.EAST, 4, FIERY);
        var second = view.skullMid(focus, Direction.EAST, 0);

        var resolution = BeamResolver.resolve(view, FORGE);

        assertThat(resolution.error()).isEmpty();
        assertThat(resolution.experienceSources()).containsExactlyInAnyOrder(first, second);
        assertThat(resolution.result().unattuned()).isEqualTo(2);
        assertThat(resolution.result().amount(FIERY)).isEqualTo(1);
    }

    @Test
    void resolve_givenConduitsBothDirections_thenConduitBothDirection()
    {
        view.forge(FORGE);
        view.track(FORGE, Direction.NORTH, 1);
        view.emitter(FORGE, Direction.NORTH, 4, BeamNodeKind.PILLAR);
        view.kinds.put(FORGE.below().south(), BeamNodeKind.CONDUIT);

        assertError(FormationError.CONDUIT_BOTH_DIRECTION);
    }

    @Test
    void resolve_givenNoConduitAroundForge_thenNoMonolithAround()
    {
        view.kinds.put(FORGE, BeamNodeKind.FORGE);
        view.axes.put(FORGE, Direction.Axis.Z);

        assertError(FormationError.NO_MONOLITH_AROUND);
    }

    @Test
    void resolve_givenCollectorAbovePillar_thenInvalidCollectorPlacement()
    {
        view.forge(FORGE);
        view.track(FORGE, Direction.NORTH, 1);
        var pillar = view.emitter(FORGE, Direction.NORTH, 4, BeamNodeKind.PILLAR);
        view.kinds.put(pillar.above(), BeamNodeKind.COLLECTOR);
        view.aspects.put(pillar.above(), FIERY);

        assertError(FormationError.INVALID_COLLECTOR_PLACEMENT);
    }

    @Test
    void resolve_givenTwoItemPillars_thenMultipleItemSources()
    {
        var focus = FORGE.north();
        view.forge(FORGE);
        view.track(FORGE, Direction.NORTH, 0);
        view.focus(focus, Direction.SOUTH);

        view.track(focus, Direction.NORTH, 1);
        view.emitter(focus, Direction.NORTH, 4, BeamNodeKind.PILLAR);
        view.track(focus, Direction.EAST, 1);
        view.emitter(focus, Direction.EAST, 4, BeamNodeKind.PILLAR);

        assertError(FormationError.MULTIPLE_ITEM_SOURCES);
    }

    @Test
    void resolve_givenOnlyCollectors_thenNoPillarAtEnd()
    {
        view.forge(FORGE);
        view.track(FORGE, Direction.NORTH, 1);
        view.collectorEmitter(FORGE, Direction.NORTH, 4, FIERY);

        assertError(FormationError.NO_PILLAR_AT_END);
    }

    @Test
    void resolve_givenEmitterNotOnPairBoundary_thenNoPillarAtEnd()
    {
        view.forge(FORGE);
        view.track(FORGE, Direction.NORTH, 1);
        view.emitter(FORGE, Direction.NORTH, 5, BeamNodeKind.PILLAR);

        assertError(FormationError.NO_PILLAR_AT_END);
    }

    @Test
    void resolve_givenFocusFacingWrongWay_thenInvalidFocusOrientation()
    {
        var focus = FORGE.north();
        view.forge(FORGE);
        view.track(FORGE, Direction.NORTH, 0);
        view.focus(focus, Direction.NORTH);

        assertError(FormationError.INVALID_FOCUS_ORIENTATION);
    }

    @Test
    void resolve_givenFociRing_thenBeamLoopAndTerminates()
    {
        var a = FORGE.north();
        var g = a.east(4);
        var e1 = g.north();
        var d = e1.west(4);

        view.forge(FORGE);
        view.track(FORGE, Direction.NORTH, 0);
        view.focus(a, Direction.SOUTH);

        view.track(a, Direction.EAST, 1);
        view.focus(g, Direction.WEST);

        view.track(g, Direction.NORTH, 0);
        view.focus(e1, Direction.SOUTH);

        view.track(e1, Direction.WEST, 1);
        view.focus(d, Direction.EAST);

        view.track(d, Direction.SOUTH, 0);

        assertError(FormationError.BEAM_LOOP);
    }

    @Test
    void resolve_givenMissingMonolith_thenTooFewMonolith()
    {
        view.forge(FORGE);
        view.track(FORGE, Direction.NORTH, 2);
        view.emitter(FORGE, Direction.NORTH, 7, BeamNodeKind.PILLAR);
        view.kinds.remove(view.at(FORGE, Direction.NORTH, 6, 2, 1));

        assertError(FormationError.TOO_FEW_MONOLITH);
    }

    private void assertError(FormationError expected)
    {
        var resolution = BeamResolver.resolve(view, FORGE);

        assertThat(resolution.isValid()).isFalse();
        assertThat(resolution.error()).contains(expected);
    }

    private static class FakeView implements BeamWorldView
    {
        final Map<BlockPos, BeamNodeKind> kinds = new HashMap<>();
        final Map<BlockPos, EssenceType> aspects = new HashMap<>();
        final Map<BlockPos, Direction> facings = new HashMap<>();
        final Map<BlockPos, Direction.Axis> axes = new HashMap<>();

        BlockPos at(BlockPos origin, Direction d, int f, int r, int y)
        {
            return origin.relative(d, f).relative(d.getClockWise(), r).above(y);
        }

        void forge(BlockPos pos)
        {
            kinds.put(pos, BeamNodeKind.FORGE);
            axes.put(pos, Direction.Axis.Z);
            pair(pos, Direction.NORTH, 0);
            kinds.put(pos.below(), BeamNodeKind.CONDUIT);
        }

        void focus(BlockPos pos, Direction facing)
        {
            kinds.put(pos, BeamNodeKind.FOCUS);
            facings.put(pos, facing);
        }

        void track(BlockPos origin, Direction d, int segments)
        {
            kinds.put(at(origin, d, 1, 0, -1), BeamNodeKind.CONDUIT);
            for (var i = 0; i < segments; i++)
            {
                for (var f = 3 * i + 1; f <= 3 * i + 3; f++)
                    kinds.put(at(origin, d, f, 0, -1), BeamNodeKind.CONDUIT);
                pair(origin, d, 3 * i + 3);
            }
        }

        BlockPos emitter(BlockPos origin, Direction d, int f, BeamNodeKind kind)
        {
            var pos = at(origin, d, f, 0, 0);
            kinds.put(pos, kind);
            return pos;
        }

        BlockPos collectorEmitter(BlockPos origin, Direction d, int f, EssenceType aspect)
        {
            var pos = at(origin, d, f, 0, 1);
            kinds.put(pos, BeamNodeKind.COLLECTOR);
            aspects.put(pos, aspect);
            return pos;
        }

        BlockPos collectorMid(BlockPos origin, Direction d, int pairIndex, EssenceType aspect)
        {
            var pos = at(origin, d, 3 * pairIndex + 3, 0, 3);
            kinds.put(pos, BeamNodeKind.COLLECTOR);
            aspects.put(pos, aspect);
            return pos;
        }

        BlockPos skullMid(BlockPos origin, Direction d, int pairIndex)
        {
            var pos = at(origin, d, 3 * pairIndex + 3, 0, 3);
            kinds.put(pos, BeamNodeKind.EXPERIENCE_SKULL);
            return pos;
        }

        private void pair(BlockPos origin, Direction d, int f)
        {
            for (var r = -2; r <= 2; r++)
                kinds.put(at(origin, d, f, r, -1), BeamNodeKind.CONDUIT);
            for (var y = 0; y <= 2; y++)
            {
                kinds.put(at(origin, d, f, -2, y), BeamNodeKind.MONOLITH);
                kinds.put(at(origin, d, f, 2, y), BeamNodeKind.MONOLITH);
            }
        }

        @Override
        public BeamNodeKind kindAt(BlockPos pos)
        {
            return kinds.getOrDefault(pos, BeamNodeKind.OTHER);
        }

        @Override
        public EssenceType collectorAspect(BlockPos pos)
        {
            return aspects.get(pos);
        }

        @Override
        public Direction focusFacing(BlockPos pos)
        {
            return facings.get(pos);
        }

        @Override
        public Direction.Axis forgeAxis(BlockPos pos)
        {
            return axes.get(pos);
        }
    }
}
