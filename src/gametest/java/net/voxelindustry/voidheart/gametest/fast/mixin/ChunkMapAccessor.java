package net.voxelindustry.voidheart.gametest.fast.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor
{
    @Accessor("worldgenTaskDispatcher")
    ChunkTaskDispatcher voidheart$worldgen();

    @Accessor("lightTaskDispatcher")
    ChunkTaskDispatcher voidheart$light();
}
