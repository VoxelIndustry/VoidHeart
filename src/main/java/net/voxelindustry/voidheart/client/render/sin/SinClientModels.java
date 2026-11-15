package net.voxelindustry.voidheart.client.render.sin;

import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderers;

/**
 * The model property and special renderer the sin weapons' item definitions name.
 */
public final class SinClientModels
{
    private static boolean registered;

    private SinClientModels()
    {
    }

    /**
     * Both the client and datagen need these known before item models are read or written.
     */
    public static void register()
    {
        if (registered)
            return;
        registered = true;

        ConditionalItemModelProperties.ID_MAPPER.put(SinFlareProperty.ID, SinFlareProperty.MAP_CODEC);
        SpecialModelRenderers.ID_MAPPER.put(SinTridentRender.Unbaked.ID, SinTridentRender.Unbaked.MAP_CODEC);
    }
}
