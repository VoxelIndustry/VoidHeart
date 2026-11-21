package net.voxelindustry.voidheart.common.content.works;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.voxelindustry.voidheart.common.content.hoard.PocketHoard;
import net.voxelindustry.voidheart.common.item.LoreBlockItem;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The works: Officiants serving stations from the hoard, and the orders they work through. Registered once at startup.
 */
public final class WorksContent
{
    public static Block OFFICIANT;

    public static BlockEntityType<OfficiantTile> OFFICIANT_TILE;

    private WorksContent()
    {
    }

    public static void register()
    {
        OFFICIANT = VoidHeartBlocks.registerBlock("officiant", OfficiantBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(3F, 6F)
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.STONE), (block, properties) -> new LoreBlockItem(block, properties, MODID + ".officiant.lore"));
        VoidHeartTiles.registerTile(OFFICIANT_TILE = FabricBlockEntityTypeBuilder.create(OfficiantTile::new, OFFICIANT).build(), "officiant");
    }

    /**
     * Adds /voidheart works, on the caller's own orders: order an item, list the orders, cancel one.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext registries)
    {
        builder.then(literal("works")
                .then(literal("order").then(argument("item", ItemArgument.item(registries))
                        .then(argument("count", IntegerArgumentType.integer(1)).executes(WorksContent::order))))
                .then(literal("jobs").executes(WorksContent::jobs))
                .then(literal("cancel").then(argument("job", IntegerArgumentType.integer(1)).executes(WorksContent::cancel))));
    }

    private static UUID caller(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return context.getSource().getPlayerOrException().getUUID();
    }

    /**
     * Plans the order against the hoard and queues its crafts, saying what is lacking. The crafts are queued even then, and wait for it.
     */
    public static WorksPlan<ItemVariant> order(net.minecraft.server.MinecraftServer server, UUID owner, ItemVariant item, long count)
    {
        var hoard = PocketHoard.get(server).of(owner);
        var plan = WorksPlan.plan(WorksCatalogues.of(server, owner, hoard).lookup(), hoard.view(), item, count);
        var tasks = plan.tasks().stream().map(task -> new WorksQueue.Task(task.recipe().id(), task.recipe().station(), task.crafts())).toList();
        if (!tasks.isEmpty())
            WorksState.get(server).edit(owner).order(item.toStack().getHoverName().getString(), count, tasks);
        return plan;
    }

    private static int order(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var item = ItemVariant.of(ItemArgument.getItem(context, "item").createItemStack(1));
        var count = IntegerArgumentType.getInteger(context, "count");
        var plan = order(context.getSource().getServer(), caller(context), item, count);
        var source = context.getSource();
        source.sendSuccess(() -> Component.literal("Queued " + plan.tasks().stream().mapToLong(WorksPlan.Task::crafts).sum() + " craft(s), essence " + plan.essence()), false);
        plan.lacking().forEach((kind, lacking) -> source.sendSuccess(() -> Component.literal("Lacking " + lacking + " ").append(kind.toStack().getHoverName()), false));
        return plan.tasks().size();
    }

    private static int jobs(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var jobs = WorksState.get(context.getSource().getServer()).of(caller(context)).jobs();
        context.getSource().sendSuccess(() -> Component.literal(jobs.size() + " order(s)"), false);
        for (var job : jobs)
        {
            var left = job.tasks().stream().mapToLong(WorksQueue.Task::remaining).sum();
            context.getSource().sendSuccess(() -> Component.literal("#" + job.id() + " " + job.count() + " " + job.what() + ", " + left + " craft(s) left"), false);
        }
        return jobs.size();
    }

    private static int cancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var job = IntegerArgumentType.getInteger(context, "job");
        var cancelled = WorksState.get(context.getSource().getServer()).edit(caller(context)).cancel(job);
        context.getSource().sendSuccess(() -> Component.literal(cancelled ? "Cancelled order #" + job : "No order #" + job), false);
        return cancelled ? 1 : 0;
    }
}
