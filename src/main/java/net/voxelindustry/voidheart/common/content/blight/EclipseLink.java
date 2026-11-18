package net.voxelindustry.voidheart.common.content.blight;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The mobs one crossbow wielder's bolts have stitched together, in the order they were struck, each held to the one struck before it. Damage any of
 * them takes is shared out to the rest. Kept on the wielder and synced, so clients draw the threads between them.
 *
 * @param members entity ids, oldest first
 * @param until   the game time it comes apart unless struck again
 * @param shares  the last times damage ran out from a member, newest last, for clients to draw it running along the threads
 */
public record EclipseLink(List<Integer> members, long until, List<Share> shares)
{
    public static final EclipseLink NONE = new EclipseLink(List.of(), 0, List.of());

    /** How many shares are kept for clients to draw: as many as can still be running along a thread. */
    private static final int SHARES_KEPT = 6;

    /** Damage running out of a member at a game time. */
    public record Share(int from, long at)
    {
        public static final StreamCodec<ByteBuf, Share> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Share::from,
                ByteBufCodecs.VAR_LONG, Share::at,
                Share::new);
    }

    public static final StreamCodec<ByteBuf, EclipseLink> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(BlightBalance.LINK_MAX)), EclipseLink::members,
            ByteBufCodecs.VAR_LONG, EclipseLink::until,
            Share.STREAM_CODEC.apply(ByteBufCodecs.list(SHARES_KEPT)), EclipseLink::shares,
            EclipseLink::new);

    public EclipseLink
    {
        members = List.copyOf(members);
        shares = List.copyOf(shares);
    }

    /**
     * A bolt struck a mob: it joins, held to the one struck before it, and the oldest lets go past the most a link holds. Striking one already in it only
     * keeps it together longer. A link left to come apart starts afresh.
     */
    public EclipseLink struck(int target, long now)
    {
        var kept = new ArrayList<>(isOver(now) ? List.<Integer>of() : members);
        if (!kept.contains(target))
            kept.add(target);
        while (kept.size() > BlightBalance.LINK_MAX)
            kept.removeFirst();
        return new EclipseLink(kept, now + BlightBalance.LINK_TICKS, isOver(now) ? List.of() : shares);
    }

    public EclipseLink without(int member)
    {
        if (!members.contains(member))
            return this;
        var kept = new ArrayList<>(members);
        kept.remove(Integer.valueOf(member));
        return new EclipseLink(kept, until, shares);
    }

    /** Damage ran out of a member now. */
    public EclipseLink shared(int from, long now)
    {
        var kept = new ArrayList<>(shares);
        kept.add(new Share(from, now));
        while (kept.size() > SHARES_KEPT)
            kept.removeFirst();
        return new EclipseLink(members, until, kept);
    }

    public boolean isOver(long now)
    {
        return now >= until || members.isEmpty();
    }

    /** Whether damage passes along it: a single mob struck is only waiting for a second. */
    public boolean holds(long now)
    {
        return !isOver(now) && members.size() >= 2;
    }

    public boolean contains(int member)
    {
        return members.contains(member);
    }

    /** The members a member's damage is shared out to. */
    public List<Integer> othersThan(int member)
    {
        return members.stream().filter(other -> other != member).toList();
    }

    /** What each other member takes when one takes this much, never shared again. */
    public static float shareOf(float taken)
    {
        return taken * BlightBalance.LINK_SHARE;
    }

    /** Whether any two members stand too far apart for the link to hold. */
    public static boolean isStretched(List<Vec3> places)
    {
        for (int i = 0; i < places.size(); i++)
            for (int j = i + 1; j < places.size(); j++)
                if (places.get(i).distanceToSqr(places.get(j)) > BlightBalance.LINK_BREAK * BlightBalance.LINK_BREAK)
                    return true;
        return false;
    }
}
