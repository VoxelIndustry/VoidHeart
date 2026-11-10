package net.voxelindustry.voidheart.client.screen;

import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.screen.ResearchMapPlan.Link;
import net.voxelindustry.voidheart.common.content.research.Research;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What can be counted of a laid out map: branches crossing, how long branches are and how far each research is from its nearest neighbour.
 */
final class ResearchMapMeasure
{
    /** How many pieces at the end of two branches meeting at a research are let to touch there. */
    private static final int SHARED = 3;

    private ResearchMapMeasure()
    {
    }

    /** Lowest, mean and highest of a set of lengths. */
    record Spread(double least, double mean, double most)
    {
        static Spread of(List<Double> values)
        {
            return new Spread(values.stream().mapToDouble(Double::doubleValue).min().orElse(0), values.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                    values.stream().mapToDouble(Double::doubleValue).max().orElse(0));
        }

        @Override
        public String toString()
        {
            return String.format("%.0f / %.0f / %.0f", least, mean, most);
        }
    }

    /** Every pair of branches that cross as drawn, named by the research each leads to. */
    static List<String> crossings(ResearchMapPlan plan, Map<Identifier, Research> tree)
    {
        var found = new ArrayList<String>();
        var links = plan.links().stream().filter(link -> isBranch(plan, link)).toList();
        for (int a = 0; a < links.size(); a++)
        {
            for (int b = a + 1; b < links.size(); b++)
            {
                if (cross(links.get(a), links.get(b)))
                    found.add(links.get(a).child().getPath() + " x " + links.get(b).child().getPath());
            }
        }
        return found;
    }

    /** Whether a link is the branch its research hangs from, rather than a further requirement drawn over the tree. */
    static boolean isBranch(ResearchMapPlan plan, Link link)
    {
        return link.parent() == null || link.parent().equals(plan.parentOf(link.child()));
    }

    private static boolean cross(Link first, Link second)
    {
        int pieces = first.xs().length - 1;
        boolean sameStart = first.parent() != null && first.parent().equals(second.parent());
        boolean firstIntoSecond = first.child().equals(second.parent());
        boolean secondIntoFirst = second.child().equals(first.parent());
        for (int i = 0; i < pieces; i++)
        {
            for (int j = 0; j < pieces; j++)
            {
                if (sameStart && i < SHARED && j < SHARED || firstIntoSecond && i >= pieces - SHARED && j < SHARED || secondIntoFirst && j >= pieces - SHARED && i < SHARED)
                    continue;
                if (ResearchMapRoutes.meet(first.xs()[i], first.ys()[i], first.xs()[i + 1], first.ys()[i + 1], second.xs()[j], second.ys()[j], second.xs()[j + 1],
                        second.ys()[j + 1]))
                    return true;
            }
        }
        return false;
    }

    /** How long the branches between two researches are, middle to middle. */
    static Spread branches(ResearchMapPlan plan)
    {
        var lengths = new ArrayList<Double>();
        plan.nodes().forEach((id, node) ->
        {
            var parent = plan.parentOf(id);
            if (parent != null)
                lengths.add(Math.hypot(node.x() - plan.nodes().get(parent).x(), node.y() - plan.nodes().get(parent).y()));
        });
        return Spread.of(lengths);
    }

    /** How far each research is from the one nearest to it. */
    static Spread neighbours(ResearchMapPlan plan)
    {
        var nearest = new ArrayList<Double>();
        plan.nodes().forEach((id, node) -> nearest.add(plan.nodes().entrySet().stream().filter(other -> !other.getKey().equals(id))
                .mapToDouble(other -> Math.hypot(node.x() - other.getValue().x(), node.y() - other.getValue().y())).min().orElse(0)));
        return Spread.of(nearest);
    }

    /** The sharpest turn between two pieces of one branch, in degrees. */
    static double sharpestTurn(Link link)
    {
        double sharpest = 0;
        for (int i = 1; i < link.xs().length - 1; i++)
        {
            double inX = link.xs()[i] - link.xs()[i - 1];
            double inY = link.ys()[i] - link.ys()[i - 1];
            double outX = link.xs()[i + 1] - link.xs()[i];
            double outY = link.ys()[i + 1] - link.ys()[i];
            if (Math.hypot(inX, inY) < 1e-3 || Math.hypot(outX, outY) < 1e-3)
                continue;
            sharpest = Math.max(sharpest, Math.abs(Math.toDegrees(Math.atan2(inX * outY - inY * outX, inX * outX + inY * outY))));
        }
        return sharpest;
    }

    static String report(ResearchMapPlan plan, Map<Identifier, Research> tree)
    {
        var bounds = plan.bounds();
        return String.format("%d researches, %dx%d, %d crossings, branches %s, nearest neighbour %s", tree.size(), bounds.width(), bounds.height(),
                crossings(plan, tree).size(), branches(plan), neighbours(plan));
    }
}
