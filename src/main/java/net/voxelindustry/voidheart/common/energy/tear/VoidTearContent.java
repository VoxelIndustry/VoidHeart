package net.voxelindustry.voidheart.common.energy.tear;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.voxelindustry.voidheart.common.energy.EnergyPlaceholders;

/**
 * Startup registrar for W9 Rift, called once by EnergySetup. The ids start as placeholders, which the package replaces with its real content.
 */
public final class VoidTearContent
{
    public static Block VOID_TEAR;
    public static Block VOID_WOUND;

    private VoidTearContent()
    {
    }

    public static void register()
    {
        VOID_TEAR = EnergyPlaceholders.block("void_tear", MapColor.COLOR_BLACK);
        VOID_WOUND = EnergyPlaceholders.block("void_wound", MapColor.COLOR_BLACK);
    }

    /**
     * Adds this package's subcommands under /voidheart energy.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
    }
}
