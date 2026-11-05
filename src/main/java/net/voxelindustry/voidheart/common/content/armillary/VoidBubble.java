package net.voxelindustry.voidheart.common.content.armillary;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonSize;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;

/**
 * A broken dimension an armillary has found, placed in chart space: x runs from 0 to 1 and y from the overworld at 0 down to the bottom at 1.
 */
public record VoidBubble(int id, int difficulty, DungeonSize size, DungeonTheme theme, float x, long seed, long foundAt, long fadeAt, boolean broken)
{
    public static final long NEVER = Long.MAX_VALUE;

    private static final float   BAND_JITTER = 0.3F;
    private static final float[] RADII       = { 0.035F, 0.055F, 0.08F };

    public static final Codec<VoidBubble> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter(VoidBubble::id),
            Codec.intRange(DungeonAddress.MIN_DIFFICULTY, DungeonAddress.MAX_DIFFICULTY).fieldOf("difficulty").forGetter(VoidBubble::difficulty),
            StringRepresentable.fromEnum(DungeonSize::values).fieldOf("size").forGetter(VoidBubble::size),
            StringRepresentable.fromEnum(DungeonTheme::values).fieldOf("theme").forGetter(VoidBubble::theme),
            Codec.FLOAT.fieldOf("x").forGetter(VoidBubble::x),
            Codec.LONG.fieldOf("seed").forGetter(VoidBubble::seed),
            Codec.LONG.fieldOf("found_at").forGetter(VoidBubble::foundAt),
            Codec.LONG.fieldOf("fade_at").forGetter(VoidBubble::fadeAt),
            Codec.BOOL.optionalFieldOf("broken", false).forGetter(VoidBubble::broken)
    ).apply(instance, VoidBubble::new));

    public static float y(int difficulty, long seed)
    {
        float jitter = (seed >>> 8 & 0xFFFF) / 65535F * 2 - 1;
        return (difficulty - 0.5F + jitter * BAND_JITTER) / DungeonAddress.MAX_DIFFICULTY;
    }

    public static float radius(DungeonSize size)
    {
        return RADII[size.ordinal()];
    }

    public float y()
    {
        return y(difficulty, seed);
    }

    public float radius()
    {
        return radius(size);
    }

    /** How many void pearls opening it takes. */
    public int openCost()
    {
        return openCost(difficulty);
    }

    public static int openCost(int difficulty)
    {
        return difficulty;
    }

    VoidBubble withFadeAt(long fadeAt)
    {
        return new VoidBubble(id, difficulty, size, theme, x, seed, foundAt, fadeAt, broken);
    }

    VoidBubble broke(long fadeAt)
    {
        return new VoidBubble(id, difficulty, size, theme, x, seed, foundAt, fadeAt, true);
    }
}
