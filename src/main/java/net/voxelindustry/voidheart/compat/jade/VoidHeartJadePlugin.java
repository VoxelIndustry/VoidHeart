package net.voxelindustry.voidheart.compat.jade;

import net.voxelindustry.voidheart.common.content.beam.BeamFocusBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageMatrixBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageMatrixTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeBlock;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;

public class VoidHeartJadePlugin implements IWailaPlugin
{
    private final ShatterForgeProvider shatterForgeProvider = new ShatterForgeProvider();
    private final RavenousCollectorProvider ravenousCollectorProvider = new RavenousCollectorProvider();
    private final RavenousStorageProvider ravenousStorageProvider = new RavenousStorageProvider();
    private final RavenousStorageMatrixProvider ravenousStorageMatrixProvider = new RavenousStorageMatrixProvider();
    private final RavenousRelayProvider ravenousRelayProvider = new RavenousRelayProvider();

    @Override
    public void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(shatterForgeProvider, ShatterForgeTile.class);
        registration.registerBlockDataProvider(ravenousCollectorProvider, RavenousCollectorTile.class);

        registration.registerBlockDataProvider(ravenousStorageProvider, RavenousStorageTile.class);
        registration.registerBlockDataProvider(ravenousStorageMatrixProvider, RavenousStorageMatrixTile.class);
        registration.registerBlockDataProvider(ravenousRelayProvider, RavenousRelayTile.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new ShatterForgeProvider.Client(shatterForgeProvider), ShatterForgeBlock.class);
        registration.registerBlockComponent(new RavenousCollectorProvider.Client(), RavenousCollectorBlock.class);

        registration.registerBlockComponent(new RavenousStorageProvider.Client(), RavenousStorageBlock.class);
        registration.registerBlockComponent(new RavenousStorageMatrixProvider.Client(), RavenousStorageMatrixBlock.class);
        registration.registerBlockComponent(new RavenousRelayProvider.Client(), RavenousRelayBlock.class);

        registration.addRayTraceCallback(new PermeableBarrierDisguise(registration));
        registration.registerBlockComponent(new BeamFocusProvider(), BeamFocusBlock.class);
    }
}