package net.voxelindustry.voidheart;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.client.model.VoidHeartModelLoadingPlugin;
import net.voxelindustry.voidheart.client.model.monolith.VoidMonolithSpriteManager;
import net.voxelindustry.voidheart.client.model.portalframe.PortalFrameVeinSpriteManager;
import net.voxelindustry.voidheart.client.particle.AltarItemParticle;
import net.voxelindustry.voidheart.client.particle.AltarVoidFillingParticle;
import net.voxelindustry.voidheart.client.particle.PortalFrameParticle;
import net.voxelindustry.voidheart.client.render.ExperienceSkullRender;
import net.voxelindustry.voidheart.client.render.VoidAltarRender;
import net.voxelindustry.voidheart.client.render.VoidHeartRender;
import net.voxelindustry.voidheart.client.render.VoidPillarRender;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import static net.voxelindustry.voidheart.VoidHeart.*;

public class VoidHeartClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        ModelLoadingPlugin.register(new VoidHeartModelLoadingPlugin());

        BlockRenderLayerMap.INSTANCE.putBlock(VoidHeartBlocks.PORTAL_INTERIOR, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(VoidHeartBlocks.VOID_DOOR, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(VoidHeartBlocks.PERMEABLE_BARRIER, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(VoidHeartBlocks.EXPERIENCE_SKULL, RenderLayer.getCutout());

        BlockEntityRendererFactories.register(VoidHeartTiles.VOID_PILLAR, ctx -> new VoidPillarRender());
        BlockEntityRendererFactories.register(VoidHeartTiles.VOID_ALTAR, ctx -> new VoidAltarRender());
        BlockEntityRendererFactories.register(VoidHeartTiles.VOID_HEART, ctx -> new VoidHeartRender());
        BlockEntityRendererFactories.register(VoidHeartTiles.EXPERIENCE_SKULL, ctx -> new ExperienceSkullRender());

        ParticleFactoryRegistry.getInstance().register(
                ALTAR_VOID_PARTICLE,
                provider -> (parameters, world, x, y, z, velocityX, velocityY, velocityZ) ->
                        new AltarVoidFillingParticle(world, x, y, z, velocityX, velocityY, velocityZ, parameters.getSpeed(), provider));

        ParticleFactoryRegistry.getInstance().register(
                ALTAR_ITEM_PARTICLE,
                provider -> (parameters, world, x, y, z, velocityX, velocityY, velocityZ) ->
                        new AltarItemParticle(world, x, y, z, velocityX, velocityY, velocityZ, parameters.getStack(), parameters.getFirstPointBezier(), parameters.getSecondPointBezier()));

        ParticleFactoryRegistry.getInstance().register(
                PORTAL_FRAME_PARTICLE,
                provider -> (parameters, world, x, y, z, velocityX, velocityY, velocityZ) ->
                        new PortalFrameParticle(world, x, y, z, provider, parameters.direction(), parameters.width(), parameters.height()));

        FabricModelPredicateProviderRegistry.register(
                VoidHeartBlocks.VOID_LAMP.asItem(),
                new Identifier(VoidHeart.MODID, "corruption"),
                ((stack, world, entity, provider) -> stack.hasNbt() ? stack.getNbt().getInt("corruption") : 0));

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener()
        {
            @Override
            public Identifier getFabricId()
            {
                return new Identifier(MODID, "sprite_loader");
            }

            @Override
            public void reload(ResourceManager manager)
            {
                VoidMonolithSpriteManager.loadSprites();
                PortalFrameVeinSpriteManager.loadSprites();
            }
        });
    }
}
