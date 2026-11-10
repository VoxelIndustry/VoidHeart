package net.voxelindustry.voidheart.client.screen;

import java.util.Arrays;

/**
 * Lets the research map settle from the fan it starts as into an even spread around the eye.
 * <p>
 * Every branch pulls towards one length, researches near each other push apart, and everything leans towards the eye so that no corner of the map is left
 * empty. A research never moves further in one round than a third of its distance to the nearest branch that is not its own, and neither do that branch's
 * ends, so no research can pass over a branch: the map starts without crossings and so it ends without any.
 * <p>
 * Once shaped, the last rounds only part researches that still sit too close, holding back any branch they would stretch far.
 */
final class ResearchMapSettle
{
    private static final int    ROUNDS      = 900;
    private static final int    FINISH      = 400;
    private static final double STEP_FIRST  = 8;
    private static final double STEP_LAST   = 0.3;
    private static final double STEP_FINISH = 1;
    private static final double DONE        = 0.2;
    private static final double STILL       = 0.01;

    private static final double SPRING      = 0.3;
    /**
     * Once the map has its shape, only a branch stretched past this many times its length still pulls, and gently. Without it, a crowded part of the map
     * that eases apart at the end would draw the one branch holding it out to any length.
     */
    private static final double TAUT        = 1.8;
    private static final double TAUT_SPRING = 0.2;
    /** Researches closer than this push each other apart, gently, which is what evens the map out. */
    private static final double SPREAD      = ResearchMapPlan.REACH * 1.5;
    private static final double SOFT        = 0.06;
    /** The pull towards the eye grows with the distance from it, up to the most, so that what is far out is brought in and what is near is left be. */
    private static final double PULL        = 0.004;
    private static final double PULL_MOST   = 0.7;
    /** The map is wider than tall, as screens are. */
    private static final double WIDE        = 1.8;
    /** How much further from the eye than what it hangs from a research has to sit. */
    private static final double CLIMB       = 5;
    private static final double BEND        = 0.02;
    private static final double ROOT_STUB   = 8;
    private static final double HOME        = 0.1;
    private static final double SAFE_SHARE  = 0.95 / 3;

    private static final int    EYE_POINTS  = 24;
    private static final double EYE_CLEAR   = 3;
    private static final int    EYE_STEPS   = 12;
    private static final int    EDGE_SAMPLES = 8;

    private static final double NAME_SPRING = 0.04;
    private static final double NAME_SHARE  = 0.7;
    private static final double BRANCH_CLEAR = 3;
    private static final double BRANCH_PUSH = 0.25;
    private static final double BRANCH_STEP = 5;

    private final ResearchMapPlan.Frame frame;
    private final int      count;
    private final int      origin;
    private final int[]    parent;
    private final int[]    heir;
    private final double[] x;
    private final double[] y;
    private final double[] homeX;
    private final double[] homeY;
    private final double[] moveX;
    private final double[] moveY;
    private final double[] safe;

    private final double   half;
    private final double   apart;
    private final double   clear;

    private final int[][]  members;
    final double[]         nameX;
    final double[]         nameY;
    private final double[] nameHalfW;
    private final double[] nameHalfH;

    private double worst;

    /**
     * @param parent what each research hangs from, or -1 when it grows from the eye
     * @param heir   the branch each research carries straight on into, or -1
     * @param x      where each research starts, moved in place
     */
    ResearchMapSettle(ResearchMapPlan.Frame frame, int[] parent, int[] heir, double[] x, double[] y, int[][] members, double[] nameHalfW, double[] nameHalfH)
    {
        this.frame = frame;
        this.count = parent.length;
        this.origin = count;
        this.parent = parent;
        this.heir = heir;
        this.half = frame.nodeSize() / 2D;
        this.apart = apart(frame);
        this.clear = half + ResearchMapPlan.GAP + 1;

        int total = count + 1 + EYE_POINTS;
        this.x = Arrays.copyOf(x, total);
        this.y = Arrays.copyOf(y, total);
        for (int i = 0; i < EYE_POINTS; i++)
        {
            double angle = Math.PI * 2 * i / EYE_POINTS;
            this.x[origin + 1 + i] = Math.cos(angle) * (frame.eyeHalfWidth() + ResearchMapPlan.GAP);
            this.y[origin + 1 + i] = Math.sin(angle) * (frame.eyeHalfHeight() + ResearchMapPlan.GAP);
        }
        this.homeX = Arrays.copyOf(x, count);
        this.homeY = Arrays.copyOf(y, count);
        this.moveX = new double[count];
        this.moveY = new double[count];
        this.safe = new double[count];

        this.members = members;
        this.nameHalfW = nameHalfW;
        this.nameHalfH = nameHalfH;
        this.nameX = new double[members.length];
        this.nameY = new double[members.length];
        for (int name = 0; name < members.length; name++)
        {
            nameX[name] = middleX(name);
            nameY[name] = middleY(name);
        }
    }

