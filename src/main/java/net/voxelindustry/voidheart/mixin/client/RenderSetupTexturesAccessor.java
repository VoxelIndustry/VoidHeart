package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(RenderSetup.class)
public interface RenderSetupTexturesAccessor
{
    /** Values are {@code RenderSetup.TextureBinding}, read through {@link TextureBindingAccessor}. */
    @Accessor("textures")
    Map<String, Object> voidheart$textures();

    @Accessor("outlineProperty")
    RenderSetup.OutlineProperty voidheart$outlineProperty();
}
