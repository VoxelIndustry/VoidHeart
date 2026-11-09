package net.voxelindustry.voidheart.common.energy.ley;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.voxelindustry.voidheart.common.energy.EnergyPlaceholders;

/**
 * Startup registrar for W4 Standing stones and wards, called once by EnergySetup. The ids start as placeholders, which the package replaces with its real content.
 */
public final class LeyStoneContent
{
    public static Block LEY_STONE;
    public static Block LEY_WARD;
    public static Block LEY_GROWER;

    private LeyStoneContent()
    {
    }

    public static void register()
    {
        LEY_STONE = EnergyPlaceholders.block("ley_stone", MapColor.COLOR_CYAN);
        LEY_WARD = EnergyPlaceholders.block("ley_ward", MapColor.COLOR_CYAN);
        LEY_GROWER = EnergyPlaceholders.block("ley_grower", MapColor.COLOR_GREEN);
    }

    /**
     * Adds this package's subcommands under /voidheart energy.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
    }
}
