package net.voxelindustry.voidheart.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.resources.Identifier;

public class CustomRenderLayers
{
    private static final Function<Identifier, RenderType> COLOR_TEXTURE_TRANSLUCENT = Util.memoize(identifier ->
            RenderType.create("color_translucent",
                    DefaultVertexFormat.BLOCK,
                    VertexFormat.Mode.QUADS,
                    0x200000,
                    true,
                    true,
                    CompositeState.builder()
                            .setLightmapState(RenderStateShard.LIGHTMAP)
                            .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
                            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setTextureState(new RenderStateShard.TextureStateShard(identifier, false, false))
                            .createCompositeState(true)
            ));

    public static RenderType getColorTextureTranslucent(Identifier texture)
    {
        return COLOR_TEXTURE_TRANSLUCENT.apply(texture);
    }
}
