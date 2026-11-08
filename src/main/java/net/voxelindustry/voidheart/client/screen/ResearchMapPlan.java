package net.voxelindustry.voidheart.client.screen;

import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.research.Constellation;
import net.voxelindustry.voidheart.common.content.research.Research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Where every research and every constellation's name falls on the map, worked out from the tree alone so that no research is ever placed by hand.
 * <p>
 * Each constellation gets a slice of the circle around the eye sized by how many researches it holds, and grows inside it as a radial tree:
 * how many steps a research is from the eye sets how far out it sits, and its requirements set the branches. A few rounds of relaxation then push
 * apart whatever still touches. Everything is sorted by id and nothing is random, so the same tree always gives the same map, and adding one research
 * only shifts the rest a little.
 * <p>
 * The map is laid out in screen units around the eye at 0, 0 and never fitted to a screen: the spacing between researches is the same on every screen,
 * a small screen pans over it and a large one leaves dark around it.
 */
public final class ResearchMapPlan
{
    /** Kept clear between two nodes, and between a node and a name. */
    static final int GAP = 4;
    /** How far each step away from the eye carries a research, across and down. The map is wider than tall, as screens are. */
    static final double STEP_X = 46;
    static final double STEP_Y = 26;

    /** How far a child must sit beyond its parent, in steps. */
    private static final double CLIMB       = 0.3;
    /** Space left between two constellations' slices, in researches. */
    private static final double SLICE_GAP   = 1;
    private static final int    ROUNDS      = 400;
    private static final int    EYE_STEPS    = 12;
    private static final int    EDGE_SAMPLES = 8;
    /** The share of the rounds during which everything is still pulled towards where the tree would put it. */
    private static final double SPRING_SHARE = 0.6;
    private static final double NODE_SPRING = 0.05;
    private static final double NAME_SPRING = 0.03;
    /** A branch bows to one side by this share of its length, up to the most. */
    static final float          BOW_SHARE   = 0.12F;
    static final float          BOW_MAX     = 8;
    static final int            LINK_PIECES = 20;
    /** Kept clear between a name and a branch. */
    private static final double BRANCH_CLEAR = 2;
    private static final double BRANCH_PUSH  = 0.3;
    /** Where the first constellation is centred, pointing left from the eye. The rest follow anticlockwise. */
    private static final double FIRST_SLICE = Math.PI;

    /**
     * The sizes of what is placed, in screen units.
     *
     * @param eyeHalfWidth  half the width kept for the eye and the insights circling it
     * @param eyeHalfHeight likewise
     * @param nameWidths    how wide each constellation's name is, sigil included
     */
    public record Frame(double eyeHalfWidth, double eyeHalfHeight, int nodeSize, Map<Constellation, Integer> nameWidths, int nameHeight)
    {
    }

    /** What the map covers, eye, researches and names included, around the eye at 0, 0. */
    public record Bounds(int minX, int minY, int maxX, int maxY)
    {
        public int width()
        {
            return maxX - minX;
        }

        public int height()
        {
            return maxY - minY;
        }
    }

    /** A constellation's name, from its top left corner. */
    public record Name(int x, int y, int width, int height)
    {
    }

    public record Spot(int x, int y)
    {
    }

    /** A branch as drawn, from its parent or from the eye's rim to its research. */
    public record Link(Identifier parent, Identifier child, float[] xs, float[] ys)
    {
    }

    private final Map<Identifier, Spot>     nodes;
    private final Map<Constellation, Name>  names;
    private final Map<Identifier, Identifier> parents;
    private final Map<Identifier, Double>    reach;
    private final List<Link>                 links;
    private final Bounds                     bounds;

    private ResearchMapPlan(Map<Identifier, Spot> nodes, Map<Constellation, Name> names, Map<Identifier, Identifier> parents, Map<Identifier, Double> reach, List<Link> links,
                            Bounds bounds)
    {
        this.nodes = nodes;
        this.names = names;
        this.parents = parents;
        this.reach = reach;
        this.links = links;
        this.bounds = bounds;
    }

    public Bounds bounds()
    {
        return bounds;
    }

    /** Every branch, one per requirement found in the tree and one from the eye for each research that requires nothing. */
    public List<Link> links()
    {
        return links;
    }

    public Map<Identifier, Spot> nodes()
    {
        return nodes;
    }

    public Map<Constellation, Name> names()
    {
        return names;
    }

