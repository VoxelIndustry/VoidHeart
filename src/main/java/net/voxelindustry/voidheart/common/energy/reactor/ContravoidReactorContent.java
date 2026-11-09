package net.voxelindustry.voidheart.common.energy.reactor;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.voxelindustry.voidheart.common.energy.EnergyPlaceholders;

/**
 * Startup registrar for W10 Contravoid reactor, called once by EnergySetup. The ids start as placeholders, which the package replaces with its real content.
 */
public final class ContravoidReactorContent
{
    public static Block CONTRAVOID_REACTOR_CORE;
    public static Block REACTOR_CONTAINMENT;
    public static Block REACTOR_INTAKE;

    private ContravoidReactorContent()
    {
    }

    public static void register()
    {
        CONTRAVOID_REACTOR_CORE = EnergyPlaceholders.block("contravoid_reactor_core", MapColor.COLOR_PURPLE);
        REACTOR_CONTAINMENT = EnergyPlaceholders.block("reactor_containment", MapColor.COLOR_GRAY);
        REACTOR_INTAKE = EnergyPlaceholders.block("reactor_intake", MapColor.COLOR_ORANGE);
    }

    /**
     * Adds this package's subcommands under /voidheart energy.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
    }
}
