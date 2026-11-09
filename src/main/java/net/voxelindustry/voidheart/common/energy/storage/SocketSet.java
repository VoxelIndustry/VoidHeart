package net.voxelindustry.voidheart.common.energy.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.Size;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * The bundles set in a construct, by socket index. The core keeps them when it is broken, so they follow it to its
 * next frame; a frame of another size takes them in socket order.
 */
public class SocketSet
{
    public record Entry(int socket, SocketBundle bundle)
    {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("socket").forGetter(Entry::socket),
                SocketBundle.CODEC.fieldOf("bundle").forGetter(Entry::bundle)
        ).apply(instance, Entry::new));
    }

    private final SocketBundle[] bundles = new SocketBundle[ConstructLayout.MAX_SOCKETS];

    public @Nullable SocketBundle get(int socket)
    {
        return socket >= 0 && socket < bundles.length ? bundles[socket] : null;
    }

    public void set(int socket, @Nullable SocketBundle bundle)
    {
        if (socket >= 0 && socket < bundles.length)
            bundles[socket] = bundle;
    }

    public int count()
    {
        var count = 0;
        for (var bundle : bundles)
        {
            if (bundle != null)
                count++;
        }
        return count;
    }

    public int count(boolean rubrum)
    {
        var count = 0;
        for (var bundle : bundles)
        {
            if (bundle != null && bundle.isRubrum() == rubrum)
                count++;
        }
        return count;
    }

    public boolean isEmpty()
    {
        return count() == 0;
    }

    public long capacity()
    {
        return SocketBundle.capacityOf(Arrays.asList(bundles));
    }

    public long rate()
    {
        return SocketBundle.rateOf(Arrays.asList(bundles));
    }

    public boolean fits(Size size)
    {
        return count() <= ConstructLayout.socketCount(size);
    }

    /**
     * Moves bundles sitting past the size's last socket into its free sockets, in order. Does nothing unless they fit.
     */
    public void arrangeFor(Size size)
    {
        var sockets = ConstructLayout.socketCount(size);
        if (!fits(size))
            return;
        for (var index = sockets; index < bundles.length; index++)
        {
            if (bundles[index] == null)
                continue;
            for (var free = 0; free < sockets; free++)
            {
                if (bundles[free] == null)
                {
                    bundles[free] = bundles[index];
                    bundles[index] = null;
                    break;
                }
            }
        }
    }

    /**
     * The socket of this size closest to a point given relative to the core's center, among those the filter accepts, or -1.
     */
    public static int nearest(Size size, double x, double y, double z, IntPredicate filter)
    {
        var sockets = ConstructLayout.sockets(size);
        var best = -1;
        var bestDistance = Double.MAX_VALUE;
        for (var index = 0; index < sockets.size(); index++)
        {
            if (!filter.test(index))
                continue;
            var socket = sockets.get(index);
            var dx = socket.getX() - x;
            var dy = socket.getY() - y;
            var dz = socket.getZ() - z;
            var distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    public List<Entry> entries()
    {
        var out = new ArrayList<Entry>();
        for (var index = 0; index < bundles.length; index++)
        {
            if (bundles[index] != null)
                out.add(new Entry(index, bundles[index]));
        }
        return out;
    }

    public void load(List<Entry> entries)
    {
        Arrays.fill(bundles, null);
        for (var entry : entries)
            set(entry.socket(), entry.bundle());
    }
}
