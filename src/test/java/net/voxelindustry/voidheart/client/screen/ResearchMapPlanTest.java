package net.voxelindustry.voidheart.client.screen;

import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.screen.ResearchMapPlan.Frame;
import net.voxelindustry.voidheart.client.screen.ResearchMapPlan.Name;
import net.voxelindustry.voidheart.client.screen.ResearchMapPlan.Spot;
import net.voxelindustry.voidheart.common.content.research.Constellation;
import net.voxelindustry.voidheart.common.content.research.Research;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The map has to stay readable for the shipped tree and for trees that grow from it, and every screen has to be able to show all of it.
 */
class ResearchMapPlanTest
{
    private static final int NODE      = ResearchMapView.NODE_SIZE;

    static Stream<Arguments> trees()
    {
        return Stream.of(Arguments.of("shipped", ResearchTrees.shipped()), Arguments.of("grown", ResearchTrees.grown()), Arguments.of("crowded", ResearchTrees.crowded()),
                Arguments.of("twice the size", ResearchTrees.sprawling(40, 7)), Arguments.of("twice the size, otherwise", ResearchTrees.sprawling(40, 23)),
                Arguments.of("many times the size", ResearchTrees.sprawling(140, 11)), Arguments.of("one constellation", oneConstellation()),
                Arguments.of("one research", oneResearch()));
    }

    static TreeMap<Identifier, Research> oneConstellation()
    {
        var tree = ResearchTrees.shipped();
        tree.values().removeIf(research -> research.constellation() != Constellation.ARCHIVE);
        return tree;
    }

