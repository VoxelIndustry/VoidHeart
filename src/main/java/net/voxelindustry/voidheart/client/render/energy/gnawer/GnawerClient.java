package net.voxelindustry.voidheart.client.render.energy.gnawer;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.voxelindustry.voidheart.common.energy.gnawer.GnawerContent;

/**
 * Client startup registrar for the Gnawer, called once by EnergyClientSetup.
 */
public final class GnawerClient
{
    private GnawerClient()
    {
    }

    public static void register()
    {
        BlockEntityRenderers.register(GnawerContent.GNAWER_TILE, GnawerRender::new);
    }
}
