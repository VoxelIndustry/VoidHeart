package net.voxelindustry.voidheart.common.energy.siphon;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Startup registrar for W11 Deep siphon, called once by EnergySetup.
 */
public final class DeepSiphonContent
{
    public static Block                             DEEP_SIPHON;
    public static BlockEntityType<DeepSiphonTile> DEEP_SIPHON_TILE;

    private DeepSiphonContent()
    {
    }

    public static void register()
    {
        DEEP_SIPHON = VoidHeartBlocks.registerBlock("deep_siphon", DeepSiphonBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6.0F)
                .noOcclusion()
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.STONE), true);

        VoidHeartTiles.registerTile(DEEP_SIPHON_TILE = FabricBlockEntityTypeBuilder.create(DeepSiphonTile::new, DEEP_SIPHON).build(), "deep_siphon");
        DeepSiphonChunkHold.register();
    }

    /**
     * /voidheart energy siphon notice &lt;pos&gt; &lt;mobs&gt; sets how many mobs a siphon's notice will bring, for review without drawing for minutes.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
        energy.then(literal("siphon").then(literal("notice")
                .then(argument("pos", BlockPosArgument.blockPos())
                        .then(argument("mobs", IntegerArgumentType.integer(0, DeepSiphonBalance.NOTICE_CAP))
                                .executes(DeepSiphonContent::notice)))));
    }

    private static int notice(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var source = context.getSource();
        var pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        if (!(source.getLevel().getBlockEntity(pos) instanceof DeepSiphonTile siphon))
        {
            source.sendFailure(Component.literal("No Deep Siphon at " + pos.toShortString()));
            return 0;
        }

        int mobs = IntegerArgumentType.getInteger(context, "mobs");
        siphon.notice().setBanked(mobs * DeepSiphonBalance.NOTICE_PER_MOB);
        siphon.setChanged();
        source.sendSuccess(() -> Component.literal("The siphon's notice will bring " + mobs + " mobs"), false);
        return mobs;
    }
}
