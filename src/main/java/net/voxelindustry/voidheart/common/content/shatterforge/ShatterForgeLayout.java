package net.voxelindustry.voidheart.common.content.shatterforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.content.beam.BeamNodeKind;
import net.voxelindustry.voidheart.common.content.beam.BeamResolver;
import net.voxelindustry.voidheart.common.content.beam.BeamWorldView;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of a Shatter Forge with a single track, shared by the structure preview and its size inference.
 * Must stay consistent with {@link BeamResolver}.
 */
public final class ShatterForgeLayout
{
    public static final int MAX_TRACK_PAIRS = (BeamResolver.MAX_TRACK_LENGTH - 1) / 3;

    public enum Part
    {
        CONDUIT(BeamNodeKind.CONDUIT),
        MONOLITH_BOTTOM(BeamNodeKind.MONOLITH),
        MONOLITH_MIDDLE(BeamNodeKind.MONOLITH),
        MONOLITH_TOP(BeamNodeKind.MONOLITH),
        PILLAR(BeamNodeKind.PILLAR);

        private final BeamNodeKind kind;

        Part(BeamNodeKind kind)
        {
            this.kind = kind;
        }

        public BeamNodeKind kind()
        {
            return kind;
        }
    }

    public record Expected(BlockPos pos, Part part)
    {
    }

    private ShatterForgeLayout()
    {
    }

    public static Direction front(Direction.Axis forgeAxis)
    {
        return forgeAxis == Direction.Axis.Z ? Direction.NORTH : Direction.WEST;
    }

    private static BlockPos at(BlockPos origin, Direction d, int f, int r, int y)
    {
        return origin.relative(d, f).relative(d.getClockWise(), r).above(y);
    }

    private static void addPair(List<Expected> out, BlockPos origin, Direction d, int f)
    {
        for (var r = -2; r <= 2; r++)
            out.add(new Expected(at(origin, d, f, r, -1), Part.CONDUIT));

        for (var r = -2; r <= 2; r += 4)
        {
            out.add(new Expected(at(origin, d, f, r, 0), Part.MONOLITH_BOTTOM));
            out.add(new Expected(at(origin, d, f, r, 1), Part.MONOLITH_MIDDLE));
            out.add(new Expected(at(origin, d, f, r, 2), Part.MONOLITH_TOP));
        }
    }

    /**
     * Every block of a forge whose track runs along d with the given number of monolith pairs, the forge pair included,
     * ending with the source pillar.
     */
    public static List<Expected> expected(BlockPos forge, Direction d, int trackPairs)
    {
        var out = new ArrayList<Expected>();
        addPair(out, forge, d, 0);

        for (var i = 0; i < trackPairs; i++)
        {
            out.add(new Expected(at(forge, d, 3 * i + 1, 0, -1), Part.CONDUIT));
            out.add(new Expected(at(forge, d, 3 * i + 2, 0, -1), Part.CONDUIT));
            addPair(out, forge, d, 3 * i + 3);
        }

        out.add(new Expected(endSlot(forge, d, trackPairs), Part.PILLAR));
        return out;
    }

    public static BlockPos endSlot(BlockPos forge, Direction d, int trackPairs)
    {
        return at(forge, d, 3 * trackPairs + 1, 0, 0);
    }

    private static int segmentMatches(BeamWorldView view, BlockPos forge, Direction d, int segment)
    {
        var matches = 0;
        var f = 3 * segment + 3;

        if (view.kindAt(at(forge, d, f - 2, 0, -1)) == BeamNodeKind.CONDUIT)
            matches++;
        if (view.kindAt(at(forge, d, f - 1, 0, -1)) == BeamNodeKind.CONDUIT)
            matches++;
        for (var r = -2; r <= 2; r++)
        {
            if (view.kindAt(at(forge, d, f, r, -1)) == BeamNodeKind.CONDUIT)
                matches++;
        }
        for (var r = -2; r <= 2; r += 4)
        {
            for (var y = 0; y <= 2; y++)
            {
                if (view.kindAt(at(forge, d, f, r, y)) == BeamNodeKind.MONOLITH)
                    matches++;
            }
        }
        return matches;
    }

    private static boolean isSegmentComplete(BeamWorldView view, BlockPos forge, Direction d, int segment)
    {
        return segmentMatches(view, forge, d, segment) == 13;
    }

    /**
     * Picks the side of the forge that already holds the most track blocks, the front when nothing is built.
     */
    public static Direction inferDirection(BeamWorldView view, BlockPos forge, Direction.Axis forgeAxis)
    {
        var front = front(forgeAxis);
        var back = front.getOpposite();

        var frontScore = directionScore(view, forge, front);
        var backScore = directionScore(view, forge, back);
        return backScore > frontScore ? back : front;
    }

    private static int directionScore(BeamWorldView view, BlockPos forge, Direction d)
    {
        var score = 0;
        for (var segment = 0; segment < MAX_TRACK_PAIRS; segment++)
        {
            var matches = segmentMatches(view, forge, d, segment);
            if (matches == 0)
                break;
            score += matches;
        }
        if (isEmitter(view, endSlot(forge, d, completeSegments(view, forge, d))))
            score += 1;
        return score;
    }

    public static int completeSegments(BeamWorldView view, BlockPos forge, Direction d)
    {
        var segments = 0;
        while (segments < MAX_TRACK_PAIRS && isSegmentComplete(view, forge, d, segments))
            segments++;
        return segments;
    }

    /**
     * Whether something that can emit a beam already ends the track at this slot.
     */
    public static boolean isEmitter(BeamWorldView view, BlockPos slot)
    {
        var kind = view.kindAt(slot);
        return kind == BeamNodeKind.PILLAR || kind == BeamNodeKind.FOCUS || view.kindAt(slot.above()) == BeamNodeKind.COLLECTOR;
    }
}
