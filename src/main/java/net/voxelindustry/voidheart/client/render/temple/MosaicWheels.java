package net.voxelindustry.voidheart.client.render.temple;

/**
 * The wheels within wheels around the angel's great eye. Each painted ellipse is a circle seen at a slant, so each wheel is drawn as that circle: while the
 * mosaic stirs it lies flattened onto its painted ellipse a pixel off the wall, turning so its eyes travel round it. When it reaches, the hub comes out of the
 * wall with the great eye, each wheel rights itself into its full circle and tumbles about its own axis, one against the next.
 * <p>
 * Places are in the picture's pixels: {@code u} rightward and {@code v} downward from its top left, and {@code h} out of the wall.
 */
public final class MosaicWheels
{
    static final int SEGMENTS = 48;
    /** How far the hub stands off the wall while stirring, and out of it at full reach, past the widest wheel so none turns into the wall. */
    static final float HUB_STIR = 1F;
    static final float HUB_OUT = 26F;
    /** How fast and which way each wheel turns and tumbles, against the others. */
    private static final float[] SPIN = {1F, -1.3F, 0.8F};
    private static final float[] TUMBLE = {1F, -0.85F, 1.2F};

    /**
     * @param rings each wheel's points round it, as {@code u, v, h}
     * @param eyes  each wheel's eyes, as {@code u, v, h}
     * @param hub   where the great eye is
     */
    public record Shape(float[][][] rings, float[][][] eyes, float[] hub)
    {
    }

    private MosaicWheels()
    {
    }

    /** @return how fast and which way a wheel turns, against the first */
    static float spinPace(int wheel)
    {
        return SPIN[wheel % SPIN.length];
    }

    /** @return how fast and which way a wheel tumbles, against the first */
    static float tumblePace(int wheel)
    {
        return TUMBLE[wheel % TUMBLE.length];
    }

    /**
     * @param spin   each wheel's turn within the wall, in radians, as {@link MosaicMotion} adds it up
     * @param tumble each wheel's tumble about its own axis, the same way
     */
    public static Shape shape(MosaicArt art, float stir, float reach, float[] spin, float[] tumble)
    {
        var wheels = art.wheels();
        float hubU = art.hubX() + 0.5F;
        float hubV = art.hubY() + 0.5F;
        float hubH = HUB_STIR * stir + HUB_OUT * reach;
        var rings = new float[wheels.size()][][];
        var eyes = new float[wheels.size()][][];
        for (int k = 0; k < wheels.size(); k++)
        {
            var wheel = wheels.get(k);
            double tilt = Math.toRadians(wheel.tilt());
            // The ellipse's own axes in the picture, the second a quarter turn on from the first
            double[] first = {Math.cos(tilt), Math.sin(tilt), 0};
            double[] second = {-Math.sin(tilt), Math.cos(tilt), 0};
            double[] out = {0, 0, 1};
            float radius = Math.max(wheel.rx(), wheel.ry());
            // The circle's axes: its long one lies in the picture, its short one leans out of it, flattened back onto the wall until it reaches
            double[] a, b, axis;
            if (wheel.rx() >= wheel.ry())
            {
                double lean = wheel.ry() / radius;
                a = first;
                b = add(scale(second, lean), scale(out, reach * Math.sqrt(1 - lean * lean)));
                axis = a;
            }
            else
            {
                double lean = wheel.rx() / radius;
                a = add(scale(first, lean), scale(out, reach * Math.sqrt(1 - lean * lean)));
                b = second;
                axis = b;
            }
            // Both angles settle back to rest as the mosaic calms, so a calm wheel lies on its painting with its eyes where they were painted
            double turn = k < tumble.length ? tumble[k] : 0;
            a = rotate(a, axis, turn);
            b = rotate(b, axis, turn);
            float gap = (float) (Math.PI * 2 / wheel.eyes());
            float offset = k < spin.length ? spin[k] : 0;

            rings[k] = new float[SEGMENTS][];
            for (int s = 0; s < SEGMENTS; s++)
                rings[k][s] = at(hubU, hubV, hubH, radius, a, b, s * Math.PI * 2 / SEGMENTS + offset);
            eyes[k] = new float[wheel.eyes()][];
            for (int n = 0; n < wheel.eyes(); n++)
                eyes[k][n] = at(hubU, hubV, hubH, radius, a, b, n * gap + tilt + offset);
        }
        return new Shape(rings, eyes, new float[]{hubU, hubV, hubH});
    }

    private static float[] at(float hubU, float hubV, float hubH, float radius, double[] a, double[] b, double angle)
    {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new float[]{
                (float) (hubU + radius * (cos * a[0] + sin * b[0])),
                (float) (hubV + radius * (cos * a[1] + sin * b[1])),
                (float) (hubH + radius * (cos * a[2] + sin * b[2]))};
    }

    private static double[] scale(double[] v, double by)
    {
        return new double[]{v[0] * by, v[1] * by, v[2] * by};
    }

    private static double[] add(double[] a, double[] b)
    {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    /** Turns a vector about a unit axis, by Rodrigues' formula. */
    private static double[] rotate(double[] v, double[] axis, double angle)
    {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double dot = v[0] * axis[0] + v[1] * axis[1] + v[2] * axis[2];
        double[] cross = {axis[1] * v[2] - axis[2] * v[1], axis[2] * v[0] - axis[0] * v[2], axis[0] * v[1] - axis[1] * v[0]};
        return new double[]{
                v[0] * cos + cross[0] * sin + axis[0] * dot * (1 - cos),
                v[1] * cos + cross[1] * sin + axis[1] * dot * (1 - cos),
                v[2] * cos + cross[2] * sin + axis[2] * dot * (1 - cos)};
    }
}
