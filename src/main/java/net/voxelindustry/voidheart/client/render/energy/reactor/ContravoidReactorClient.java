package net.voxelindustry.voidheart.client.render.energy.reactor;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorContent;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorTile;

/**
 * Client startup registrar for W10 Contravoid reactor, called once by EnergyClientSetup: the core's renderer and its warning sounds.
 */
public final class ContravoidReactorClient
{
    private ContravoidReactorClient()
    {
    }

    public static void register()
    {
        BlockEntityRenderers.register(ContravoidReactorContent.CONTRAVOID_REACTOR_TILE, ContravoidReactorRender::new);
        ContravoidReactorTile.clientSounds = ReactorWarningSound::follow;
    }
}