    /** Two researches this far apart never touch, however they sit and once rounded to whole units. */
    static double apart(ResearchMapPlan.Frame frame)
    {
        return frame.nodeSize() * Math.sqrt(2) + 1.5;
    }

    double x(int index)
    {
        return x[index];
    }

    double y(int index)
    {
        return y[index];
    }

    /** Shapes the map with every force while the step shrinks, then only parts what still sits too close, until nothing does or the rounds run out. */
    void run()
    {
        for (int round = 0; round < ROUNDS + FINISH; round++)
        {
            boolean shaping = round < ROUNDS;
            double cap = shaping ? STEP_FIRST * Math.pow(STEP_LAST / STEP_FIRST, round / (double) ROUNDS) : STEP_FINISH;
            Arrays.fill(moveX, 0);
            Arrays.fill(moveY, 0);
            worst = 0;

            if (shaping)
            {
                springs();
                pull();
                bend();
                names();
            }
            else
                taut();
            spread(shaping);
            branches(cap);
            climb();
            clearEye();

            if (!shaping && worst < DONE)
                break;
            if (apply(cap) < STILL && shaping)
                round = ROUNDS - 1;
        }
    }

    private double apply(double cap)
    {
        double most = 0;
        for (int i = 0; i < count; i++)
        {
            double length = Math.hypot(moveX[i], moveY[i]);
            if (length < 1e-9)
                continue;
            double step = Math.min(length, Math.min(cap, safe[i]));
            x[i] += moveX[i] / length * step;
            y[i] += moveY[i] / length * step;
            most = Math.max(most, step);
        }
        return most;
    }

    private int from(int edge)
    {
        return parent[edge] < 0 ? origin : parent[edge];
    }

    /**
     * Goes over every branch and everything near it that is not one of its ends. Works out how far each research may move this round without any research
     * reaching a branch, slides researches off the branches running under them, and keeps branches off the eye.
     */
    private void branches(double cap)
    {
        Arrays.fill(safe, Double.MAX_VALUE);
        double near = Math.max(clear, cap / SAFE_SHARE);
        for (int edge = 0; edge < count; edge++)
        {
            int a = from(edge);
            boolean rooted = a == origin;
            double minX = Math.min(x[a], x[edge]) - near;
            double maxX = Math.max(x[a], x[edge]) + near;
            double minY = Math.min(y[a], y[edge]) - near;
            double maxY = Math.max(y[a], y[edge]) + near;
            int last = rooted ? count : x.length;
            for (int point = 0; point < last; point++)
            {
                if (point == a || point == edge || x[point] < minX || x[point] > maxX || y[point] < minY || y[point] > maxY)
                    continue;
                double t = along(point, a, edge);
                double dx = x[point] - x[a] - (x[edge] - x[a]) * t;
                double dy = y[point] - y[a] - (y[edge] - y[a]) * t;
                double distance = Math.hypot(dx, dy);

                double limit = distance * SAFE_SHARE;
                if (point < count)
                    safe[point] = Math.min(safe[point], limit);
                if (!rooted)
                    safe[a] = Math.min(safe[a], limit);
                safe[edge] = Math.min(safe[edge], limit);

                if (point < count && distance < clear)
                    slideOff(point, a, edge, t, dx, dy, distance);
                else if (point > origin && distance < EYE_CLEAR)
                    keepOffEye(point, a, edge, t, -dx, -dy, distance);
            }
        }
    }

    private void slideOff(int node, int a, int edge, double t, double dx, double dy, double distance)
    {
        worst = Math.max(worst, clear - distance);
        if (distance < 1e-6)
        {
            dx = -(y[edge] - y[a]);
            dy = x[edge] - x[a];
            distance = Math.max(1e-6, Math.hypot(dx, dy));
        }
        double push = (clear - distance) / 2 / distance;
        moveX[node] += dx * push * 1.2;
        moveY[node] += dy * push * 1.2;
        moveX[edge] -= dx * push * 0.8 * t;
        moveY[edge] -= dy * push * 0.8 * t;
        if (a != origin)
        {
            moveX[a] -= dx * push * 0.8 * (1 - t);
            moveY[a] -= dy * push * 0.8 * (1 - t);
        }
    }

