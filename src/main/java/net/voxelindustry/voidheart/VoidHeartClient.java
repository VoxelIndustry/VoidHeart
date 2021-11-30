package net.voxelindustry.voidheart;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendereregistry.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.event.client.ClientSpriteRegistryCallback;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.client.model.monolith.VoidMonolithSpriteManager;
import net.voxelindustry.voidheart.client.model.portalframe.PortalFrameVeinSpriteManager;
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
        BlockRenderLayerMap.INSTANCE.putBlock(VoidHeartBlocks.VOID_DOOR, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(VoidHeartBlocks.PERMEABLE_BARRIER, RenderLayer.getCutout());

        BlockEntityRendererRegistry.INSTANCE.register(VoidHeartTiles.VOID_PILLAR, ctx -> new VoidPillarRender());
        BlockEntityRendererRegistry.INSTANCE.register(VoidHeartTiles.VOID_ALTAR, ctx -> new VoidAltarRender());
        BlockEntityRendererRegistry.INSTANCE.register(VoidHeartTiles.VOID_HEART, ctx -> new VoidHeartRender());

        ParticleFactoryRegistry.getInstance().register(
                ALTAR_VOID_PARTICLE,
                provider -> (parameters, world, x, y, z, velocityX, velocityY, velocityZ) ->
                        new AltarVoidFillingParticle(world, x, y, z, velocityX, velocityY, velocityZ, parameters.getSpeed(), provider));

        ParticleFactoryRegistry.getInstance().register(
                ALTAR_ITEM_PARTICLE,
                provider -> (parameters, world, x, y, z, velocityX, velocityY, velocityZ) ->
                        new AltarItemParticle(world, x, y, z, velocityX, velocityY, velocityZ, parameters.getStack(), parameters.getFirstPointBezier(), parameters.getSecondPointBezier()));

        FabricModelPredicateProviderRegistry.register(
                VoidHeartBlocks.VOID_LAMP.asItem(),
                new Identifier(VoidHeart.MODID, "corruption"),
                ((stack, world, entity, provider) -> stack.hasNbt() ? stack.getNbt().getInt("corruption") : 0));

        ClientSpriteRegistryCallback.event(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).register((atlasTexture, registry) ->
        {
            VoidMonolithSpriteManager.registerSprites(registry::register);
            PortalFrameVeinSpriteManager.registerSprites(registry::register);
        });

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId()
            {
                return new Identifier(MODID, "sprite_loader");
            }

            @Override
            public void reload(ResourceManager manager)
            {
                for (Direction value : Direction.values())
                {
                    PortalFrameVeinSpriteManager.getFrameSprite(value);
                    VoidMonolithSpriteManager.getFrameSprite(value);
                }
                PortalFrameVeinSpriteManager.getActiveCoreSprite();
                PortalFrameVeinSpriteManager.getBrokenCoreSprite();
                PortalFrameVeinSpriteManager.getInactiveCoreSprite();
            }
        });
    }
}
