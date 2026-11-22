package net.voxelindustry.voidheart;

import net.voxelindustry.voidheart.client.render.RavenousBitePreview;
import net.voxelindustry.voidheart.client.render.sin.SinClientModels;
import net.voxelindustry.voidheart.client.render.sin.SinFlare;
import net.voxelindustry.voidheart.client.render.sin.SinTooltip;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleGroupRegistry;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.particle.FlameParticle;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.voxelindustry.voidheart.client.render.BarrierWaveTracker;
import net.voxelindustry.voidheart.client.ClarityFog;
import net.voxelindustry.voidheart.client.OilEmbers;
import net.voxelindustry.voidheart.client.levitation.LevitationClient;
import net.voxelindustry.voidheart.client.BrewTooltip;
import net.voxelindustry.voidheart.client.InsightMemory;
import net.voxelindustry.voidheart.client.apparatus.ShieldHits;
import eu.pb4.trinkets.api.client.TrinketRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.voxelindustry.voidheart.client.apparatus.ApparatusWornRender;
import net.voxelindustry.voidheart.client.apparatus.ClientApparatusTooltip;
import net.voxelindustry.voidheart.client.armor.ArmorHud;
import net.voxelindustry.voidheart.client.armor.ArmorSight;
import net.voxelindustry.voidheart.client.armor.ArmorWings;
import net.voxelindustry.voidheart.client.render.mob.DormantTreantRender;
import net.voxelindustry.voidheart.client.apparatus.ShieldHud;
import net.voxelindustry.voidheart.client.secondheart.HeartBurstRender;
import net.voxelindustry.voidheart.client.render.crystal.CrystalCradleRender;
import net.voxelindustry.voidheart.client.render.crystal.CrystalHabitTooltip;
import net.voxelindustry.voidheart.client.secondheart.HeartCradleRender;
import net.voxelindustry.voidheart.common.content.crystal.CrystalContent;
import net.voxelindustry.voidheart.client.secondheart.VentricleTearRender;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.TwinVentricleContent;
import net.voxelindustry.voidheart.client.secondheart.HeartDissolve;
import net.voxelindustry.voidheart.client.secondheart.HeartMoteParticle;
import net.voxelindustry.voidheart.client.secondheart.SecondHeartHud;
import net.voxelindustry.voidheart.client.render.temple.MosaicDoorHint;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusTooltip;
import net.voxelindustry.voidheart.client.apparatus.ShieldRuneRender;
import net.voxelindustry.voidheart.client.screen.ApparatusScreen;
import net.voxelindustry.voidheart.client.screen.VoidArmorScreen;
import net.voxelindustry.voidheart.client.screen.HoardScreen;
import net.voxelindustry.voidheart.common.content.hoard.HoardContent;
import net.voxelindustry.voidheart.common.content.hoard.HoardMenu;
import net.voxelindustry.voidheart.common.content.hoard.HoardPayloads;
import net.voxelindustry.voidheart.common.content.works.WorksPayloads;
import net.voxelindustry.voidheart.client.render.armor.ParkedArmorRender;
import net.voxelindustry.voidheart.client.render.armor.VoidArmorBaseRender;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorContent;
import net.voxelindustry.voidheart.client.screen.BlueprintScreen;
import net.voxelindustry.voidheart.client.render.blight.BlightEdgeRender;
import net.voxelindustry.voidheart.client.render.blight.HarvestWraithRender;
import net.voxelindustry.voidheart.client.render.blight.EclipseThreadRender;
import net.voxelindustry.voidheart.client.render.blight.HoleLook;
import net.voxelindustry.voidheart.client.render.blight.PunctureRender;
import net.voxelindustry.voidheart.common.content.blight.BlueprintPayloads;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldPayloads;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatusItem;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.voxelindustry.voidheart.client.OilTooltip;
import net.voxelindustry.voidheart.client.render.PermeableBarrierRender;
import net.voxelindustry.voidheart.client.render.WeaverPreviewRender;
import net.voxelindustry.voidheart.client.render.mob.VoidMobRenderers;
import net.voxelindustry.voidheart.client.ArmillaryTremor;
import net.voxelindustry.voidheart.client.render.ArmillaryRender;
import net.voxelindustry.voidheart.client.render.ArmillaryRiftRender;
import net.voxelindustry.voidheart.client.screen.ArmillaryScreen;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryPayloads;
import net.voxelindustry.voidheart.common.content.sanctum.BlessedCradleTile;
import net.voxelindustry.voidheart.common.content.repair.MendingAltarTile;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassages;
import net.voxelindustry.voidheart.client.screen.PermeableBarrierScreen;
import net.voxelindustry.voidheart.client.screen.ResearchScreen;
import net.voxelindustry.voidheart.common.content.research.KnowledgePayloads;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.voxelindustry.voidheart.common.content.permeablebarrier.BarrierWavePayload;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartMenus;

