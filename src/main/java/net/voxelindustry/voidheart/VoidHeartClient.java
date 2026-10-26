package net.voxelindustry.voidheart;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleGroupRegistry;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.voxelindustry.voidheart.client.render.BarrierWaveTracker;
import net.voxelindustry.voidheart.client.render.PermeableBarrierRender;
import net.voxelindustry.voidheart.client.render.WeaverPreviewRender;
import net.voxelindustry.voidheart.client.screen.PermeableBarrierScreen;
import net.voxelindustry.voidheart.common.content.permeablebarrier.BarrierWavePayload;
import net.voxelindustry.voidheart.common.setup.VoidHeartMenus;
import net.voxelindustry.voidheart.client.model.VoidHeartModelLoadingPlugin;
import net.voxelindustry.voidheart.client.particle.AltarItemParticle;
import net.voxelindustry.voidheart.client.particle.AltarVoidFillingParticle;
import net.voxelindustry.voidheart.client.particle.EyeBeamParticle;
import net.voxelindustry.voidheart.client.model.ShoulderEyeModel;
import net.voxelindustry.voidheart.client.render.ShoulderEyeRender;
import net.voxelindustry.voidheart.common.content.shouldereye.ShoulderEyeEntity;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.client.particle.GeometryParticle;
import net.voxelindustry.voidheart.client.particle.PortalFrameParticle;
import net.voxelindustry.voidheart.client.particle.ShatterForgeItemParticle;
import net.voxelindustry.voidheart.client.particle.ShatterForgeRuneParticle;
import net.voxelindustry.voidheart.client.render.ExperienceSkullRender;
import net.voxelindustry.voidheart.client.render.EyeBottleRender;
import net.voxelindustry.voidheart.client.render.FloatingItemRender;
import net.voxelindustry.voidheart.client.render.AnchorWaypointRender;
import net.voxelindustry.voidheart.client.render.LeyDebugRender;
import net.voxelindustry.voidheart.client.render.oculus.OculusControls;
import net.voxelindustry.voidheart.client.render.oculus.OculusFigurine;
import net.voxelindustry.voidheart.client.render.oculus.OculusLeyLayer;
import net.voxelindustry.voidheart.client.render.oculus.OculusMarkers;
import net.voxelindustry.voidheart.common.content.oculus.OculusLeyPayloads;
import net.voxelindustry.voidheart.common.content.oculus.OculusEntityPayloads;
import net.voxelindustry.voidheart.client.render.oculus.OculusRenderer;
import net.voxelindustry.voidheart.client.render.oculus.OculusSky;
import net.voxelindustry.voidheart.common.content.oculus.OculusChunkPayloads;
import net.voxelindustry.voidheart.common.content.oculus.OculusSightPayloads;
import net.voxelindustry.voidheart.client.render.oculus.OculusBeholderLayer;
import net.voxelindustry.voidheart.client.render.BeholderRender;
import net.voxelindustry.voidheart.client.screen.TravelMapScreen;
import net.voxelindustry.voidheart.common.ley.anchor.AnchorPayloads;
import net.voxelindustry.voidheart.client.render.ShatterForgeRender;
import net.voxelindustry.voidheart.client.render.RavenousCollectorRender;
import net.voxelindustry.voidheart.client.render.RavenousRelayRender;
import net.voxelindustry.voidheart.client.render.VoidAltarRender;
import net.voxelindustry.voidheart.client.render.VoidHeartRender;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.ley.LeyDebugPayload;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import static net.voxelindustry.voidheart.VoidHeart.*;

