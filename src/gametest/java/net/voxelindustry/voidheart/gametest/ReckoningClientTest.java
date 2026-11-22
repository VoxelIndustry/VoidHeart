package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.client.render.oculus.ReckoningHologram;
import net.voxelindustry.voidheart.client.screen.HoardScreen;
import net.voxelindustry.voidheart.common.content.crystal.BundleSize;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.content.hoard.HoardContent;
import net.voxelindustry.voidheart.common.content.hoard.HoardCofferTile;
import net.voxelindustry.voidheart.common.content.hoard.PocketHoard;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableStructure;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.works.OfficiantTile;
import net.voxelindustry.voidheart.common.content.works.WorksContent;
import net.voxelindustry.voidheart.common.content.works.WorksState;
import net.voxelindustry.voidheart.common.energy.creative.CreativeEnergyContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import java.util.List;
import net.voxelindustry.voidheart.common.content.works.WorksGraph;
import java.util.function.Function;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * A Memoriam Lens set in an Oculus Table makes a Reckoning: used with an empty hand it opens the owner's hoard on its works tab, which lists what the
 * Officiants' stations can make, draws the plan for a chosen item, begins it as an order whose progress it shows, and keeps a level in the hoard.
 * The table itself shows the running order as a map laid on it, a sigil while idle. Sneaking takes the lens back.
 */
public class ReckoningClientTest implements FabricClientGameTest
{
    private static final BlockPos WELL   = new BlockPos(0, FLAT_FEET - 3, 0);
    private static final BlockPos TABLE  = new BlockPos(8, FLAT_FEET, -6);
    private static final BlockPos ALTAR  = new BlockPos(0, FLAT_FEET, 8);
    private static final List<BlockPos> PILLARS = List.of(
            new BlockPos(-1, 0, 3), new BlockPos(1, 0, 3), new BlockPos(1, 0, -3), new BlockPos(-1, 0, -3),
            new BlockPos(3, 0, -1), new BlockPos(3, 0, 1), new BlockPos(-3, 0, -1), new BlockPos(-3, 0, 1));
    private static final BlockPos OCULUS = new BlockPos(-6, FLAT_FEET, -6);

    private static final ItemVariant PEARL = ItemVariant.of(VoidHeartItems.VOID_PEARL);
    private static final ItemVariant SHARD = ItemVariant.of(VoidHeartItems.ENDER_SHARD);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            // Survival, so setting the lens in uses it up
            server.runCommand("gamemode survival @a");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            server.runOnServer(ReckoningClientTest::buildBase);
            // Officiants make their stations known on their first ticks
            context.waitTicks(25);

