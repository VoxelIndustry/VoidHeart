package net.voxelindustry.voidheart.common.content.research;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Research delving too deep into the void weighs on whoever runs it. A player bears what their attunement allows, and Meat Proxies bear the rest
 * while they last, each wearing only for the time it is needed.
 */
public final class Strain
{
    private static final int[] TOLERANCE = { 0, 10, 25, 45 };

    public static final int MAX_ATTUNEMENT = TOLERANCE.length - 1;

    private static final double WORN = 1E-6;

    private Strain()
    {
    }

    /** What a player bears unaided: nothing before the void heart, then more with each graft. */
    public static int tolerance(int attunement)
    {
        return TOLERANCE[Math.clamp(attunement, 0, MAX_ATTUNEMENT)];
    }

    public static int proxiesNeeded(int strain, int tolerance, int perProxy)
    {
        if (strain <= tolerance)
            return 0;
        return perProxy <= 0 ? Integer.MAX_VALUE : Math.ceilDiv(strain - tolerance, perProxy);
    }

    public static int alive(Map<UUID, Double> proxies)
    {
        return (int) proxies.values().stream().filter(left -> left > WORN).count();
    }

    /**
     * @param seconds the research covered, less than asked when the proxies gave out first
     * @param proxies what each proxy has left afterward, worn out ones at 0
     */
    public record Cover(double seconds, Map<UUID, Double> proxies)
    {
    }

    /**
     * Needed proxies are taken most worn first and a fresh one steps in whenever one gives out, so they break one at a time rather than all at the end.
     */
    public static Cover cover(Map<UUID, Double> proxies, int needed, double seconds)
    {
        if (needed <= 0)
            return new Cover(seconds, proxies);

        var queue = new ArrayDeque<UUID>(proxies.entrySet().stream()
                .filter(entry -> entry.getValue() > WORN)
                .sorted(Map.Entry.<UUID, Double>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList());
        if (queue.size() < needed)
            return new Cover(0, proxies);

        var left = new HashMap<>(proxies);
        List<UUID> bearing = new ArrayList<>();
        for (int slot = 0; slot < needed; slot++)
            bearing.add(queue.poll());

        double elapsed = 0;
        while (elapsed < seconds)
        {
            double step = Math.min(seconds - elapsed, bearing.stream().mapToDouble(left::get).min().orElseThrow());
            for (var id : bearing)
                left.put(id, Math.max(0, left.get(id) - step));
            elapsed += step;

            for (int slot = 0; slot < bearing.size(); slot++)
            {
                if (left.get(bearing.get(slot)) > WORN)
                    continue;
                left.put(bearing.get(slot), 0D);
                if (queue.isEmpty())
                    return new Cover(elapsed, left);
                bearing.set(slot, queue.poll());
            }
        }
        return new Cover(seconds, left);
    }

    /** How long the proxies can carry this much missing strain, infinite when none is missing. */
    public static double secondsCovered(Map<UUID, Double> proxies, int needed)
    {
        if (needed <= 0)
            return Double.POSITIVE_INFINITY;
        return cover(proxies, needed, proxies.values().stream().mapToDouble(Double::doubleValue).sum()).seconds();
    }
}