    /** The branch a research hangs from, or null when it grows from the eye. */
    public Identifier parentOf(Identifier id)
    {
        return parents.get(id);
    }

    /** How far out a research sits, in steps from just outside the eye. */
    public double reachOf(Identifier id)
    {
        return reach.get(id);
    }

    public static Identifier parentOf(Research research, Map<Identifier, Research> tree)
    {
        return research.requires().stream().filter(tree::containsKey).findFirst().orElse(null);
    }

    public static ResearchMapPlan plan(Map<Identifier, Research> tree, Frame frame)
    {
        return new Planner(new TreeMap<>(tree), frame).plan();
    }

    /**
     * Where the eye may sit along one axis of the screen, so that the map can be panned until either edge shows, or stays put showing all of it when it
     * fits. Returns the lowest and highest screen position of the eye.
     *
     * @param mapMin  the map's lowest point around the eye
     * @param mapMax  the map's highest point
     * @param viewMin where the screen shows the map from
     * @param viewMax and up to
     * @param margin  dark kept beyond the map's edge when panned all the way
     */
    public static int[] eyeRange(int mapMin, int mapMax, int viewMin, int viewMax, int margin)
    {
        int first = viewMin + margin - mapMin;
        int second = viewMax - margin - mapMax;
        return new int[] { Math.min(first, second), Math.max(first, second) };
    }

    /** A branch leaning the same way along its whole length reads as grown rather than wired. */
    static void curve(double startX, double startY, double endX, double endY, Identifier child, float[] xs, float[] ys)
    {
        double acrossX = endX - startX;
        double acrossY = endY - startY;
        double span = Math.max(1, Math.hypot(acrossX, acrossY));
        double bow = Math.min(BOW_MAX, span * BOW_SHARE) * ((child.hashCode() & 1) == 0 ? 1 : -1);
        int pieces = xs.length - 1;
        for (int i = 0; i <= pieces; i++)
        {
            double t = i / (double) pieces;
            double along = (t + t * t * (3 - 2 * t)) / 2;
            double offset = Math.sin(t * Math.PI) * bow;
            xs[i] = (float) (startX + acrossX * along - acrossY / span * offset);
            ys[i] = (float) (startY + acrossY * along + acrossX / span * offset);
        }
    }

    private static final class Body
    {
        double x;
        double y;
        double targetX;
        double targetY;
        final double halfW;
        final double halfH;

        Body(double halfW, double halfH)
        {
            this.halfW = halfW;
            this.halfH = halfH;
        }
    }

    private static final class Planner
    {
        private final TreeMap<Identifier, Research>      tree;
        private final Frame                              frame;
        private final Map<Identifier, Identifier>        parents  = new HashMap<>();
        private final Map<Identifier, List<Identifier>>  children = new HashMap<>();
        private final Map<Identifier, Integer>           local    = new HashMap<>();
        private final Map<Identifier, Double>            target   = new HashMap<>();
        private final Map<Identifier, Double>            angle    = new HashMap<>();
        private final Map<Identifier, List<Constellation>> hung  = new HashMap<>();
        private final List<Identifier>                   order    = new ArrayList<>();

        private final double half;

        Planner(TreeMap<Identifier, Research> tree, Frame frame)
        {
            this.tree = tree;
            this.frame = frame;
            this.half = frame.nodeSize() / 2D;
        }

