package net.voxelindustry.voidheart.common.ley.anchor;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a client needs to know about an anchor to show it. {@code y} is the anchor's lower block. Wrought anchors are the ones players build, with
 * negative ids; Old Anchors carry their node's id.
 */
public record AnchorView(long id, String name, int x, int y, int z, boolean active, boolean wrought)
{
    public static final StreamCodec<RegistryFriendlyByteBuf, AnchorView> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, AnchorView::id,
            ByteBufCodecs.STRING_UTF8, AnchorView::name,
            ByteBufCodecs.VAR_INT, AnchorView::x,
            ByteBufCodecs.VAR_INT, AnchorView::y,
            ByteBufCodecs.VAR_INT, AnchorView::z,
            ByteBufCodecs.BOOL, AnchorView::active,
            ByteBufCodecs.BOOL, AnchorView::wrought,
            AnchorView::new);
}
