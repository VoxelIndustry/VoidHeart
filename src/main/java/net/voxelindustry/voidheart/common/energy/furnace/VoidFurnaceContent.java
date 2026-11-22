package net.voxelindustry.voidheart.common.energy.furnace;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Startup registrar for the Void Furnace, called once by EnergySetup since it runs on void energy.
 */
public final class VoidFurnaceContent
{
    public static Block                                         VOID_FURNACE;
    public static BlockEntityType<VoidFurnaceTile>              VOID_FURNACE_TILE;
    public static ExtendedMenuType<VoidFurnaceMenu, BlockPos>   VOID_FURNACE_MENU;

    private VoidFurnaceContent()
    {
    }

    public static void register()
    {
        VOID_FURNACE = VoidHeartBlocks.registerBlock("void_furnace", VoidFurnaceBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(3.5F, 6.0F)
                .lightLevel(state -> state.getValue(VoidFurnaceBlock.FLAME) == VoidFurnaceBlock.Flame.NONE ? 0 : 13)
                .sound(SoundType.STONE), true);

        VoidHeartTiles.registerTile(VOID_FURNACE_TILE = FabricBlockEntityTypeBuilder.create(VoidFurnaceTile::new, VOID_FURNACE).build(), "void_furnace");

        VOID_FURNACE_MENU = Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(MODID, "void_furnace"),
                new ExtendedMenuType<>(VoidFurnaceMenu::new, BlockPos.STREAM_CODEC));
    }
}
