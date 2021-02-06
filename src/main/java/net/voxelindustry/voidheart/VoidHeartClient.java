package net.voxelindustry.voidheart;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendereregistry.v1.BlockEntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.voxelindustry.voidheart.client.particle.AltarItemParticle;
import net.voxelindustry.voidheart.client.particle.AltarVoidFillingParticle;
import net.voxelindustry.voidheart.client.render.VoidAltarRender;
import net.voxelindustry.voidheart.client.render.VoidHeartRender;
import net.voxelindustry.voidheart.client.render.VoidPillarRender;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import static net.voxelindustry.voidheart.VoidHeart.ALTAR_ITEM_PARTICLE;
import static net.voxelindustry.voidheart.VoidHeart.ALTAR_VOID_PARTICLE;

public class VoidHeartClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        BlockRenderLayerMap.INSTANCE.putBlock(VoidHeartBlocks.PORTAL_INTERIOR, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(VoidHeartBlocks.PERMEABLE_BARRIER, RenderLayer.getCutout());

        BlockEntityRendererRegistry.INSTANCE.register(VoidHeartTiles.VOID_PILLAR, VoidPillarRender::new);
        BlockEntityRendererRegistry.INSTANCE.register(VoidHeartTiles.VOID_ALTAR, VoidAltarRender::new);
        BlockEntityRendererRegistry.INSTANCE.register(VoidHeartTiles.VOID_HEART, VoidHeartRender::new);

        ParticleFactoryRegistry.getInstance().register(
                ALTAR_VOID_PARTICLE,
                provider -> (parameters, world, x, y, z, velocityX, velocityY, velocityZ) ->
                        new AltarVoidFillingParticle(world, x, y, z, velocityX, velocityY, velocityZ, parameters.getSpeed(), provider));

        ParticleFactoryRegistry.getInstance().register(
                ALTAR_ITEM_PARTICLE,
                provider -> (parameters, world, x, y, z, velocityX, velocityY, velocityZ) ->
                        new AltarItemParticle(world, x, y, z, velocityX, velocityY, velocityZ, parameters.getStack(), parameters.getFirstPointBezier(), parameters.getSecondPointBezier()));
    }
}
