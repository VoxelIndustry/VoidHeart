package net.voxelindustry.voidheart;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.voxelindustry.voidheart.common.VoidHeartTicker;
import net.voxelindustry.voidheart.common.content.door.VoidDoorTeleporter;
import net.voxelindustry.voidheart.common.command.VoidHeartCommands;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.content.altar.AltarItemParticleEffect;
import net.voxelindustry.voidheart.common.content.altar.AltarVoidParticleEffect;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.oculus.OculusChunkPayloads;
import net.voxelindustry.voidheart.common.content.oculus.OculusChunkServer;
import net.voxelindustry.voidheart.common.content.oculus.OculusControlPayload;
import net.voxelindustry.voidheart.common.ley.LeyDebugPayload;
import net.voxelindustry.voidheart.common.ley.LeyDebugView;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.ley.anchor.AnchorPayloads;
import net.voxelindustry.voidheart.common.ley.anchor.AnchorTravel;
import net.voxelindustry.voidheart.common.ley.anchor.AnchorWaypoints;
import net.voxelindustry.voidheart.common.ley.anchor.OldAnchors;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameParticleEffect;
import net.voxelindustry.voidheart.common.content.portalframe.PortalLinkCriterion;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeItemParticleEffect;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeRuneParticleEffect;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartMenus;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import net.voxelindustry.voidheart.common.setup.VoidHeartWorld;
import net.voxelindustry.voidheart.common.world.VoidChunkGenerator;

import java.util.logging.Logger;

public class VoidHeart implements ModInitializer
{
    public static final String MODID = "voidheart";

    public static final Logger LOGGER = Logger.getLogger(MODID);

    public static CreativeModeTab ITEMGROUP;

    public static ResourceKey<Level> VOID_WORLD_KEY;
    public static ResourceKey<DimensionType> VOID_DIMENSION_KEY;
    public static ParticleType<AltarVoidParticleEffect> ALTAR_VOID_PARTICLE;
    public static ParticleType<AltarItemParticleEffect> ALTAR_ITEM_PARTICLE;
    public static ParticleType<PortalFrameParticleEffect> PORTAL_FRAME_PARTICLE;
    public static ParticleType<ShatterForgeRuneParticleEffect> SHATTER_FORGE_RUNE_PARTICLE;
    public static ParticleType<ShatterForgeItemParticleEffect> SHATTER_FORGE_ITEM_PARTICLE;

    public static PortalLinkCriterion PORTAL_LINK_CRITERION;

    @Override
    public void onInitialize()
    {
        VoidHeartConfig.load();

        VoidHeartDataComponents.registerComponents();
        VoidHeartBlocks.registerBlocks();
        VoidHeartItems.registerItems();

        VoidHeartTiles.registerTiles();
        VoidHeartMenus.registerMenus();

        ITEMGROUP = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MODID, "item_group"), FabricCreativeModeTab.builder()
                .icon(() -> new ItemStack(VoidHeartItems.VOID_HEART))
                .title(Component.translatable("itemGroup.voidheart.item_group"))
                .displayItems((parameters, output) -> VoidHeartItems.CREATIVE_TAB_ITEMS.forEach(output::accept))
                .build());

        PORTAL_LINK_CRITERION = Registry.register(BuiltInRegistries.TRIGGER_TYPES, PortalLinkCriterion.ID, new PortalLinkCriterion());

        VoidHeartRecipes.registerRecipes();
        VoidHeartWorld.registerGeneration();

        ServerTickEvents.START_SERVER_TICK.register(VoidHeartTicker::tickServer);
        ServerTickEvents.START_LEVEL_TICK.register(VoidHeartTicker::tickWorld);
        ServerTickEvents.END_SERVER_TICK.register(VoidDoorTeleporter::tickServer);

        CommandRegistrationCallback.EVENT.register(VoidHeartCommands::register);
        PayloadTypeRegistry.clientboundPlay().register(LeyDebugPayload.TYPE, LeyDebugPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(LeyDebugView::tickServer);

        PayloadTypeRegistry.serverboundPlay().register(OculusControlPayload.TYPE, OculusControlPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OculusControlPayload.TYPE, OculusControlPayload::handle);
        OculusChunkPayloads.register();
        ServerPlayNetworking.registerGlobalReceiver(OculusChunkPayloads.Request.TYPE, OculusChunkServer::handle);
        ServerTickEvents.END_SERVER_TICK.register(OculusChunkServer::tick);

        AnchorPayloads.register();
        ServerPlayNetworking.registerGlobalReceiver(AnchorPayloads.TravelRequest.TYPE, AnchorTravel::handle);
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> OldAnchors.get(level).ifPresent(anchors -> anchors.onChunkLoad(chunk)));
        ServerTickEvents.END_SERVER_TICK.register(AnchorWaypoints::tickServer);

        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
        {
            LeyLattice.clear();
            LeyDebugView.clear();
            OldAnchors.clear();
            AnchorWaypoints.clear();
            AnchorTravel.clear();
            OculusChunkServer.clear();
        });

        VOID_WORLD_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(MODID, "void"));
        VOID_DIMENSION_KEY = ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.fromNamespaceAndPath(MODID, "void"));

        Registry.register(
                BuiltInRegistries.CHUNK_GENERATOR,
                Identifier.fromNamespaceAndPath(MODID, "void_gen"),
                VoidChunkGenerator.CODEC);

        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath(MODID, "altar_void"), ALTAR_VOID_PARTICLE = FabricParticleTypes.complex(true, AltarVoidParticleEffect.CODEC, AltarVoidParticleEffect.STREAM_CODEC));
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath(MODID, "altar_item"), ALTAR_ITEM_PARTICLE = FabricParticleTypes.complex(true, AltarItemParticleEffect.CODEC, AltarItemParticleEffect.STREAM_CODEC));
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath(MODID, "portal_frame"), PORTAL_FRAME_PARTICLE = FabricParticleTypes.complex(true, PortalFrameParticleEffect.CODEC, PortalFrameParticleEffect.STREAM_CODEC));
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath(MODID, "shatter_forge_rune"), SHATTER_FORGE_RUNE_PARTICLE = FabricParticleTypes.complex(true, ShatterForgeRuneParticleEffect.CODEC, ShatterForgeRuneParticleEffect.STREAM_CODEC));
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath(MODID, "shatter_forge_item"), SHATTER_FORGE_ITEM_PARTICLE = FabricParticleTypes.complex(true, ShatterForgeItemParticleEffect.CODEC, ShatterForgeItemParticleEffect.STREAM_CODEC));

        EssenceRegistry.bootstrap();
    }
}
