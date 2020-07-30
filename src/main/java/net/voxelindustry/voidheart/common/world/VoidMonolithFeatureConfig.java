package net.voxelindustry.voidheart.common.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.RequiredArgsConstructor;
import net.minecraft.world.gen.feature.FeatureConfig;

@RequiredArgsConstructor
public class VoidMonolithFeatureConfig implements FeatureConfig
{
    public static final Codec<VoidMonolithFeatureConfig> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    Codec.INT.fieldOf("minimum_radius")
                            .forGetter((deltaFeatureConfig) -> deltaFeatureConfig.minHeight),
                    Codec.INT.fieldOf("maximum_radius")
                            .forGetter((deltaFeatureConfig) -> deltaFeatureConfig.maxHeight)
            ).apply(instance, VoidMonolithFeatureConfig::new));

    public final int minHeight;
    public final int maxHeight;
}
