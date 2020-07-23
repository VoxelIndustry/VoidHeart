package net.voxelindustry.voidheart;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendereregistry.v1.BlockEntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.voxelindustry.voidheart.client.render.VoidAltarRender;
import net.voxelindustry.voidheart.client.render.VoidPillarRender;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class VoidHeartClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        BlockRenderLayerMap.INSTANCE.putBlock(VoidHeartBlocks.PORTAL_INTERIOR, RenderLayer.getTranslucent());

        BlockEntityRendererRegistry.INSTANCE.register(VoidHeartTiles.VOID_PILLAR, VoidPillarRender::new);
        BlockEntityRendererRegistry.INSTANCE.register(VoidHeartTiles.VOID_ALTAR, VoidAltarRender::new);
    }
}
