package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * The passes of a post effect, to reach the uniforms of the one that dims the scene around a Klein Star.
 */
@Mixin(PostChain.class)
public interface KleinDimPostChainAccessor
{
    @Accessor("passes")
    List<PostPass> voidheart$passes();
}
