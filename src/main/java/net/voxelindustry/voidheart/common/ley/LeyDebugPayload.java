package net.voxelindustry.voidheart.common.ley;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Ley lines and nodes around a player with the debug view on. An empty payload turns the view off.
 */
public record LeyDebugPayload(List<Line> lines, List<Node> nodes) implements CustomPacketPayload
{
    public static final Type<LeyDebugPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "ley_debug"));

    public static final LeyDebugPayload CLEAR = new LeyDebugPayload(List.of(), List.of());

    public static final StreamCodec<RegistryFriendlyByteBuf, LeyDebugPayload> CODEC = StreamCodec.composite(
            Line.CODEC.apply(ByteBufCodecs.list()), LeyDebugPayload::lines,
            Node.CODEC.apply(ByteBufCodecs.list()), LeyDebugPayload::nodes,
            LeyDebugPayload::new);

    @Override
    public Type<LeyDebugPayload> type()
    {
        return TYPE;
    }

    /**
     * Flows from the from point to the to point, heights sampled evenly between them.
     */
    public record Line(int fromX, int fromZ, int toX, int toZ, List<Float> heights)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, Line> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Line::fromX,
                ByteBufCodecs.VAR_INT, Line::fromZ,
                ByteBufCodecs.VAR_INT, Line::toX,
                ByteBufCodecs.VAR_INT, Line::toZ,
                ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), Line::heights,
                Line::new);
    }

    public record Node(int x, int y, int z, LeyNodeKind kind, int lines, float power)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, Node> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Node::x,
                ByteBufCodecs.VAR_INT, Node::y,
                ByteBufCodecs.VAR_INT, Node::z,
                ByteBufCodecs.idMapper(index -> LeyNodeKind.values()[index], LeyNodeKind::ordinal), Node::kind,
                ByteBufCodecs.VAR_INT, Node::lines,
                ByteBufCodecs.FLOAT, Node::power,
                Node::new);
    }
}
