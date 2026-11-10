package net.voxelindustry.voidheart.client.screen;

import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.research.Constellation;
import net.voxelindustry.voidheart.common.content.research.Research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Where every research and every constellation's name falls on the map, worked out from the tree alone so that no research is ever placed by hand.
 * <p>
 * The tree is first fanned out around the eye, each research in the wedge of what it requires, which can never cross. It then settles, see
 * {@link ResearchMapSettle}: every branch pulls towards the same length and researches spread evenly, without any of them ever passing over a branch.
 * Last, each branch is drawn as one curve, see {@link ResearchMapRoutes}, and each name takes the free place nearest the middle of its constellation.
 * <p>
 * Nothing here counts on how many researches or constellations there are, or on which, so a tree that grows or is added to by another mod is laid out the
 * same way. Everything is sorted by id and nothing is random, so the same tree always gives the same map.
 * <p>
 * The map is laid out in screen units around the eye at 0, 0 and never fitted to a screen: the spacing between researches is the same on every screen,
 * a small screen pans over it and a large one leaves dark around it.
 */
public final class ResearchMapPlan
{
    /** Kept clear between two nodes, and between a node and a name. */
    static final int    GAP         = 4;
    /** How long a branch is between two researches, middle to middle. */
    static final double REACH       = 42;
    static final int    LINK_PIECES = 20;

    /** Space left between the wedges of two researches growing from the eye, in researches. */
    private static final double SLICE_GAP   = 1;
    /** Where the first research growing from the eye is centred, pointing left. The rest follow anticlockwise. */
    private static final double FIRST_SLICE = Math.PI;
    /**
     * Each ring of the fan is this much further out than the one inside it. Rings that grew by a share instead put a long line of researches so far out
     * that settling could not bring it back in.
     */
    private static final double RING_STEP   = REACH;
    private static final double WEDGE_SHARE = 0.95;
    private static final int    NAME_SEARCH = 240;
    private static final int    NAME_STEP   = 2;
    private static final double NAME_CLEAR  = 2;

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

    /** A branch as drawn, one curve from its parent or from the eye's rim to its research. */
    public record Link(Identifier parent, Identifier child, float[] xs, float[] ys)
    {
    }

    private final Map<Identifier, Spot>       nodes;
    private final Map<Constellation, Name>    names;
    private final Map<Identifier, Identifier> parents;
    private final Map<Identifier, Double>     reach;
    private final List<Link>                  links;
    private final Bounds                      bounds;

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

    /** How far a research sits beyond the eye's rim. */
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

    private static final class Planner
    {
        private final Map<Identifier, Research> tree;
        private final Frame                     frame;
        private final List<Identifier>          ids;
        private final Map<Identifier, Integer>  index = new HashMap<>();
        private final int                       count;
        private final int[]                     parent;
        private final int[][]                   children;
        private final int[]                     leaves;
        private final int[]                     size;
        private final double                    half;

        Planner(TreeMap<Identifier, Research> tree, Frame frame)
        {
            this.tree = tree;
            this.frame = frame;
            this.ids = List.copyOf(tree.keySet());
            this.count = ids.size();
            this.half = frame.nodeSize() / 2D;
            for (int i = 0; i < count; i++)
                index.put(ids.get(i), i);

            parent = new int[count];
            var below = new ArrayList<List<Integer>>();
            for (int i = 0; i < count; i++)
                below.add(new ArrayList<>());
            for (int i = 0; i < count; i++)
            {
                var above = parentOf(tree.get(ids.get(i)), tree);
                parent[i] = above == null || leadsBack(above, ids.get(i)) ? -1 : index.get(above);
                if (parent[i] >= 0)
                    below.get(parent[i]).add(i);
            }

            // A research's own constellation comes first among its branches and the others after, so a constellation growing off several researches stays together
            children = new int[count][];
            for (int i = 0; i < count; i++)
            {
                var own = constellationOf(i);
                children[i] = below.get(i).stream()
                        .sorted(Comparator.<Integer, Boolean>comparing(child -> constellationOf(child) != own).thenComparing(this::constellationOf).thenComparing(ids::get))
                        .mapToInt(Integer::intValue).toArray();
            }
            leaves = new int[count];
            size = new int[count];
            for (int i = 0; i < count; i++)
            {
                if (parent[i] < 0)
                    measure(i);
            }
        }

