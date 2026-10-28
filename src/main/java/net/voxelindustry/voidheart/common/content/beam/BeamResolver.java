package net.voxelindustry.voidheart.common.content.beam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Walks the beam graph backward from a Shatter Forge.
 * <p>
 * Rules:
 * <ul>
 * <li>A track runs from an endpoint (forge or focus input face) along a direction d. Segment i has conduits at y-1 on
 * f = 3i+1..3i+3 and, at f = 3i+3, a sideways conduit row r-2..r+2 at y-1 with 3-high monolith columns at r±2.</li>
 * <li>The emitter of a track sits at f = 3k+1 (k = complete segments) and is either a PILLAR at y0 (item source),
 * a FOCUS at y0 whose output faces the endpoint, or a COLLECTOR at y+1 with no pillar at y0 under it.
 * A collector at y+1 above a pillar is an INVALID_COLLECTOR_PLACEMENT.</li>
 * <li>A track adds k unattuned energy to the beam of its emitter (pillar and collector start from nothing, a focus
 * starts from the sum of its input tracks).</li>
 * <li>A collector retunes the whole beam to its aspect: the emitter collector, or a collector centred on top of a
 * pair (f = 3i+3, r 0, y+3). The collector nearest the endpoint wins and owns the whole contribution.</li>
 * <li>A focus has inputs on its back, left and right faces; each input face may start a track.</li>
 * <li>An experience skull centred on top of a pair (f = 3i+3, r 0, y+3) is an experience source, it does not touch
 * the beam.</li>
 * <li>The forge needs its own monolith pair and exactly one track (front or back); exactly one item pillar in the
 * whole graph.</li>
 * </ul>
 */
public final class BeamResolver
{
    public static final int MAX_TRACK_LENGTH = 32;

    private BeamResolver()
    {
    }

    public static BeamResolution resolve(Level level, BlockPos forgePos)
    {
        return resolve(new LevelBeamWorldView(level), forgePos);
    }

    public static BeamResolution resolve(BeamWorldView view, BlockPos forgePos)
    {
        try
        {
            return new Run(view).resolve(forgePos);
        }
        catch (FormationException e)
        {
            return BeamResolution.failed(e.error);
        }
    }

    private static final class FormationException extends RuntimeException
    {
        private final FormationError error;

        private FormationException(FormationError error)
        {
            super(error.name(), null, false, false);
            this.error = error;
        }
    }

    private record TrackResult(BeamComposition beam, Map<BlockPos, Integer> contributions)
    {
    }

    private static final class Run
    {
        private final BeamWorldView view;
        private final Set<BlockPos> structure = new LinkedHashSet<>();
        private final List<BeamSegment> segments = new ArrayList<>();
        private final List<BlockPos> itemSources = new ArrayList<>();
        private final List<BlockPos> experienceSources = new ArrayList<>();
        private final Set<BlockPos> visitedFoci = new HashSet<>();
        private int totalPairs = 0;

        private Run(BeamWorldView view)
        {
            this.view = view;
        }

        private BeamResolution resolve(BlockPos forgePos)
        {
            var axis = view.forgeAxis(forgePos);
            var front = axis == Direction.Axis.Z ? Direction.NORTH : Direction.WEST;
            var back = front.getOpposite();

            checkPair(forgePos, front, 0, FormationError.NO_MONOLITH_AROUND);

            var conduitFront = is(forgePos.below().relative(front), BeamNodeKind.CONDUIT);
            var conduitBack = is(forgePos.below().relative(back), BeamNodeKind.CONDUIT);
            if (conduitFront && conduitBack)
                throw new FormationException(FormationError.CONDUIT_BOTH_DIRECTION);
            if (!conduitFront && !conduitBack)
                throw new FormationException(FormationError.NO_PILLAR_AT_END);

            var track = resolveTrack(forgePos, conduitFront ? front : back);

            if (itemSources.isEmpty())
                throw new FormationException(FormationError.NO_PILLAR_AT_END);
            if (itemSources.size() > 1)
                throw new FormationException(FormationError.MULTIPLE_ITEM_SOURCES);

            return new BeamResolution(track.beam(),
                    List.copyOf(segments),
                    Map.copyOf(track.contributions()),
                    Optional.of(itemSources.get(0)),
                    totalPairs,
                    List.copyOf(structure),
                    List.copyOf(experienceSources),
                    Optional.empty());
        }

        private BlockPos at(BlockPos origin, Direction d, int f, int r, int y)
        {
            return origin.relative(d, f).relative(d.getClockWise(), r).above(y);
        }

        private boolean is(BlockPos pos, BeamNodeKind kind)
        {
            return view.kindAt(pos) == kind;
        }

        private void require(BlockPos pos, BeamNodeKind kind, FormationError error)
        {
            if (!is(pos, kind))
                throw new FormationException(error);
            structure.add(pos);
        }

