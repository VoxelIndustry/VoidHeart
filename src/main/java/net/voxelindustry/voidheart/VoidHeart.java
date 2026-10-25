package net.voxelindustry.voidheart;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.voxelindustry.voidheart.common.VoidHeartTicker;
import net.voxelindustry.voidheart.common.command.VoidHeartCommands;
import net.voxelindustry.voidheart.common.content.altar.AltarItemParticleEffect;
import net.voxelindustry.voidheart.common.content.altar.AltarVoidParticleEffect;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameParticleEffect;
import net.voxelindustry.voidheart.common.content.portalframe.PortalLinkCriterion;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeItemParticleEffect;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeRuneParticleEffect;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import net.voxelindustry.voidheart.common.setup.VoidHeartWorld;
import net.voxelindustry.voidheart.common.world.VoidChunkGenerator;

import java.util.logging.Logger;

public class VoidHeart implements ModInitializer
{
    public static final String MODID = "voidheart";

    public static final Logger LOGGER = Logger.getLogger(MODID);

    public static CreativeModeTab ITEMGROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(VoidHeartItems.VOID_HEART))
            .title(Component.translatable("itemGroup.voidheart.item_group"))
            .build();

    public static ResourceKey<Level> VOID_WORLD_KEY;
    public static ResourceKey<DimensionType> VOID_DIMENSION_KEY;
    public static ParticleType<AltarVoidParticleEffect> ALTAR_VOID_PARTICLE;
    public static ParticleType<AltarItemParticleEffect> ALTAR_ITEM_PARTICLE;
    public static ParticleType<PortalFrameParticleEffect> PORTAL_FRAME_PARTICLE;
    public static ParticleType<ShatterForgeRuneParticleEffect> SHATTER_FORGE_RUNE_PARTICLE;
    public static ParticleType<ShatterForgeItemParticleEffect> SHATTER_FORGE_ITEM_PARTICLE;

    public static PortalLinkCriterion PORTAL_LINK_CRITERION = CriteriaTriggers.register(new PortalLinkCriterion());

    @Override
    public void onInitialize()
    {
        VoidHeartBlocks.registerBlocks();
        VoidHeartItems.registerItems();

        VoidHeartTiles.registerTiles();

        VoidHeartRecipes.registerRecipes();
        VoidHeartWorld.registerGeneration();

        ServerTickEvents.START_SERVER_TICK.register(VoidHeartTicker::tickServer);
        ServerTickEvents.START_WORLD_TICK.register(VoidHeartTicker::tickWorld);

        CommandRegistrationCallback.EVENT.register(VoidHeartCommands::register);

        VOID_WORLD_KEY = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(MODID, "void"));
        VOID_DIMENSION_KEY = ResourceKey.create(Registries.DIMENSION_TYPE, new ResourceLocation(MODID, "void"));

        Registry.register(
                BuiltInRegistries.CHUNK_GENERATOR,
                new ResourceLocation(MODID, "void_gen"),
                VoidChunkGenerator.CODEC);

        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                new ResourceLocation(MODID, "altar_void"), ALTAR_VOID_PARTICLE = FabricParticleTypes.complex(true, AltarVoidParticleEffect.PARAMETERS_FACTORY));
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                new ResourceLocation(MODID, "altar_item"), ALTAR_ITEM_PARTICLE = FabricParticleTypes.complex(true, AltarItemParticleEffect.PARAMETERS_FACTORY));
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                new ResourceLocation(MODID, "portal_frame"), PORTAL_FRAME_PARTICLE = FabricParticleTypes.complex(true, PortalFrameParticleEffect.PARAMETERS_FACTORY));
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                new ResourceLocation(MODID, "shatter_forge_rune"), SHATTER_FORGE_RUNE_PARTICLE = FabricParticleTypes.complex(true, ShatterForgeRuneParticleEffect.PARAMETERS_FACTORY));
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                new ResourceLocation(MODID, "shatter_forge_item"), SHATTER_FORGE_ITEM_PARTICLE = FabricParticleTypes.complex(true, ShatterForgeItemParticleEffect.PARAMETERS_FACTORY));

        EssenceRegistry.bootstrap();
    }
}
