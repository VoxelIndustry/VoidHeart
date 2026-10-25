package net.voxelindustry.voidheart.common.content.portalframe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record PortalKey(ResourceKey<Level> worldKey, BlockPos corePos)
{
    public static final Codec<PortalKey> CODEC = RecordCodecBuilder
            .create(instance -> instance.group(
                    ResourceKey.codec(Registries.DIMENSION).fieldOf("worldKey").forGetter(PortalKey::worldKey),
                    BlockPos.CODEC.fieldOf("corePos").forGetter(PortalKey::corePos)
            ).apply(instance, PortalKey::new));

    @Override
    public String toString()
    {
        return "[" +
                "worldKey: " + worldKey.location() +
                ", corePos: " + corePos.toShortString() +
                ']';
    }
}