    private void keepOffEye(int point, int a, int edge, double t, double dx, double dy, double distance)
    {
        if (distance < 1e-6)
        {
            dx = x[point];
            dy = y[point];
            distance = Math.hypot(dx, dy);
        }
        double push = (EYE_CLEAR - distance) / distance;
        moveX[edge] += dx * push * t;
        moveY[edge] += dy * push * t;
        moveX[a] += dx * push * (1 - t);
        moveY[a] += dy * push * (1 - t);
    }

    private double along(int point, int a, int b)
    {
        double dx = x[b] - x[a];
        double dy = y[b] - y[a];
        double length = dx * dx + dy * dy;
        return length < 1e-12 ? 0 : Math.clamp(((x[point] - x[a]) * dx + (y[point] - y[a]) * dy) / length, 0, 1);
    }

    private void springs()
    {
        for (int i = 0; i < count; i++)
        {
            int p = parent[i];
            if (p < 0)
            {
                double distance = Math.hypot(x[i], y[i]);
                if (distance < 1e-6)
                    continue;
                double pull = (clearRadius(x[i] / distance, y[i] / distance) + ROOT_STUB - distance) * SPRING;
                moveX[i] += x[i] / distance * pull;
                moveY[i] += y[i] / distance * pull;
                // It also keeps to the side of the eye it started on, or the whole map would be free to turn around the eye
                double turn = Math.atan2(x[i] * homeY[i] - y[i] * homeX[i], x[i] * homeX[i] + y[i] * homeY[i]) * distance * HOME;
                moveX[i] -= y[i] / distance * turn;
                moveY[i] += x[i] / distance * turn;
                continue;
            }
            double dx = x[i] - x[p];
            double dy = y[i] - y[p];
            double distance = Math.max(1e-6, Math.hypot(dx, dy));
            double pull = (distance - ResearchMapPlan.REACH) * SPRING / 2 / distance;
            moveX[i] -= dx * pull;
            moveY[i] -= dy * pull;
            moveX[p] += dx * pull;
            moveY[p] += dy * pull;
        }
    }

    /** Draws in each branch stretched past {@link #TAUT} times its length, by a share of how far past it is. */
    private void taut()
    {
        for (int i = 0; i < count; i++)
        {
            int p = parent[i];
            if (p < 0)
                continue;
            double dx = x[i] - x[p];
            double dy = y[i] - y[p];
            double distance = Math.max(1e-6, Math.hypot(dx, dy));
            double over = distance - ResearchMapPlan.REACH * TAUT;
            if (over <= 0)
                continue;
            worst = Math.max(worst, over);
            double pull = over * TAUT_SPRING / 2 / distance;
            moveX[i] -= dx * pull;
            moveY[i] -= dy * pull;
            moveX[p] += dx * pull;
            moveY[p] += dy * pull;
        }
    }

    private void spread(boolean shaping)
    {
        for (int a = 0; a < count; a++)
        {
            for (int b = a + 1; b < count; b++)
            {
                double dx = x[b] - x[a];
                double dy = y[b] - y[a];
                if (dx * dx + dy * dy >= SPREAD * SPREAD)
                    continue;
                double distance = Math.hypot(dx, dy);
                double push = 0;
                if (shaping)
                    push += SOFT * (SPREAD - distance) * (SPREAD - distance) / SPREAD;
                if (distance < apart)
                {
                    push += (apart - distance) / 2;
                    worst = Math.max(worst, apart - distance);
                }
                // Two researches on the same spot part along a line that depends on nothing but which they are
                double nx = distance < 1e-6 ? Math.cos(a + b) : dx / distance;
                double ny = distance < 1e-6 ? Math.sin(a + b) : dy / distance;
                moveX[a] -= nx * push;
                moveY[a] -= ny * push;
                moveX[b] += nx * push;
                moveY[b] += ny * push;
            }
        }
    }

    private void pull()
    {
        for (int i = 0; i < count; i++)
        {
            if (parent[i] < 0)
                continue;
            // Up and down pull harder than left and right, which is what lays the map out wide
            double pullX = x[i] / (WIDE * WIDE) * PULL;
            double pullY = y[i] * PULL;
            double ease = Math.min(1, PULL_MOST / Math.max(1e-9, Math.hypot(pullX, pullY)));
            moveX[i] -= pullX * ease;
            moveY[i] -= pullY * ease;
        }
    }

