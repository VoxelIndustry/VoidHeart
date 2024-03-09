package net.voxelindustry.voidheart.client.util;

import lombok.experimental.UtilityClass;
import org.joml.AxisAngle4f;
import org.joml.Math;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import static java.lang.Math.pow;

@UtilityClass
public class MathUtil
{
    public final Vector3fc ZERO = new Vector3f(0);

    public final Vector3fc NEGATIVE_X = new Vector3f(-1, 0, 0);
    public final Vector3fc NEGATIVE_Y = new Vector3f(0, -1, 0);
    public final Vector3fc NEGATIVE_Z = new Vector3f(0, 0, -1);

    public final Vector3fc POSITIVE_X = new Vector3f(1, 0, 0);
    public final Vector3fc POSITIVE_Y = new Vector3f(0, 1, 0);
    public final Vector3fc POSITIVE_Z = new Vector3f(0, 0, 1);

    public Quaternionf quatFromAngleDegrees(float angleDegrees, Vector3fc vector)
    {
        return new Quaternionf(new AxisAngle4f(Math.toRadians(angleDegrees), vector));
    }

    public float interpolateBounce(float delta)
    {
        if (delta > 1)
            return 1;

        float a = 0;
        float b = 1;
        while (!(delta >= (7 - 4 * a) / 11D))
        {
            a += b;
            b /= 2;
        }
        return (float) (-pow((11 - 6 * a - 11 * delta) / 4, 2) + pow(b, 2));
    }
}
