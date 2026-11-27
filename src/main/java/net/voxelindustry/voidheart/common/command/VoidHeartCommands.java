package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.Commands.CommandSelection;
import net.voxelindustry.voidheart.common.content.crystal.CrystalContent;
import net.voxelindustry.voidheart.common.content.hoard.HoardContent;
import net.voxelindustry.voidheart.common.content.works.WorksContent;
import net.voxelindustry.voidheart.common.energy.EnergyCommands;

import static net.minecraft.commands.Commands.literal;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartCommands
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandRegistryAccess, CommandSelection environment)
    {
        LiteralArgumentBuilder<CommandSourceStack> builder = literal(MODID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        PocketCommands.register(builder);
        DungeonCommands.register(builder);
        DaedalusCommands.register(builder);
        UnderwayCommands.register(builder);
        SanctumCommands.register(builder);
        MausoleumCommands.register(builder);
        ArmillaryCommands.register(builder);
        LeyCommands.register(builder);
        CurseCommands.register(builder);
        ResearchCommands.register(builder);
        EnergyCommands.register(builder);
        SinCommands.register(builder);
        BlueprintCommands.register(builder);
        SecondHeartCommands.register(builder);
        CrystalContent.registerCommands(builder);
        HoardContent.registerCommands(builder, commandRegistryAccess);
        WorksContent.registerCommands(builder, commandRegistryAccess);
        ArmorCommands.register(builder);

        dispatcher.register(builder);
    }
}
