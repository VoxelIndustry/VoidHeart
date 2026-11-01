package net.voxelindustry.voidheart.common.content.shouldereye;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What a player keeps of their Shoulder Eye while it is not in the world: its health, and how long it still needs to grow back once slain.
 */
public record ShoulderEyeData(float health, int regenTicks)
{
    public static final Codec<ShoulderEyeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("health").forGetter(ShoulderEyeData::health),
            Codec.INT.optionalFieldOf("regen_ticks", 0).forGetter(ShoulderEyeData::regenTicks)
    ).apply(instance, ShoulderEyeData::new));

    public static ShoulderEyeData fresh()
    {
        return new ShoulderEyeData(ShoulderEyeEntity.MAX_HEALTH, 0);
    }

    public boolean isRegenerating()
    {
        return regenTicks > 0;
    }
}
