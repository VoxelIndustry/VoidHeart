package net.voxelindustry.voidheart.common.content.sin;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.voxelindustry.voidheart.common.content.oil.SinVessel;

import java.util.Locale;
import java.util.function.IntFunction;

/**
 * A temperament fused into a weapon with a sin metal. The weapon holds only its sin, the player wielding it holds their proficiency in it.
 */
public enum Sin implements StringRepresentable
{
    PRIDE(SinVessel.ARROGANT_IRON, 0xB9C6D8),
    WRATH(SinVessel.SEETHING_STEEL, 0xD5513A),
    GLUTTONY(SinVessel.RAVENOUS_GOLD, 0xE2B740),
    SLOTH(SinVessel.LISTLESS_LEAD, 0x8A8BA6),
    GREED(SinVessel.COVETOUS_COPPER, 0xD27E4B);

    public static final Codec<Sin> CODEC = StringRepresentable.fromEnum(Sin::values);

    private static final IntFunction<Sin> BY_ID = ByIdMap.continuous(Sin::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);

    public static final StreamCodec<ByteBuf, Sin> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Sin::ordinal);

    private final SinVessel metal;
    private final int       color;

    Sin(SinVessel metal, int color)
    {
        this.metal = metal;
        this.color = color;
    }

    /**
     * The sin metal fused into the weapon, shared with the alchemy flasks.
     */
    public SinVessel metal()
    {
        return metal;
    }

    /**
     * Text color of the sin's name in tooltips.
     */
    public int color()
    {
        return color;
    }

    public String translationKey()
    {
        return "voidheart.sin." + getSerializedName();
    }

    /**
     * @param stage from 1 to {@link SinBalance#STAGES}
     */
    public String loreKey(int stage)
    {
        return translationKey() + ".stage." + stage;
    }

    /**
     * The line a tool of this sin speaks at a digging stage.
     *
     * @param stage from 1 to {@link SinBalance#STAGES}
     */
    public String diggingLoreKey(int stage)
    {
        return translationKey() + ".digging.stage." + stage;
    }

    /**
     * The line shown on another sin's weapon to a player devoted to this one.
     */
    public String resentKey()
    {
        return translationKey() + ".resents";
    }

    @Override
    public String getSerializedName()
    {
        return name().toLowerCase(Locale.ROOT);
    }
}
