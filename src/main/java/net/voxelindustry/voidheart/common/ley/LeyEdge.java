package net.voxelindustry.voidheart.common.ley;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A ley line segment between two nodes. Power flows from the {@code from} node to the {@code to} node.
 */
public record LeyEdge(long from, long to, int fromX, int fromZ, int toX, int toZ)
{
    public static final Codec<LeyEdge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("from").forGetter(LeyEdge::from),
            Codec.LONG.fieldOf("to").forGetter(LeyEdge::to),
            Codec.INT.fieldOf("fx").forGetter(LeyEdge::fromX),
            Codec.INT.fieldOf("fz").forGetter(LeyEdge::fromZ),
            Codec.INT.fieldOf("tx").forGetter(LeyEdge::toX),
            Codec.INT.fieldOf("tz").forGetter(LeyEdge::toZ)
    ).apply(instance, LeyEdge::new));

    public boolean touches(long nodeId)
    {
        return from == nodeId || to == nodeId;
    }

    public long other(long nodeId)
    {
        return from == nodeId ? to : from;
    }

    public double length()
    {
        return Math.hypot(toX - fromX, toZ - fromZ);
    }

    public double distanceSq(double px, double pz)
    {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double t = ((px - fromX) * dx + (pz - fromZ) * dz) / (dx * dx + dz * dz);
        t = Math.clamp(t, 0, 1);

        double cx = fromX + dx * t - px;
        double cz = fromZ + dz * t - pz;
        return cx * cx + cz * cz;
    }
}
