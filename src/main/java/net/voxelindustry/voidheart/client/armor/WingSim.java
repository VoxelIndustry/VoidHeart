package net.voxelindustry.voidheart.client.armor;

import org.joml.Vector3f;

/**
 * One filament of a wing as a chain of weights: each node is pulled by gravity, slowed by the air and drawn by a spring toward where the wing's shape wants
 * it, while the links between nodes keep their length. The springs are stiff at the root and slack at the tip, so the wing keeps its shape where it leaves
 * the frame and its tips sag, lag behind a turn and swing on after a beat. Pure, so it is unit tested.
 * <p>
 * Positions are in blocks and time in ticks, worked through in a few smaller steps a tick so the stiff root stays steady.
 */
public final class WingSim
{
    /** Steps a tick, and passes a step that put the links back to length. */
    public static final int SUBSTEPS   = 3;
    public static final int ITERATIONS = 3;

    /** Nodes at the root that follow the shape exactly, so the filament leaves the frame the way the shape says. */
    public static final int PINNED     = 2;

    private final int        nodes;
    private final Vector3f[] position;
    private final Vector3f[] previous;
    /** Where each node stood at the end of the last tick, and the one before, for drawing between ticks. */
    private final Vector3f[] shown;
    private final Vector3f[] shownBefore;
    private final Vector3f[] targetBefore;
    private boolean          settled;

    private final Vector3f scratch = new Vector3f();
    private final Vector3f target  = new Vector3f();

    public WingSim(int nodes)
    {
        this.nodes = nodes;
        position = filled(nodes);
        previous = filled(nodes);
        shown = filled(nodes);
        shownBefore = filled(nodes);
        targetBefore = filled(nodes);
    }

    private static Vector3f[] filled(int count)
    {
        var points = new Vector3f[count];
        for (int i = 0; i < count; i++)
            points[i] = new Vector3f();
        return points;
    }

    public int nodes()
    {
        return nodes;
    }

    /**
     * Stands every node where the shape wants it, at rest.
     */
    public void reset(Vector3f[] targets)
    {
        for (int i = 0; i < nodes; i++)
        {
            position[i].set(targets[i]);
            previous[i].set(targets[i]);
            shown[i].set(targets[i]);
            shownBefore[i].set(targets[i]);
            targetBefore[i].set(targets[i]);
        }
        settled = true;
    }

    public boolean settled()
    {
        return settled;
    }

    /**
     * Moves the whole chain, as when the world shifts under it, so nothing swings from a jump that didn't happen.
     */
    public void shift(Vector3f by)
    {
        for (int i = 0; i < nodes; i++)
        {
            position[i].add(by);
            previous[i].add(by);
            shown[i].add(by);
            shownBefore[i].add(by);
            targetBefore[i].add(by);
        }
    }

    /**
     * Runs one tick.
     *
     * @param targets   where the shape wants each node at the end of this tick; the shape is followed smoothly from where it was at the last
     * @param stiffness the spring toward the shape, per tick squared, at the root and at the tip, eased between by the square of the way along
     * @param gravity   added to the downward speed each tick, in blocks a tick
     * @param keep      share of its speed a node keeps each tick against the air
     * @param push      a force on each node this tick, such as the air the filament moves through, or null
     */
    public void step(Vector3f[] targets, float rootStiffness, float tipStiffness, float gravity, float keep, Vector3f[] push)
    {
        step(targets, rootStiffness, tipStiffness, gravity, keep, push, null);
    }

    /**
     * Something a filament can't pass through, which moves a node out of it.
     */
    @FunctionalInterface
    public interface Solid
    {
        void keepOut(Vector3f node);
    }

    /**
     * Runs one tick, keeping the free nodes out of a solid after each step.
     */
    public void step(Vector3f[] targets, float rootStiffness, float tipStiffness, float gravity, float keep, Vector3f[] push, Solid solid)
    {
        if (!settled)
            reset(targets);
        for (int i = 0; i < nodes; i++)
            shownBefore[i].set(shown[i]);

        float dt = 1F / SUBSTEPS;
        float keepStep = (float) Math.pow(keep, dt);
        for (int sub = 1; sub <= SUBSTEPS; sub++)
        {
            float along = sub * dt;
            for (int i = 0; i < nodes; i++)
            {
                targetBefore[i].lerp(targets[i], along, target);
                if (i < PINNED)
                {
                    previous[i].set(position[i]);
                    position[i].set(target);
                    continue;
                }
                float u = i / (float) (nodes - 1);
                float stiffness = tipStiffness + (rootStiffness - tipStiffness) * (1 - u) * (1 - u);
                // Verlet: the speed is what moved last step, kept against the air, then the spring, gravity and push accelerate it
                scratch.set(position[i]).sub(previous[i]).mul(keepStep);
                previous[i].set(position[i]);
                target.sub(position[i]).mul(stiffness * dt * dt);
                scratch.add(target);
                scratch.y -= gravity * dt * dt;
                if (push != null)
                    scratch.add(push[i].x * dt * dt, push[i].y * dt * dt, push[i].z * dt * dt);
                position[i].add(scratch);
            }
            for (int pass = 0; pass < ITERATIONS; pass++)
                links(targets, targetBefore, along);
            if (solid != null)
                for (int i = PINNED; i < nodes; i++)
                    solid.keepOut(position[i]);
        }
        for (int i = 0; i < nodes; i++)
        {
            targetBefore[i].set(targets[i]);
            shown[i].set(position[i]);
        }
    }

    /**
     * Puts each link back to the length the shape gives it, moving only the outer node of a link that starts at a pinned one.
     */
    private void links(Vector3f[] targets, Vector3f[] before, float along)
    {
        // Across every two links, so the filament bends as one arc rather than folding at a single node; the links themselves come last, so they keep
        // their length whatever the bend asks
        for (int i = 2; i < nodes; i++)
            hold(i - 2, i, targets, before, along, BEND);
        for (int i = 1; i < nodes; i++)
            hold(i - 1, i, targets, before, along, 1);
    }

    /** Share of a bend put right each pass: how stiff a filament is against folding. */
    public static final float BEND = 0.5F;

    /**
     * Moves two nodes toward the distance the shape gives them, by a share of the error, moving only the outer one when the inner is pinned.
     */
    private void hold(int inner, int outer, Vector3f[] targets, Vector3f[] before, float along, float strength)
    {
        float length = lerp(before[inner].distance(before[outer]), targets[inner].distance(targets[outer]), along);
        var a = position[inner];
        var b = position[outer];
        scratch.set(b).sub(a);
        float distance = scratch.length();
        if (distance < 1.0E-6F)
            return;
        float error = strength * (distance - length) / distance;
        if (inner < PINNED)
            b.sub(scratch.mul(error));
        else
        {
            scratch.mul(error * 0.5F);
            a.add(scratch);
            b.sub(scratch);
        }
    }

    private static float lerp(float from, float to, float along)
    {
        return from + (to - from) * along;
    }

    /**
     * Where a node is drawn between the last two ticks.
     */
    public Vector3f shown(int node, float partialTicks, Vector3f out)
    {
        return shownBefore[node].lerp(shown[node], partialTicks, out);
    }

    public Vector3f position(int node)
    {
        return position[node];
    }
}