        ResearchMapPlan plan()
        {
            tree.forEach((id, research) ->
            {
                var parent = parentOf(research, tree);
                if (parent != null && !leadsBack(parent, id))
                {
                    parents.put(id, parent);
                    children.computeIfAbsent(parent, key -> new ArrayList<>()).add(id);
                }
            });
            tree.keySet().forEach(id -> localDepth(id));
            slice();
            tree.keySet().forEach(this::reach);
            walkOrder();

            var bodies = new TreeMap<Identifier, Body>();
            tree.forEach((id, research) ->
            {
                var body = new Body(half, half);
                double[] at = at(angle.get(id), target.get(id));
                body.x = body.targetX = at[0];
                body.y = body.targetY = at[1];
                bodies.put(id, body);
            });

            var labels = new EnumMap<Constellation, Body>(Constellation.class);
            for (var constellation : Constellation.values())
            {
                var members = tree.entrySet().stream().filter(entry -> entry.getValue().constellation() == constellation).map(Map.Entry::getKey).toList();
                if (members.isEmpty())
                    continue;
                var body = new Body(frame.nameWidths().getOrDefault(constellation, 40) / 2D, frame.nameHeight() / 2D);
                body.x = body.targetX = members.stream().mapToDouble(id -> bodies.get(id).x).average().orElseThrow();
                body.y = body.targetY = members.stream().mapToDouble(id -> bodies.get(id).y).average().orElseThrow();
                labels.put(constellation, body);
            }

            relax(bodies, labels);

            var nodes = new TreeMap<Identifier, Spot>();
            bodies.forEach((id, body) -> nodes.put(id, new Spot((int) Math.round(body.x), (int) Math.round(body.y))));
            var names = new EnumMap<Constellation, Name>(Constellation.class);
            labels.forEach((constellation, body) -> names.put(constellation, new Name((int) Math.round(body.x - body.halfW), (int) Math.round(body.y - body.halfH),
                    (int) Math.round(body.halfW * 2), (int) Math.round(body.halfH * 2))));
            var reach = new HashMap<Identifier, Double>();
            bodies.forEach((id, body) -> reach.put(id, reachAt(body.x, body.y)));
            var links = new ArrayList<Link>();
            tree.forEach((id, research) ->
            {
                var to = nodes.get(id);
                if (research.requires().isEmpty())
                    links.add(link(null, id, rimToward(to.x(), to.y()), to));
                research.requires().stream().filter(tree::containsKey).sorted().forEach(parent -> links.add(link(parent, id, nodes.get(parent), to)));
            });

            int minX = (int) -Math.ceil(frame.eyeHalfWidth());
            int maxX = (int) Math.ceil(frame.eyeHalfWidth());
            int minY = (int) -Math.ceil(frame.eyeHalfHeight());
            int maxY = (int) Math.ceil(frame.eyeHalfHeight());
            for (var node : nodes.values())
            {
                minX = Math.min(minX, node.x() - frame.nodeSize() / 2);
                maxX = Math.max(maxX, node.x() + frame.nodeSize() / 2);
                minY = Math.min(minY, node.y() - frame.nodeSize() / 2);
                maxY = Math.max(maxY, node.y() + frame.nodeSize() / 2);
            }
            for (var name : names.values())
            {
                minX = Math.min(minX, name.x());
                maxX = Math.max(maxX, name.x() + name.width());
                minY = Math.min(minY, name.y());
                maxY = Math.max(maxY, name.y() + name.height());
            }
            return new ResearchMapPlan(nodes, names, parents, reach, List.copyOf(links), new Bounds(minX, minY, maxX, maxY));
        }

        /** Where a branch from the eye leaves its rim. */
        private Spot rimToward(double x, double y)
        {
            double angle = Math.atan2(y, x);
            return new Spot((int) Math.round(Math.cos(angle) * frame.eyeHalfWidth()), (int) Math.round(Math.sin(angle) * frame.eyeHalfHeight()));
        }

        private static Link link(Identifier parent, Identifier child, Spot from, Spot to)
        {
            var xs = new float[LINK_PIECES + 1];
            var ys = new float[LINK_PIECES + 1];
            curve(from.x(), from.y(), to.x(), to.y(), child, xs, ys);
            return new Link(parent, child, xs, ys);
        }

        private boolean leadsBack(Identifier from, Identifier to)
        {
            for (var at = from; at != null; at = parentOf(tree.get(at), tree))
            {
                if (at.equals(to))
                    return true;
            }
            return false;
        }

        private Constellation constellationOf(Identifier id)
        {
            return tree.get(id).constellation();
        }

        /** Steps from where a research's constellation starts, either at the eye or where it branches off another. */
        private int localDepth(Identifier id)
        {
            var known = local.get(id);
            if (known != null)
                return known;
            var parent = parents.get(id);
            int depth = parent == null || constellationOf(parent) != constellationOf(id) ? 0 : localDepth(parent) + 1;
            local.put(id, depth);
            return depth;
        }

        private List<Identifier> ownChildren(Identifier id)
        {
            return children.getOrDefault(id, List.of()).stream().filter(child -> constellationOf(child) == constellationOf(id)).sorted().toList();
        }

        private List<Identifier> rootsOf(Constellation constellation)
        {
            return tree.keySet().stream().filter(id -> constellationOf(id) == constellation && localDepth(id) == 0).toList();
        }

