package net.voxelindustry.voidheart.compat.jade;

import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
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

    @Override
    public void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(shatterForgeProvider, ShatterForgeTile.class);
        registration.registerBlockDataProvider(ravenousCollectorProvider, RavenousCollectorTile.class);

        registration.registerBlockDataProvider(ravenousStorageProvider, RavenousStorageTile.class);
        registration.registerBlockDataProvider(ravenousStorageMatrixProvider, RavenousStorageMatrixTile.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(shatterForgeProvider, ShatterForgeBlock.class);
        registration.registerBlockComponent(ravenousCollectorProvider, RavenousCollectorBlock.class);

        registration.registerBlockComponent(ravenousStorageProvider, RavenousStorageBlock.class);
        registration.registerBlockComponent(ravenousStorageMatrixProvider, RavenousStorageMatrixBlock.class);
    }
}