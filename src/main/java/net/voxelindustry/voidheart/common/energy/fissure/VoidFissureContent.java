package net.voxelindustry.voidheart.common.energy.fissure;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.voxelindustry.voidheart.common.energy.EnergyPlaceholders;

/**
 * Startup registrar for W9 Rift, called once by EnergySetup. The ids start as placeholders, which the package replaces with its real content.
 */
public final class VoidFissureContent
{
    public static Block VOID_FISSURE;
    public static Block VOID_WOUND;

    private VoidFissureContent()
    {
    }

    public static void register()
    {
        VOID_FISSURE = EnergyPlaceholders.block("void_fissure", MapColor.COLOR_BLACK);
        VOID_WOUND = EnergyPlaceholders.block("void_wound", MapColor.COLOR_BLACK);
    }

    /**
     * Adds this package's subcommands under /voidheart energy.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
    }
}
