package net.voxelindustry.voidheart.common.energy.spike;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.voxelindustry.voidheart.common.energy.EnergyPlaceholders;

/**
 * Startup registrar for W8 Spike, called once by EnergySetup. The ids start as placeholders, which the package replaces with its real content.
 */
public final class LeySpikeContent
{
    public static Block AZURIUM_SPIKE;

    private LeySpikeContent()
    {
    }

    public static void register()
    {
        AZURIUM_SPIKE = EnergyPlaceholders.block("azurium_spike", MapColor.COLOR_LIGHT_BLUE);
    }

    /**
     * Adds this package's subcommands under /voidheart energy.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
    }
}
