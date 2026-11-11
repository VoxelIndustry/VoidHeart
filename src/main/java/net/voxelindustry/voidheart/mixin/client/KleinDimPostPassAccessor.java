package net.voxelindustry.voidheart.mixin.client;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Vanilla bakes a post pass's uniforms once when it loads. The dimming around a Klein Star changes every frame, so its block is swapped here.
 */
@Mixin(PostPass.class)
public interface KleinDimPostPassAccessor
{
    @Accessor("customUniforms")
    Map<String, GpuBuffer> voidheart$customUniforms();
}
