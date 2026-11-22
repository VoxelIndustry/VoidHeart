package net.voxelindustry.voidheart.client.curse;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What crawls in from each edge of the screen: a lumpy rim of flesh, teeth along its lip, tentacles reaching past them and eyes set in it.
 * Positions along an edge go from 0 to 1, and sizes are fractions of the screen's shorter side, so the same seed lays out the same flesh on any
 * screen of the same shape.
 */
public final class HymnLayout
{
    /** How far anything may come in from its edge, as a fraction of the screen across that edge. */
    public static final float REACH = 0.10F;
    /** Ticks the flesh takes to spread from the corners to the middle of each edge. */
    public static final float SPREAD = 22;

    private static final float TENTACLES_PER_SIDE = 3.6F;
    private static final float TENTACLE_SHORTEST  = 0.78F;
    private static final float TENTACLE_WIDEST    = 0.085F;
    private static final float TENTACLE_THINNEST  = 0.05F;
    private static final float TENTACLE_TILT      = 0.4F;
    private static final float TENTACLE_LAST_OFF  = 20;
    private static final float TENTACLE_CURL      = 0.8F;

    private static final float TOOTH_STEP      = 0.03F;
    private static final float TOOTH_GAP       = 0.3F;
    private static final float TOOTH_WIDEST    = 0.034F;
    private static final float TOOTH_NARROWEST = 0.016F;
    private static final float TOOTH_LONGEST   = 0.032F;
    private static final float TOOTH_SHORTEST  = 0.012F;
    private static final float FANG_CHANCE     = 0.12F;
    private static final float FANG_LENGTH     = 0.045F;
    private static final float TOOTH_LAST_OFF  = 10;

    private static final float EYES_PER_SIDE  = 1.2F;
    private static final float EYE_LARGEST    = 0.11F;
    private static final float EYE_SMALLEST   = 0.065F;
    private static final float EYE_FIRST_OFF  = 8;
    private static final float EYE_LAST_OFF   = 28;

    private static final int RIM_WAVES = 4;

    /**
     * When the flesh reaches this far along an edge, spreading in from both its corners.
     */
    public static float reached(float along)
    {
        return SPREAD * Math.min(along, 1 - along) * 2;
    }

    public enum Edge
    {
        TOP, BOTTOM, LEFT, RIGHT;

        public boolean horizontal()
        {
            return this == TOP || this == BOTTOM;
        }
    }

    /**
     * @param along    where its root is along the edge
     * @param tilt     radians away from straight in
     * @param width    at the root
     * @param length   of the reach for its edge
     * @param waves    how many bends run down it at once
     * @param swing    radians each bend turns it
     * @param speed    radians per tick the bends run down it
     */
    public record Tentacle(Edge edge, float along, float tilt, float width, float length, float start, float crawl, float waves, float swing,
                           float speed, float phase, float curl)
    {
    }

    /**
     * @param lean how far its tip leans along the edge, as a fraction of its length
     * @param hook how far its middle bends the other way
     */
    public record Tooth(Edge edge, float along, float width, float length, float lean, float hook, float start, float phase)
    {
    }

    /**
     * @param pupils  one, two or three
     * @param iris    index into the irises the mesh knows
     * @param blinks  ticks at which it blinks
     */
    public record Eye(Edge edge, float along, float size, int pupils, boolean slit, int iris, float start, float[] blinks,
                      float phase)
    {
    }

    /**
     * Waves summed into the depth of the rim along one edge.
     */
    public record Rim(Edge edge, float[] frequency, float[] amplitude, float[] phase, float[] speed)
    {
        public float wave(float along, float time)
        {
            float sum = 0;
            for (int i = 0; i < frequency.length; i++)
                sum += amplitude[i] * (float) Math.sin(frequency[i] * along * Math.PI * 2 + phase[i] + speed[i] * time);
            return sum;
        }
    }

    private final List<Tentacle> tentacles = new ArrayList<>();
    private final List<Tooth>    teeth     = new ArrayList<>();
    private final List<Eye>      eyes      = new ArrayList<>();
    private final Rim[]          rims      = new Rim[Edge.values().length];
    private long                 salt;

    private HymnLayout()
    {
    }

    /**
     * @param width  of the screen
     * @param height of the screen
     */
    public static HymnLayout of(long seed, float width, float height)
    {
        var layout = new HymnLayout();
        var random = new Random(seed);
        layout.salt = random.nextLong();
        float shorter = Math.min(width, height);
        for (var edge : Edge.values())
        {
            float span = (edge.horizontal() ? width : height) / shorter;
            layout.rims[edge.ordinal()] = rim(edge, random);
            layout.placeTentacles(edge, span, random);
            layout.placeTeeth(edge, span, random);
            layout.placeEyes(edge, span, random);
        }
        // Never only plain eyes
        var eyes = layout.eyes;
        if (eyes.stream().allMatch(eye -> eye.pupils() == 1))
        {
            int odd = random.nextInt(eyes.size());
            var eye = eyes.get(odd);
            eyes.set(odd, new Eye(eye.edge(), eye.along(), eye.size(), 2 + random.nextInt(2), eye.slit(), eye.iris(), eye.start(), eye.blinks(), eye.phase()));
        }
        return layout;
    }

