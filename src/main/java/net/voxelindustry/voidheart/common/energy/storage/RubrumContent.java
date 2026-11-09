package net.voxelindustry.voidheart.common.energy.storage;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.voxelindustry.voidheart.common.energy.EnergyPlaceholders;

/**
 * Startup registrar for W2 Rubrum storage, called once by EnergySetup. The ids start as placeholders, which the package replaces with its real content.
 */
public final class RubrumContent
{
    public static Block RUBRUM_CONSTRUCT_CORE;
    public static Block RUBRUM_CONSTRUCT_FRAME;
    public static Item RUBRUM_CELL;
    public static Block CELL_SOCKET;

    private RubrumContent()
    {
    }

    public static void register()
    {
        RUBRUM_CONSTRUCT_CORE = EnergyPlaceholders.block("rubrum_construct_core", MapColor.COLOR_RED);
        RUBRUM_CONSTRUCT_FRAME = EnergyPlaceholders.block("rubrum_construct_frame", MapColor.COLOR_RED);
        RUBRUM_CELL = EnergyPlaceholders.item("rubrum_cell");
        CELL_SOCKET = EnergyPlaceholders.block("cell_socket", MapColor.COLOR_RED);
    }

    /**
     * Adds this package's subcommands under /voidheart energy.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
    }
}
