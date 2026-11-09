package net.voxelindustry.voidheart.client.render.energy.storage;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;

/**
 * Client startup registrar for W2 Rubrum storage, called once by EnergyClientSetup: renderers, the mote particle and the cell's model property.
 */
public final class RubrumClient
{
    private RubrumClient()
    {
    }

    public static void register()
    {
        RubrumChargeProperty.register();

        BlockEntityRenderers.register(RubrumContent.RUBRUM_CONSTRUCT, RubrumConstructRender::new);
        BlockEntityRenderers.register(RubrumContent.CELL_SOCKET_TILE, CellSocketRender::new);

        ParticleProviderRegistry.getInstance().register(RubrumContent.RUBRUM_MOTE,
                spriteSet -> (options, level, x, y, z, xAux, yAux, zAux, random) ->
                        new RubrumMoteParticle(level, x, y, z, xAux, yAux, zAux, spriteSet.get(random)));
    }
}
