package net.voxelindustry.voidheart.common.content.portalframe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public record PortalKey(RegistryKey<World> worldKey, BlockPos corePos)
{
    public static final Codec<PortalKey> CODEC = RecordCodecBuilder
            .create(instance -> instance.group(
                    RegistryKey.createCodec(RegistryKeys.WORLD).fieldOf("worldKey").forGetter(PortalKey::worldKey),
                    BlockPos.CODEC.fieldOf("corePos").forGetter(PortalKey::corePos)
            ).apply(instance, PortalKey::new));
}
