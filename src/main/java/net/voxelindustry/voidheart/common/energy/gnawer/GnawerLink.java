package net.voxelindustry.voidheart.common.energy.gnawer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.Optional;

/**
 * Held on a Gnawing Stake between using it on a Gnawer and on the two stakes that mark its box.
 */
public record GnawerLink(BlockPos gnawer, Optional<BlockPos> first)
{
    public static final Codec<GnawerLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("gnawer").forGetter(GnawerLink::gnawer),
            BlockPos.CODEC.optionalFieldOf("first").forGetter(GnawerLink::first)
    ).apply(instance, GnawerLink::new));

    public GnawerLink withFirst(BlockPos pos)
    {
        return new GnawerLink(gnawer, Optional.of(pos.immutable()));
    }
}