        /** Whether following requirements up from one research comes round to another, as a broken data pack may have it. */
        private boolean leadsBack(Identifier from, Identifier to)
        {
            var seen = new HashSet<Identifier>();
            for (var at = from; at != null && seen.add(at); at = parentOf(tree.get(at), tree))
            {
                if (at.equals(to))
                    return true;
            }
            return false;
        }

        private Constellation constellationOf(int i)
        {
            return tree.get(ids.get(i)).constellation();
        }

        private void measure(int i)
        {
            size[i] = 1;
            for (int child : children[i])
            {
                measure(child);
                leaves[i] += leaves[child];
                size[i] += size[child];
            }
            leaves[i] = Math.max(1, leaves[i]);
        }

        ResearchMapPlan plan()
        {
            var x = new double[count];
            var y = new double[count];
            fan(x, y);

            var constellations = new TreeMap<Constellation, List<Integer>>();
            for (int i = 0; i < count; i++)
                constellations.computeIfAbsent(constellationOf(i), key -> new ArrayList<>()).add(i);
            var named = List.copyOf(constellations.keySet());
            var members = new int[named.size()][];
            var nameHalfW = new double[named.size()];
            var nameHalfH = new double[named.size()];
            for (int name = 0; name < named.size(); name++)
            {
                members[name] = constellations.get(named.get(name)).stream().mapToInt(Integer::intValue).toArray();
                nameHalfW[name] = frame.nameWidths().getOrDefault(named.get(name), 40) / 2D;
                nameHalfH[name] = frame.nameHeight() / 2D;
            }

            // The branch a research carries straight on into is the one with the most beyond it
            var heir = new int[count];
            for (int i = 0; i < count; i++)
            {
                heir[i] = -1;
                for (int child : children[i])
                {
                    if (heir[i] < 0 || size[child] > size[heir[i]])
                        heir[i] = child;
                }
            }

            var settle = new ResearchMapSettle(frame, parent, heir, x, y, members, nameHalfW, nameHalfH);
            settle.run();
            for (int i = 0; i < count; i++)
            {
                x[i] = Math.round(settle.x(i));
                y[i] = Math.round(settle.y(i));
            }

            var nodes = new TreeMap<Identifier, Spot>();
            var parents = new HashMap<Identifier, Identifier>();
            var reach = new HashMap<Identifier, Double>();
            for (int i = 0; i < count; i++)
            {
                nodes.put(ids.get(i), new Spot((int) x[i], (int) y[i]));
                reach.put(ids.get(i), settle.reach(x[i], y[i]));
                if (parent[i] >= 0)
                    parents.put(ids.get(i), ids.get(parent[i]));
            }

            var routes = new ResearchMapRoutes(frame, parent, children, x, y);
            routes.route();
            var links = new ArrayList<Link>();
            for (int i = 0; i < count; i++)
            {
                var id = ids.get(i);
                var research = tree.get(id);
                if (parent[i] >= 0 || research.requires().isEmpty())
                    links.add(new Link(parent[i] < 0 ? null : ids.get(parent[i]), id, routes.xs(i), routes.ys(i)));
                for (var required : research.requires().stream().filter(tree::containsKey).sorted().toList())
                {
                    int from = index.get(required);
                    if (from == parent[i])
                        continue;
                    var xs = new float[LINK_PIECES + 1];
                    var ys = new float[LINK_PIECES + 1];
                    ResearchMapRoutes.extra(x[from], y[from], x[i], y[i], required.compareTo(id) < 0, xs, ys);
                    links.add(new Link(required, id, xs, ys));
                }
            }

            var names = new TreeMap<Constellation, Name>();
            for (int name = 0; name < named.size(); name++)
                names.put(named.get(name), place(settle.nameX[name], settle.nameY[name], nameHalfW[name], nameHalfH[name], x, y, links, List.copyOf(names.values())));

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

        /**
         * Fans the tree out on rings around the eye. Each research growing from the eye gets a wedge sized by what grows beyond it, and each research's branches
         * share its wedge, narrowed to what it can see past its own ring, which is what keeps any two branches from crossing.
         */
        private void fan(double[] x, double[] y)
        {
            var roots = new ArrayList<Integer>();
            for (int i = 0; i < count; i++)
            {
                if (parent[i] < 0)
                    roots.add(i);
            }
            roots.sort(Comparator.<Integer, Constellation>comparing(this::constellationOf).thenComparing(ids::get));
            double total = roots.stream().mapToDouble(root -> leaves[root] + SLICE_GAP).sum();
            double base = Math.max(frame.eyeHalfWidth(), frame.eyeHalfHeight()) + frame.nodeSize() + GAP;

            double high = Double.NaN;
            for (int root : roots)
            {
                double width = (leaves[root] + SLICE_GAP) / total * Math.PI * 2;
                if (Double.isNaN(high))
                    high = FIRST_SLICE + width / 2;
                double pad = width * SLICE_GAP / 2 / (leaves[root] + SLICE_GAP);
                fan(root, high - width + pad, high - pad, base, x, y);
                high -= width;
            }
        }

        /** Places one research in the middle of its wedge on its ring, then shares out what it can see of the next ring among its branches. */
        private void fan(int i, double low, double high, double radius, double[] x, double[] y)
        {
            double angle = (low + high) / 2;
            x[i] = Math.cos(angle) * radius;
            y[i] = Math.sin(angle) * radius;

            double next = radius + RING_STEP;
            double sight = Math.acos(radius / next) * WEDGE_SHARE;
            double from = Math.min(high, angle + sight);
            double width = from - Math.max(low, angle - sight);
            for (int child : children[i])
            {
                double share = width * leaves[child] / leaves[i];
                fan(child, from - share, from, next, x, y);
                from -= share;
            }
        }

        /** The free place for a name nearest to where it settled: off every research, branch and other name, and off the eye. */
        private Name place(double middleX, double middleY, double halfW, double halfH, double[] x, double[] y, List<Link> links, List<Name> taken)
        {
            int width = (int) Math.round(halfW * 2);
            int height = (int) Math.round(halfH * 2);
            int left = (int) Math.round(middleX - halfW);
            int top = (int) Math.round(middleY - halfH);
            for (int ring = 0; ring <= NAME_SEARCH; ring += NAME_STEP)
            {
                Name best = null;
                int bestOff = Integer.MAX_VALUE;
                for (int dx = -ring; dx <= ring; dx += NAME_STEP)
                {
                    for (int dy = -ring; dy <= ring; dy += Math.abs(dx) == ring ? NAME_STEP : Math.max(NAME_STEP, ring * 2))
                    {
                        int off = dx * dx + dy * dy;
                        if (off < bestOff && free(left + dx, top + dy, width, height, x, y, links, taken))
                        {
                            best = new Name(left + dx, top + dy, width, height);
                            bestOff = off;
                        }
                    }
                }
                if (best != null)
                    return best;
            }
            return new Name(left, top, width, height);
        }

        private boolean free(int left, int top, int width, int height, double[] x, double[] y, List<Link> links, List<Name> taken)
        {
            double middleX = left + width / 2D;
            double middleY = top + height / 2D;
            if (ResearchMapSettle.touchesEye(frame, middleX, middleY, width / 2D, height / 2D))
                return false;
            for (int i = 0; i < count; i++)
            {
                if (Math.abs(x[i] - middleX) < width / 2D + half + NAME_CLEAR && Math.abs(y[i] - middleY) < height / 2D + half + NAME_CLEAR)
                    return false;
            }
            for (var name : taken)
            {
                if (left < name.x() + name.width() + NAME_CLEAR && name.x() < left + width + NAME_CLEAR
                        && top < name.y() + name.height() + NAME_CLEAR && name.y() < top + height + NAME_CLEAR)
                    return false;
            }
            for (var link : links)
            {
                for (int piece = 0; piece < link.xs().length; piece++)
                {
                    if (Math.abs(link.xs()[piece] - middleX) < width / 2D + NAME_CLEAR && Math.abs(link.ys()[piece] - middleY) < height / 2D + NAME_CLEAR)
                        return false;
                }
            }
            return true;
        }
    }
}