            checkTrimmedGraph();
            setLens(context, server);
            openReckoning(context, server);
            checkPlan(context, server);
            checkOrder(context, server);
            checkKeep(context, server);
            checkEssencePlan(context, server);
            checkCraftLeftoversNotFromHoard(server);
            checkRoster(context, server);
            checkUnservedTable(context, server);
            checkHoardTab(context, server);
            takeLensBack(context, server);
            server.runCommand("gamemode creative @a");
        }
    }

    /**
     * A rail from iron ingots unpacked from a block shows the ingots as wanted while the hoard holds no block, and keeps the unpacking once it holds one.
     */
    private static void checkTrimmedGraph()
    {
        int table = net.voxelindustry.voidheart.common.content.works.StationKind.TABLE.ordinal();
        java.util.function.LongFunction<WorksGraph> rail = blocksHeld -> new WorksGraph(List.of(
                new WorksGraph.Node(ItemVariant.of(Items.RAIL), 16, 0, 1, "rail", table, 0, false),
                new WorksGraph.Node(ItemVariant.of(Items.IRON_INGOT), 6, 0, 1, "ingots_from_block", table, 0, true),
                new WorksGraph.Node(ItemVariant.of(Items.IRON_BLOCK), 1, blocksHeld, 0, "", -1, 1 - blocksHeld, false),
                new WorksGraph.Node(ItemVariant.of(Items.STICK), 1, 1, 0, "", -1, 0, false)),
                List.of(new WorksGraph.Edge(0, 1, 6), new WorksGraph.Edge(1, 2, 1), new WorksGraph.Edge(0, 3, 1)), java.util.Map.of(), List.of());

        var trimmed = rail.apply(0).trimmed();
        expect(trimmed.nodes().stream().map(node -> node.item().getItem()).toList().equals(List.of(Items.RAIL, Items.IRON_INGOT, Items.STICK)),
                "the trimmed rail shows " + trimmed.nodes());
        expect(!trimmed.nodes().get(1).isMade() && trimmed.nodes().get(1).lacking() == 6, "the ingots are not shown as wanted: " + trimmed.nodes().get(1));
        expect(trimmed.edges().equals(List.of(new WorksGraph.Edge(0, 1, 6), new WorksGraph.Edge(0, 2, 1))), "the trimmed rail's arcs are " + trimmed.edges());
        var held = rail.apply(1);
        expect(held.trimmed() == held, "a block in the hoard was trimmed from the plan");
    }

    private static PocketHoard.Hoard hoard(MinecraftServer minecraft)
    {
        return PocketHoard.get(minecraft).of(player(minecraft).getUUID());
    }

    private static long held(MinecraftServer minecraft, ItemLike item)
    {
        return hoard(minecraft).count(ItemVariant.of(item));
    }

    private static void officiant(ServerLevel level, BlockPos pos, MinecraftServer minecraft)
    {
        level.setBlockAndUpdate(pos, WorksContent.OFFICIANT.defaultBlockState());
        ((OfficiantTile) level.getBlockEntity(pos)).setOwner(player(minecraft).getUUID());
    }

    /**
     * A pocket with a large coffer, a field covering the stations, an Officiant under a crafting table and an altar, and an Oculus Table.
     */
    private static void buildBase(MinecraftServer minecraft)
    {
        var owner = player(minecraft).getUUID();
        var pockets = VoidPocketState.getVoidPocketState(minecraft.overworld());
        pockets.createPocket(minecraft.getLevel(VoidHeart.VOID_WORLD_KEY), owner);
        var voidLevel = minecraft.getLevel(VoidHeart.VOID_WORLD_KEY);
        var coffer = pockets.getHeartData(owner).pocketPos().offset(3, 0, 3);
        voidLevel.setBlockAndUpdate(coffer, HoardContent.HOARD_COFFER.defaultBlockState());
        ((HoardCofferTile) voidLevel.getBlockEntity(coffer)).setBundle(new ItemStack(CrystalBundles.bundle(CrystalKind.RUBRUM, BundleSize.LARGE)));

        var level = minecraft.overworld();
        level.setBlockAndUpdate(WELL, CreativeEnergyContent.CREATIVE_VOID_WELL.defaultBlockState());
        level.setBlockAndUpdate(TABLE, Blocks.CRAFTING_TABLE.defaultBlockState());
        officiant(level, TABLE.below(), minecraft);
        level.setBlockAndUpdate(ALTAR, VoidHeartBlocks.VOID_ALTAR.defaultBlockState());
        for (var offset : PILLARS)
            level.setBlockAndUpdate(ALTAR.offset(offset), VoidHeartBlocks.VOID_PILLAR.defaultBlockState());
        officiant(level, ALTAR.below(), minecraft);

        Knowledge.lockOf(minecraft.registryAccess(), id("altar/void_pearl")).ifPresent(knowledge(minecraft)::grant);
        hoard(minecraft).insert(ItemVariant.of(Items.ENDER_PEARL), 1);
        hoard(minecraft).insert(ItemVariant.of(Items.OBSIDIAN), 2);
        hoard(minecraft).insert(ItemVariant.of(Items.COBBLESTONE), 300);
    }

    private static <T> T onScreen(ClientGameTestContext context, Function<HoardScreen, T> read)
    {
        return context.computeOnClient(minecraft ->
        {
            if (!(minecraft.gui.screen() instanceof HoardScreen screen))
                throw new AssertionError("expected the Reckoning open, found " + minecraft.gui.screen());
            return read.apply(screen);
        });
    }

    private static void clickControl(ClientGameTestContext context, String name)
    {
        click(context, onScreen(context, screen -> screen.worksControl(name)));
        context.waitTicks(3);
    }

    private static void waitOnScreen(ClientGameTestContext context, int maxTicks, String message, Function<HoardScreen, Boolean> condition)
    {
        for (int tick = 0; tick < maxTicks; tick++)
        {
            if (onScreen(context, condition))
                return;
            context.waitTick();
        }
        throw new AssertionError(message);
    }

    private static void shot(ClientGameTestContext context, String name)
    {
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot(name);
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    /**
     * A lens used on the table is set in and used up.
     */
    private static void setLens(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("item replace entity @a weapon.mainhand with voidheart:memoriam_lens");
        server.runCommand("fill " + (OCULUS.getX() - 1) + " " + OCULUS.getY() + " " + (OCULUS.getZ() - 1) + " " + (OCULUS.getX() + 1) + " " + OCULUS.getY() + " "
                + (OCULUS.getZ() + 1) + " voidheart:oculus_table");
        context.waitTicks(10);
        aimFrom(context, server, OCULUS.getX() + 0.5, FLAT_FEET, OCULUS.getZ() - 2.5, OCULUS.getX() + 0.5, OCULUS.getY() + 0.6, OCULUS.getZ() + 0.5, 5);
        rightClick(context);
        server.runOnServer(minecraft ->
        {
            var tile = OculusTableStructure.getTile(minecraft.overworld(), OCULUS);
            expect(tile != null && tile.getView().reckoning(), "the lens was not set in the table");
            expect(player(minecraft).getMainHandItem().isEmpty(), "the lens was not used up: " + player(minecraft).getMainHandItem());
        });
        context.waitTicks(20);
        expect(context.computeOnClient(minecraft -> ReckoningHologram.shown()) == null, "the table shows an order before any was begun");
        server.runCommand("gamemode spectator @a");
        tableView(context, server, "reckoning-hologram-idle", 0, 1.2, 2.8);
        server.runCommand("gamemode survival @a");
        aimAtTable(context, server);
    }

    private static void aimAtTable(ClientGameTestContext context, TestServerContext server)
    {
        aimFrom(context, server, OCULUS.getX() + 0.5, FLAT_FEET, OCULUS.getZ() - 2.5, OCULUS.getX() + 0.5, OCULUS.getY() + 0.6, OCULUS.getZ() + 0.5, 5);
    }

    /**
     * A shot of the table from its centre's offset, looking at the middle of its top, once the map has turned to face that side.
     */
    private static void tableView(ClientGameTestContext context, TestServerContext server, String name, double dx, double dy, double dz)
    {
        double x = OCULUS.getX() + 0.5;
        double z = OCULUS.getZ() + 0.5;
        // A half turn of the map takes 20 ticks
        aimFrom(context, server, x + dx, FLAT_FEET + dy, z + dz, x, OCULUS.getY() + 0.5, z, 25);
        shot(context, name);
    }

    /**
     * An empty hand opens the Reckoning on the works tab, listing what the table and altar can make.
     */
    private static void openReckoning(ClientGameTestContext context, TestServerContext server)
    {
        rightClick(context);
        waitOnScreen(context, 40, "the Reckoning listed nothing makeable",
                screen -> screen.getMenu().isWorks() && screen.worksShown() && screen.getMenu().getMakeable().contains(PEARL)
                        && screen.getMenu().getMakeable().contains(SHARD));
        shot(context, "reckoning-list");
    }

    /**
     * A Void Pearl chosen draws its plan through the table's ender and obsidian shards; two of them lack what the hoard holds.
     */
    private static void checkPlan(ClientGameTestContext context, TestServerContext server)
    {
        click(context, onScreen(context, screen -> screen.makeableCenter(PEARL)));
        waitOnScreen(context, 40, "no plan came for the Void Pearl",
                screen -> screen.getMenu().getPlan() != null && screen.getMenu().getPlan().item().equals(PEARL));
        var nodes = onScreen(context, screen -> screen.getMenu().getPlan().graph().nodes());
        expect(nodes.size() >= 4, "the Void Pearl plan drew only " + nodes);
        expect(nodes.stream().allMatch(node -> node.lacking() == 0), "the Void Pearl plan lacks " + nodes);
        expect(nodes.stream().anyMatch(node -> node.item().equals(SHARD) && node.isMade()), "the plan makes no ender shards: " + nodes);

        int shard = nodes.indexOf(nodes.stream().filter(node -> node.item().equals(SHARD)).findFirst().orElseThrow());
        hover(context, onScreen(context, screen -> screen.nodeCenter(shard)));
        shot(context, "reckoning-plan");

        // The wheel zooms the graph and dragging pans it, and the plates are still aimed at where they are drawn
        var center = onScreen(context, HoardScreen::worksRegionCenter);
        hover(context, center);
        context.getInput().scroll(1);
        context.getInput().scroll(1);
        context.waitTicks(2);
        var zoom = onScreen(context, HoardScreen::worksZoom);
        expect(zoom > 1.3F, "two notches zoomed the graph to " + zoom);
        double scale = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScale());
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(1);
        context.getInput().moveCursor(-24 * scale, -12 * scale);
        context.waitTicks(2);
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
        var pan = onScreen(context, HoardScreen::worksPan);
        expect(pan[0] < -10 && pan[1] < -4, "dragging panned the graph to " + pan[0] + ", " + pan[1]);
        hover(context, onScreen(context, screen -> screen.nodeCenter(shard)));
        shot(context, "reckoning-plan-zoomed");
        expect(onScreen(context, screen -> screen.getMenu().getPlan() != null), "panning lost the plan");
        hover(context, center);
        context.getInput().scroll(-1);
        context.getInput().scroll(-1);
        context.waitTicks(2);

        clickControl(context, "more");
        waitOnScreen(context, 40, "no plan came for two Void Pearls", screen -> screen.getMenu().getPlan() != null && screen.getMenu().getPlan().count() == 2);
        var lacking = onScreen(context, screen -> screen.getMenu().getPlan().graph().nodes().stream().filter(node -> node.lacking() > 0).count());
        expect(lacking > 0, "two Void Pearls lack nothing from one ender pearl");
        var begin = onScreen(context, screen -> screen.worksControlText("begin"));
        expect(begin.equals("Queue anyway"), "a plan lacking ingredients offers " + begin);
        hover(context, new int[] { 2, 2 });
        shot(context, "reckoning-plan-lacking");
        // Hovered, the whisper lists everything the plan lacks
        hover(context, onScreen(context, HoardScreen::whisperCenter));
        shot(context, "reckoning-lacking-list");
        clickControl(context, "less");
        waitOnScreen(context, 40, "the plan did not go back to one", screen -> screen.getMenu().getPlan() != null && screen.getMenu().getPlan().count() == 1);
        expect(onScreen(context, screen -> screen.worksControlText("begin")).equals("Begin"), "a plan lacking nothing does not offer Begin");

        // An amount typed in plans that many, and Escape goes back a step rather than closing the screen
        click(context, onScreen(context, screen -> screen.worksControl("amount")));
        for (int i = 0; i < 4; i++)
            context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
        context.getInput().typeChars("12");
        waitOnScreen(context, 40, "no plan came for twelve typed Void Pearls", screen -> screen.getMenu().getPlan() != null && screen.getMenu().getPlan().count() == 12);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(2);
        expect(onScreen(context, screen -> !screen.worksGraphShown()), "Escape did not go back from the plan");
        click(context, onScreen(context, screen -> screen.makeableCenter(PEARL)));
        waitOnScreen(context, 40, "the plan never came back", screen -> screen.getMenu().getPlan() != null && screen.getMenu().getPlan().item().equals(PEARL));
        for (int i = 0; i < 11; i++)
            clickControl(context, "less");
        waitOnScreen(context, 40, "the plan did not go back to one", screen -> screen.getMenu().getPlan() != null && screen.getMenu().getPlan().count() == 1);
    }

    /**
     * Begun, the plan becomes an order the Officiants work, shown with its progress until the pearl is in the hoard.
     */
    private static void checkOrder(ClientGameTestContext context, TestServerContext server)
    {
        clickControl(context, "begin");
        waitOnScreen(context, 40, "the order never showed", screen -> screen.getMenu().getJobs().size() == 1);
        server.runOnServer(minecraft -> expect(WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs().size() == 1, "the order was not queued"));
        click(context, onScreen(context, screen -> screen.jobCenter(0)));

        // The order shown once the altar is at work: the shards are done and the pearl is being made
        waitFor(context, server, 900, "the altar never started the Void Pearl",
                minecraft -> ((net.voxelindustry.voidheart.common.content.altar.VoidAltarTile) minecraft.overworld().getBlockEntity(ALTAR)).isCrafting());
        waitOnScreen(context, 40, "the order left the view while its last craft was worked",
                screen -> screen.getMenu().getJobs().size() == 1 && screen.getMenu().getJobs().getFirst().working().values().stream().anyMatch(working -> working > 0));
        expect(onScreen(context, screen -> screen.getMenu().getJobs().getFirst().graph().nodes().size()) >= 4, "the order shows no plan");
        context.waitTicks(10);
        hover(context, new int[] { 2, 2 });
        shot(context, "reckoning-order");
        checkHologram(context, server);

        // Opened again, the Reckoning no longer lists the order
        server.runCommand("gamemode survival @a");
        aimAtTable(context, server);
        rightClick(context);
        waitOnScreen(context, 60, "the finished order still shows", screen -> screen.worksShown() && screen.getMenu().getJobs().isEmpty());
    }

    /**
     * The table shows the order at work as a map, frozen mid-craft while the camera moves, then flares and sinks once it is done.
     */
    private static void checkHologram(ClientGameTestContext context, TestServerContext server)
    {
        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        context.waitTicks(3);
        server.runCommand("tick freeze");
        server.runCommand("gamemode spectator @a");
        var shownOrder = context.computeOnClient(minecraft -> ReckoningHologram.shown());
        expect(shownOrder != null && shownOrder.working().values().stream().anyMatch(working -> working > 0), "the table shows no order at work: " + shownOrder);

        tableView(context, server, "reckoning-hologram-south", 0, 1.1, 2.6);
        var drawn = context.computeOnClient(minecraft -> ReckoningHologram.drawnLastFrame());
        expect(drawn >= 4, "the table drew " + drawn + " items of the order");
        tableView(context, server, "reckoning-hologram-east", 2.8, 0.6, 0.3);
        tableView(context, server, "reckoning-hologram-top", 0.05, 2.6, 0.25);
        for (int step = 0; step < 6; step++)
            tableView(context, server, "reckoning-hologram-sweep-" + step, -0.3 + step * 0.12, 1.1, 2.4 - step * 0.05);
        server.runCommand("tick unfreeze");

        tableView(context, server, "reckoning-hologram-south-running", 0, 1.1, 2.6);
        waitFor(context, server, 2000, "no Void Pearl reached the hoard", minecraft -> held(minecraft, VoidHeartItems.VOID_PEARL) == 1);
        for (int tick = 0; tick < 60 && context.computeOnClient(minecraft -> ReckoningHologram.shown()) != null; tick++)
            context.waitTick();
        expect(context.computeOnClient(minecraft -> ReckoningHologram.shown()) == null, "the table still shows the finished order");
        shot(context, "reckoning-hologram-flare");
        context.waitTicks(12);
        shot(context, "reckoning-hologram-sinking");
        context.waitTicks(30);
        shot(context, "reckoning-hologram-after");
    }

    /**
     * Four ender shards kept: the level is ordered from the ender pearl in the hoard, and lifting it stops keeping them.
     */
    private static void checkKeep(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> hoard(minecraft).insert(ItemVariant.of(Items.ENDER_PEARL), 1));
        click(context, onScreen(context, screen -> screen.makeableCenter(SHARD)));
        waitOnScreen(context, 40, "no plan came for ender shards", screen -> screen.getMenu().getPlan() != null && screen.getMenu().getPlan().item().equals(SHARD));
        for (int step = 0; step < 3; step++)
            clickControl(context, "more");
        waitOnScreen(context, 40, "no plan came for four ender shards", screen -> screen.getMenu().getPlan() != null && screen.getMenu().getPlan().count() == 4);
        clickControl(context, "keep");
        waitOnScreen(context, 40, "the keep never showed", screen -> screen.getMenu().getKeeps().stream().anyMatch(kept -> kept.item().equals(SHARD) && kept.level() == 4));
        shot(context, "reckoning-kept");
        waitFor(context, server, 600, "the kept ender shards were never made", minecraft -> held(minecraft, VoidHeartItems.ENDER_SHARD) >= 4);

        clickControl(context, "keep");
        waitOnScreen(context, 40, "the keep was not lifted", screen -> screen.getMenu().getKeeps().isEmpty());
        server.runOnServer(minecraft -> expect(WorksState.get(minecraft).owner(player(minecraft).getUUID()).keeps().isEmpty(), "the server still keeps a level"));
        clickControl(context, "back");
    }

    /**
     * A Fold planned from the hoard drinks Stillness, drawn as a leaf of the graph in the aspect's colour.
     */
    private static void checkEssencePlan(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            Knowledge.lockOf(minecraft.registryAccess(), id("altar/fold")).ifPresent(knowledge(minecraft)::grant);
            hoard(minecraft).insert(ItemVariant.of(Items.HOPPER), 1);
            hoard(minecraft).insert(ItemVariant.of(VoidHeartItems.COVETOUS_COPPER_INGOT), 4);
            hoard(minecraft).insert(ItemVariant.of(VoidHeartItems.VOID_PEARL), 1);
            hoard(minecraft).insert(ItemVariant.of(VoidHeartItems.ENDER_SHARD), 2);
            hoard(minecraft).insert(ItemVariant.of(net.voxelindustry.voidheart.common.content.crystal.CrystalBundles.shard(CrystalKind.AZURIUM)), 1);
        });
        var fold = ItemVariant.of(net.voxelindustry.voidheart.common.content.hoard.HoardContent.FOLD);
        waitOnScreen(context, 60, "the Fold never became makeable", screen -> screen.getMenu().getMakeable().contains(fold));
        click(context, onScreen(context, screen -> screen.makeableCenter(fold)));
        waitOnScreen(context, 40, "no plan came for the Fold", screen -> screen.getMenu().getPlan() != null && screen.getMenu().getPlan().item().equals(fold));
        var graph = onScreen(context, screen -> screen.getMenu().getPlan().graph());
        var count = onScreen(context, screen -> screen.getMenu().getPlan().count());
        expect(graph.drinks().size() == 1 && graph.drinks().getFirst().aspect().equals("voidheart:stillness") && graph.drinks().getFirst().amount() == 1_500 * count,
                "the plan for " + count + " Folds drinks " + graph.drinks());
        // The leaf comes after the kinds the view shows, which leaves out unpacking what the hoard holds no packed form of
        var shown = graph.trimmed();
        expect(shown.drinks().size() == 1, "the Fold's drink was trimmed from the view: " + shown.drinks());
        hover(context, onScreen(context, screen -> screen.nodeCenter(shown.nodes().size())));
        shot(context, "reckoning-plan-essence");
        clickControl(context, "back");
    }

    /**
     * A Beam Focus asks for three Ravenous Gold, one per pillar. With none in the hoard, one block unpacked would give all three, but no block is held:
     * none of the three is counted as from the hoard, and the view shows all three as lacking.
     */
    private static void checkCraftLeftoversNotFromHoard(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            Knowledge.lockOf(minecraft.registryAccess(), id("altar/beam_focus")).ifPresent(knowledge(minecraft)::grant);
            var gold = ItemVariant.of(VoidHeartItems.RAVENOUS_GOLD_INGOT);
            hoard(minecraft).extract(gold, hoard(minecraft).count(gold));
            var owner = player(minecraft).getUUID();
            var plan = net.voxelindustry.voidheart.common.content.works.WorksPlan.plan(
                    net.voxelindustry.voidheart.common.content.works.WorksCatalogues.of(minecraft, owner, hoard(minecraft)).lookup(), hoard(minecraft).view(),
                    ItemVariant.of(net.voxelindustry.voidheart.common.setup.VoidHeartBlocks.BEAM_FOCUS), 1);
            var node = plan.nodes().get(gold);
            expect(node != null && node.needed() == 3 && node.fromStock() == 0, "the Beam Focus plan takes Ravenous Gold as " + node);
            var shown = WorksGraph.of(plan).trimmed().nodes().stream().filter(candidate -> candidate.item().equals(gold)).findFirst().orElseThrow();
            expect(shown.lacking() == 3, "the view shows " + shown.lacking() + " Ravenous Gold lacking of 3, none held");
        });
    }

    /**
     * The roster lists both Officiants: the table's and the altar's.
     */
    private static void checkRoster(ClientGameTestContext context, TestServerContext server)
    {
        clickControl(context, "stations");
        waitOnScreen(context, 60, "the roster never listed both Officiants", screen -> screen.getMenu().getRoster().size() == 2);
        hover(context, new int[] { 2, 2 });
        shot(context, "reckoning-roster");
        clickControl(context, "back");
        expect(onScreen(context, screen -> screen.makeableCenter(PEARL) != null), "going back from the roster did not list what can be made");
    }

    /**
     * With its table taken away, a Void Pearl from an ender pearl still plans its shards at the table, and the plan says no Officiant serves one.
     */
    private static void checkUnservedTable(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            minecraft.overworld().removeBlock(TABLE, false);
            hoard(minecraft).extract(SHARD, held(minecraft, VoidHeartItems.ENDER_SHARD));
            hoard(minecraft).insert(ItemVariant.of(Items.ENDER_PEARL), 1);
        });
        var table = net.voxelindustry.voidheart.common.content.works.StationKind.TABLE.ordinal();
        waitOnScreen(context, 100, "the roster still shows a table", screen -> !screen.getMenu().getRoster().isEmpty()
                && screen.getMenu().getRoster().stream().noneMatch(station -> station.station() == table));
        // What only the table makes leaves the list before the plates are aimed at
        waitOnScreen(context, 100, "the ender shards are still offered without a table", screen -> !screen.getMenu().getMakeable().contains(SHARD));
        // Lets the list settle before its plates are aimed at
        shot(context, "reckoning-unserved-list");
        var aim = onScreen(context, screen -> screen.makeableCenter(PEARL));
        expect(aim != null, "the Void Pearl is not offered without a table: " + onScreen(context, screen -> screen.getMenu().getMakeable().size()));
        click(context, aim);
        waitOnScreen(context, 40, "no plan came for the Void Pearl", screen -> screen.getMenu().getPlan() != null && screen.getMenu().getPlan().item().equals(PEARL));
        expect(onScreen(context, screen -> screen.getMenu().getPlan().graph().nodes().stream().anyMatch(node -> node.station() == table)),
                "the plan made no shards at the table no Officiant serves");
        var whisper = onScreen(context, HoardScreen::worksWhisper);
        expect(whisper.equals("No table Officiant"), "the plan says " + whisper);
        shot(context, "reckoning-plan-unserved");

        // Queued anyway, with two more orders, the panel shows them as one branch, each waiting on the table
        clickControl(context, "begin");
        server.runOnServer(minecraft ->
        {
            net.voxelindustry.voidheart.common.content.works.WorksContent.order(minecraft, player(minecraft).getUUID(), SHARD, 8);
            net.voxelindustry.voidheart.common.content.works.WorksContent.order(minecraft, player(minecraft).getUUID(), PEARL, 2);
        });
        waitOnScreen(context, 60, "the three orders never showed", screen -> screen.getMenu().getJobs().size() == 3);
        hover(context, onScreen(context, screen -> screen.jobCenter(1)));
        shot(context, "reckoning-side-orders");
        server.runOnServer(minecraft ->
        {
            var works = WorksState.get(minecraft).edit(player(minecraft).getUUID());
            works.jobs().forEach(job -> works.cancel(job.id()));
        });
        waitOnScreen(context, 60, "the orders were never cancelled", screen -> screen.getMenu().getJobs().isEmpty());
        server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(TABLE, Blocks.CRAFTING_TABLE.defaultBlockState()));
    }

    /**
     * The hoard tab shows the hoard and the inventory, as a Fold's view does.
     */
    private static void checkHoardTab(ClientGameTestContext context, TestServerContext server)
    {
        clickControl(context, "hoard");
        expect(!onScreen(context, HoardScreen::worksShown), "the hoard tab did not show");
        shot(context, "reckoning-hoard-tab");
        clickControl(context, "works");
        expect(onScreen(context, HoardScreen::worksShown), "the works tab did not come back");
        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        context.waitTicks(3);
    }

    /**
     * Sneaking with an empty hand takes the lens back, and the table is an Oculus Table again.
     */
    private static void takeLensBack(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setShiftKeyDown(true);
            useOn(player, OCULUS);
            player.setShiftKeyDown(false);
            var tile = OculusTableStructure.getTile(minecraft.overworld(), OCULUS);
            expect(tile != null && !tile.getView().reckoning(), "the table still holds the lens");
            expect(player.getInventory().countItem(WorksContent.MEMORIAM_LENS) == 1, "the lens did not come back");
        });
    }
}