import java.util.List;
import net.voxelindustry.voidheart.client.curse.CurseDirector;
import net.voxelindustry.voidheart.client.render.AvengingReachRender;
import net.voxelindustry.voidheart.client.render.KleinStarRender;
import net.voxelindustry.voidheart.client.render.WallPassageRender;
import net.voxelindustry.voidheart.client.model.VoidHeartModelLoadingPlugin;
import net.voxelindustry.voidheart.client.particle.DepthsShoals;
import net.voxelindustry.voidheart.client.particle.MarineSnowParticle;
import net.voxelindustry.voidheart.client.particle.ShoalSpeckParticle;
import net.voxelindustry.voidheart.client.particle.SmokerGlowParticle;
import net.voxelindustry.voidheart.client.particle.SmokerPlumeParticle;
import net.voxelindustry.voidheart.client.particle.VoidFireParticle;
import net.voxelindustry.voidheart.client.particle.AltarExperienceParticle;
import net.voxelindustry.voidheart.client.particle.AltarItemParticle;
import net.voxelindustry.voidheart.client.particle.AltarVoidFillingParticle;
import net.voxelindustry.voidheart.client.particle.EyeBeamParticle;
import net.voxelindustry.voidheart.client.model.ShoulderEyeModel;
import net.voxelindustry.voidheart.client.model.VigilFigureModel;
import net.voxelindustry.voidheart.client.render.ShoulderEyeRender;
import net.voxelindustry.voidheart.common.content.shouldereye.ShoulderEyeEntity;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.client.particle.GeometryParticle;
import net.voxelindustry.voidheart.client.particle.PortalFrameParticle;
import net.voxelindustry.voidheart.client.particle.ShatterForgeExperienceParticle;
import net.voxelindustry.voidheart.client.particle.KnowledgeGlyphParticle;
import net.voxelindustry.voidheart.client.particle.KnowledgeMoteParticle;
import net.voxelindustry.voidheart.client.particle.ShatterForgeItemParticle;
import net.voxelindustry.voidheart.client.particle.ShatterForgeRuneParticle;
import net.voxelindustry.voidheart.client.render.ExperienceSkullRender;
import net.voxelindustry.voidheart.client.render.EyeBottleRender;
import net.voxelindustry.voidheart.client.render.FloatingItemRender;
import net.voxelindustry.voidheart.client.render.AnchorWaypointRender;
import net.voxelindustry.voidheart.client.render.LeyDebugRender;
import net.voxelindustry.voidheart.client.render.divining.LeyDiviningRender;
import net.voxelindustry.voidheart.client.render.energy.EnergyClientSetup;
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
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.voxelindustry.voidheart.client.render.ScenePictureRender;
import net.voxelindustry.voidheart.client.render.BeholderRender;
import net.voxelindustry.voidheart.client.screen.TravelMapScreen;
import net.voxelindustry.voidheart.common.ley.anchor.AnchorPayloads;
import net.voxelindustry.voidheart.client.render.ShatterForgeRender;
import net.voxelindustry.voidheart.client.render.RavenousCollectorRender;
import net.voxelindustry.voidheart.client.render.CogitantRender;
import net.voxelindustry.voidheart.client.render.KnowledgeColumnRender;
import net.voxelindustry.voidheart.client.render.MeatProxyRender;
import net.voxelindustry.voidheart.client.render.VoidLoomRender;
import net.voxelindustry.voidheart.client.render.KnowledgeRepositoryRender;
import net.voxelindustry.voidheart.client.render.RavenousRelayRender;
import net.voxelindustry.voidheart.client.render.RavenousStorageRender;
import net.voxelindustry.voidheart.client.render.RavenousStorageMatrixRender;
import net.voxelindustry.voidheart.client.render.GreatRavenousStorageRender;
import net.voxelindustry.voidheart.client.render.RelayLinkPreviewRender;
import net.voxelindustry.voidheart.client.render.CabinetHighlightRender;
import net.voxelindustry.voidheart.client.render.temple.MosaicStare;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.voxelindustry.voidheart.client.render.temple.MosaicWakeRender;
import net.voxelindustry.voidheart.client.render.KnowledgeColumnOutline;
import net.voxelindustry.voidheart.client.render.tormenter.MalignantTormenterRender;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.client.render.tormenter.MalignantTormenterItemRender;
import net.voxelindustry.voidheart.client.render.tormenter.TormenterHeads;
import net.voxelindustry.voidheart.client.render.VoidAltarRender;
import net.voxelindustry.voidheart.client.render.VoidHeartRender;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.content.athanor.CrucibleTile;
import net.voxelindustry.voidheart.client.render.AthanorRender;
import net.voxelindustry.voidheart.client.render.PlacedFlasksRender;
import net.voxelindustry.voidheart.common.content.divining.LeySightPayload;
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
        PictureInPictureRendererRegistry.register(context -> new ScenePictureRender());

        // A forge source pillar raises its item to the forge item height so the beam runs level
        BlockEntityRenderers.register(VoidHeartTiles.VOID_PILLAR, ctx -> new FloatingItemRender<VoidPillarTile>(ctx, VoidPillarTile::getStack,
                pillar -> pillar.isForgeSource() ? 1.2 : 1.1));
        BlockEntityRenderers.register(VoidHeartTiles.BLESSED_CRADLE, ctx -> new FloatingItemRender<BlessedCradleTile>(ctx, BlessedCradleTile::getContents, 0.85));
        BlockEntityRenderers.register(VoidHeartTiles.VOID_ALTAR, VoidAltarRender::new);
        BlockEntityRenderers.register(net.voxelindustry.voidheart.common.content.hoard.HoardContent.HOARD_COFFER_TILE,
                net.voxelindustry.voidheart.client.render.HoardCofferRender::new);
        // The tool floats where the void altar's item does, the mending altar being its shape in amber
        BlockEntityRenderers.register(VoidHeartTiles.MENDING_ALTAR, ctx -> new FloatingItemRender<MendingAltarTile>(ctx, MendingAltarTile::getTool, 1 + 2 / 16D));
        BlockEntityRenderers.register(VoidHeartTiles.DORMANT_TREANT, DormantTreantRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.ATHANOR, AthanorRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.PLACED_FLASKS, PlacedFlasksRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.CRUCIBLE, ctx -> new FloatingItemRender<CrucibleTile>(ctx, CrucibleTile::getStack, 0.8));
        BlockEntityRenderers.register(VoidHeartTiles.VOID_HEART, VoidHeartRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.EXPERIENCE_SKULL, ExperienceSkullRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.EYE_BOTTLE, EyeBottleRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.SHATTER_FORGE, ctx -> new ShatterForgeRender(ctx));
        BlockEntityRenderers.register(VoidHeartTiles.RAVENOUS_COLLECTOR, RavenousCollectorRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.PERMEABLE_BARRIER, PermeableBarrierRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.BEHOLDER, BeholderRender::new);
        EnergyClientSetup.register();

        ModelLayerRegistry.registerModelLayer(ShoulderEyeModel.LAYER, ShoulderEyeModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(VigilFigureModel.LAYER, VigilFigureModel::createBodyLayer);
        EntityRendererRegistry.register(VoidHeartEntities.SHOULDER_EYE, ShoulderEyeRender::new);
        net.voxelindustry.voidheart.client.render.mob.VoidMobRenderers.register();
        ShoulderEyeEntity.hiddenFromLocalPick = eye ->
        {
            var player = Minecraft.getInstance().player;
            return player != null && player.getId() == eye.getOwnerId();
        };

        MenuScreens.register(VoidHeartMenus.PERMEABLE_BARRIER, PermeableBarrierScreen::new);
        MenuScreens.register(VoidHeartMenus.KNOWLEDGE_REPOSITORY, ResearchScreen::new);
        MenuScreens.register(VoidHeartMenus.ARMILLARY, ArmillaryScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(ArmillaryPayloads.View.TYPE, (payload, context) -> ArmillaryScreen.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(ArmillaryPayloads.Tremor.TYPE, (payload, context) -> ArmillaryTremor.receive(payload));
        ClientTickEvents.END_CLIENT_TICK.register(ArmillaryTremor::tickClient);
        BlockEntityRenderers.register(VoidHeartTiles.ARMILLARY, ArmillaryRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.ARMILLARY_RIFT, ArmillaryRiftRender::new);
        ClientPlayNetworking.registerGlobalReceiver(KnowledgePayloads.Snapshot.TYPE, (payload, context) -> ResearchScreen.snapshot = payload);
        ClientPlayNetworking.registerGlobalReceiver(KnowledgePayloads.Recipes.TYPE, (payload, context) -> ResearchScreen.recipeSheets = payload.sheets());
        ItemTooltipCallback.EVENT.register(ResearchScreen::appendKnowledgeTooltip);
        ItemTooltipCallback.EVENT.register(OilTooltip::append);
        ItemTooltipCallback.EVENT.register(BrewTooltip::append);
        ClientTickEvents.END_CLIENT_TICK.register(OilEmbers::tick);
        LevitationClient.register();
        ClientTickEvents.END_CLIENT_TICK.register(ClarityFog::tick);
        ClientTickEvents.END_CLIENT_TICK.register(InsightMemory::tick);
        ClientTickEvents.END_CLIENT_TICK.register(ArmorSight::tick);
        ArmorWings.register();

        ClientPlayNetworking.registerGlobalReceiver(ShieldPayloads.Hit.TYPE, (payload, context) -> ShieldHits.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(BlueprintPayloads.Offer.TYPE, (payload, context) -> BlueprintScreen.receive(payload));
        ClientTickEvents.END_CLIENT_TICK.register(ShieldHits::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(ShieldRuneRender::collectSubmits);
        HudElementRegistry.attachElementAfter(VanillaHudElements.ARMOR_BAR, Identifier.fromNamespaceAndPath(VoidHeart.MODID, "shield"), ShieldHud::extract);
        HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, ArmorHud::replacing);
        HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, Identifier.fromNamespaceAndPath(VoidHeart.MODID, "void_armor_charge"), ArmorHud::charge);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath(VoidHeart.MODID, "void_armor_mask"), ArmorHud::maskEdge);
        HudElementRegistry.attachElementAfter(VanillaHudElements.HEALTH_BAR, Identifier.fromNamespaceAndPath(VoidHeart.MODID, "second_heart"), SecondHeartHud::extractIcon);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath(VoidHeart.MODID, "second_heart_view"), SecondHeartHud::extractView);
        ClientTickEvents.END_CLIENT_TICK.register(SecondHeartHud::tick);
        ClientTickEvents.END_CLIENT_TICK.register(HeartDissolve::tickMotes);
        ClientTickEvents.END_CLIENT_TICK.register(HeartBurstRender::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(HeartBurstRender::collectSubmits);
        BlockEntityRenderers.register(TwinVentricleContent.HEART_CRADLE_TILE, HeartCradleRender::new);
        EntityRendererRegistry.register(TwinVentricleContent.VENTRICLE_TEAR, VentricleTearRender::new);
        EntityRendererRegistry.register(VoidArmorContent.PARKED_ARMOR, ParkedArmorRender::new);
        BlockEntityRenderers.register(VoidArmorContent.VOID_ARMOR_BASE_TILE, VoidArmorBaseRender::new);
        MenuScreens.register(VoidHeartMenus.APPARATUS, ApparatusScreen::new);
        MenuScreens.register(VoidArmorContent.VOID_ARMOR_MENU, VoidArmorScreen::new);
        MenuScreens.register(HoardContent.HOARD_MENU, HoardScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(HoardPayloads.Page.TYPE, (payload, context) ->
        {
            if (context.player().containerMenu instanceof HoardMenu menu && menu.containerId == payload.containerId())
                menu.receive(payload);
        });
        ClientPlayNetworking.registerGlobalReceiver(WorksPayloads.Makeable.TYPE, (payload, context) ->
        {
            if (context.player().containerMenu instanceof HoardMenu menu && menu.containerId == payload.containerId())
                menu.receive(payload);
        });
        ClientPlayNetworking.registerGlobalReceiver(WorksPayloads.Plan.TYPE, (payload, context) ->
        {
            if (context.player().containerMenu instanceof HoardMenu menu && menu.containerId == payload.containerId())
                menu.receive(payload);
        });
        ClientPlayNetworking.registerGlobalReceiver(WorksPayloads.Jobs.TYPE, (payload, context) ->
        {
            if (context.player().containerMenu instanceof HoardMenu menu && menu.containerId == payload.containerId())
                menu.receive(payload);
        });
        ClientPlayNetworking.registerGlobalReceiver(WorksPayloads.Roster.TYPE, (payload, context) ->
        {
            if (context.player().containerMenu instanceof HoardMenu menu && menu.containerId == payload.containerId())
                menu.receive(payload);
        });
        ShieldingApparatusItem.openFromSlot = holder -> ClientPlayNetworking.send(new ShieldPayloads.Open(holder));
        ClientTooltipComponentCallback.EVENT.register(data -> data instanceof ApparatusTooltip tooltip ? new ClientApparatusTooltip(tooltip) : null);
        ClientTooltipComponentCallback.EVENT.register(data -> data instanceof net.voxelindustry.voidheart.client.screen.ItemRowsTooltip rows
                ? new net.voxelindustry.voidheart.client.screen.ItemRowsTooltip.Client(rows) : null);
        ModelLayerRegistry.registerModelLayer(ApparatusWornRender.LAYER, ApparatusWornRender::createLayer);
        for (var apparatus : List.of(VoidHeartItems.LESSER_SHIELDING_APPARATUS, VoidHeartItems.GREATER_SHIELDING_APPARATUS, VoidHeartItems.SUPREME_SHIELDING_APPARATUS))
            TrinketRendererRegistry.registerRenderer(apparatus, ApparatusWornRender::new);
        ClientPlayNetworking.registerGlobalReceiver(BarrierWavePayload.TYPE, BarrierWaveTracker::handle);
        LevelRenderEvents.COLLECT_SUBMITS.register(WeaverPreviewRender::collectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(RelayLinkPreviewRender::collectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(CabinetHighlightRender::collectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(RavenousBitePreview::collectSubmits);
        ClientTickEvents.END_CLIENT_TICK.register(MosaicStare::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(MosaicWakeRender::collectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(PunctureRender::collectSubmits);
        HarvestWraithRender.register();
        HoleLook.register();
        LevelRenderEvents.COLLECT_SUBMITS.register(EclipseThreadRender::collectSubmits);
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Identifier.fromNamespaceAndPath(VoidHeart.MODID, "temple_mosaic"),
                (ResourceManagerReloadListener) resources -> MosaicStare.reloaded());
        LevelRenderEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register(KnowledgeColumnOutline::afterExtraction);
        BlockEntityRenderers.register(VoidHeartTiles.RAVENOUS_RELAY, RavenousRelayRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.RAVENOUS_STORAGE, RavenousStorageRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.RAVENOUS_STORAGE_MATRIX, RavenousStorageMatrixRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.GREAT_RAVENOUS_STORAGE, GreatRavenousStorageRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.MALIGNANT_TORMENTER, MalignantTormenterRender::new);
        SpecialModelRenderers.ID_MAPPER.put(Identifier.fromNamespaceAndPath(VoidHeart.MODID, "malignant_tormenter"), MalignantTormenterItemRender.Unbaked.MAP_CODEC);
        SpecialModelRenderers.ID_MAPPER.put(Identifier.fromNamespaceAndPath(VoidHeart.MODID, "blight_edge"), BlightEdgeRender.Unbaked.MAP_CODEC);
        SinClientModels.register();
        SinFlare.register();
        SinTooltip.register();
        CrystalHabitTooltip.register();
        BlockEntityRenderers.register(CrystalContent.CRYSTAL_CRADLE_TILE, CrystalCradleRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.KNOWLEDGE_COLUMN, KnowledgeColumnRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.COGITANT, CogitantRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.MEAT_PROXY, MeatProxyRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.VOID_LOOM, VoidLoomRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.KNOWLEDGE_REPOSITORY, KnowledgeRepositoryRender::new);

        ParticleGroupRegistry.register(GeometryParticle.GEOMETRY, GeometryParticle.Group::new);

        ClientPlayNetworking.registerGlobalReceiver(LeyDebugPayload.TYPE, LeyDebugRender::receive);
        ClientPlayNetworking.registerGlobalReceiver(LeySightPayload.TYPE, LeyDiviningRender::receive);
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
            LeyDiviningRender.clear();
            AnchorWaypointRender.clear();
            ArmillaryTremor.clear();
            OculusRenderer.clear();
            OculusControls.clear();
            OculusMarkers.clear();
            OculusLeyLayer.clear();
            CabinetHighlightRender.clear();
            MosaicStare.clear();
            TormenterHeads.clear();
            WallPassages.clearClient();
            AvengingReachRender.clear();
            KleinStarRender.leave();
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(OculusLeyLayer::collectSubmits);
        ClientPlayNetworking.registerGlobalReceiver(OculusLeyPayloads.Layer.TYPE, OculusLeyLayer::receive);
        LevelRenderEvents.COLLECT_SUBMITS.register(OculusControls::collectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(OculusMarkers::collectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(OculusFigurine::collectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(OculusSky::collectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(net.voxelindustry.voidheart.client.render.oculus.ReckoningHologram::collectSubmits);
        ClientPlayNetworking.registerGlobalReceiver(WorksPayloads.Shown.TYPE, net.voxelindustry.voidheart.client.render.oculus.ReckoningHologram::receive);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> net.voxelindustry.voidheart.client.render.oculus.ReckoningHologram.clear());
        LevelRenderEvents.COLLECT_SUBMITS.register(net.voxelindustry.voidheart.client.render.OfficiantFoldRender::collectSubmits);
        ClientPlayNetworking.registerGlobalReceiver(net.voxelindustry.voidheart.common.content.works.OfficiantFolds.Payload.TYPE,
                net.voxelindustry.voidheart.client.render.OfficiantFoldRender::receive);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> net.voxelindustry.voidheart.client.render.OfficiantFoldRender.clear());
        ClientPlayNetworking.registerGlobalReceiver(OculusEntityPayloads.Markers.TYPE, OculusMarkers::receive);

        ClientPlayNetworking.registerGlobalReceiver(OculusChunkPayloads.Sections.TYPE, OculusRenderer::receiveSections);
        ClientPlayNetworking.registerGlobalReceiver(net.voxelindustry.voidheart.common.content.oculus.OculusMemoryPayloads.Header.TYPE, OculusRenderer::receiveMemoryHeader);
        ClientPlayNetworking.registerGlobalReceiver(net.voxelindustry.voidheart.common.content.oculus.OculusMemoryPayloads.Land.TYPE, OculusRenderer::receiveMemoryLand);
        ClientPlayNetworking.registerGlobalReceiver(OculusSightPayloads.Relief.TYPE, OculusRenderer::receiveRelief);
        ClientPlayNetworking.registerGlobalReceiver(OculusSightPayloads.Sight.TYPE, OculusBeholderLayer::receive);
        LevelRenderEvents.COLLECT_SUBMITS.register(OculusBeholderLayer::collectSubmits);
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register(OculusRenderer::onLoad);
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(OculusRenderer::onUnload);
        ClientTickEvents.END_CLIENT_TICK.register(OculusRenderer::tick);
        ClientTickEvents.END_CLIENT_TICK.register(DepthsShoals::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(LeyDebugRender::collectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(LeyDiviningRender::collectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(AnchorWaypointRender::collectSubmits);

        CurseDirector.register();
        WallPassageRender.register();
        MosaicDoorHint.register();
        AvengingReachRender.register();
        KleinStarRender.register();

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
                KNOWLEDGE_MOTE_PARTICLE,
                spriteSet ->
                        (options, level, x, y, z, xAux, yAux, zAux, random) ->
                                new KnowledgeMoteParticle(level, x, y, z, xAux, yAux, zAux, spriteSet.get(random)));

        particles.register(
                KNOWLEDGE_GLYPH_PARTICLE,
                spriteSet ->
                        (options, level, x, y, z, xAux, yAux, zAux, random) ->
                                new KnowledgeGlyphParticle(level, x, y, z, xAux, yAux, zAux, spriteSet.get(random)));

        particles.register(
                EYE_BEAM_PARTICLE,
                spriteSet ->
                        (options, level, x, y, z, xAux, yAux, zAux, random) ->
                                new EyeBeamParticle(level, x, y, z, xAux, yAux, zAux, spriteSet.get(random)));

        particles.register(
                SHATTER_FORGE_ITEM_PARTICLE,
                (options, level, x, y, z, xAux, yAux, zAux, random) ->
                        new ShatterForgeItemParticle(level, x, y, z, xAux, yAux, zAux, options.input(), options.output()));

        particles.register(SMOKER_WISP_PARTICLE, spriteSet -> (options, level, x, y, z, xAux, yAux, zAux, random) ->
                SmokerPlumeParticle.wisp(level, x, y, z, yAux, spriteSet.get(random)));
        particles.register(SMOKER_PLUME_PARTICLE, spriteSet -> (options, level, x, y, z, xAux, yAux, zAux, random) ->
                SmokerPlumeParticle.plume(level, x, y, z, yAux, spriteSet.get(random)));
        particles.register(SMOKER_CORE_PARTICLE, spriteSet -> (options, level, x, y, z, xAux, yAux, zAux, random) ->
                SmokerGlowParticle.core(level, x, y, z, yAux, spriteSet.get(random)));
        particles.register(VOID_FLAME_PARTICLE, spriteSet -> (options, level, x, y, z, xAux, yAux, zAux, random) ->
                VoidFireParticle.flame(level, x, y, z, xAux, yAux, zAux, spriteSet.get(random)));
        particles.register(VOID_EMBER_PARTICLE, spriteSet -> (options, level, x, y, z, xAux, yAux, zAux, random) ->
                VoidFireParticle.ember(level, x, y, z, xAux, yAux, zAux, spriteSet.get(random)));
        particles.register(SMOKER_EMBER_PARTICLE, spriteSet -> (options, level, x, y, z, xAux, yAux, zAux, random) ->
                SmokerGlowParticle.ember(level, x, y, z, yAux, spriteSet.get(random)));
        particles.register(MARINE_SNOW_PARTICLE, spriteSet -> (options, level, x, y, z, xAux, yAux, zAux, random) ->
                new MarineSnowParticle(level, x, y, z, spriteSet.get(random)));
        particles.register(SHOAL_SPECK_PARTICLE, spriteSet -> (options, level, x, y, z, xAux, yAux, zAux, random) ->
                new ShoalSpeckParticle(level, x, y, z, xAux, yAux, zAux, spriteSet.get(random)));
        particles.register(SMALL_SOUL_FLAME_PARTICLE, FlameParticle.SmallFlameProvider::new);
        particles.register(VoidHeart.SECOND_HEART_MOTE_PARTICLE, spriteSet -> (options, level, x, y, z, xAux, yAux, zAux, random) ->
                HeartMoteParticle.lifting(level, x, y, z, xAux, yAux, zAux, spriteSet.get(random)));
        particles.register(VoidHeart.SECOND_HEART_INFALL_PARTICLE, spriteSet -> (options, level, x, y, z, xAux, yAux, zAux, random) ->
                HeartMoteParticle.infalling(level, x, y, z, xAux, yAux, zAux, spriteSet.get(random)));

        particles.register(
                SHATTER_FORGE_EXPERIENCE_PARTICLE,
                spriteSet ->
                        (options, level, x, y, z, xAux, yAux, zAux, random) ->
                                new ShatterForgeExperienceParticle(level, x, y, z, xAux, yAux, zAux, spriteSet.get(random)));
    }
}
