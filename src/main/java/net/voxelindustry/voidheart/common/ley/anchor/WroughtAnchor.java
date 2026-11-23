package net.voxelindustry.voidheart.common.ley.anchor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * An anchor a player built beside a ley line. Its id is negative so it never meets an Old Anchor's node id. {@code y} is the block's own height.
 */
public record WroughtAnchor(long id, String name, int x, int y, int z)
{
    public static final Codec<WroughtAnchor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(WroughtAnchor::id),
            Codec.STRING.fieldOf("name").forGetter(WroughtAnchor::name),
            Codec.INT.fieldOf("x").forGetter(WroughtAnchor::x),
            Codec.INT.fieldOf("y").forGetter(WroughtAnchor::y),
            Codec.INT.fieldOf("z").forGetter(WroughtAnchor::z)
    ).apply(instance, WroughtAnchor::new));

    public static boolean isWrought(long id)
    {
        return id < 0;
    }

    public BlockPos pos()
    {
        return new BlockPos(x, y, z);
    }

    public WroughtAnchor named(String name)
    {
        return new WroughtAnchor(id, name, x, y, z);
    }

    public double distanceTo(int otherX, int otherZ)
    {
        return Math.hypot(otherX - x, otherZ - z);
    }

    public AnchorView view()
    {
        return new AnchorView(id, name, x, y, z, true, true);
    }
}
