package net.voxelindustry.voidheart.common.content.sin;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * A player's proficiency in every sin, in points. It only grows by fighting a sin's way, and fades only while another sin grows: idle time takes nothing.
 */
public final class SinProficiency
{
    public static final SinProficiency NONE = new SinProficiency(new float[Sin.values().length]);

    public static final Codec<SinProficiency> CODEC = Codec.unboundedMap(Sin.CODEC, Codec.FLOAT).xmap(SinProficiency::fromMap, SinProficiency::toMap);

    public static final StreamCodec<ByteBuf, SinProficiency> STREAM_CODEC = StreamCodec.of(
            (buffer, proficiency) ->
            {
                for (var value : proficiency.points)
                    buffer.writeFloat(value);
            },
            buffer ->
            {
                var points = new float[Sin.values().length];
                for (int index = 0; index < points.length; index++)
                    points[index] = buffer.readFloat();
                return new SinProficiency(points);
            });

    private final float[] points;

    private SinProficiency(float[] points)
    {
        this.points = points;
    }

    public static SinProficiency of(Map<Sin, Float> points)
    {
        return fromMap(points);
    }

    private static SinProficiency fromMap(Map<Sin, Float> map)
    {
        var points = new float[Sin.values().length];
        map.forEach((sin, value) -> points[sin.ordinal()] = clamp(value));
        return new SinProficiency(points);
    }

    private Map<Sin, Float> toMap()
    {
        var map = new EnumMap<Sin, Float>(Sin.class);
        for (var sin : Sin.values())
        {
            if (points[sin.ordinal()] > 0)
                map.put(sin, points[sin.ordinal()]);
        }
        return map;
    }

    private static float clamp(float value)
    {
        return Math.clamp(value, 0, SinBalance.DEVOTED_POINTS);
    }

    public float points(Sin sin)
    {
        return points[sin.ordinal()];
    }

    /**
     * @return from 0 for a novice to 1 for a devoted wielder
     */
    public float devotion(Sin sin)
    {
        return points(sin) / SinBalance.DEVOTED_POINTS;
    }

    /**
     * @return the lore stage, from 1 to {@link SinBalance#STAGES}
     */
    public int stage(Sin sin)
    {
        return stageOf(devotion(sin));
    }

    public static int stageOf(float devotion)
    {
        int stage = 1;
        for (int index = 1; index < SinBalance.STAGE_STARTS.length; index++)
        {
            if (devotion >= SinBalance.STAGE_STARTS[index])
                stage = index + 1;
        }
        return stage;
    }

    /**
     * Grows one sin, and fades every other by {@link SinBalance#FADE_RATIO} of what was fought for, so a devoted player fighting on with another sin still
     * moves over.
     */
    public SinProficiency gain(Sin sin, float amount)
    {
        if (amount <= 0)
            return this;

        var next = Arrays.copyOf(points, points.length);
        next[sin.ordinal()] = clamp(points[sin.ordinal()] + amount);

        for (var other : Sin.values())
        {
            if (other != sin)
                next[other.ordinal()] = clamp(next[other.ordinal()] - amount * SinBalance.FADE_RATIO);
        }
        return new SinProficiency(next);
    }

    public SinProficiency with(Sin sin, float value)
    {
        var next = Arrays.copyOf(points, points.length);
        next[sin.ordinal()] = clamp(value);
        return new SinProficiency(next);
    }

    /**
     * The sin the player is most devoted to, or null while none has a point.
     */
    public Sin strongest()
    {
        Sin strongest = null;
        for (var sin : Sin.values())
        {
            if (points(sin) > 0 && (strongest == null || points(sin) > points(strongest)))
                strongest = sin;
        }
        return strongest;
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof SinProficiency proficiency && Arrays.equals(points, proficiency.points);
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(points);
    }

    @Override
    public String toString()
    {
        return "SinProficiency" + toMap();
    }
}
