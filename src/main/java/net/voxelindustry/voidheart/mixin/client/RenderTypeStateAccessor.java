package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the second heart's dissolve find which texture a layer draws, to draw the same texture through its own pipeline.
 */
@Mixin(RenderType.class)
public interface RenderTypeStateAccessor
{
    @Accessor("state")
    RenderSetup voidheart$state();
}
