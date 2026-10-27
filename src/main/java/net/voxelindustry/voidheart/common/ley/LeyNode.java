package net.voxelindustry.voidheart.common.ley;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record LeyNode(long id, int x, int z, LeyNodeKind kind, float weight, int lines, float power)
{
    public static final Codec<LeyNode> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(LeyNode::id),
            Codec.INT.fieldOf("x").forGetter(LeyNode::x),
            Codec.INT.fieldOf("z").forGetter(LeyNode::z),
            LeyNodeKind.CODEC.fieldOf("kind").forGetter(LeyNode::kind),
            Codec.FLOAT.fieldOf("weight").forGetter(LeyNode::weight),
            Codec.INT.fieldOf("lines").forGetter(LeyNode::lines),
            Codec.FLOAT.fieldOf("power").forGetter(LeyNode::power)
    ).apply(instance, LeyNode::new));

    public LeyNode withLines(int lines)
    {
        return new LeyNode(id, x, z, kind, weight, lines, LeyLatticeGenerator.power(weight, lines));
    }

    public double distanceSq(double px, double pz)
    {
        double dx = px - x;
        double dz = pz - z;
        return dx * dx + dz * dz;
    }
}
