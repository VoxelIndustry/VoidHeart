package net.voxelindustry.voidheart.client.armor;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WingSimTest
{
    private static final int   NODES = 8;
    private static final float LINK  = 0.25F;

    /** A straight filament standing out sideways from a root. */
    private static Vector3f[] sideways(float rootX, float rootY)
    {
        var points = new Vector3f[NODES];
        for (int i = 0; i < NODES; i++)
            points[i] = new Vector3f(rootX + i * LINK, rootY, 0);
        return points;
    }

    private static void run(WingSim sim, Vector3f[] targets, float root, float tip, int ticks)
    {
        for (int t = 0; t < ticks; t++)
            sim.step(targets, root, tip, 0.05F, 0.86F, null);
    }

    @Test
    void withNoSpringItHangsStraightDownAndKeepsItsLength()
    {
        var sim = new WingSim(NODES);
        var targets = sideways(0, 0);
        run(sim, targets, 0, 0, 400);
        var tip = sim.position(NODES - 1);
        // The pinned nodes hold the start; the rest hang below the last of them, bending round from it rather than folding
        var hinge = sim.position(WingSim.PINNED - 1);
        assertThat(tip.y).isLessThan(hinge.y - (NODES - WingSim.PINNED) * LINK * 0.7F);
        assertThat(Math.abs(tip.x - hinge.x)).isLessThan(LINK * 3);
        for (int i = 1; i < NODES; i++)
            assertThat(sim.position(i - 1).distance(sim.position(i))).as("link %d", i).isCloseTo(LINK, within(0.01F));
    }

    @Test
    void stiffSpringsHoldTheShapeWhileSlackTipsSag()
    {
        var stiff = new WingSim(NODES);
        var slack = new WingSim(NODES);
        var targets = sideways(0, 0);
        run(stiff, targets, 2, 2, 200);
        run(slack, targets, 2, 0.05F, 200);
        assertThat(stiff.position(NODES - 1).y).isCloseTo(0, within(0.05F));
        assertThat(slack.position(NODES - 1).y).as("a slack tip sags").isLessThan(-0.2F);
        assertThat(slack.position(2).y).as("the stiff root holds").isGreaterThan(-0.1F);
    }

    @Test
    void theTipLagsBehindAMoveAndSwingsPastOnStopping()
    {
        var sim = new WingSim(NODES);
        var targets = sideways(0, 0);
        run(sim, targets, 1.6F, 0.07F, 200);
        float restTip = sim.position(NODES - 1).z;

        // The root moves 0.3 a tick along z for ten ticks, then stops
        float tipLag = 0;
        for (int t = 1; t <= 10; t++)
        {
            for (var point : targets)
                point.z += 0.3F;
            run(sim, targets, 1.6F, 0.07F, 1);
            tipLag = Math.max(tipLag, targets[NODES - 1].z - sim.position(NODES - 1).z);
        }
        assertThat(tipLag).as("the tip trails behind the moving root").isGreaterThan(0.3F);

        float overshoot = 0;
        for (int t = 0; t < 40; t++)
        {
            run(sim, targets, 1.6F, 0.07F, 1);
            overshoot = Math.max(overshoot, sim.position(NODES - 1).z - targets[NODES - 1].z);
        }
        assertThat(overshoot).as("the tip's weight carries it past where the root stopped").isGreaterThan(0.02F);
        assertThat(restTip).isNotNull();
    }

    @Test
    void aFilamentStandingUpSagsAsAnArcWithoutFolding()
    {
        var sim = new WingSim(NODES);
        var targets = new Vector3f[NODES];
        for (int i = 0; i < NODES; i++)
            targets[i] = new Vector3f(i * 0.02F, i * LINK, 0);
        for (int t = 0; t < 300; t++)
        {
            sim.step(targets, 1.6F, 0.05F, 0.08F, 0.82F, null);
            for (int i = 2; i < NODES; i++)
            {
                var before = new Vector3f(sim.position(i - 1)).sub(sim.position(i - 2)).normalize();
                var after = new Vector3f(sim.position(i)).sub(sim.position(i - 1)).normalize();
                assertThat(Math.toDegrees(before.angle(after))).as("bend at node %d, tick %d", i - 1, t).isLessThan(75);
            }
        }
        assertThat(sim.position(NODES - 1).y).as("the tip sags under its weight").isLessThan(targets[NODES - 1].y - 0.2F);
    }

    @Test
    void aSolidKeepsFreeNodesOut()
    {
        var sim = new WingSim(NODES);
        var targets = sideways(0, 0);
        for (int t = 0; t < 200; t++)
            sim.step(targets, 0, 0, 0.05F, 0.86F, null, node -> node.y = Math.max(node.y, -0.5F));
        for (int i = 0; i < NODES; i++)
            assertThat(sim.position(i).y).as("node %d", i).isGreaterThanOrEqualTo(-0.5F - 1.0E-4F);
    }

    @Test
    void shiftingMovesEverythingWithoutSwinging()
    {
        var sim = new WingSim(NODES);
        var targets = sideways(0, 0);
        run(sim, targets, 1.6F, 0.07F, 200);
        float tipY = sim.position(NODES - 1).y;
        var by = new Vector3f(-40, 0, 12);
        sim.shift(by);
        for (var point : targets)
            point.add(by);
        run(sim, targets, 1.6F, 0.07F, 1);
        assertThat(sim.position(NODES - 1).y).isCloseTo(tipY, within(0.01F));
        assertThat(sim.position(0)).isEqualTo(targets[0]);
    }
}
