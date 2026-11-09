package net.voxelindustry.voidheart.common.energy.grid;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.voxelindustry.voidheart.common.energy.EnergyPlaceholders;

/**
 * Startup registrar for W1 Void grid, called once by EnergySetup. The ids start as placeholders, which the package replaces with its real content.
 */
public final class VoidGridContent
{
    public static Block AZURIUM_PYLON;

    private VoidGridContent()
    {
    }

    public static void register()
    {
        AZURIUM_PYLON = EnergyPlaceholders.block("azurium_pylon", MapColor.COLOR_LIGHT_BLUE);
    }

    /**
     * Adds this package's subcommands under /voidheart energy.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
    }
}
