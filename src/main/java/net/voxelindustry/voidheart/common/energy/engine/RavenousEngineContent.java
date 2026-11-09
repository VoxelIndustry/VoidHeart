package net.voxelindustry.voidheart.common.energy.engine;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.voxelindustry.voidheart.common.energy.EnergyPlaceholders;

/**
 * Startup registrar for W5 Ravenous engine, called once by EnergySetup. The ids start as placeholders, which the package replaces with its real content.
 */
public final class RavenousEngineContent
{
    public static Block RAVENOUS_ENGINE;

    private RavenousEngineContent()
    {
    }

    public static void register()
    {
        RAVENOUS_ENGINE = EnergyPlaceholders.block("ravenous_engine", MapColor.GOLD);
    }

    /**
     * Adds this package's subcommands under /voidheart energy.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
    }
}
