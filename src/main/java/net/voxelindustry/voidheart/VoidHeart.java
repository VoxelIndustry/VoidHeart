package net.voxelindustry.voidheart;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.voxelindustry.voidheart.common.command.VoidHeartCommands;
import net.voxelindustry.voidheart.common.generator.VoidChunkGenerator;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class VoidHeart implements ModInitializer
{
    public static final String MODID = "voidheart";

    public static ItemGroup                  ITEMGROUP = FabricItemGroupBuilder.build(
            new Identifier(MODID, "item_group"),
            () -> new ItemStack(VoidHeartItems.VOID_HEART));
    public static RegistryKey<World>         VOID_WORLD_KEY;
    public static RegistryKey<DimensionType> VOID_DIMENSION_KEY;

    @Override
    public void onInitialize()
    {
        VoidHeartBlocks.registerBlocks();
        VoidHeartItems.registerItems();

        VoidHeartTiles.registerTiles();

        CommandRegistrationCallback.EVENT.register(VoidHeartCommands::register);

        VOID_WORLD_KEY = RegistryKey.of(Registry.DIMENSION, new Identifier(MODID, "void"));
        VOID_DIMENSION_KEY = RegistryKey.of(Registry.DIMENSION_TYPE_KEY, new Identifier(MODID, "void"));

        Registry.register(
                Registry.CHUNK_GENERATOR,
                new Identifier(MODID, "void_gen"),
                VoidChunkGenerator.codec);

    }
}
