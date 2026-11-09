package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.FramePart;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The boxes a formed frame block is drawn and shaped with, in pixels: a beam hugging the cube's outer edge, or at a
 * corner a gold cap with a stub toward each of the three edges meeting there. Shared by the block's shape, its
 * generated models and the veins the construct lights on its pillars.
 */
public final class FrameGeometry
{
    /** How thick a beam is, from the cube's outer edge inward. */
    public static final int BEAM = 10;
    /** A cap stands a pixel proud of the beams meeting under it. */
    public static final int CAP  = 11;

    public enum Skin
    {
        /** Voidstone with the rubrum vein running down its middle. */
        PILLAR,
        BEAM,
        GOLD
    }

    /**
     * @param runs the axis a beam or stub runs along, whose end faces are hidden against the next piece; null for a cap
     */
    public record Box(int x0, int y0, int z0, int x1, int y1, int z1, Skin skin, Direction.@Nullable Axis runs)
    {
        public int min(Direction.Axis axis)
        {
            return axis.choose(x0, y0, z0);
        }

        public int max(Direction.Axis axis)
        {
            return axis.choose(x1, y1, z1);
        }
    }

    private FrameGeometry()
    {
    }

    private static int low(int sign, int size)
    {
        return sign > 0 ? 16 - size : 0;
    }

    private static int high(int sign, int size)
    {
        return sign < 0 ? size : 16;
    }

    public static List<Box> boxes(FramePart part)
    {
        var out = new ArrayList<Box>();
        if (part == FramePart.LOOSE)
        {
            out.add(new Box(0, 0, 0, 16, 16, 16, Skin.BEAM, null));
            return out;
        }

        if (!part.isCorner())
        {
            var runs = part.x() == 0 ? Direction.Axis.X : part.y() == 0 ? Direction.Axis.Y : Direction.Axis.Z;
            out.add(new Box(low(part.x(), BEAM), low(part.y(), BEAM), low(part.z(), BEAM),
                    high(part.x(), BEAM), high(part.y(), BEAM), high(part.z(), BEAM), part.isPillar() ? Skin.PILLAR : Skin.BEAM, runs));
            return out;
        }

        out.add(new Box(low(part.x(), CAP), low(part.y(), CAP), low(part.z(), CAP),
                high(part.x(), CAP), high(part.y(), CAP), high(part.z(), CAP), Skin.GOLD, null));
        for (var axis : Direction.Axis.values())
        {
            int[] from = new int[3], to = new int[3];
            int[] signs = {part.x(), part.y(), part.z()};
            for (var other : Direction.Axis.values())
            {
                var index = other.ordinal();
                if (other == axis)
                {
                    from[index] = signs[index] < 0 ? CAP : 0;
                    to[index] = signs[index] < 0 ? 16 : 16 - CAP;
                }
                else
                {
                    from[index] = low(signs[index], BEAM);
                    to[index] = high(signs[index], BEAM);
                }
            }
            out.add(new Box(from[0], from[1], from[2], to[0], to[1], to[2], axis == Direction.Axis.Y ? Skin.PILLAR : Skin.BEAM, axis));
        }
        return out;
    }

    /**
     * Where the vein runs along a pillar's outer face, in blocks from the pillar's own side of the block: the middle of the beam.
     */
    public static float veinCenter(int sign)
    {
        return sign < 0 ? BEAM / 32F : 1 - BEAM / 32F;
    }
}