    static TreeMap<Identifier, Research> oneResearch()
    {
        var tree = new TreeMap<Identifier, Research>();
        tree.put(ResearchTrees.id("altar_rite"), ResearchTrees.research(Constellation.RITES, 1));
        return tree;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("trees")
    void nodesNeverOverlap(String name, TreeMap<Identifier, Research> tree)
    {
        var plan = ResearchMapPlan.plan(tree, ResearchTrees.frame());
        var ids = new ArrayList<>(tree.keySet());
        for (int a = 0; a < ids.size(); a++)
        {
            for (int b = a + 1; b < ids.size(); b++)
            {
                var first = plan.nodes().get(ids.get(a));
                var second = plan.nodes().get(ids.get(b));
                assertThat(Math.max(Math.abs(first.x() - second.x()), Math.abs(first.y() - second.y()))).as("%s and %s", ids.get(a), ids.get(b)).isGreaterThanOrEqualTo(NODE);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("trees")
    void namesNeverCoverNodesOrEachOther(String name, TreeMap<Identifier, Research> tree)
    {
        var plan = ResearchMapPlan.plan(tree, ResearchTrees.frame());
        plan.names().forEach((constellation, label) ->
        {
            plan.nodes().forEach((id, node) -> assertThat(overlaps(label, node)).as("%s name over %s", constellation, id).isFalse());
            plan.names().forEach((other, otherLabel) ->
            {
                if (other != constellation)
                    assertThat(overlaps(label, otherLabel)).as("%s name over %s name", constellation, other).isFalse();
            });
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("trees")
    void branchesNeverCross(String name, TreeMap<Identifier, Research> tree)
    {
        var plan = ResearchMapPlan.plan(tree, ResearchTrees.frame());
        assertThat(ResearchMapMeasure.crossings(plan, tree)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("trees")
    void branchesNeverRunUnderAnotherResearch(String name, TreeMap<Identifier, Research> tree)
    {
        var plan = ResearchMapPlan.plan(tree, ResearchTrees.frame());
        for (var link : plan.links())
        {
            if (!ResearchMapMeasure.isBranch(plan, link))
                continue;
            plan.nodes().forEach((id, node) ->
            {
                if (id.equals(link.child()) || id.equals(link.parent()))
                    return;
                for (int i = 0; i < link.xs().length; i++)
                    assertThat(Math.hypot(link.xs()[i] - node.x(), link.ys()[i] - node.y())).as("branch to %s under %s", link.child(), id).isGreaterThanOrEqualTo(NODE / 2D);
            });
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("trees")
    void namesNeverCoverBranches(String name, TreeMap<Identifier, Research> tree)
    {
        var plan = ResearchMapPlan.plan(tree, ResearchTrees.frame());
        plan.names().forEach((constellation, label) ->
        {
            for (var link : plan.links())
            {
                for (int i = 0; i < link.xs().length; i++)
                {
                    boolean covered = link.xs()[i] > label.x() && link.xs()[i] < label.x() + label.width() && link.ys()[i] > label.y() && link.ys()[i] < label.y() + label.height();
                    assertThat(covered).as("%s name over the branch to %s", constellation, link.child()).isFalse();
                }
            }
        });
    }

    /** No part of the map is packed tight while another is left loose: every research has its nearest neighbour about as near, and no branch is cramped or strung out. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("trees")
    void researchesAreSpreadEvenly(String name, TreeMap<Identifier, Research> tree)
    {
        var plan = ResearchMapPlan.plan(tree, ResearchTrees.frame());
        if (tree.size() < 2)
            return;
        var neighbours = ResearchMapMeasure.neighbours(plan);
        assertThat(neighbours.most()).as("loosest research against tightest, %s", neighbours).isLessThan(neighbours.least() * 1.5);
        var branches = ResearchMapMeasure.branches(plan);
        assertThat(branches.least()).as("shortest branch, %s", branches).isGreaterThan(ResearchMapPlan.REACH * 0.75);
        assertThat(branches.mean()).as("usual branch, %s", branches).isBetween(ResearchMapPlan.REACH * 0.9, ResearchMapPlan.REACH * 1.2);
        assertThat(branches.most()).as("longest branch, %s", branches).isLessThan(ResearchMapPlan.REACH * 2.2);
    }

    /** A branch is one curve from end to end, without a corner anywhere along it. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("trees")
    void everyBranchIsOneCurve(String name, TreeMap<Identifier, Research> tree)
    {
        var plan = ResearchMapPlan.plan(tree, ResearchTrees.frame());
        for (var link : plan.links())
            assertThat(ResearchMapMeasure.sharpestTurn(link)).as("sharpest turn along the branch to %s", link.child()).isLessThan(12);
    }

    /** What is drawn of a branch under the eye's lid follows the line out of the eye's middle, so the branch has to leave the rim along that line. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("trees")
    void branchesLeaveTheEyeStraightOut(String name, TreeMap<Identifier, Research> tree)
    {
        var plan = ResearchMapPlan.plan(tree, ResearchTrees.frame());
        for (var link : plan.links())
        {
            if (link.parent() != null)
                continue;
            double leaving = Math.atan2(link.ys()[1] - link.ys()[0], link.xs()[1] - link.xs()[0]);
            double outward = Math.atan2(link.ys()[0], link.xs()[0]);
            assertThat(Math.abs(Math.toDegrees(Math.atan2(Math.sin(leaving - outward), Math.cos(leaving - outward))))).as("branch to %s", link.child()).isLessThan(10);
        }
    }

    /** A research with one branch arriving and one leaving passes the line on, so the two read as a single sweep through it. */
    @Test
    void aLineOfResearchesIsOneSweep()
    {
        var tree = ResearchTrees.shipped();
        var plan = ResearchMapPlan.plan(tree, ResearchTrees.frame());
        int swept = 0;
        for (var arriving : plan.links())
        {
            var leaving = plan.links().stream().filter(link -> arriving.child().equals(link.parent())).toList();
            if (leaving.size() != 1)
                continue;
            int last = arriving.xs().length - 1;
            double in = Math.atan2(arriving.ys()[last] - arriving.ys()[last - 1], arriving.xs()[last] - arriving.xs()[last - 1]);
            double out = Math.atan2(leaving.getFirst().ys()[1] - leaving.getFirst().ys()[0], leaving.getFirst().xs()[1] - leaving.getFirst().xs()[0]);
            if (Math.abs(Math.toDegrees(Math.atan2(Math.sin(out - in), Math.cos(out - in)))) < 8)
                swept++;
        }
        assertThat(swept).as("lines carried through a research").isGreaterThanOrEqualTo(6);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("trees")
    void nothingEntersTheEye(String name, TreeMap<Identifier, Research> tree)
    {
        var frame = ResearchTrees.frame();
        var plan = ResearchMapPlan.plan(tree, frame);
        plan.nodes().forEach((id, node) -> assertThat(touchesEye(frame, node.x() - NODE / 2D, node.y() - NODE / 2D, NODE, NODE)).as("%s on the eye", id).isFalse());
        plan.names().forEach((constellation, label) ->
                assertThat(touchesEye(frame, label.x(), label.y(), label.width(), label.height())).as("%s name on the eye", constellation).isFalse());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("trees")
    void boundsHoldTheWholeMap(String name, TreeMap<Identifier, Research> tree)
    {
        var frame = ResearchTrees.frame();
        var plan = ResearchMapPlan.plan(tree, frame);
        var bounds = plan.bounds();
        plan.nodes().forEach((id, node) ->
        {
            assertThat(node.x() - NODE / 2).as("%s", id).isGreaterThanOrEqualTo(bounds.minX());
            assertThat(node.x() + NODE / 2).as("%s", id).isLessThanOrEqualTo(bounds.maxX());
            assertThat(node.y() - NODE / 2).as("%s", id).isGreaterThanOrEqualTo(bounds.minY());
            assertThat(node.y() + NODE / 2).as("%s", id).isLessThanOrEqualTo(bounds.maxY());
        });
        plan.names().forEach((constellation, label) ->
        {
            assertThat(label.x()).isGreaterThanOrEqualTo(bounds.minX());
            assertThat(label.x() + label.width()).isLessThanOrEqualTo(bounds.maxX());
            assertThat(label.y()).isGreaterThanOrEqualTo(bounds.minY());
            assertThat(label.y() + label.height()).isLessThanOrEqualTo(bounds.maxY());
        });
        assertThat(bounds.minX()).isLessThanOrEqualTo((int) -frame.eyeHalfWidth());
        assertThat(bounds.maxY()).isGreaterThanOrEqualTo((int) frame.eyeHalfHeight());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("trees")
    void childrenSitBeyondTheirParents(String name, TreeMap<Identifier, Research> tree)
    {
        var plan = ResearchMapPlan.plan(tree, ResearchTrees.frame());
        tree.keySet().forEach(id ->
        {
            var parent = plan.parentOf(id);
            if (parent != null)
                assertThat(plan.reachOf(id)).as("%s beyond %s", id, parent).isGreaterThan(plan.reachOf(parent));
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("trees")
    void everyResearchAndRequirementIsDrawn(String name, TreeMap<Identifier, Research> tree)
    {
        var plan = ResearchMapPlan.plan(tree, ResearchTrees.frame());
        assertThat(plan.nodes()).containsOnlyKeys(tree.keySet());
        long requirements = tree.values().stream().mapToLong(research -> research.requires().isEmpty() ? 1 : research.requires().stream().filter(tree::containsKey).count()).sum();
        assertThat(plan.links()).hasSize((int) requirements);
        var constellations = tree.values().stream().map(Research::constellation).distinct().toList();
        assertThat(plan.names()).containsOnlyKeys(constellations);
    }

    @Test
    void sameTreeGivesTheSameMap()
    {
        var sorted = ResearchTrees.shipped();
        var hashed = new HashMap<>(sorted);
        var first = ResearchMapPlan.plan(sorted, ResearchTrees.frame());
        var second = ResearchMapPlan.plan(hashed, ResearchTrees.frame());
        assertThat(second.nodes()).isEqualTo(first.nodes());
        assertThat(second.names()).isEqualTo(first.names());
        assertThat(second.bounds()).isEqualTo(first.bounds());
    }

    /** A requirement beyond the first is drawn as well, over the tree. */
    @Test
    void aSecondRequirementIsDrawnToo()
    {
        var tree = ResearchTrees.shipped();
        tree.put(ResearchTrees.id("concord"), ResearchTrees.requiring(tree.get(ResearchTrees.id("concord")), "cogitation", "steady_mind"));
        var plan = ResearchMapPlan.plan(tree, ResearchTrees.frame());
        assertThat(plan.links()).filteredOn(link -> link.child().equals(ResearchTrees.id("concord"))).extracting(ResearchMapPlan.Link::parent)
                .containsExactlyInAnyOrder(ResearchTrees.id("cogitation"), ResearchTrees.id("steady_mind"));
        assertThat(plan.parentOf(ResearchTrees.id("concord"))).isEqualTo(ResearchTrees.id("cogitation"));
    }

    /** Requirements that lead back to themselves, as a broken data pack might have, are still laid out. */
    @Test
    void aLoopOfRequirementsIsStillLaidOut()
    {
        var tree = ResearchTrees.shipped();
        tree.put(ResearchTrees.id("archive_expansion"), ResearchTrees.requiring(tree.get(ResearchTrees.id("archive_expansion")), "concord"));
        var plan = ResearchMapPlan.plan(tree, ResearchTrees.frame());
        assertThat(plan.nodes()).containsOnlyKeys(tree.keySet());
        assertThat(ResearchMapMeasure.crossings(plan, tree)).isEmpty();
    }

    /** The branches beside the new research make room and the rest barely turn, measured in node widths since the map keeps its size. */
    @Test
    void oneMoreResearchMovesTheRestLittle()
    {
        var before = ResearchMapPlan.plan(ResearchTrees.shipped(), ResearchTrees.frame());
        var after = ResearchMapPlan.plan(ResearchTrees.grown(), ResearchTrees.frame());

        double total = 0;
        double most = 0;
        for (var entry : before.nodes().entrySet())
        {
            var moved = after.nodes().get(entry.getKey());
            double distance = Math.hypot(moved.x() - entry.getValue().x(), moved.y() - entry.getValue().y());
            total += distance;
            most = Math.max(most, distance);
        }
        assertThat(total / before.nodes().size()).as("average move").isLessThan(NODE * 0.75);
        assertThat(most).as("largest move").isLessThan(NODE * 4);
    }

    @Test
    void aMapThatFitsStaysWhole()
    {
        int[] range = ResearchMapPlan.eyeRange(-100, 150, 0, 400, 10);
        assertThat(range[0] - 100).isGreaterThanOrEqualTo(10);
        assertThat(range[1] + 150).isLessThanOrEqualTo(390);
    }

    @Test
    void aMapLargerThanTheScreenPansToEitherEdge()
    {
        int[] range = ResearchMapPlan.eyeRange(-300, 250, 0, 400, 10);
        assertThat(range[1] - 300).as("left edge shown").isEqualTo(10);
        assertThat(range[0] + 250).as("right edge shown").isEqualTo(390);
    }

    @ParameterizedTest(name = "{0}x{1}")
    @MethodSource("sizes")
    void everyScreenCanShowEveryResearch(int width, int height)
    {
        var plan = ResearchMapPlan.plan(ResearchTrees.crowded(), ResearchTrees.frame());
        var bounds = plan.bounds();
        int[] view = ResearchTrees.view(width, height);
        int[] across = ResearchMapPlan.eyeRange(bounds.minX(), bounds.maxX(), view[0], view[2], 10);
        int[] down = ResearchMapPlan.eyeRange(bounds.minY(), bounds.maxY(), view[1], view[3], 10);
        plan.nodes().forEach((id, node) ->
        {
            // Some place of the eye within reach puts the whole research on screen, above the insight button
            boolean shownAcross = node.x() - NODE / 2 + across[1] >= view[0] && node.x() + NODE / 2 + across[0] <= view[2];
            boolean shownDown = node.y() - NODE / 2 + down[1] >= view[1] && node.y() + NODE / 2 + down[0] <= view[3];
            assertThat(shownAcross && shownDown).as("%s reachable", id).isTrue();
        });
    }

    static Stream<Arguments> sizes()
    {
        return Stream.of(ResearchTrees.SIZES).map(size -> Arguments.of(size[0], size[1]));
    }

    private static boolean overlaps(Name label, Spot node)
    {
        return label.x() < node.x() + NODE / 2 && node.x() - NODE / 2 < label.x() + label.width()
                && label.y() < node.y() + NODE / 2 && node.y() - NODE / 2 < label.y() + label.height();
    }

    private static boolean overlaps(Name first, Name second)
    {
        return first.x() < second.x() + second.width() && second.x() < first.x() + first.width() && first.y() < second.y() + second.height() && second.y() < first.y() + first.height();
    }

    /** Whether any point of the box falls inside the eye and the insights circling it, the eye sitting at 0, 0. */
    private static boolean touchesEye(Frame frame, double x, double y, double width, double height)
    {
        for (double px = x; px <= x + width; px += 1)
        {
            if (insideEye(frame, px, y) || insideEye(frame, px, y + height))
                return true;
        }
        for (double py = y; py <= y + height; py += 1)
        {
            if (insideEye(frame, x, py) || insideEye(frame, x + width, py))
                return true;
        }
        return insideEye(frame, Math.clamp(0, x, x + width), Math.clamp(0, y, y + height));
    }

    private static boolean insideEye(Frame frame, double x, double y)
    {
        double dx = x / frame.eyeHalfWidth();
        double dy = y / frame.eyeHalfHeight();
        return dx * dx + dy * dy < 1;
    }
}
