package net.voxelindustry.voidheart.common.content.altar;

import com.mojang.math.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

/**
 * The curving streams of items and experience drawn from pillars and skulls into an altar.
 */
public final class AltarStreams
{
    private AltarStreams()
    {
    }

    /**
     * Where the item on an altar floats, which the streams flow into.
     */
    public static Vector3f altarTop(BlockPos altar)
    {
        return new Vector3f(altar.getX() + 0.5F, altar.getY() + 1 + 4 / 16F, altar.getZ() + 0.5F);
    }

    /**
     * @return both inner control points of a stream from the given start to the given end, swung to a random side
     */
    public static Vector3f[] controlPoints(RandomSource random, Vector3f start, Vector3f end)
    {
        Vector3f bezierFirstPoint;
        Vector3f bezierSecondPoint;

        var forward = new Vector3f(end).sub(start);
        float length = forward.length();
        forward = forward.normalize();

        float dispersion = random.nextBoolean() ? random.nextFloat() * 50 + 20 : -(random.nextFloat() * 50 + 20);
        var rotation = Axis.YP.rotationDegrees(dispersion);

        bezierFirstPoint = start.add(forward.rotate(rotation).mul(length * (random.nextFloat() / 3 + 0.2F)));

        rotation.conjugate();
        bezierSecondPoint = start.add(forward.rotate(rotation).mul(length * (random.nextFloat() / 3 + 0.5F)));

        if (random.nextBoolean())
        {
            bezierFirstPoint = bezierFirstPoint.add(0, random.nextFloat() * 1.25F, 0);
            bezierFirstPoint = bezierSecondPoint.add(0, -random.nextFloat() * 1.25F, 0);
        }
        else
        {
            bezierFirstPoint = bezierFirstPoint.add(0, -random.nextFloat() * 1.25F, 0);
            bezierFirstPoint = bezierSecondPoint.add(0, random.nextFloat() * 1.25F, 0);
        }
        return new Vector3f[]{bezierFirstPoint, bezierSecondPoint};
    }
}
