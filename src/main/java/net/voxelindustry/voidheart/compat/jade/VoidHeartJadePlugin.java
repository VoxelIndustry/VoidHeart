package net.voxelindustry.voidheart.compat.jade;

import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeBlock;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;

public class VoidHeartJadePlugin implements IWailaPlugin
{
    private final ShatterForgeProvider shatterForgeProvider = new ShatterForgeProvider();

    @Override
    public void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(shatterForgeProvider, ShatterForgeTile.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration)
    {
        registration.hideTarget(VoidHeartBlocks.PORTAL_IMMERSIVE_INTERIOR);

        registration.registerBlockComponent(shatterForgeProvider, ShatterForgeBlock.class);
    }
}