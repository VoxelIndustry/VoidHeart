package net.voxelindustry.voidheart.common.energy.gnawer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * The box two stakes mark for a Gnawer, both stakes included. It is eaten from the top layer down, each layer row by row, so the hole always has a clear
 * floor. An index walks it in that order.
 */
public record GnawerBox(BlockPos min, BlockPos max)
{
    public static final Codec<GnawerBox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("min").forGetter(GnawerBox::min),
            BlockPos.CODEC.fieldOf("max").forGetter(GnawerBox::max)
    ).apply(instance, GnawerBox::of));

    public static GnawerBox of(BlockPos a, BlockPos b)
    {
        return new GnawerBox(
                new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
    }

    public int width()
    {
        return max.getX() - min.getX() + 1;
    }

    public int depth()
    {
        return max.getZ() - min.getZ() + 1;
    }

    public int height()
    {
        return max.getY() - min.getY() + 1;
    }

    public int layerSize()
    {
        return width() * depth();
    }

    public long volume()
    {
        return (long) layerSize() * height();
    }

    /**
     * The position at an index, from the top layer's first row to the bottom layer's last.
     */
    public BlockPos at(long index)
    {
        var layer = (int) (index / layerSize());
        var inLayer = (int) (index % layerSize());
        return new BlockPos(min.getX() + inLayer % width(), max.getY() - layer, min.getZ() + inLayer / width());
    }

    public boolean contains(BlockPos pos)
    {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /**
     * How far a position is from the box, on its farthest axis, 0 inside it.
     */
    public int distanceTo(BlockPos pos)
    {
        var dx = Math.max(0, Math.max(min.getX() - pos.getX(), pos.getX() - max.getX()));
        var dy = Math.max(0, Math.max(min.getY() - pos.getY(), pos.getY() - max.getY()));
        var dz = Math.max(0, Math.max(min.getZ() - pos.getZ(), pos.getZ() - max.getZ()));
        return Math.max(dx, Math.max(dy, dz));
    }

    /**
     * Why two stakes can't mark a box for a Gnawer at a position, or null when they can.
     */
    public static Refusal check(BlockPos gnawer, BlockPos first, BlockPos second)
    {
        var box = of(first, second);
        if (box.width() > GnawerBalance.MAX_WIDTH || box.depth() > GnawerBalance.MAX_WIDTH)
            return Refusal.TOO_WIDE;
        if (box.distanceTo(gnawer) > GnawerBalance.RANGE)
            return Refusal.TOO_FAR;
        return null;
    }

    public enum Refusal
    {
        TOO_WIDE,
        TOO_FAR
    }
}
