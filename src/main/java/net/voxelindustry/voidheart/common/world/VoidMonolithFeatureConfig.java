package net.voxelindustry.voidheart.common.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public record VoidMonolithFeatureConfig(int minHeight, int maxHeight) implements FeatureConfiguration
{
    public static final Codec<VoidMonolithFeatureConfig> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    Codec.INT.fieldOf("minimum_height")
                            .forGetter((deltaFeatureConfig) -> deltaFeatureConfig.minHeight),
                    Codec.INT.fieldOf("maximum_height")
                            .forGetter((deltaFeatureConfig) -> deltaFeatureConfig.maxHeight)
            ).apply(instance, VoidMonolithFeatureConfig::new));

}
