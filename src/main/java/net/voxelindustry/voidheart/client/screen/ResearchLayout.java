package net.voxelindustry.voidheart.client.screen;

import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.research.Research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Places research on rings around the eye, one ring per tier. Each branch owns a sector sized by what grows from it,
 * then research sharing a ring is pushed apart until none overlap.
 */
public final class ResearchLayout
{
    public record Polar(double fraction, double angle)
    {
    }

    /**
     * An ellipse on screen, where a fraction of 1 is the outermost ring.
     */
    public record Projection(double centerX, double centerY, double radiusX, double radiusY)
    {
        public double x(double fraction, double angle)
        {
            return centerX + Math.cos(angle) * radiusX * fraction;
        }

        public double y(double fraction, double angle)
        {
            return centerY + Math.sin(angle) * radiusY * fraction;
        }
    }

    public static final double EYE_FRACTION = 0.3;

    private static final double INNER_RING    = 0.5;
    private static final double OUTER_GAP     = 0.34;
    private static final int    RELAX_ROUNDS  = 80;
    private static final double CHAIN_NUDGE   = 1E-3;

    private ResearchLayout()
    {
    }

    public static double fractionOf(int tier, int tiers)
    {
        return tiers <= 1 ? 1 : INNER_RING + (1 - INNER_RING) * (tier - 1) / (tiers - 1);
    }

    public static Identifier parentOf(Research research, Map<Identifier, Research> tree)
    {
        return research.requires().stream().filter(tree::containsKey).findFirst().orElse(null);
    }

    public static Map<Identifier, Polar> compute(Map<Identifier, Research> tree)
    {
        var children = new HashMap<Identifier, List<Identifier>>();
        var roots = new ArrayList<Identifier>();
        var order = Comparator.<Identifier>comparingInt(id -> tree.get(id).row()).thenComparing(Identifier::toString);

        tree.keySet().stream().sorted(order).forEach(id ->
        {
            var parent = parentOf(tree.get(id), tree);
            if (parent == null)
                roots.add(id);
            else
                children.computeIfAbsent(parent, key -> new ArrayList<>()).add(id);
        });

        var weights = new HashMap<Identifier, Integer>();
        roots.forEach(root -> weigh(root, children, weights));

        var angles = new HashMap<Identifier, Double>();
        double total = Math.max(1, roots.stream().mapToInt(weights::get).sum());
        double start = roots.isEmpty() ? 0 : -Math.PI / 2 - Math.PI * weights.get(roots.getFirst()) / total;
        for (var root : roots)
        {
            double span = Math.PI * 2 * weights.get(root) / total;
            assign(root, start, span, children, weights, angles);
            start += span;
        }

        tree.forEach((id, research) -> leaveTheWayClear(id, research, tree, angles));

        int tiers = tree.values().stream().mapToInt(Research::tier).max().orElse(1);
        var polar = new HashMap<Identifier, Polar>();
        for (int tier = 1; tier <= tiers; tier++)
        {
            int ring = tier;
            var onRing = tree.keySet().stream().filter(id -> tree.get(id).tier() == ring).sorted(Comparator.<Identifier>comparingDouble(angles::get).thenComparing(order)).toList();
            double fraction = fractionOf(tier, tiers);
            double[] spread = relax(onRing.stream().mapToDouble(angles::get).toArray(), OUTER_GAP / fraction);
            for (int i = 0; i < spread.length; i++)
                polar.put(onRing.get(i), new Polar(fraction, spread[i]));
        }
        return polar;
    }

    /**
     * Research growing on another of its own ring sits on the far side of it, so the branch arriving at the parent does not pass through it.
     */
    private static void leaveTheWayClear(Identifier id, Research research, Map<Identifier, Research> tree, Map<Identifier, Double> angles)
    {
        var parent = parentOf(research, tree);
        if (parent == null || tree.get(parent).tier() != research.tier())
            return;

        var grandparent = parentOf(tree.get(parent), tree);
        if (grandparent != null)
            angles.put(id, angles.get(parent) + Math.signum(angles.get(parent) - angles.get(grandparent)) * CHAIN_NUDGE);
    }

    private static int weigh(Identifier id, Map<Identifier, List<Identifier>> children, Map<Identifier, Integer> weights)
    {
        int weight = Math.max(1, children.getOrDefault(id, List.of()).stream().mapToInt(child -> weigh(child, children, weights)).sum());
        weights.put(id, weight);
        return weight;
    }

    private static void assign(Identifier id, double start, double span, Map<Identifier, List<Identifier>> children, Map<Identifier, Integer> weights, Map<Identifier, Double> angles)
    {
        angles.put(id, start + span / 2);
        double at = start;
        for (var child : children.getOrDefault(id, List.of()))
        {
            double childSpan = span * weights.get(child) / weights.get(id);
            assign(child, at, childSpan, children, weights, angles);
            at += childSpan;
        }
    }

    /**
     * Keeps the order of the sorted angles and moves neighbours away from each other until each pair is a gap apart, wrapping around the ring.
     */
    private static double[] relax(double[] angles, double gap)
    {
        int count = angles.length;
        double reachable = Math.min(gap, Math.PI * 2 / Math.max(1, count));
        var result = angles.clone();
        for (int round = 0; round < RELAX_ROUNDS; round++)
        {
            for (int i = 0; i < count && count > 1; i++)
            {
                int next = (i + 1) % count;
                double distance = result[next] - result[i] + (next == 0 ? Math.PI * 2 : 0);
                if (distance >= reachable)
                    continue;
                double push = (reachable - distance) / 2;
                result[i] -= push;
                result[next] += push;
            }
        }
        return result;
    }
}
