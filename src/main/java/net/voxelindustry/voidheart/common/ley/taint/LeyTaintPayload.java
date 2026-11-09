package net.voxelindustry.voidheart.common.ley.taint;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Every tainted node of the overworld, for the veins on the ground, the Oculus ley layer and the Old Anchor flicker.
 * Tainted nodes are few, so the whole set is sent whenever one of them changes by a step.
 */
public record LeyTaintPayload(List<Node> nodes) implements CustomPacketPayload
{
    public static final int NO_ANCHOR = Integer.MIN_VALUE;

    /**
     * A line leaving the node, to the node at its other end.
     */
    public record Spoke(int x, int z, boolean downstream)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, Spoke> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Spoke::x,
                ByteBufCodecs.VAR_INT, Spoke::z,
                ByteBufCodecs.BOOL, Spoke::downstream,
                Spoke::new);
    }

    /**
     * @param anchorY the lower block of the Old Anchor built on the node, or {@link #NO_ANCHOR}
     */
    public record Node(long id, int x, int z, float taint, int anchorY, List<Spoke> spokes)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, Node> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Node::id,
                ByteBufCodecs.VAR_INT, Node::x,
                ByteBufCodecs.VAR_INT, Node::z,
                ByteBufCodecs.FLOAT, Node::taint,
                ByteBufCodecs.INT, Node::anchorY,
                Spoke.CODEC.apply(ByteBufCodecs.list()), Node::spokes,
                Node::new);
    }

    public static final Type<LeyTaintPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "ley_taint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LeyTaintPayload> CODEC = StreamCodec.composite(
            Node.CODEC.apply(ByteBufCodecs.list()), LeyTaintPayload::nodes,
            LeyTaintPayload::new);

    @Override
    public Type<LeyTaintPayload> type()
    {
        return TYPE;
    }
}
