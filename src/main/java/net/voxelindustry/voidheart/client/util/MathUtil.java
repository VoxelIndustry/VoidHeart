package net.voxelindustry.voidheart.client.util;

import lombok.experimental.UtilityClass;

import static java.lang.Math.pow;

@UtilityClass
public class MathUtil
{
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
