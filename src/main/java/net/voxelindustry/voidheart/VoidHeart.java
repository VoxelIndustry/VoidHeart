package net.voxelindustry.voidheart;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.voxelindustry.voidheart.common.VoidHeartTicker;
import net.voxelindustry.voidheart.common.command.VoidHeartCommands;
import net.voxelindustry.voidheart.common.content.altar.AltarItemParticleEffect;
import net.voxelindustry.voidheart.common.content.altar.AltarVoidParticleEffect;
import net.voxelindustry.voidheart.common.content.altar.PortalFrameParticleEffect;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import net.voxelindustry.voidheart.common.setup.VoidHeartWorld;
import net.voxelindustry.voidheart.common.world.VoidChunkGenerator;

import java.util.logging.Logger;

public class VoidHeart implements ModInitializer
{
    public static final String MODID             = "voidheart";
    public static final String IMMERSIVE_PORTALS = "imm_ptl_core";

    public static final Logger LOGGER = Logger.getLogger(MODID);

    public static ItemGroup                               ITEMGROUP = FabricItemGroupBuilder.build(
            new Identifier(MODID, "item_group"),
            () -> new ItemStack(VoidHeartItems.VOID_HEART));
    public static RegistryKey<World>                      VOID_WORLD_KEY;
    public static RegistryKey<DimensionType>              VOID_DIMENSION_KEY;
    public static ParticleType<AltarVoidParticleEffect>   ALTAR_VOID_PARTICLE;
    public static ParticleType<AltarItemParticleEffect>   ALTAR_ITEM_PARTICLE;
    public static ParticleType<PortalFrameParticleEffect> PORTAL_FRAME_PARTICLE;

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

        VOID_WORLD_KEY = RegistryKey.of(Registry.WORLD_KEY, new Identifier(MODID, "void"));
        VOID_DIMENSION_KEY = RegistryKey.of(Registry.DIMENSION_TYPE_KEY, new Identifier(MODID, "void"));

        Registry.register(
                Registry.CHUNK_GENERATOR,
                new Identifier(MODID, "void_gen"),
                VoidChunkGenerator.codec);

        Registry.register(Registry.PARTICLE_TYPE,
                new Identifier(MODID, "altar_void"), ALTAR_VOID_PARTICLE = FabricParticleTypes.complex(true, AltarVoidParticleEffect.PARAMETERS_FACTORY));
        Registry.register(Registry.PARTICLE_TYPE,
                new Identifier(MODID, "altar_item"), ALTAR_ITEM_PARTICLE = FabricParticleTypes.complex(true, AltarItemParticleEffect.PARAMETERS_FACTORY));
        Registry.register(Registry.PARTICLE_TYPE,
                new Identifier(MODID, "portal_frame"), PORTAL_FRAME_PARTICLE = FabricParticleTypes.complex(true, PortalFrameParticleEffect.PARAMETERS_FACTORY));
    }

    public static boolean useImmersivePortal()
    {
        return FabricLoader.getInstance().isModLoaded(VoidHeart.IMMERSIVE_PORTALS);
    }
}