        /**
         * The research a constellation hangs from when none of its roots grow from the eye, so that it sits beyond that research rather than in a slice of its own.
         * Null for a constellation reaching the eye, and for one whose hanging would lead back to itself.
         */
        private Identifier anchorOf(Constellation constellation)
        {
            var roots = rootsOf(constellation);
            if (roots.isEmpty() || roots.stream().anyMatch(id -> !parents.containsKey(id)))
                return null;
            var anchor = parents.get(roots.getFirst());
            var seen = EnumSet.of(constellation);
            for (var at = anchor; at != null; )
            {
                if (!seen.add(constellationOf(at)))
                    return null;
                var above = rootsOf(constellationOf(at));
                at = above.stream().anyMatch(id -> !parents.containsKey(id)) ? null : parents.get(above.getFirst());
            }
            return anchor;
        }

        /** What grows beyond a research: its own branches, then the constellations hanging from it, each kept whole. */
        private List<Object> beyond(Identifier id)
        {
            var items = new ArrayList<Object>(ownChildren(id));
            hung.getOrDefault(id, List.of()).forEach(items::add);
            items.sort(Comparator.comparing(this::keyOf));
            return items;
        }

        private Identifier keyOf(Object item)
        {
            return item instanceof Constellation constellation ? rootsOf(constellation).getFirst() : (Identifier) item;
        }

        private int leaves(Object item)
        {
            if (item instanceof Constellation constellation)
                return rootsOf(constellation).stream().mapToInt(this::leaves).sum();
            var beyond = beyond((Identifier) item);
            return beyond.isEmpty() ? 1 : beyond.stream().mapToInt(this::leaves).sum();
        }

        private int size(Constellation constellation)
        {
            int size = (int) tree.values().stream().filter(research -> research.constellation() == constellation).count();
            for (var other : Constellation.values())
            {
                var anchor = anchorOf(other);
                if (anchor != null && constellationOf(anchor) == constellation)
                    size += size(other);
            }
            return size;
        }

        /** Each constellation reaching the eye gets a slice sized by how many researches it holds, counting those hanging from it. */
        private void slice()
        {
            for (var constellation : Constellation.values())
            {
                var anchor = anchorOf(constellation);
                if (anchor != null)
                    hung.computeIfAbsent(anchor, key -> new ArrayList<>()).add(constellation);
            }

            var weights = new EnumMap<Constellation, Double>(Constellation.class);
            for (var constellation : Constellation.values())
            {
                if (!rootsOf(constellation).isEmpty() && anchorOf(constellation) == null)
                    weights.put(constellation, size(constellation) + SLICE_GAP);
            }
            double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();

            double high = Double.NaN;
            for (var entry : weights.entrySet())
            {
                double width = entry.getValue() / total * Math.PI * 2;
                if (Double.isNaN(high))
                    high = FIRST_SLICE + width / 2;
                double pad = width * SLICE_GAP / 2 / entry.getValue();
                spread(List.of(entry.getKey()), high - width + pad, high - pad);
                high -= width;
            }
        }

        private void spread(List<?> items, double low, double high)
        {
            double total = items.stream().mapToInt(this::leaves).sum();
            double at = low;
            for (var item : items)
            {
                double width = (high - low) * leaves(item) / total;
                if (item instanceof Constellation constellation)
                    spread(rootsOf(constellation), at, at + width);
                else
                {
                    angle.put((Identifier) item, at + width / 2);
                    spread(beyond((Identifier) item), at, at + width);
                }
                at += width;
            }
        }


        /** One step further out than what it hangs from, so a constellation branching off another starts beyond its branch. */
        private double reach(Identifier id)
        {
            var known = target.get(id);
            if (known != null)
                return known;
            var parent = parents.get(id);
            double reach = parent == null ? 0 : reach(parent) + 1;
            target.put(id, reach);
            return reach;
        }

        private void walkOrder()
        {
            var roots = tree.keySet().stream().filter(id -> !parents.containsKey(id)).toList();
            var queue = new ArrayList<>(roots);
            while (!queue.isEmpty())
            {
                var at = queue.removeFirst();
                order.add(at);
                children.getOrDefault(at, List.of()).stream().sorted().forEach(queue::add);
            }
        }

        private double innerX()
        {
            return frame.eyeHalfWidth() + half + GAP;
        }

        private double innerY()
        {
            return frame.eyeHalfHeight() + half + GAP;
        }

        private double innerRadius(double cos, double sin)
        {
            return 1 / Math.sqrt(sq(cos / innerX()) + sq(sin / innerY()));
        }

