package net.voxelindustry.voidheart.client.screen;

import java.util.Arrays;

/**
 * Draws every branch of the research map as one curve.
 * <p>
 * A research hands the way its branch arrived on to the branch that leaves it straightest, so a line of researches reads as a single sweep through all of
 * them. Every other branch peels off that sweep and arrives as one arc. A curve that would cross another, or run under a research that is not its own, is
 * eased towards the straight line between its ends, and those straight lines never cross.
 */
final class ResearchMapRoutes
{
    /** A branch turning more than this is no longer the same sweep. */
    private static final double CARRY_TURN    = Math.toRadians(75);
    private static final double PEEL          = Math.toRadians(28);
    private static final double PEEL_MOST     = Math.toRadians(150);
    private static final double LEAN_MOST     = Math.toRadians(55);
    private static final double HANDLE        = 0.36;
    private static final double EXTRA_LEAN    = Math.toRadians(14);
    private static final int    EASES         = 5;
    private static final int    SHARED_PIECES = 3;
    private static final int    PIECES        = ResearchMapPlan.LINK_PIECES;

    private final int        count;
    private final int[]      parent;
    private final int[][]    children;
    private final double[]   x;
    private final double[]   y;
    private final double     nodeClear;

    private final double[]   startX;
    private final double[]   startY;
    private final double[]   inX;
    private final double[]   inY;
    private final int[]      carry;
    private final double[]   throughX;
    private final double[]   throughY;
    private final double[]   ease;
    private final float[][]  xs;
    private final float[][]  ys;

    /**
     * @param parent what each research hangs from, or -1 when it grows from the eye
     * @param x      where each research sits
     */
    ResearchMapRoutes(ResearchMapPlan.Frame frame, int[] parent, int[][] children, double[] x, double[] y)
    {
        this.count = parent.length;
        this.parent = parent;
        this.children = children;
        this.x = x;
        this.y = y;
        this.nodeClear = frame.nodeSize() / 2D + 1;

        startX = new double[count];
        startY = new double[count];
        inX = new double[count];
        inY = new double[count];
        carry = new int[count];
        throughX = new double[count];
        throughY = new double[count];
        ease = new double[count];
        xs = new float[count][PIECES + 1];
        ys = new float[count][PIECES + 1];
        Arrays.fill(ease, 1);

        for (int i = 0; i < count; i++)
        {
            if (parent[i] >= 0)
            {
                startX[i] = x[parent[i]];
                startY[i] = y[parent[i]];
            }
            else
            {
                // A branch from the eye leaves its rim straight out of the eye's middle, so what is drawn of it under the lid lines up
                double distance = Math.max(1e-6, Math.hypot(x[i], y[i]));
                double rim = 1 / Math.hypot(x[i] / distance / frame.eyeHalfWidth(), y[i] / distance / frame.eyeHalfHeight());
                startX[i] = x[i] / distance * rim;
                startY[i] = y[i] / distance * rim;
            }
            double length = Math.max(1e-6, Math.hypot(x[i] - startX[i], y[i] - startY[i]));
            inX[i] = (x[i] - startX[i]) / length;
            inY[i] = (y[i] - startY[i]) / length;
        }

        for (int i = 0; i < count; i++)
        {
            carry[i] = -1;
            double least = CARRY_TURN;
            for (int child : children[i])
            {
                double turn = Math.abs(turn(inX[i], inY[i], inX[child], inY[child]));
                if (turn < least)
                {
                    least = turn;
                    carry[i] = child;
                }
            }
            if (carry[i] >= 0)
            {
                double sumX = inX[i] + inX[carry[i]];
                double sumY = inY[i] + inY[carry[i]];
                double length = Math.hypot(sumX, sumY);
                throughX[i] = sumX / length;
                throughY[i] = sumY / length;
            }
        }
    }

    float[] xs(int child)
    {
        return xs[child];
    }

    float[] ys(int child)
    {
        return ys[child];
    }

    void route()
    {
        for (int pass = 0; pass <= EASES; pass++)
        {
            for (int i = 0; i < count; i++)
                draw(i);
            if (pass == EASES)
                return;

            var eased = new boolean[count];
            boolean any = false;
            for (int a = 0; a < count; a++)
            {
                if (underNode(a))
                    eased[a] = true;
                for (int b = a + 1; b < count; b++)
                {
                    if (crosses(a, b))
                        eased[a] = eased[b] = true;
                }
                any |= eased[a];
            }
            if (!any)
                return;
            for (int i = 0; i < count; i++)
            {
                if (eased[i])
                    ease[i] = pass == EASES - 1 ? 0 : ease[i] / 2;
            }
        }
    }

    private void draw(int i)
    {
        double chord = Math.hypot(x[i] - startX[i], y[i] - startY[i]);
        int p = parent[i];
        double leave = 0;
        if (p >= 0 && carry[p] == i)
            leave = turn(inX[i], inY[i], throughX[p], throughY[p]);
        else if (p >= 0)
        {
            double trunk = carry[p] >= 0 ? turn(inX[i], inY[i], throughX[p], throughY[p]) : turn(inX[i], inY[i], inX[p], inY[p]);
            if (Math.abs(trunk) < PEEL_MOST)
                leave = Math.clamp(trunk / 2, -PEEL, PEEL);
        }
        leave = Math.clamp(leave, -LEAN_MOST, LEAN_MOST);
        double arrive = carry[i] >= 0 ? Math.clamp(turn(inX[i], inY[i], throughX[i], throughY[i]), -LEAN_MOST, LEAN_MOST) : -leave;
        bezier(startX[i], startY[i], x[i], y[i], inX[i], inY[i], leave, arrive, chord * HANDLE * ease[i], xs[i], ys[i]);
    }

