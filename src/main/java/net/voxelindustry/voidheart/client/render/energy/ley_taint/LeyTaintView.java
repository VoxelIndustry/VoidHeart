package net.voxelindustry.voidheart.client.render.energy.ley_taint;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.voxelindustry.voidheart.common.ley.TaintStage;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaintMath;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaintPayload;

import java.util.Collection;
import java.util.List;

/**
 * The client's copy of the tainted nodes, each easing its look from one taint step to the next over
 * {@link LeyTaintLook#EASE_TICKS}. A node that heals clean fades out before it is forgotten.
 */
public final class LeyTaintView
{
    public static final class Node
    {
        private final long id;
        private final int x;
        private final int z;
        private int anchorY;
        private List<LeyTaintPayload.Spoke> spokes;
        private float taint;
        private float steppedTaint = Float.NaN;
        private LeyTaintLook.Shape from = LeyTaintLook.Shape.NONE;
        private LeyTaintLook.Shape to = LeyTaintLook.Shape.NONE;
        private long changedAt;
        private final AnchorFlicker flicker;

        private Node(long id, int x, int z)
        {
            this.id = id;
            this.x = x;
            this.z = z;
            flicker = new AnchorFlicker(id);
        }

        public long id()
        {
            return id;
        }

        public int x()
        {
            return x;
        }

        public int z()
        {
            return z;
        }

        public int anchorY()
        {
            return anchorY;
        }

        public List<LeyTaintPayload.Spoke> spokes()
        {
            return spokes;
        }

        /**
         * The latest taint the server sent.
         */
        public float taint()
        {
            return taint;
        }

        public TaintStage stage()
        {
            return LeyTaintMath.stage(taint);
        }

        /**
         * The taint step the veins are drawn at, which changes far less often than the taint.
         */
        public float steppedTaint()
        {
            return steppedTaint;
        }

        public LeyTaintLook.Shape shape(double time)
        {
            float t = (float) Math.clamp((time - changedAt) / LeyTaintLook.EASE_TICKS, 0, 1);
            return from.lerp(to, t * t * (3 - 2 * t));
        }

        public AnchorFlicker flicker()
        {
            return flicker;
        }

        private boolean faded(long tick)
        {
            return to.isNone() && tick - changedAt > LeyTaintLook.EASE_TICKS;
        }

        private void update(LeyTaintPayload.Node sent, long tick)
        {
            anchorY = sent.anchorY();
            spokes = sent.spokes();
            setTaint(sent.taint(), tick);
        }

        private void setTaint(float value, long tick)
        {
            taint = value;
            float stepped = LeyTaintLook.stepped(value);
            if (stepped == steppedTaint && LeyTaintMath.stage(value) == LeyTaintMath.stage(steppedTaint))
                return;
            from = shape(tick);
            to = LeyTaintLook.shape(value);
            changedAt = tick;
            steppedTaint = stepped;
        }
    }

    private static final Long2ObjectLinkedOpenHashMap<Node> NODES = new Long2ObjectLinkedOpenHashMap<>();
    private static long tick;
    private static int version;

    private LeyTaintView()
    {
    }

    public static void receive(LeyTaintPayload payload)
    {
        var sent = new LongOpenHashSet();
        for (var node : payload.nodes())
        {
            sent.add(node.id());
            NODES.computeIfAbsent(node.id(), id -> new Node(id, node.x(), node.z())).update(node, tick);
        }
        for (var node : NODES.values())
        {
            if (!sent.contains(node.id()))
                node.setTaint(0, tick);
        }
        version++;
    }

    public static void tick()
    {
        tick++;
        NODES.values().removeIf(node -> node.faded(tick));
        for (var node : NODES.values())
            node.flicker().tick(tick, node.stage());
    }

    /**
     * Ticks counted by this view, the clock its easing runs on.
     */
    public static long ticks()
    {
        return tick;
    }

    /**
     * Grows each time the server sends a new set.
     */
    public static int version()
    {
        return version;
    }

    public static Collection<Node> nodes()
    {
        return NODES.values();
    }

    public static boolean isEmpty()
    {
        return NODES.isEmpty();
    }

    /**
     * The tainted node at these coordinates, if any.
     */
    public static Node at(int x, int z)
    {
        for (var node : NODES.values())
        {
            if (node.x == x && node.z == z)
                return node;
        }
        return null;
    }

    public static void clear()
    {
        NODES.clear();
    }
}