        private static double stepRadius(double cos, double sin)
        {
            return 1 / Math.sqrt(sq(cos / STEP_X) + sq(sin / STEP_Y));
        }

        /** A slice's angle is measured as if the steps were round, then stretched to their shape, so that the wide sides are not starved. */
        private double[] at(double theta, double reach)
        {
            double phi = Math.atan2(STEP_Y * Math.sin(theta), STEP_X * Math.cos(theta));
            double cos = Math.cos(phi);
            double sin = Math.sin(phi);
            double radius = innerRadius(cos, sin) + reach * stepRadius(cos, sin);
            return new double[] { cos * radius, sin * radius };
        }

        private double reachAt(double x, double y)
        {
            double distance = Math.hypot(x, y);
            if (distance == 0)
                return 0;
            double cos = x / distance;
            double sin = y / distance;
            return (distance - innerRadius(cos, sin)) / stepRadius(cos, sin);
        }

        private void relax(TreeMap<Identifier, Body> bodies, EnumMap<Constellation, Body> labels)
        {
            var all = new ArrayList<Body>(bodies.values());
            var nodeCount = all.size();
            all.addAll(labels.values());

            for (int round = 0; round < ROUNDS; round++)
            {
                // The pull home fades out and the last rounds only part what still touches, so the map settles rather than holds a tug of war
                double settle = Math.max(0, 1 - round / (ROUNDS * SPRING_SHARE));
                for (int i = 0; i < all.size(); i++)
                {
                    var body = all.get(i);
                    double spring = (i < nodeCount ? NODE_SPRING : NAME_SPRING) * settle;
                    body.x += (body.targetX - body.x) * spring;
                    body.y += (body.targetY - body.y) * spring;
                }

                if (settle > 0)
                    clearBranches(bodies, labels);

                for (int a = 0; a < all.size(); a++)
                {
                    for (int b = a + 1; b < all.size(); b++)
                        separate(all.get(a), all.get(b), a < nodeCount && b < nodeCount);
                }

                for (int i = 0; i < all.size(); i++)
                {
                    var body = all.get(i);
                    if (i < nodeCount)
                        clearEye(body);
                    else
                        clearEyeBox(body);
                }

                for (var id : order)
                {
                    var parent = parents.get(id);
                    if (parent != null)
                        climb(bodies.get(id), bodies.get(parent));
                }
            }
        }

        /** Two nodes push straight apart, which keeps branches from shearing; a name against anything slides off along the shorter way. */
        private void separate(Body first, Body second, boolean nodes)
        {
            double dx = second.x - first.x;
            double dy = second.y - first.y;
            double needX = first.halfW + second.halfW + GAP;
            double needY = first.halfH + second.halfH + GAP;
            double overX = needX - Math.abs(dx);
            double overY = needY - Math.abs(dy);
            if (overX <= 0 || overY <= 0)
                return;

            if (nodes)
            {
                double distance = Math.hypot(dx, dy);
                double nx = distance < 1e-6 ? 1 : dx / distance;
                double ny = distance < 1e-6 ? 0 : dy / distance;
                double push = Math.min(overX / Math.max(Math.abs(nx), 1e-6), overY / Math.max(Math.abs(ny), 1e-6)) / 2 * 0.5;
                first.x -= nx * push;
                first.y -= ny * push;
                second.x += nx * push;
                second.y += ny * push;
            }
            else if (overX < overY)
            {
                double push = overX / 2 * (dx < 0 ? -1 : 1);
                first.x -= push;
                second.x += push;
            }
            else
            {
                double push = overY / 2 * (dy < 0 ? -1 : 1);
                first.y -= push;
                second.y += push;
            }
        }

        /** Names and researches slide off any branch running under them that is not their own, so the figure stays whole. */
        private void clearBranches(TreeMap<Identifier, Body> bodies, EnumMap<Constellation, Body> labels)
        {
            var xs = new float[LINK_PIECES + 1];
            var ys = new float[LINK_PIECES + 1];
            tree.forEach((id, research) ->
            {
                var to = bodies.get(id);
                if (research.requires().isEmpty())
                {
                    var rim = rimToward(to.x, to.y);
                    curve(rim.x(), rim.y(), to.x, to.y, id, xs, ys);
                    pushOff(id, null, xs, ys, bodies, labels);
                }
                for (var parent : research.requires())
                {
                    var from = bodies.get(parent);
                    if (from == null)
                        continue;
                    curve(from.x, from.y, to.x, to.y, id, xs, ys);
                    pushOff(id, parent, xs, ys, bodies, labels);
                }
            });
        }

