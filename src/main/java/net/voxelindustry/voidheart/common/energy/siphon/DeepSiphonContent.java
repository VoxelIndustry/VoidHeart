package net.voxelindustry.voidheart.common.energy.siphon;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.voxelindustry.voidheart.common.energy.EnergyPlaceholders;

/**
 * Startup registrar for W11 Deep siphon, called once by EnergySetup. The ids start as placeholders, which the package replaces with its real content.
 */
public final class DeepSiphonContent
{
    public static Block DEEP_SIPHON;

    private DeepSiphonContent()
    {
    }

    public static void register()
    {
        DEEP_SIPHON = EnergyPlaceholders.block("deep_siphon", MapColor.COLOR_BLACK);
    }

    /**
     * Adds this package's subcommands under /voidheart energy.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
    }
}