    /** A branch carries on the way it came, so that a line of researches runs as one sweep rather than a zigzag. */
    private void bend()
    {
        for (int i = 0; i < count; i++)
        {
            int next = heir[i];
            if (next < 0)
                continue;
            int a = from(i);
            double dx = x[i] - x[a];
            double dy = y[i] - y[a];
            double length = Math.hypot(dx, dy);
            if (length < 1e-6)
                continue;
            double offX = (x[i] + dx / length * ResearchMapPlan.REACH - x[next]) * BEND;
            double offY = (y[i] + dy / length * ResearchMapPlan.REACH - y[next]) * BEND;
            moveX[next] += offX;
            moveY[next] += offY;
            moveX[i] -= offX / 2;
            moveY[i] -= offY / 2;
        }
    }

    private double rim(double cos, double sin)
    {
        return 1 / Math.sqrt(sq(cos / frame.eyeHalfWidth()) + sq(sin / frame.eyeHalfHeight()));
    }

    /** How far beyond the eye's rim a point sits. */
    double reach(double px, double py)
    {
        double distance = Math.hypot(px, py);
        return distance < 1e-6 ? -rim(1, 0) : distance - rim(px / distance, py / distance);
    }

    private void climb()
    {
        for (int i = 0; i < count; i++)
        {
            int p = parent[i];
            if (p < 0)
                continue;
            double lack = reach(x[p], y[p]) + CLIMB - reach(x[i], y[i]);
            if (lack <= 0)
                continue;
            worst = Math.max(worst, lack);
            double distance = Math.hypot(x[i], y[i]);
            double above = Math.hypot(x[p], y[p]);
            if (distance > 1e-6)
            {
                moveX[i] += x[i] / distance * lack / 2;
                moveY[i] += y[i] / distance * lack / 2;
            }
            if (above > 1e-6)
            {
                moveX[p] -= x[p] / above * lack / 4;
                moveY[p] -= y[p] / above * lack / 4;
            }
        }
    }

    private void clearEye()
    {
        double reachable = Math.max(frame.eyeHalfWidth(), frame.eyeHalfHeight()) + half * 2 + ResearchMapPlan.GAP;
        for (int i = 0; i < count; i++)
        {
            double distance = Math.hypot(x[i], y[i]);
            if (distance >= reachable)
                continue;
            double cos = distance < 1e-6 ? 0 : x[i] / distance;
            double sin = distance < 1e-6 ? -1 : y[i] / distance;
            double lack = clearRadius(cos, sin) - distance;
            if (lack <= 0)
                continue;
            worst = Math.max(worst, lack);
            moveX[i] += cos * lack;
            moveY[i] += sin * lack;
        }
    }

    /** A research's corners reach further in than its middle, so this is how far out along a line it has to be for no part of it to touch the eye. */
    private double clearRadius(double cos, double sin)
    {
        double low = rim(cos, sin);
        double high = low + half + ResearchMapPlan.GAP;
        while (touchesEye(frame, cos * high, sin * high, half, half))
            high += half;
        for (int step = 0; step < EYE_STEPS; step++)
        {
            double middle = (low + high) / 2;
            if (touchesEye(frame, cos * middle, sin * middle, half, half))
                low = middle;
            else
                high = middle;
        }
        return high + 0.5;
    }

