package net.voxelindustry.voidheart;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleGroupRegistry;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.voxelindustry.voidheart.client.render.BarrierWaveTracker;
import net.voxelindustry.voidheart.client.render.PermeableBarrierRender;
import net.voxelindustry.voidheart.client.render.WeaverPreviewRender;
import net.voxelindustry.voidheart.client.screen.PermeableBarrierScreen;
import net.voxelindustry.voidheart.common.content.permeablebarrier.BarrierWavePayload;
import net.voxelindustry.voidheart.common.setup.VoidHeartMenus;
import net.voxelindustry.voidheart.client.model.VoidHeartModelLoadingPlugin;
import net.voxelindustry.voidheart.client.particle.AltarExperienceParticle;
import net.voxelindustry.voidheart.client.particle.AltarItemParticle;
import net.voxelindustry.voidheart.client.particle.AltarVoidFillingParticle;
import net.voxelindustry.voidheart.client.particle.GeometryParticle;
import net.voxelindustry.voidheart.client.particle.PortalFrameParticle;
import net.voxelindustry.voidheart.client.particle.ShatterForgeExperienceParticle;
import net.voxelindustry.voidheart.client.particle.ShatterForgeItemParticle;
import net.voxelindustry.voidheart.client.particle.ShatterForgeRuneParticle;
import net.voxelindustry.voidheart.client.render.ExperienceSkullRender;
import net.voxelindustry.voidheart.client.render.EyeBottleRender;
import net.voxelindustry.voidheart.client.render.FloatingItemRender;
import net.voxelindustry.voidheart.client.render.AnchorWaypointRender;
import net.voxelindustry.voidheart.client.render.LeyDebugRender;
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
        });
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
                ALTAR_EXPERIENCE_PARTICLE,
                spriteSet ->
                        (options, level, x, y, z, xAux, yAux, zAux, random) ->
                                new AltarExperienceParticle(level, x, y, z, xAux, yAux, zAux, options.firstPointBezier(), options.secondPointBezier(), spriteSet.get(random)));

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
                SHATTER_FORGE_ITEM_PARTICLE,
                (options, level, x, y, z, xAux, yAux, zAux, random) ->
                        new ShatterForgeItemParticle(level, x, y, z, xAux, yAux, zAux, options.input(), options.output()));

        particles.register(
                SHATTER_FORGE_EXPERIENCE_PARTICLE,
                spriteSet ->
                        (options, level, x, y, z, xAux, yAux, zAux, random) ->
                                new ShatterForgeExperienceParticle(level, x, y, z, xAux, yAux, zAux, spriteSet.get(random)));
    }
}
