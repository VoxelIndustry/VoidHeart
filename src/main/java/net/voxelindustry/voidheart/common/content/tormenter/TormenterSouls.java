package net.voxelindustry.voidheart.common.content.tormenter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

/**
 * The souls a Malignant Tormenter has taken, all from one kind of mob.
 */
public record TormenterSouls(EntityType<?> type, int souls)
{
    public static final int REQUIRED = 16;

    public static final Codec<TormenterSouls> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("type").forGetter(TormenterSouls::type),
            Codec.intRange(1, REQUIRED).fieldOf("souls").forGetter(TormenterSouls::souls)
    ).apply(instance, TormenterSouls::new));

    public boolean isComplete()
    {
        return souls >= REQUIRED;
    }

    public TormenterSouls withOneMore()
    {
        return new TormenterSouls(type, Math.min(REQUIRED, souls + 1));
    }
}