    /** Whether a box this far from the eye's middle reaches into the eye and its insights, with a gap kept around them. */
    static boolean touchesEye(ResearchMapPlan.Frame frame, double dx, double dy, double halfW, double halfH)
    {
        double radiusX = frame.eyeHalfWidth() + ResearchMapPlan.GAP;
        double radiusY = frame.eyeHalfHeight() + ResearchMapPlan.GAP;
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

    private double middleX(int name)
    {
        return Arrays.stream(members[name]).mapToDouble(member -> x[member]).average().orElse(0);
    }

    private double middleY(int name)
    {
        return Arrays.stream(members[name]).mapToDouble(member -> y[member]).average().orElse(0);
    }

    /** A name stays amid its constellation and makes itself room there, the researches and branches around it giving way a little. */
    private void names()
    {
        for (int name = 0; name < nameX.length; name++)
        {
            nameX[name] += (middleX(name) - nameX[name]) * NAME_SPRING;
            nameY[name] += (middleY(name) - nameY[name]) * NAME_SPRING;
        }

        for (int name = 0; name < nameX.length; name++)
        {
            for (int node = 0; node < count; node++)
            {
                double dx = x[node] - nameX[name];
                double dy = y[node] - nameY[name];
                double overX = nameHalfW[name] + half + ResearchMapPlan.GAP - Math.abs(dx);
                double overY = nameHalfH[name] + half + ResearchMapPlan.GAP - Math.abs(dy);
                if (overX <= 0 || overY <= 0)
                    continue;
                if (overX < overY)
                {
                    double push = overX / 2 * (dx < 0 ? -1 : 1);
                    nameX[name] -= push * NAME_SHARE;
                    moveX[node] += push * (1 - NAME_SHARE);
                }
                else
                {
                    double push = overY / 2 * (dy < 0 ? -1 : 1);
                    nameY[name] -= push * NAME_SHARE;
                    moveY[node] += push * (1 - NAME_SHARE);
                }
            }

            for (int other = name + 1; other < nameX.length; other++)
            {
                double dx = nameX[other] - nameX[name];
                double dy = nameY[other] - nameY[name];
                double overX = nameHalfW[name] + nameHalfW[other] + ResearchMapPlan.GAP - Math.abs(dx);
                double overY = nameHalfH[name] + nameHalfH[other] + ResearchMapPlan.GAP - Math.abs(dy);
                if (overX <= 0 || overY <= 0)
                    continue;
                if (overX < overY)
                {
                    nameX[name] -= overX / 2 * (dx < 0 ? -1 : 1);
                    nameX[other] += overX / 2 * (dx < 0 ? -1 : 1);
                }
                else
                {
                    nameY[name] -= overY / 2 * (dy < 0 ? -1 : 1);
                    nameY[other] += overY / 2 * (dy < 0 ? -1 : 1);
                }
            }

            for (int edge = 0; edge < count; edge++)
                clearName(name, edge);

            double overX = frame.eyeHalfWidth() + nameHalfW[name] + ResearchMapPlan.GAP - Math.abs(nameX[name]);
            double overY = frame.eyeHalfHeight() + nameHalfH[name] + ResearchMapPlan.GAP - Math.abs(nameY[name]);
            if (overX > 0 && overY > 0)
            {
                if (overX < overY)
                    nameX[name] += overX * (nameX[name] < 0 ? -1 : 1);
                else
                    nameY[name] += overY * (nameY[name] < 0 ? -1 : 1);
            }
        }
    }

    private void clearName(int name, int edge)
    {
        int a = from(edge);
        double reachX = nameHalfW[name] + BRANCH_CLEAR;
        double reachY = nameHalfH[name] + BRANCH_CLEAR;
        if (Math.min(x[a], x[edge]) > nameX[name] + reachX || Math.max(x[a], x[edge]) < nameX[name] - reachX
                || Math.min(y[a], y[edge]) > nameY[name] + reachY || Math.max(y[a], y[edge]) < nameY[name] - reachY)
            return;
        double length = Math.hypot(x[edge] - x[a], y[edge] - y[a]);
        int pieces = Math.max(1, (int) Math.ceil(length / BRANCH_STEP));
        for (int piece = 0; piece <= pieces; piece++)
        {
            double t = piece / (double) pieces;
            double dx = x[a] + (x[edge] - x[a]) * t - nameX[name];
            double dy = y[a] + (y[edge] - y[a]) * t - nameY[name];
            double overX = nameHalfW[name] + BRANCH_CLEAR - Math.abs(dx);
            double overY = nameHalfH[name] + BRANCH_CLEAR - Math.abs(dy);
            if (overX <= 0 || overY <= 0)
                continue;
            boolean across = overX < overY;
            double push = (across ? overX * (dx < 0 ? -1 : 1) : overY * (dy < 0 ? -1 : 1)) * BRANCH_PUSH;
            if (across)
                nameX[name] -= push * NAME_SHARE;
            else
                nameY[name] -= push * NAME_SHARE;

            double give = push * (1 - NAME_SHARE);
            if (across)
            {
                moveX[edge] += give * t;
                if (a != origin)
                    moveX[a] += give * (1 - t);
            }
            else
            {
                moveY[edge] += give * t;
                if (a != origin)
                    moveY[a] += give * (1 - t);
            }
        }
    }

    private static double sq(double value)
    {
        return value * value;
    }
}
