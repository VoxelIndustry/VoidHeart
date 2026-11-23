package net.voxelindustry.voidheart.common.content.divining;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.ley.LeyNodeKind;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What a Ley Divining Rod reveals around where it was raised: the lines near it with the flow they carry, the nodes among them, and the spurs standing
 * stones bend off them. Empty where no lattice runs, outside the overworld.
 */
public record LeySightPayload(double originX, double originY, double originZ, List<Line> lines, List<Node> nodes, List<Spur> spurs)
        implements CustomPacketPayload
{
    public static final Type<LeySightPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "ley_sight"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LeySightPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, LeySightPayload::originX,
            ByteBufCodecs.DOUBLE, LeySightPayload::originY,
            ByteBufCodecs.DOUBLE, LeySightPayload::originZ,
            Line.CODEC.apply(ByteBufCodecs.list()), LeySightPayload::lines,
            Node.CODEC.apply(ByteBufCodecs.list()), LeySightPayload::nodes,
            Spur.CODEC.apply(ByteBufCodecs.list()), LeySightPayload::spurs,
            LeySightPayload::new);

    @Override
    public Type<LeySightPayload> type()
    {
        return TYPE;
    }

    /**
     * Flows from the from point to the to point, heights sampled evenly between them. The taints are those of its two nodes, from 0 to 1.
     */
    public record Line(int fromX, int fromZ, int toX, int toZ, List<Float> heights, float flow, float fromTaint, float toTaint)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, Line> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Line::fromX,
                ByteBufCodecs.VAR_INT, Line::fromZ,
                ByteBufCodecs.VAR_INT, Line::toX,
                ByteBufCodecs.VAR_INT, Line::toZ,
                ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), Line::heights,
                ByteBufCodecs.FLOAT, Line::flow,
                ByteBufCodecs.FLOAT, Line::fromTaint,
                ByteBufCodecs.FLOAT, Line::toTaint,
                Line::new);
    }

    public record Node(int x, int y, int z, LeyNodeKind kind, float flow, float taint)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, Node> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Node::x,
                ByteBufCodecs.VAR_INT, Node::y,
                ByteBufCodecs.VAR_INT, Node::z,
                ByteBufCodecs.idMapper(index -> LeyNodeKind.values()[index], LeyNodeKind::ordinal), Node::kind,
                ByteBufCodecs.FLOAT, Node::flow,
                ByteBufCodecs.FLOAT, Node::taint,
                Node::new);
    }

    /**
     * One reach of a spur, flowing from where it leaves its line, or from the stone before, to a stone. The flow and taint are those of the line it taps.
     */
    public record Spur(float fromX, float fromY, float fromZ, float toX, float toY, float toZ, float flow, float taint)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, Spur> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Spur::fromX,
                ByteBufCodecs.FLOAT, Spur::fromY,
                ByteBufCodecs.FLOAT, Spur::fromZ,
                ByteBufCodecs.FLOAT, Spur::toX,
                ByteBufCodecs.FLOAT, Spur::toY,
                ByteBufCodecs.FLOAT, Spur::toZ,
                ByteBufCodecs.FLOAT, Spur::flow,
                ByteBufCodecs.FLOAT, Spur::taint,
                Spur::new);
    }
}
