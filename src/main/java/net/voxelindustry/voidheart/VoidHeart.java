package net.voxelindustry.voidheart;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.voxelindustry.voidheart.client.particle.AltarItemParticle;
import net.voxelindustry.voidheart.client.particle.AltarVoidFillingParticle;
import net.voxelindustry.voidheart.common.command.VoidHeartCommands;
import net.voxelindustry.voidheart.common.generator.VoidChunkGenerator;
import net.voxelindustry.voidheart.common.particle.AltarItemParticleEffect;
import net.voxelindustry.voidheart.common.particle.AltarVoidParticleEffect;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class VoidHeart implements ModInitializer
{
    public static final String MODID             = "voidheart";
    public static final String IMMERSIVE_PORTALS = "immersive_portals";

    public static ItemGroup                             ITEMGROUP = FabricItemGroupBuilder.build(
            new Identifier(MODID, "item_group"),
            () -> new ItemStack(VoidHeartItems.VOID_HEART));
    public static RegistryKey<World>                    VOID_WORLD_KEY;
    public static RegistryKey<DimensionType>            VOID_DIMENSION_KEY;
    public static ParticleType<AltarVoidParticleEffect> ALTAR_VOID_PARTICLE;
    public static ParticleType<AltarItemParticleEffect> ALTAR_ITEM_PARTICLE;

    @Override
    public void onInitialize()
    {
        VoidHeartBlocks.registerBlocks();
        VoidHeartItems.registerItems();

        VoidHeartTiles.registerTiles();

        VoidHeartRecipes.registerRecipes();

        CommandRegistrationCallback.EVENT.register(VoidHeartCommands::register);

        VOID_WORLD_KEY = RegistryKey.of(Registry.DIMENSION, new Identifier(MODID, "void"));
        VOID_DIMENSION_KEY = RegistryKey.of(Registry.DIMENSION_TYPE_KEY, new Identifier(MODID, "void"));

        Registry.register(
                Registry.CHUNK_GENERATOR,
                new Identifier(MODID, "void_gen"),
                VoidChunkGenerator.codec);

        Registry.register(Registry.PARTICLE_TYPE,
                new Identifier(MODID, "altar_void"), ALTAR_VOID_PARTICLE = FabricParticleTypes.complex(true, AltarVoidParticleEffect.PARAMETERS_FACTORY));
        Registry.register(Registry.PARTICLE_TYPE,
                new Identifier(MODID, "altar_item"), ALTAR_ITEM_PARTICLE = FabricParticleTypes.complex(true, AltarItemParticleEffect.PARAMETERS_FACTORY));

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
