package net.voxelindustry.voidheart.common.content.works;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.voxelindustry.voidheart.common.content.hoard.PocketHoard;
import net.voxelindustry.voidheart.common.item.LoreBlockItem;
import net.voxelindustry.voidheart.common.item.LoreItem;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.List;
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
    /** Set in an Oculus Table, it turns the table to planning works. */
    public static Item  MEMORIAM_LENS;

    public static BlockEntityType<OfficiantTile> OFFICIANT_TILE;

    private static final int KEEP_TICKS = 100;

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
        MEMORIAM_LENS = VoidHeartItems.registerItem("memoriam_lens", properties -> new LoreItem(properties, MODID + ".memoriam_lens.lore"));
        WorksPayloads.register();
        OfficiantFolds.register();
        ServerTickEvents.END_SERVER_TICK.register(WorksContent::keepLevels);
        ReckoningSync.register();
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
     * @param job the order's id, 0 when nothing needed crafting
     */
    public record Ordered(WorksPlan<ItemVariant> plan, int job)
    {
    }

    /**
     * Plans the order against the hoard and queues its crafts with the plan drawn for them. What is lacking doesn't stop it: those crafts wait for it.
     */
    public static Ordered order(MinecraftServer server, UUID owner, ItemVariant item, long asked)
    {
        var count = Math.clamp(asked, 1, WorksBalance.MAX_ORDER);
        var hoard = PocketHoard.get(server).of(owner);
        var plan = WorksPlan.plan(WorksCatalogues.of(server, owner, hoard).lookup(), hoard.view(), item, count);
        var tasks = tasksOf(plan);
        if (tasks.isEmpty())
            return new Ordered(plan, 0);
        var state = WorksState.get(server);
        var job = state.edit(owner).order(item.toStack().getHoverName().getString(), count, tasks);
        state.owner(owner).graphs().put(job, WorksGraph.of(plan));
        return new Ordered(plan, job);
    }

    private static List<WorksQueue.Task> tasksOf(WorksPlan<ItemVariant> plan)
    {
        return plan.tasks().stream().map(task -> new WorksQueue.Task(task.recipe().id(), task.recipe().station(), task.crafts())).toList();
    }

    /** The hoard and catalogue each owner's idle orders were last worked out against, so they are only worked out again once either changes. */
    private static final java.util.Map<UUID, List<Object>> REPLANNED = new java.util.HashMap<>();

    /**
     * Every few seconds, an order none of whose crafts is being worked is worked out again against the hoard as it now stands, so what was brought
     * since is used: a kind first planned from what the hoard lacked may now be taken as it is, or made another way. An order whose item is all made
     * goes, with whatever was left of the crafts planned for it.
     */
    private static void replanIdle(MinecraftServer server)
    {
        var state = WorksState.get(server);
        for (var entry : List.copyOf(state.owners().entrySet()))
        {
            var owner = entry.getKey();
            var works = entry.getValue();
            if (works.queue().jobs().isEmpty())
                continue;
            var hoard = PocketHoard.get(server).of(owner);
            var catalogue = WorksCatalogues.of(server, owner, hoard);
            var seen = List.<Object>of(hoard.version(), System.identityHashCode(catalogue), works.queue().jobs().hashCode());
            if (seen.equals(REPLANNED.get(owner)))
                continue;

            boolean changed = false;
            for (var job : works.queue().jobs())
            {
                var graph = works.graphs().get(job.id());
                if (graph == null || graph.nodes().isEmpty() || job.tasks().stream().anyMatch(task -> task.working() > 0))
                    continue;
                var target = graph.nodes().getFirst();
                var crafts = job.tasks().stream().filter(task -> task.recipe().equals(target.recipe())).mapToLong(WorksQueue.Task::remaining).sum();
                if (crafts <= 0)
                {
                    // The item itself is made: what was planned for it and is still waiting is no longer needed
                    works.queue().cancel(job.id());
                    changed = true;
                    continue;
                }
                var recipes = catalogue.byId(target.recipe());
                var makes = recipes.isEmpty() ? 1 : Math.max(1, recipes.getFirst().makes(target.item()));
                var plan = WorksPlan.plan(catalogue.lookup(), hoard.view(), target.item(), Math.min(crafts * makes, WorksBalance.MAX_ORDER));
                var tasks = WorksQueue.merged(tasksOf(plan));
                var left = job.tasks().stream().filter(task -> task.remaining() > 0).toList();
                if (tasks.isEmpty() || tasks.equals(left))
                    continue;
                if (works.queue().replan(job.id(), tasks))
                {
                    works.graphs().put(job.id(), WorksGraph.of(plan));
                    changed = true;
                }
            }
            if (changed)
            {
                works.tidy();
                state.setDirty();
            }
            REPLANNED.put(owner, List.<Object>of(hoard.version(), System.identityHashCode(catalogue), works.queue().jobs().hashCode()));
        }
    }

    /**
     * Every few seconds, each kept item the hoard holds too few of is ordered up to its level, unless its last order is still running.
     */
    private static void keepLevels(MinecraftServer server)
    {
        if (server.getTickCount() % KEEP_TICKS != 0)
            return;
        replanIdle(server);
        var state = WorksState.get(server);
        for (var entry : List.copyOf(state.owners().entrySet()))
        {
            var owner = entry.getKey();
            var works = entry.getValue();
            if (works.keeps().isEmpty())
                continue;
            works.tidy();
            var hoard = PocketHoard.get(server).of(owner);
            for (var keep : List.copyOf(works.keeps().entrySet()))
            {
                var held = hoard.count(keep.getKey());
                if (works.keepJobs().containsKey(keep.getKey()) || held >= keep.getValue())
                    continue;
                var ordered = order(server, owner, keep.getKey(), keep.getValue() - held);
                if (ordered.job() != 0)
                    works.keepJobs().put(keep.getKey(), ordered.job());
            }
            state.setDirty();
        }
    }

    private static int order(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var item = ItemVariant.of(ItemArgument.getItem(context, "item").createItemStack(1));
        var count = IntegerArgumentType.getInteger(context, "count");
        var plan = order(context.getSource().getServer(), caller(context), item, count).plan();
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