        /**
         * Checks the sideways conduit row and the pair of monolith columns at distance f from an origin.
         */
        private void checkPair(BlockPos origin, Direction d, int f, FormationError error)
        {
            for (var r = -2; r <= 2; r++)
                require(at(origin, d, f, r, -1), BeamNodeKind.CONDUIT, error);

            for (var y = 0; y <= 2; y++)
            {
                require(at(origin, d, f, -2, y), BeamNodeKind.MONOLITH, error);
                require(at(origin, d, f, 2, y), BeamNodeKind.MONOLITH, error);
            }
        }

        private TrackResult resolveTrack(BlockPos origin, Direction d)
        {
            var emitterF = -1;
            var emitterKind = BeamNodeKind.OTHER;
            for (var f = 1; f <= MAX_TRACK_LENGTH; f++)
            {
                var base = at(origin, d, f, 0, 0);
                var baseKind = view.kindAt(base);
                var collectorAbove = is(base.above(), BeamNodeKind.COLLECTOR);

                if (baseKind == BeamNodeKind.PILLAR && collectorAbove)
                    throw new FormationException(FormationError.INVALID_COLLECTOR_PLACEMENT);

                if (baseKind == BeamNodeKind.PILLAR || baseKind == BeamNodeKind.FOCUS)
                {
                    emitterF = f;
                    emitterKind = baseKind;
                    break;
                }
                if (collectorAbove)
                {
                    emitterF = f;
                    emitterKind = BeamNodeKind.COLLECTOR;
                    break;
                }
            }

            if (emitterF < 0 || (emitterF - 1) % 3 != 0)
                throw new FormationException(FormationError.NO_PILLAR_AT_END);

            var k = (emitterF - 1) / 3;
            var emitterBase = at(origin, d, emitterF, 0, 0);

            for (var i = 0; i < k; i++)
            {
                for (var f = 3 * i + 1; f <= 3 * i + 3; f++)
                    require(at(origin, d, f, 0, -1), BeamNodeKind.CONDUIT, FormationError.TOO_FEW_MONOLITH);
                checkPair(origin, d, 3 * i + 3, FormationError.TOO_FEW_MONOLITH);
            }
            totalPairs += k;

            var beam = BeamComposition.EMPTY;
            Map<BlockPos, Integer> contributions = new HashMap<>();
            BlockPos emitterPos;
            BlockPos retunePos = null;
            var retuneIsMidTrack = false;

            switch (emitterKind)
            {
                case PILLAR ->
                {
                    emitterPos = emitterBase;
                    itemSources.add(emitterBase);
                    structure.add(emitterBase);
                }
                case COLLECTOR ->
                {
                    emitterPos = emitterBase.above();
                    retunePos = emitterPos;
                    structure.add(emitterPos);
                }
                default ->
                {
                    emitterPos = emitterBase;
                    structure.add(emitterBase);
                    var focus = resolveFocus(emitterBase, d);
                    beam = focus.beam();
                    contributions = new HashMap<>(focus.contributions());
                }
            }

            for (var i = 0; i < k; i++)
            {
                var skull = at(origin, d, 3 * i + 3, 0, 3);
                if (is(skull, BeamNodeKind.EXPERIENCE_SKULL))
                    experienceSources.add(skull);
            }

            for (var i = 0; i < k; i++)
            {
                var candidate = at(origin, d, 3 * i + 3, 0, 3);
                if (is(candidate, BeamNodeKind.COLLECTOR))
                {
                    retunePos = candidate;
                    retuneIsMidTrack = true;
                    structure.add(candidate);
                    break;
                }
            }

            beam = beam.plusUnattuned(k);

            if (retunePos == null)
            {
                segments.add(new BeamSegment(emitterPos, origin, beam));
                return new TrackResult(beam, contributions);
            }

            var aspect = view.collectorAspect(retunePos);
            var preRetune = beam;
            beam = beam.retune(aspect);
            contributions = new HashMap<>();
            if (!isEmpty(aspect))
                contributions.put(retunePos, beam.total());

            if (retuneIsMidTrack)
            {
                segments.add(new BeamSegment(emitterPos, retunePos, preRetune));
                segments.add(new BeamSegment(retunePos, origin, beam));
            }
            else
                segments.add(new BeamSegment(emitterPos, origin, beam));

            return new TrackResult(beam, contributions);
        }

        private TrackResult resolveFocus(BlockPos focusPos, Direction d)
        {
            if (!visitedFoci.add(focusPos))
                throw new FormationException(FormationError.BEAM_LOOP);

            var facing = view.focusFacing(focusPos);
            if (facing != d.getOpposite())
                throw new FormationException(FormationError.INVALID_FOCUS_ORIENTATION);

            var beam = BeamComposition.EMPTY;
            Map<BlockPos, Integer> contributions = new HashMap<>();

            for (var face : BeamFocusBlock.getInputFaces(facing))
            {
                if (!is(focusPos.below().relative(face), BeamNodeKind.CONDUIT))
                    continue;

                var input = resolveTrack(focusPos, face);
                beam = beam.plus(input.beam());
                input.contributions().forEach((pos, amount) -> contributions.merge(pos, amount, Integer::sum));
            }
            return new TrackResult(beam, contributions);
        }

        private static boolean isEmpty(EssenceType aspect)
        {
            return aspect == null || aspect == EssenceRegistry.EMPTY;
        }
    }
}
