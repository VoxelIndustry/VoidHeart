package net.voxelindustry.voidheart.compat.jade;

import net.voxelindustry.voidheart.common.content.armillary.ArmillaryCoreBlock;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryTile;
import net.voxelindustry.voidheart.common.content.athanor.AthanorBlock;
import net.voxelindustry.voidheart.common.content.athanor.AthanorTile;
import net.voxelindustry.voidheart.common.content.beam.BeamFocusBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageMatrixBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageMatrixTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile;
import net.voxelindustry.voidheart.common.content.research.KnowledgeColumnBlock;
import net.voxelindustry.voidheart.common.content.research.KnowledgeColumnTile;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeBlock;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import net.voxelindustry.voidheart.common.content.tormenter.MalignantTormenterBlock;
import net.voxelindustry.voidheart.common.content.tormenter.MalignantTormenterTile;
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
    private final KnowledgeColumnProvider knowledgeColumnProvider = new KnowledgeColumnProvider();
    private final MalignantTormenterProvider malignantTormenterProvider = new MalignantTormenterProvider();
    private final ArmillaryProvider armillaryProvider = new ArmillaryProvider();
    private final AthanorProvider athanorProvider = new AthanorProvider();

    @Override
    public void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(shatterForgeProvider, ShatterForgeTile.class);
        registration.registerBlockDataProvider(ravenousCollectorProvider, RavenousCollectorTile.class);

        registration.registerBlockDataProvider(ravenousStorageProvider, RavenousStorageTile.class);
        registration.registerBlockDataProvider(ravenousStorageMatrixProvider, RavenousStorageMatrixTile.class);
        registration.registerBlockDataProvider(ravenousRelayProvider, RavenousRelayTile.class);
        registration.registerBlockDataProvider(knowledgeColumnProvider, KnowledgeColumnTile.class);
        registration.registerBlockDataProvider(malignantTormenterProvider, MalignantTormenterTile.class);
        registration.registerBlockDataProvider(armillaryProvider, ArmillaryTile.class);
        registration.registerBlockDataProvider(athanorProvider, AthanorTile.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new ShatterForgeProvider.Client(shatterForgeProvider), ShatterForgeBlock.class);
        registration.registerBlockComponent(new RavenousCollectorProvider.Client(), RavenousCollectorBlock.class);

        registration.registerBlockComponent(new RavenousStorageProvider.Client(), RavenousStorageBlock.class);
        registration.registerBlockComponent(new RavenousStorageMatrixProvider.Client(), RavenousStorageMatrixBlock.class);
        registration.registerBlockComponent(new RavenousRelayProvider.Client(), RavenousRelayBlock.class);
        registration.registerBlockComponent(new KnowledgeColumnProvider.Client(), KnowledgeColumnBlock.class);
        registration.registerBlockComponent(new MalignantTormenterProvider.Client(), MalignantTormenterBlock.class);
        registration.registerBlockComponent(new ArmillaryProvider.Client(), ArmillaryCoreBlock.class);
        registration.registerBlockComponent(new AthanorProvider.Client(athanorProvider), AthanorBlock.class);

        registration.addRayTraceCallback(new PermeableBarrierDisguise(registration));
        registration.addRayTraceCallback(new MalignantTormenterPartRedirect(registration));
        registration.registerBlockComponent(new BeamFocusProvider(), BeamFocusBlock.class);
    }
}