package net.voxelindustry.voidheart.common.ley.flow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who draws on each pool of ley, and what each is granted. A pool is a node or one of its downstream lines.
 * Claims upstream are served first: a claimant gets what is left after every claimant before it along the flow, by their latest
 * grants. Claimants at the same point are served by seniority, so a newcomer never takes from one already drawing.
 * Claims are made in block entity order, so a claimant sees the grants of those before it from this tick or the last;
 * the shares settle within a tick or two. Pure, so it is unit tested.
 */
public final class LeyClaimLedger
{
    /**
     * @param position along the line from its upstream node, 0 to 1, or 0 on a node
     * @param since    the tick it first claimed, kept while it keeps claiming
     */
    public record Claim(long claimant, double position, long since, double requested, double granted, long tick)
    {
    }

    private static final Comparator<Claim> FLOW_ORDER = Comparator.comparingDouble(Claim::position)
            .thenComparingLong(Claim::since)
            .thenComparingLong(Claim::claimant);

    private final Map<Object, Map<Long, Claim>> pools = new HashMap<>();
    private final int holdTicks;

    public LeyClaimLedger(int holdTicks)
    {
        this.holdTicks = holdTicks;
    }

    /**
     * @return what was granted, between 0 and the amount asked
     */
    public double claim(Object pool, long claimant, double position, double capacity, double amount, long tick)
    {
        var claims = pools.computeIfAbsent(pool, key -> new LinkedHashMap<>());
        var previous = claims.get(claimant);
        long since = previous != null && isActive(previous, tick) ? previous.since() : tick;
        var self = new Claim(claimant, position, since, amount, 0, tick);

        double usedBefore = 0;
        for (var other : claims.values())
        {
            if (other.claimant() != claimant && isActive(other, tick) && FLOW_ORDER.compare(other, self) < 0)
                usedBefore += other.granted();
        }

        double granted = amount > 0 ? Math.clamp(capacity - usedBefore, 0, amount) : 0;
        claims.put(claimant, new Claim(claimant, position, since, amount, granted, tick));
        return granted;
    }

    /**
     * Everything granted in the pool to claimants still drawing.
     */
    public double granted(Object pool, long tick)
    {
        var claims = pools.get(pool);
        if (claims == null)
            return 0;
        double total = 0;
        for (var claim : claims.values())
        {
            if (isActive(claim, tick))
                total += claim.granted();
        }
        return total;
    }

    /**
     * Claimants still drawing on the pool, upstream first.
     */
    public List<Claim> claims(Object pool, long tick)
    {
        var claims = pools.get(pool);
        if (claims == null)
            return List.of();
        var active = new ArrayList<Claim>();
        for (var claim : claims.values())
        {
            if (isActive(claim, tick))
                active.add(claim);
        }
        active.sort(FLOW_ORDER);
        return active;
    }

    /**
     * Forgets claimants that stopped drawing, and pools left empty.
     */
    public void prune(long tick)
    {
        pools.values().forEach(claims -> claims.values().removeIf(claim -> !isActive(claim, tick)));
        pools.values().removeIf(Map::isEmpty);
    }

    public void clear()
    {
        pools.clear();
    }

    private boolean isActive(Claim claim, long tick)
    {
        return tick - claim.tick() <= holdTicks;
    }
}
