package net.voxelindustry.voidheart.common.ley;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

import static net.voxelindustry.voidheart.common.ley.LeyLatticeGenerator.REGION_SIZE;

/**
 * Nodes positioned inside the region and every edge touching one of them.
 * Edges crossing a region border are stored by both regions.
 */
public record LeyRegion(int regionX, int regionZ, List<LeyNode> nodes, List<LeyEdge> edges)
{
    public static final Codec<LeyRegion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(LeyRegion::regionX),
            Codec.INT.fieldOf("z").forGetter(LeyRegion::regionZ),
            LeyNode.CODEC.listOf().fieldOf("nodes").forGetter(LeyRegion::nodes),
            LeyEdge.CODEC.listOf().fieldOf("edges").forGetter(LeyRegion::edges)
    ).apply(instance, LeyRegion::new));

    public long key()
    {
        return key(regionX, regionZ);
    }

    public boolean contains(int x, int z)
    {
        return Math.floorDiv(x, REGION_SIZE) == regionX && Math.floorDiv(z, REGION_SIZE) == regionZ;
    }

    public static long key(int regionX, int regionZ)
    {
        return (long) regionX << 32 | regionZ & 0xFFFFFFFFL;
    }
}