    /** A requirement beyond the one a research hangs from, as a plain shallow arc. These are not kept from crossing, since a tree of them may not allow it. */
    static void extra(double fromX, double fromY, double toX, double toY, boolean leanLeft, float[] xs, float[] ys)
    {
        double chord = Math.max(1e-6, Math.hypot(toX - fromX, toY - fromY));
        double lean = leanLeft ? -EXTRA_LEAN : EXTRA_LEAN;
        bezier(fromX, fromY, toX, toY, (toX - fromX) / chord, (toY - fromY) / chord, lean, -lean, chord * HANDLE, xs, ys);
    }

    private static void bezier(double fromX, double fromY, double toX, double toY, double alongX, double alongY, double leave, double arrive, double handle,
                               float[] xs, float[] ys)
    {
        double firstX = fromX + (alongX * Math.cos(leave) - alongY * Math.sin(leave)) * handle;
        double firstY = fromY + (alongX * Math.sin(leave) + alongY * Math.cos(leave)) * handle;
        double secondX = toX - (alongX * Math.cos(arrive) - alongY * Math.sin(arrive)) * handle;
        double secondY = toY - (alongX * Math.sin(arrive) + alongY * Math.cos(arrive)) * handle;
        int pieces = xs.length - 1;
        for (int piece = 0; piece <= pieces; piece++)
        {
            double t = piece / (double) pieces;
            double s = 1 - t;
            xs[piece] = (float) (s * s * s * fromX + 3 * s * s * t * firstX + 3 * s * t * t * secondX + t * t * t * toX);
            ys[piece] = (float) (s * s * s * fromY + 3 * s * s * t * firstY + 3 * s * t * t * secondY + t * t * t * toY);
        }
    }

    /** The turn from one heading to another, positive one way and negative the other. */
    private static double turn(double fromX, double fromY, double toX, double toY)
    {
        return Math.atan2(fromX * toY - fromY * toX, fromX * toX + fromY * toY);
    }

    private boolean underNode(int link)
    {
        for (int node = 0; node < count; node++)
        {
            if (node == link || node == parent[link])
                continue;
            for (int piece = 1; piece < PIECES; piece++)
            {
                if (Math.hypot(xs[link][piece] - x[node], ys[link][piece] - y[node]) < nodeClear)
                    return true;
            }
        }
        return false;
    }

    private boolean crosses(int a, int b)
    {
        if (!boxesMeet(a, b))
            return false;
        // Two branches meeting at a research are let to touch there, each end counted from where they meet
        boolean sameStart = parent[a] >= 0 && parent[a] == parent[b];
        boolean aIntoB = parent[b] == a;
        boolean bIntoA = parent[a] == b;
        for (int i = 0; i < PIECES; i++)
        {
            for (int j = 0; j < PIECES; j++)
            {
                if (sameStart && i < SHARED_PIECES && j < SHARED_PIECES)
                    continue;
                if (aIntoB && i >= PIECES - SHARED_PIECES && j < SHARED_PIECES)
                    continue;
                if (bIntoA && j >= PIECES - SHARED_PIECES && i < SHARED_PIECES)
                    continue;
                if (meet(xs[a][i], ys[a][i], xs[a][i + 1], ys[a][i + 1], xs[b][j], ys[b][j], xs[b][j + 1], ys[b][j + 1]))
                    return true;
            }
        }
        return false;
    }

    private boolean boxesMeet(int a, int b)
    {
        float minAX = Float.MAX_VALUE, maxAX = -Float.MAX_VALUE, minAY = Float.MAX_VALUE, maxAY = -Float.MAX_VALUE;
        float minBX = Float.MAX_VALUE, maxBX = -Float.MAX_VALUE, minBY = Float.MAX_VALUE, maxBY = -Float.MAX_VALUE;
        for (int piece = 0; piece <= PIECES; piece++)
        {
            minAX = Math.min(minAX, xs[a][piece]);
            maxAX = Math.max(maxAX, xs[a][piece]);
            minAY = Math.min(minAY, ys[a][piece]);
            maxAY = Math.max(maxAY, ys[a][piece]);
            minBX = Math.min(minBX, xs[b][piece]);
            maxBX = Math.max(maxBX, xs[b][piece]);
            minBY = Math.min(minBY, ys[b][piece]);
            maxBY = Math.max(maxBY, ys[b][piece]);
        }
        return minAX <= maxBX && minBX <= maxAX && minAY <= maxBY && minBY <= maxAY;
    }

    /** Whether two pieces cross or touch. */
    static boolean meet(double ax, double ay, double bx, double by, double cx, double cy, double dx, double dy)
    {
        double first = side(cx, cy, dx, dy, ax, ay);
        double second = side(cx, cy, dx, dy, bx, by);
        double third = side(ax, ay, bx, by, cx, cy);
        double fourth = side(ax, ay, bx, by, dx, dy);
        return first * second <= 0 && third * fourth <= 0 && (first != 0 || second != 0 || third != 0 || fourth != 0);
    }

    private static double side(double ax, double ay, double bx, double by, double px, double py)
    {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }
}
