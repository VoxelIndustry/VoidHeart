package net.voxelindustry.voidheart;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.voxelindustry.voidheart.client.model.VoidHeartModelLoadingPlugin;
import net.voxelindustry.voidheart.client.model.conduit.ConduitSpriteManager;
import net.voxelindustry.voidheart.client.model.monolith.VoidMonolithSpriteManager;
import net.voxelindustry.voidheart.client.model.portalframe.PortalFrameVeinSpriteManager;
import net.voxelindustry.voidheart.client.particle.AltarItemParticle;
import net.voxelindustry.voidheart.client.particle.AltarVoidFillingParticle;
import net.voxelindustry.voidheart.client.particle.PortalFrameParticle;
import net.voxelindustry.voidheart.client.particle.ShatterForgeItemParticle;
import net.voxelindustry.voidheart.client.particle.ShatterForgeRuneParticle;
import net.voxelindustry.voidheart.client.render.ExperienceSkullRender;
import net.voxelindustry.voidheart.client.render.EyeBottleRender;
import net.voxelindustry.voidheart.client.render.RavenousCollectorRender;
import net.voxelindustry.voidheart.client.render.ShatterForgeRender;
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

        BlockRenderLayerMap.INSTANCE.putBlock(VoidHeartBlocks.PORTAL_INTERIOR, RenderType.translucent());
        BlockRenderLayerMap.INSTANCE.putBlock(VoidHeartBlocks.VOID_DOOR, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(VoidHeartBlocks.PERMEABLE_BARRIER, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(VoidHeartBlocks.EXPERIENCE_SKULL, RenderType.cutout());

        BlockEntityRenderers.register(VoidHeartTiles.VOID_PILLAR, ctx -> new VoidPillarRender());
        BlockEntityRenderers.register(VoidHeartTiles.VOID_ALTAR, ctx -> new VoidAltarRender());
        BlockEntityRenderers.register(VoidHeartTiles.VOID_HEART, ctx -> new VoidHeartRender());
        BlockEntityRenderers.register(VoidHeartTiles.EXPERIENCE_SKULL, ctx -> new ExperienceSkullRender());
        BlockEntityRenderers.register(VoidHeartTiles.EYE_BOTTLE, ctx -> new EyeBottleRender());
        BlockEntityRenderers.register(VoidHeartTiles.SHATTER_FORGE, ctx -> new ShatterForgeRender());
        BlockEntityRenderers.register(VoidHeartTiles.RAVENOUS_COLLECTOR, ctx -> new RavenousCollectorRender());

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

        ParticleFactoryRegistry.getInstance().register(
                SHATTER_FORGE_RUNE_PARTICLE,
                provider -> (parameters, world, x, y, z, velocityX, velocityY, velocityZ) ->
                        new ShatterForgeRuneParticle(world, x, y, z, provider, parameters.axis()));

        ParticleFactoryRegistry.getInstance().register(
                SHATTER_FORGE_ITEM_PARTICLE,
                provider -> (parameters, world, x, y, z, velocityX, velocityY, velocityZ) ->
                        new ShatterForgeItemParticle(world, x, y, z, velocityX, velocityY, velocityZ, parameters.input(), parameters.output()));

        FabricModelPredicateProviderRegistry.register(
                VoidHeartBlocks.VOID_LAMP.asItem(),
                new ResourceLocation(VoidHeart.MODID, "corruption"),
                ((stack, world, entity, provider) -> stack.hasTag() ? stack.getTag().getInt("corruption") : 0));

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener()
        {
            @Override
            public ResourceLocation getFabricId()
            {
                return new ResourceLocation(MODID, "sprite_loader");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager)
            {
                VoidMonolithSpriteManager.loadSprites();
                PortalFrameVeinSpriteManager.loadSprites();
                ConduitSpriteManager.loadSprites();
            }
        });
    }
}
