package net.voxelindustry.voidheart.common.content.hoard;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.voxelindustry.voidheart.common.item.LoreBlockItem;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The Hoard: items kept in a pocket as data, given room by coffers in that pocket, and reached through Folds anywhere. Registered once at startup.
 */
public final class HoardContent
{
    public static Block HOARD_COFFER;
    public static Block FOLD;

    public static BlockEntityType<HoardCofferTile> HOARD_COFFER_TILE;
    public static BlockEntityType<FoldTile>        FOLD_TILE;

    public static ExtendedMenuType<HoardMenu, HoardMenu.Opening> HOARD_MENU;

    private HoardContent()
    {
    }

    public static void register()
    {
        HOARD_COFFER = VoidHeartBlocks.registerBlock("hoard_coffer", HoardCofferBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_ORANGE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(3F, 6F)
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.COPPER), (block, properties) -> new LoreBlockItem(block, properties, MODID + ".hoard_coffer.lore"));
        FOLD = VoidHeartBlocks.registerBlock("fold", FoldBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_ORANGE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(3F, 6F)
                .lightLevel(state -> 4)
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.COPPER), (block, properties) -> new LoreBlockItem(block, properties, MODID + ".fold.lore"));

        VoidHeartTiles.registerTile(HOARD_COFFER_TILE = FabricBlockEntityTypeBuilder.create(HoardCofferTile::new, HOARD_COFFER).build(), "hoard_coffer");
        VoidHeartTiles.registerTile(FOLD_TILE = FabricBlockEntityTypeBuilder.create(FoldTile::new, FOLD).build(), "fold");

        ItemStorage.SIDED.registerForBlockEntity((fold, direction) -> fold.storage(), FOLD_TILE);

        HOARD_MENU = Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(MODID, "hoard"),
                new ExtendedMenuType<>(HoardMenu::new, HoardMenu.Opening.STREAM_CODEC));
        HoardPayloads.register();
    }

    /**
     * Adds /voidheart hoard, on the caller's own pocket: list, fill an item, capacity and clear.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext registries)
    {
        builder.then(literal("hoard")
                .then(literal("list").executes(HoardContent::list))
                .then(literal("capacity").executes(HoardContent::capacity))
                .then(literal("clear").executes(HoardContent::clear))
                .then(literal("fill").then(argument("item", ItemArgument.item(registries))
                        .then(argument("count", IntegerArgumentType.integer(1))
                                .executes(HoardContent::fill)))));
    }

    private static PocketHoard.Hoard callerHoard(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        return PocketHoard.get(context.getSource().getServer()).of(player.getUUID());
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var hoard = callerHoard(context);
        var source = context.getSource();
        source.sendSuccess(() -> Component.literal("Hoard: " + hoard.total() + "/" + hoard.capacity() + " in " + hoard.view().size() + " kind(s)"), false);
        hoard.view().forEach((variant, count) -> source.sendSuccess(() -> Component.literal(count + " ").append(variant.toStack().getHoverName()), false));
        return hoard.view().size();
    }

    private static int capacity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var hoard = callerHoard(context);
        context.getSource().sendSuccess(() -> Component.literal(hoard.cofferCount() + " coffer(s) give room for " + hoard.capacity()), false);
        return (int) Math.min(Integer.MAX_VALUE, hoard.capacity());
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        callerHoard(context).clear();
        context.getSource().sendSuccess(() -> Component.literal("Hoard emptied"), false);
        return 1;
    }

    private static int fill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var stack = ItemArgument.getItem(context, "item").createItemStack(1);
        var put = callerHoard(context).insert(ItemVariant.of(stack), IntegerArgumentType.getInteger(context, "count"));
        context.getSource().sendSuccess(() -> Component.literal("Put " + put + " ").append(stack.getHoverName()).append(" in the hoard"), false);
        return (int) put;
    }
}