        private static void pushOff(Identifier child, Identifier parent, float[] xs, float[] ys, TreeMap<Identifier, Body> bodies, EnumMap<Constellation, Body> labels)
        {
            labels.values().forEach(label -> pushOff(label, xs, ys));
            bodies.forEach((id, body) ->
            {
                if (!id.equals(child) && !id.equals(parent))
                    pushOff(body, xs, ys);
            });
        }

        private static void pushOff(Body label, float[] xs, float[] ys)
        {
            for (int i = 0; i < xs.length; i++)
            {
                double dx = xs[i] - label.x;
                double dy = ys[i] - label.y;
                double overX = label.halfW + BRANCH_CLEAR - Math.abs(dx);
                double overY = label.halfH + BRANCH_CLEAR - Math.abs(dy);
                if (overX <= 0 || overY <= 0)
                    continue;
                if (overX < overY)
                    label.x -= overX * (dx < 0 ? -1 : 1) * BRANCH_PUSH;
                else
                    label.y -= overY * (dy < 0 ? -1 : 1) * BRANCH_PUSH;
            }
        }

        /** A node's corners reach further in than its middle, so it is carried out along its own line until no part of it touches the eye. */
        private void clearEye(Body body)
        {
            double dx = body.x;
            double dy = body.y;
            if (!touchesEye(dx, dy, body.halfW, body.halfH))
                return;
            if (Math.hypot(dx, dy) < 1e-6)
                dy = -1;

            double low = 1;
            double high = 2;
            while (touchesEye(dx * high, dy * high, body.halfW, body.halfH))
                high *= 2;
            for (int step = 0; step < EYE_STEPS; step++)
            {
                double middle = (low + high) / 2;
                if (touchesEye(dx * middle, dy * middle, body.halfW, body.halfH))
                    low = middle;
                else
                    high = middle;
            }
            body.x = dx * high;
            body.y = dy * high;
        }

        /** Whether a box this far from the eye's middle reaches into the eye and its insights, with a gap kept around them. */
        private boolean touchesEye(double dx, double dy, double halfW, double halfH)
        {
            double radiusX = frame.eyeHalfWidth() + GAP;
            double radiusY = frame.eyeHalfHeight() + GAP;
            double nearestX = Math.clamp(0, dx - halfW, dx + halfW);
            double nearestY = Math.clamp(0, dy - halfH, dy + halfH);
            if (sq(nearestX / radiusX) + sq(nearestY / radiusY) < 1)
                return true;
            // The ellipse bulges past a box's nearest point when it meets the box along an edge, so walk the edges facing the eye
            double edgeX = dx > 0 ? dx - halfW : dx + halfW;
            double edgeY = dy > 0 ? dy - halfH : dy + halfH;
            for (int i = 0; i <= EDGE_SAMPLES; i++)
            {
                double along = -1 + 2D * i / EDGE_SAMPLES;
                if (sq(edgeX / radiusX) + sq((dy + along * halfH) / radiusY) < 1 || sq((dx + along * halfW) / radiusX) + sq(edgeY / radiusY) < 1)
                    return true;
            }
            return false;
        }

        private void clearEyeBox(Body body)
        {
            double dx = body.x;
            double dy = body.y;
            double overX = frame.eyeHalfWidth() + body.halfW + GAP - Math.abs(dx);
            double overY = frame.eyeHalfHeight() + body.halfH + GAP - Math.abs(dy);
            if (overX <= 0 || overY <= 0)
                return;
            if (overX < overY)
                body.x += overX * (dx < 0 ? -1 : 1);
            else
                body.y += overY * (dy < 0 ? -1 : 1);
        }

        private void climb(Body child, Body parent)
        {
            double want = reachAt(parent.x, parent.y) + CLIMB;
            double dx = child.x;
            double dy = child.y;
            double distance = Math.hypot(dx, dy);
            if (distance == 0 || reachAt(child.x, child.y) >= want)
                return;
            double cos = dx / distance;
            double sin = dy / distance;
            double inner = innerRadius(cos, sin);
            double radius = inner + want * stepRadius(cos, sin);
            child.x = cos * radius;
            child.y = sin * radius;
        }

        private static double sq(double value)
        {
            return value * value;
        }
    }
}
