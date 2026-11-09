package net.voxelindustry.voidheart.common.energy.spike;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.Arrays;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Startup registrar for W8 Spike, called once by EnergySetup.
 */
public final class LeySpikeContent
{
    public static Block                              AZURIUM_SPIKE;
    public static BlockEntityType<AzuriumSpikeTile> AZURIUM_SPIKE_TILE;

    private LeySpikeContent()
    {
    }

    public static void register()
    {
        AZURIUM_SPIKE = VoidHeartBlocks.registerBlock("azurium_spike", AzuriumSpikeBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6.0F)
                .noOcclusion()
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.AMETHYST_CLUSTER), true);

        VoidHeartTiles.registerTile(AZURIUM_SPIKE_TILE = FabricBlockEntityTypeBuilder.create(AzuriumSpikeTile::new, AZURIUM_SPIKE).build(), "azurium_spike");
    }

    /**
     * /voidheart energy spike setting &lt;pos&gt; &lt;percent&gt; sets the share of the flow a spike bleeds, for review.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
        energy.then(literal("spike").then(literal("setting")
                .then(argument("pos", BlockPosArgument.blockPos())
                        .then(argument("percent", IntegerArgumentType.integer(1, 100))
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(LeySpikeBalance.SHARES).mapToObj(share -> String.valueOf(Math.round(share * 100))), builder))
                                .executes(LeySpikeContent::setting)))));
    }

    private static int setting(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var source = context.getSource();
        var pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        var tile = source.getLevel().getBlockEntity(pos);
        if (!(tile instanceof AzuriumSpikeTile) && source.getLevel().getBlockEntity(pos.below()) instanceof AzuriumSpikeTile below)
            tile = below;
        if (!(tile instanceof AzuriumSpikeTile spike))
        {
            source.sendFailure(Component.literal("No Azurium Spike at " + pos.toShortString()));
            return 0;
        }

        var percent = IntegerArgumentType.getInteger(context, "percent");
        var setting = 0;
        for (int i = 0; i < LeySpikeBalance.SHARES.length; i++)
        {
            if (Math.abs(LeySpikeBalance.SHARES[i] * 100 - percent) < Math.abs(LeySpikeBalance.SHARES[setting] * 100 - percent))
                setting = i;
        }
        spike.setSetting(setting);
        var chosen = Math.round(LeySpikeMath.share(setting) * 100);
        source.sendSuccess(() -> Component.literal("The spike bleeds " + chosen + "% of the flow"), false);
        return (int) chosen;
    }
}
