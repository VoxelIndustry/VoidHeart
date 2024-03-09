package net.voxelindustry.voidheart.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayer.MultiPhaseParameters;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

public class CustomRenderLayers
{
    private static final Function<Identifier, RenderLayer> COLOR_TEXTURE_TRANSLUCENT = Util.memoize(identifier ->
            RenderLayer.of("color_translucent",
                    VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL,
                    VertexFormat.DrawMode.QUADS,
                    0x200000,
                    true,
                    true,
                    MultiPhaseParameters.builder()
                            .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                            .program(RenderPhase.TRANSLUCENT_PROGRAM)
                            .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                            .texture(new RenderPhase.Texture(identifier, false, false))
                            .build(true)
            ));

    public static RenderLayer getColorTextureTranslucent(Identifier texture)
    {
        return COLOR_TEXTURE_TRANSLUCENT.apply(texture);
    }
}
