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
import java.util.function.Function;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * A Memoriam Lens set in an Oculus Table makes a Reckoning: used with an empty hand it opens the owner's hoard on its works tab, which lists what the
 * Officiants' stations can make, draws the plan for a chosen item, begins it as an order whose progress it shows, and keeps a level in the hoard.
 * Sneaking takes the lens back.
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

            setLens(context, server);
            openReckoning(context, server);
            checkPlan(context, server);
            checkOrder(context, server);
            checkKeep(context, server);
            checkHoardTab(context, server);
            takeLensBack(context, server);
            server.runCommand("gamemode creative @a");
        }
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
        context.waitTicks(10);
        aimFrom(context, server, OCULUS.getX() + 0.5, FLAT_FEET + 1, OCULUS.getZ() - 3.5, OCULUS.getX() + 0.5, OCULUS.getY() + 0.6, OCULUS.getZ() + 0.5, 5);
        shot(context, "reckoning-table");
        aimFrom(context, server, OCULUS.getX() + 0.5, FLAT_FEET, OCULUS.getZ() - 2.5, OCULUS.getX() + 0.5, OCULUS.getY() + 0.6, OCULUS.getZ() + 0.5, 5);
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

        clickControl(context, "more");
        waitOnScreen(context, 40, "no plan came for two Void Pearls", screen -> screen.getMenu().getPlan() != null && screen.getMenu().getPlan().count() == 2);
        var lacking = onScreen(context, screen -> screen.getMenu().getPlan().graph().nodes().stream().filter(node -> node.lacking() > 0).count());
        expect(lacking > 0, "two Void Pearls lack nothing from one ender pearl");
        hover(context, new int[] { 2, 2 });
        shot(context, "reckoning-plan-lacking");
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

        waitFor(context, server, 2000, "no Void Pearl reached the hoard", minecraft -> held(minecraft, VoidHeartItems.VOID_PEARL) == 1);
        waitOnScreen(context, 60, "the finished order still shows", screen -> screen.getMenu().getJobs().isEmpty());
        shot(context, "reckoning-done");
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