    private static Rim rim(Edge edge, Random random)
    {
        var frequency = new float[RIM_WAVES];
        var amplitude = new float[RIM_WAVES];
        var phase = new float[RIM_WAVES];
        var speed = new float[RIM_WAVES];
        for (int i = 0; i < RIM_WAVES; i++)
        {
            frequency[i] = (1.5F + i * 2.5F) * (0.8F + random.nextFloat() * 0.4F);
            amplitude[i] = 1F / (i + 1.5F);
            phase[i] = random.nextFloat() * (float) Math.PI * 2;
            speed[i] = (random.nextBoolean() ? 1 : -1) * (0.03F + random.nextFloat() * 0.05F);
        }
        return new Rim(edge, frequency, amplitude, phase, speed);
    }

    private void placeTentacles(Edge edge, float span, Random random)
    {
        int count = Math.max(2, Math.round(span * TENTACLES_PER_SIDE));
        for (int i = 0; i < count; i++)
        {
            // Spread evenly with some give, so two never sprout from one place
            float along = (i + 0.2F + random.nextFloat() * 0.6F) / count;
            // Leaning away from the middle of the edge more often than toward it, as if reaching around the screen
            float tilt = (random.nextFloat() * 2 - 1) * TENTACLE_TILT + (along - 0.5F) * 0.5F;
            tentacles.add(new Tentacle(edge, along, tilt,
                    lerp(random.nextFloat(), TENTACLE_THINNEST, TENTACLE_WIDEST),
                    lerp((float) Math.sqrt(random.nextFloat()), TENTACLE_SHORTEST, 1),
                    reached(along) + random.nextFloat() * TENTACLE_LAST_OFF,
                    18 + random.nextFloat() * 16,
                    0.8F + random.nextFloat() * 0.9F,
                    0.25F + random.nextFloat() * 0.3F,
                    (random.nextBoolean() ? 1 : -1) * (0.18F + random.nextFloat() * 0.2F),
                    random.nextFloat() * (float) Math.PI * 2,
                    (random.nextFloat() * 2 - 1) * TENTACLE_CURL));
        }
    }

    private void placeTeeth(Edge edge, float span, Random random)
    {
        float step = TOOTH_STEP / span;
        for (float along = random.nextFloat() * step; along < 1; along += step * (0.7F + random.nextFloat() * 0.6F))
        {
            if (random.nextFloat() < TOOTH_GAP)
                continue;
            boolean fang = random.nextFloat() < FANG_CHANCE;
            float length = fang ? FANG_LENGTH : lerp(random.nextFloat(), TOOTH_SHORTEST, TOOTH_LONGEST);
            float width = lerp(random.nextFloat(), TOOTH_NARROWEST, TOOTH_WIDEST) * (fang ? 1.2F : 1);
            teeth.add(new Tooth(edge, along, width, length,
                    (random.nextFloat() * 2 - 1) * 0.35F,
                    (random.nextFloat() * 2 - 1) * 0.15F,
                    reached(along) + random.nextFloat() * TOOTH_LAST_OFF,
                    random.nextFloat() * (float) Math.PI * 2));
        }
    }

    private void placeEyes(Edge edge, float span, Random random)
    {
        int count = Math.max(1, Math.round(span * EYES_PER_SIDE + random.nextFloat() - 0.5F));
        for (int i = 0; i < count; i++)
        {
            float along = (i + 0.15F + random.nextFloat() * 0.7F) / count;
            float roll = random.nextFloat();
            int pupils = roll < 0.55F ? 1 : roll < 0.85F ? 2 : 3;
            float start = reached(along) + lerp(random.nextFloat(), EYE_FIRST_OFF, EYE_LAST_OFF);
            int blinkCount = 1 + random.nextInt(2);
            var blinks = new float[blinkCount];
            for (int blink = 0; blink < blinkCount; blink++)
                blinks[blink] = start + 18 + random.nextFloat() * (HymnTimeline.RETRACT_FROM - start - 26);
            eyes.add(new Eye(edge, along,
                    lerp(random.nextFloat(), EYE_SMALLEST, EYE_LARGEST),
                    pupils,
                    random.nextFloat() < 0.6F,
                    random.nextInt(HymnMesh.IRISES),
                    start, blinks,
                    random.nextFloat() * (float) Math.PI * 2));
        }
    }

    public List<Tentacle> tentacles()
    {
        return tentacles;
    }

    public List<Tooth> teeth()
    {
        return teeth;
    }

    public List<Eye> eyes()
    {
        return eyes;
    }

    /**
     * Speckles the flesh, so each strike looks a little different.
     */
    public long salt()
    {
        return salt;
    }

    public Rim rim(Edge edge)
    {
        return rims[edge.ordinal()];
    }

    private static float lerp(float delta, float from, float to)
    {
        return from + (to - from) * delta;
    }
}