public class VoidHeartClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        ModelLoadingPlugin.register(new VoidHeartModelLoadingPlugin());

        // A forge source pillar raises its item to the forge item height so the beam runs level
        BlockEntityRenderers.register(VoidHeartTiles.VOID_PILLAR, ctx -> new FloatingItemRender<VoidPillarTile>(ctx, VoidPillarTile::getStack,
                pillar -> pillar.isForgeSource() ? 1.2 : 1.1));
        BlockEntityRenderers.register(VoidHeartTiles.VOID_ALTAR, VoidAltarRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.VOID_HEART, VoidHeartRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.EXPERIENCE_SKULL, ExperienceSkullRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.EYE_BOTTLE, EyeBottleRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.SHATTER_FORGE, ctx -> new ShatterForgeRender(ctx));
        BlockEntityRenderers.register(VoidHeartTiles.RAVENOUS_COLLECTOR, RavenousCollectorRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.PERMEABLE_BARRIER, PermeableBarrierRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.BEHOLDER, BeholderRender::new);

        ModelLayerRegistry.registerModelLayer(ShoulderEyeModel.LAYER, ShoulderEyeModel::createBodyLayer);
        EntityRendererRegistry.register(VoidHeartEntities.SHOULDER_EYE, ShoulderEyeRender::new);
        ShoulderEyeEntity.hiddenFromLocalPick = eye ->
        {
            var player = Minecraft.getInstance().player;
            return player != null && player.getId() == eye.getOwnerId() && !player.isShiftKeyDown();
        };

        MenuScreens.register(VoidHeartMenus.PERMEABLE_BARRIER, PermeableBarrierScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(BarrierWavePayload.TYPE, BarrierWaveTracker::handle);
        LevelRenderEvents.COLLECT_SUBMITS.register(WeaverPreviewRender::collectSubmits);
        BlockEntityRenderers.register(VoidHeartTiles.RAVENOUS_RELAY, RavenousRelayRender::new);

        ParticleGroupRegistry.register(GeometryParticle.GEOMETRY, GeometryParticle.Group::new);

        ClientPlayNetworking.registerGlobalReceiver(LeyDebugPayload.TYPE, LeyDebugRender::receive);
        ClientPlayNetworking.registerGlobalReceiver(AnchorPayloads.Waypoints.TYPE, AnchorWaypointRender::receive);
        ClientPlayNetworking.registerGlobalReceiver(AnchorPayloads.TravelMap.TYPE,
                (payload, context) -> context.client().setScreenAndShow(new TravelMapScreen(payload)));
        ClientPlayNetworking.registerGlobalReceiver(AnchorPayloads.Terrain.TYPE, (payload, context) ->
        {
            if (context.client().gui.screen() instanceof TravelMapScreen screen && screen.sourceId() == payload.source())
                screen.setTerrain(payload);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
        {
            LeyDebugRender.clear();
            AnchorWaypointRender.clear();
            OculusRenderer.clear();
            OculusControls.clear();
            OculusMarkers.clear();
            OculusLeyLayer.clear();
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(OculusLeyLayer::collectSubmits);
        ClientPlayNetworking.registerGlobalReceiver(OculusLeyPayloads.Layer.TYPE, OculusLeyLayer::receive);
        LevelRenderEvents.COLLECT_SUBMITS.register(OculusControls::collectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(OculusMarkers::collectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(OculusFigurine::collectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(OculusSky::collectSubmits);
        ClientPlayNetworking.registerGlobalReceiver(OculusEntityPayloads.Markers.TYPE, OculusMarkers::receive);

        ClientPlayNetworking.registerGlobalReceiver(OculusChunkPayloads.Sections.TYPE, OculusRenderer::receiveSections);
        ClientPlayNetworking.registerGlobalReceiver(OculusSightPayloads.Relief.TYPE, OculusRenderer::receiveRelief);
        ClientPlayNetworking.registerGlobalReceiver(OculusSightPayloads.Sight.TYPE, OculusBeholderLayer::receive);
        LevelRenderEvents.COLLECT_SUBMITS.register(OculusBeholderLayer::collectSubmits);
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register(OculusRenderer::onLoad);
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(OculusRenderer::onUnload);
        ClientTickEvents.END_CLIENT_TICK.register(OculusRenderer::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(LeyDebugRender::collectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(AnchorWaypointRender::collectSubmits);

        var particles = ParticleProviderRegistry.getInstance();
        particles.register(
                ALTAR_VOID_PARTICLE,
                spriteSet ->
                        (options, level, x, y, z, xAux, yAux, zAux, random) ->
                                new AltarVoidFillingParticle(level, x, y, z, xAux, yAux, zAux, options.getSpeed(), spriteSet));

        particles.register(
                ALTAR_ITEM_PARTICLE,
                (options, level, x, y, z, xAux, yAux, zAux, random) ->
                        new AltarItemParticle(level, x, y, z, xAux, yAux, zAux, options.getStack(), options.getFirstPointBezier(), options.getSecondPointBezier()));

        particles.register(
                PORTAL_FRAME_PARTICLE,
                spriteSet ->
                        (options, level, x, y, z, xAux, yAux, zAux, random) ->
                                new PortalFrameParticle(level, x, y, z, spriteSet, options.direction(), options.width(), options.height()));

        particles.register(
                SHATTER_FORGE_RUNE_PARTICLE,
                spriteSet ->
                        (options, level, x, y, z, xAux, yAux, zAux, random) ->
                                new ShatterForgeRuneParticle(level, x, y, z, spriteSet, options.axis(), options.forge(), options.index()));

        particles.register(
                EYE_BEAM_PARTICLE,
                spriteSet ->
                        (options, level, x, y, z, xAux, yAux, zAux, random) ->
                                new EyeBeamParticle(level, x, y, z, xAux, yAux, zAux, spriteSet.get(random)));

        particles.register(
                SHATTER_FORGE_ITEM_PARTICLE,
                (options, level, x, y, z, xAux, yAux, zAux, random) ->
                        new ShatterForgeItemParticle(level, x, y, z, xAux, yAux, zAux, options.input(), options.output()));
    }
}
