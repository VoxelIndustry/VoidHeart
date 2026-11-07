package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.client.ArmillaryTremor;
import net.voxelindustry.voidheart.client.screen.ArmillaryScreen;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryChart;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryConnections;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryCoreBlock;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryHubBlock;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryLayout;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonSize;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The whole way into a dungeon: building the instrument, finding and reaching a bubble, paying to open it, crossing both ways, the warnings, and being thrown out.
 */
public class ArmillaryClientTest implements FabricClientGameTest
{
    private static final BlockPos CORE = new BlockPos(0, 120, 0);
    private static final BlockPos HUB  = ArmillaryLayout.hub(CORE);

    private static final int FORMED_TICKS = 30;
    private static final int RISEN_TICKS  = 80;
    private static final int DIAL_SETTLE_TICKS = 120;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        TestWorldSave save;
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            server.runOnServer(ArmillaryClientTest::buildGround);
            context.waitTicks(10);

            checkBaseCanBeCrafted(server);
            checkAssembly(context, server);
            context.getInput().pressKey(InputConstants.KEY_F1);
            screenshotRings(context, server, "armillary-idle");
            server.runCommand("time set midnight");
            context.waitTicks(5);
            screenshotRings(context, server, "armillary-idle-night");
            screenshotBase(context, server, "armillary-base-night");
            server.runCommand("time set noon");
            context.waitTicks(5);
            screenshotBase(context, server, "armillary-base");
            sweepBase(context, server);
            context.getInput().pressKey(InputConstants.KEY_F1);
            checkBaseBlocks(context, server);
            expect(context.computeOnClient(minecraft -> clientArmillary(minecraft).getDisplay().hud(1)) == 0, "A new armillary should have nothing to show on its dial");

            int target = checkChart(context, server);
            checkDialWhenReached(context, server);
            checkOpening(context, server);
            checkDialDraining(context, server);
            context.getInput().pressKey(InputConstants.KEY_F1);
            screenshotRings(context, server, "armillary-portal");
            server.runCommand("time set midnight");
            context.waitTicks(5);
            screenshotRings(context, server, "armillary-portal-night");
            server.runCommand("time set noon");
            context.getInput().pressKey(InputConstants.KEY_F1);

            checkCrossing(context, server);
            checkWarningsAndCollapse(context, server, target);
            checkCollapseOnTheMap(context, server);
            shootDial(context, server, "armillary-dial-bubbles", 0);
            checkRejoin(context, server);
            checkBrokenArmillary(context, server);
            checkArmillaryInsideDungeon(context, server);
            prepareReload(context, server);
        }

        try (var world = save.open())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender(false, 1200);
            checkAfterReload(context, server);
        }
    }

    private static void formArmillary(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            minecraft.overworld().setBlockAndUpdate(CORE, VoidHeartBlocks.ARMILLARY_CORE.defaultBlockState());
            placeBase(minecraft, false);
        });
        context.waitTicks(FORMED_TICKS);
        expect(onServer(server, minecraft -> armillary(minecraft).isFormed()), "A rebuilt armillary should form");
    }

    /**
     * Built inside a dungeon it would be a way of teleporting out, so a survival player is refused like everywhere else in there.
     */
    private static void checkArmillaryInsideDungeon(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode survival @a");
        var feet = onServer(server, minecraft ->
        {
            var player = player(minecraft);
            VoidDungeons.enter(player, new DungeonAddress(DungeonSize.SMALL, DungeonTheme.TEMPLE, 1, 902));
            player.getAbilities().invulnerable = true;
            return player.blockPosition();
        });
        context.waitTicks(30);

        var core = feet.offset(2, 0, 0);
        server.runOnServer(minecraft -> player(minecraft).level().setBlockAndUpdate(core, VoidHeartBlocks.ARMILLARY_CORE.defaultBlockState()));
        context.waitTicks(5);
        aimAt(context, server, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, core.getX() + 0.5, core.getY() + 0.9, core.getZ() + 0.5);
        rightClick(context);
        expect(overlay(context).equals("A force is interfering with your attempt"), "A core used inside a dungeon should be refused, the action bar shows: " + overlay(context));

        server.runCommand("gamemode creative @a");
        server.runOnServer(minecraft -> player(minecraft).teleportTo(minecraft.overworld(), 3.5, CORE.getY(), 0.5, Set.of(), 0, 0, false));
        context.waitTicks(20);
    }

    private static void prepareReload(ClientGameTestContext context, TestServerContext server)
    {
        formArmillary(context, server);
        act(server, armillary ->
        {
            armillary.revealBubble();
            armillary.revealBubble();
        });
        server.runOnServer(minecraft ->
        {
            var chart = armillary(minecraft).getChart();
            chart.target(chart.bubbles().getFirst().id(), ArmillaryTile.rules(), RandomSource.create());
            chart.advanceSearch(0.4F);

            var address = new DungeonAddress(DungeonSize.SMALL, DungeonTheme.FOREST, 1, 903);
            ArmillaryConnections.get(minecraft).open(address, Level.OVERWORLD, CORE, ArmillaryConnections.now(minecraft), 20 * 25);
            VoidDungeons.enter(player(minecraft), address);
        });
        context.waitTicks(40);
    }

    /**
     * Everything that matters survives closing the world: the chart mid search, the connection, and the player inside it, who still gets thrown out when it ends.
     */
    private static void checkAfterReload(ClientGameTestContext context, TestServerContext server)
    {
        expect(dimension(context, server).equals(VoidDungeons.WORLD_KEY.identifier().toString()), "The player should come back inside the dungeon");
        expect(onServer(server, minecraft -> ArmillaryConnections.get(minecraft).get(DungeonAddress.at(player(minecraft).getBlockX(), player(minecraft).getBlockZ()))) != null,
                "The connection should survive a reload");

        context.waitTicks(20 * 25);
        expect(dimension(context, server).equals(Level.OVERWORLD.identifier().toString()), "The connection should still end after a reload");
        context.waitTicks(30);

        expect(onServer(server, minecraft -> armillary(minecraft).isFormed()), "The armillary should still be formed after a reload");
        checkBaseShape(server, true, "A reloaded armillary");
        var phase = onServer(server, minecraft -> armillary(minecraft).getChart().getPhase());
        expect(phase == ArmillaryChart.Phase.SEARCHING, "The search should survive a reload, the chart is " + phase);
        var ticks = onServer(server, minecraft -> armillary(minecraft).getChart().getSearchTicks());
        expect(ticks > 0, "The search should keep its progress, it is at " + ticks + " ticks");
    }

    /**
     * Its corners have to be obtainable in survival, or nobody could ever build one.
     */
    private static void checkBaseCanBeCrafted(TestServerContext server)
    {
        for (var part : List.of(VoidHeartBlocks.VOIDSTONE_CHISELED, VoidHeartBlocks.CUT_RAVENOUS_GOLD, VoidHeartBlocks.ARMILLARY_CORE))
        {
            var found = onServer(server, minecraft -> minecraft.getRecipeManager().getRecipes().stream()
                    .anyMatch(recipe -> recipe.id().identifier().getPath().contains(BuiltInRegistries.BLOCK.getKey(part).getPath())));
            expect(found, "Nothing makes " + BuiltInRegistries.BLOCK.getKey(part));
        }
    }

    private static void buildGround(MinecraftServer server)
    {
        var level = server.overworld();
        for (var pos : BlockPos.betweenClosed(CORE.offset(-12, -1, -12), CORE.offset(12, -1, 12)))
            level.setBlockAndUpdate(pos, Blocks.DEEPSLATE_TILES.defaultBlockState());
    }

    private static void placeBase(MinecraftServer server, boolean leaveOneOut)
    {
        var level = server.overworld();
        var parts = ArmillaryLayout.base(CORE);
        for (int index = leaveOneOut ? 1 : 0; index < parts.size(); index++)
            level.setBlockAndUpdate(parts.get(index).pos(), parts.get(index).block().defaultBlockState());
        if (!leaveOneOut)
            level.setBlockAndUpdate(parts.getFirst().pos(), parts.getFirst().block().defaultBlockState());
    }

    private static ArmillaryTile armillary(MinecraftServer server)
    {
        return (ArmillaryTile) server.overworld().getBlockEntity(CORE);
    }

    private static ArmillaryTile clientArmillary(net.minecraft.client.Minecraft minecraft)
    {
        return (ArmillaryTile) minecraft.level.getBlockEntity(CORE);
    }

    private static <T> T onServer(TestServerContext server, Function<MinecraftServer, T> function)
    {
        return server.computeOnServer(function::apply);
    }

    private static void act(TestServerContext server, Consumer<ArmillaryTile> action)
    {
        server.runOnServer(minecraft -> action.accept(armillary(minecraft)));
    }

    private static void aimAt(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z)
    {
        server.runCommand("execute in " + dimension(context, server) + " run tp @a " + aimedPose(feetX, feetY, feetZ, x, y, z));
        context.waitTicks(10);
    }

    private static String dimension(ClientGameTestContext context, TestServerContext server)
    {
        return onServer(server, minecraft -> player(minecraft).level().dimension().identifier().toString());
    }

    private static void aimAtHub(ClientGameTestContext context, TestServerContext server)
    {
        aimAt(context, server, 3.5, CORE.getY(), 0.5, 0.5, HUB.getY() + 0.5, 0.5);
    }

    private static ArmillaryScreen screen(Minecraft minecraft)
    {
        return (ArmillaryScreen) minecraft.gui.screen();
    }

    private static int selected(ClientGameTestContext context)
    {
        return context.computeOnClient(minecraft -> screen(minecraft).selected());
    }

    private static List<String> tablet(ClientGameTestContext context)
    {
        return context.computeOnClient(minecraft -> screen(minecraft).tabletText());
    }

    private static String primaryLabel(ClientGameTestContext context)
    {
        return context.computeOnClient(minecraft -> screen(minecraft).primaryLabel());
    }

    private static void captureSizes(ClientGameTestContext context, String name)
    {
        resize(context, 640, 480, 2);
        context.takeScreenshot(name + "-320x240");
        resize(context, 1920, 1080, 4);
        context.takeScreenshot(name + "-480x270");
        resize(context, 1920, 1080, 3);
        context.takeScreenshot(name + "-640x360");
        resize(context, 1920, 1080, 2);
        context.takeScreenshot(name + "-960x540");
        resize(context, 854, 480, 2);
        context.takeScreenshot(name);
    }

    /**
     * A lone core shows what is missing, rings form only over a whole base, and anything in their way brings them down.
     */
    private static void checkAssembly(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(CORE, VoidHeartBlocks.ARMILLARY_CORE.defaultBlockState()));
        context.waitTicks(FORMED_TICKS);
        expect(!onServer(server, minecraft -> armillary(minecraft).isFormed()), "A lone core should not form");

        aimAt(context, server, 2.5, CORE.getY(), 2.5, 0.5, CORE.getY() + 0.9, 0.5);
        rightClick(context);
        expect(onServer(server, minecraft -> armillary(minecraft).isPreviewActive()), "Using a lone core should show the base it needs");
        expect(overlay(context).contains("chiseled voidstone"), "Using a lone core should say what it needs, the action bar shows: " + overlay(context));
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.waitTicks(5);
        aimAt(context, server, 4.5, CORE.getY() + 1.5, 4.5, 0.5, CORE.getY() + 1, 0.5);
        context.takeScreenshot("armillary-preview");
        context.getInput().pressKey(InputConstants.KEY_F1);

        server.runOnServer(minecraft -> placeBase(minecraft, true));
        context.waitTicks(FORMED_TICKS);
        expect(!onServer(server, minecraft -> armillary(minecraft).isFormed()), "A base missing a block should not form");

        server.runOnServer(minecraft -> placeBase(minecraft, false));
        context.waitTicks(FORMED_TICKS);
        expect(onServer(server, minecraft -> armillary(minecraft).isFormed()), "A whole base should form");
        expect(onServer(server, minecraft -> minecraft.overworld().getBlockState(HUB).is(VoidHeartBlocks.ARMILLARY_HUB)), "A formed armillary should keep its middle clickable");
        checkBaseShape(server, true, "A whole base");
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot("armillary-rising");
        context.waitTicks(RISEN_TICKS);
        context.getInput().pressKey(InputConstants.KEY_F1);

        server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(CORE.offset(1, 3, 1), Blocks.STONE.defaultBlockState()));
        context.waitTicks(FORMED_TICKS);
        expect(!onServer(server, minecraft -> armillary(minecraft).isFormed()), "A block in the way of the rings should stop them");
        expect(onServer(server, minecraft -> !minecraft.overworld().getBlockState(HUB).is(VoidHeartBlocks.ARMILLARY_HUB)), "A stopped armillary should take its middle away");
        checkBaseShape(server, false, "A stopped armillary");

        aimAt(context, server, 2.5, CORE.getY(), 2.5, 0.5, CORE.getY() + 0.9, 0.5);
        rightClick(context);
        expect(overlay(context).contains("left free"), "Using a blocked core should say why, the action bar shows: " + overlay(context));

        server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(CORE.offset(1, 3, 1), Blocks.AIR.defaultBlockState()));
        context.waitTicks(FORMED_TICKS + RISEN_TICKS);
        expect(onServer(server, minecraft -> armillary(minecraft).isFormed()), "Clearing the way should bring the rings back");
        checkBaseShape(server, true, "An armillary with its way cleared");

        server.runOnServer(minecraft -> player(minecraft).setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(VoidHeartBlocks.ARMILLARY_CORE)));
        context.waitTicks(5);
        context.takeScreenshot("armillary-core-held");
        server.runOnServer(minecraft -> player(minecraft).setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY));
    }

    private static void checkBaseShape(TestServerContext server, boolean formed, String who)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            for (var part : ArmillaryLayout.base(CORE))
            {
                var state = level.getBlockState(part.pos());
                var wanted = formed ? part.formed() : part.block().defaultBlockState();
                expect(state == wanted, who + " should have " + wanted + " at " + part.pos() + ", found " + state);
            }
            expect(level.getBlockState(CORE).getValue(ArmillaryCoreBlock.FORMED) == formed, who + " should have a core showing formed=" + formed);
            expect(armillary(minecraft).getBlockState().getValue(ArmillaryCoreBlock.FORMED) == formed, who + " should have told its tile");
        });
    }

    /**
     * The pieces of the model behave like one instrument: a click anywhere opens it, and each piece still is the block it was when mined.
     */
    private static void checkBaseBlocks(ClientGameTestContext context, TestServerContext server)
    {
        for (double[] target : new double[][] { { 3.5, 0.5, 1.5, 0.5 }, { 3.2, 3.2, 1.5, 1.5 } })
        {
            aimAt(context, server, target[0], CORE.getY(), target[1], target[2], CORE.getY() + 0.5, target[3]);
            rightClick(context);
            context.waitTicks(5);
            expect(context.computeOnClient(minecraft -> minecraft.gui.screen() instanceof ArmillaryScreen), "Using the base at " + target[2] + " " + target[3] + " should open the map");
            context.runOnClient(minecraft -> minecraft.player.closeContainer());
            context.waitTicks(5);
        }

        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var pickaxe = new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
            for (var part : ArmillaryLayout.base(CORE))
            {
                expect(pickaxe.isCorrectToolForDrops(part.formed()), "A pickaxe should be right for " + part.formed());
                var drops = Block.getDrops(part.formed(), level, part.pos(), null, player(minecraft), pickaxe);
                expect(drops.size() == 1 && drops.getFirst().is(part.block().asItem()), part.pos() + " should drop " + part.block() + ", it drops " + drops);
                expect(level.getBlockState(part.pos()).getCloneItemStack(level, part.pos(), false).is(part.block().asItem()), "Picking " + part.pos() + " should give " + part.block());
            }
        });

        var broken = ArmillaryLayout.base(CORE).getFirst();
        var dropped = onServer(server, minecraft ->
        {
            var level = minecraft.overworld();
            level.destroyBlock(broken.pos(), true, player(minecraft));
            return level.getEntitiesOfClass(ItemEntity.class, new AABB(broken.pos()).inflate(1)).stream().anyMatch(item -> item.getItem().is(broken.block().asItem()));
        });
        expect(dropped, "Breaking " + broken.pos() + " should leave its block on the ground");
        context.waitTicks(FORMED_TICKS);
        expect(!onServer(server, minecraft -> armillary(minecraft).isFormed()), "A broken base should stop the armillary");
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(level.getBlockState(broken.pos()).isAir(), "The broken piece should stay gone");
            for (var part : ArmillaryLayout.base(CORE))
                expect(part.pos().equals(broken.pos()) || level.getBlockState(part.pos()).is(part.block()), part.pos() + " should be its block again");
            expect(!level.getBlockState(CORE).getValue(ArmillaryCoreBlock.FORMED), "The core should be a cube again");
            level.getEntitiesOfClass(ItemEntity.class, new AABB(CORE).inflate(3)).forEach(ItemEntity::discard);
        });
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimAt(context, server, 3.5, CORE.getY() + 0.5, 3.5, 0.5, CORE.getY() + 0.6, 0.5);
        context.takeScreenshot("armillary-base-broken");
        context.getInput().pressKey(InputConstants.KEY_F1);

        server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(broken.pos(), broken.block().defaultBlockState()));
        context.waitTicks(FORMED_TICKS);
        checkBaseShape(server, true, "A repaired armillary");
        context.waitTicks(RISEN_TICKS);
    }

    private static void screenshotBase(ClientGameTestContext context, TestServerContext server, String name)
    {
        // Spectating, since these look from below the height of a player standing on the ground
        server.runCommand("gamemode spectator @a");
        aimAt(context, server, 3.6, CORE.getY() - 0.6, 0.5, 0.5, CORE.getY() + 0.6, 0.5);
        context.takeScreenshot(name + "-side");
        aimAt(context, server, 3.1, CORE.getY() - 0.6, 3.1, 0.5, CORE.getY() + 0.6, 0.5);
        context.takeScreenshot(name + "-corner");
        aimAt(context, server, 2.2, CORE.getY() + 1.1, 1.2, 0.5, CORE.getY() + 0.6, 0.5);
        context.takeScreenshot(name + "-above");
        aimAt(context, server, -5.5, CORE.getY(), 6.5, 0.5, CORE.getY() + 1.5, 0.5);
        context.takeScreenshot(name + "-whole");
        server.runCommand("gamemode creative @a");
    }

    /**
     * Grazing the top of the base, where its steps meet between blocks.
     */
    private static void sweepBase(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode spectator @a");
        server.runCommand("tick freeze");
        for (int step = 0; step < 8; step++)
        {
            aimAt(context, server, 3.4 + step * 0.05, CORE.getY() - 0.7 + step * 0.02, -1.5 + step * 0.08, 0.5, CORE.getY() + 0.8, 0.5);
            context.takeScreenshot("armillary-base-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
        server.runCommand("gamemode creative @a");
    }

    private static void screenshotRings(ClientGameTestContext context, TestServerContext server, String name)
    {
        aimAt(context, server, 4.5, CORE.getY() + 0.5, 3.5, 0.5, HUB.getY() + 0.5, 0.5);
        context.takeScreenshot(name + "-near");
        context.waitTicks(30);
        aimAt(context, server, -3.5, CORE.getY() + 1, -4.5, 0.5, HUB.getY() + 0.5, 0.5);
        context.takeScreenshot(name + "-far-side");
        aimAt(context, server, 2.5, CORE.getY() + 5.5, 1.5, 0.5, HUB.getY() + 0.5, 0.5);
        context.takeScreenshot(name + "-above");
    }

    /**
     * Opened by a real click on the middle of the rings, targeting a bubble by a real click on the map. The hidden theme must not reach the client early.
     */
    private static int checkChart(ClientGameTestContext context, TestServerContext server)
    {
        aimAtHub(context, server);
        rightClick(context);
        context.waitTicks(5);
        expect(context.computeOnClient(minecraft -> minecraft.gui.screen() instanceof ArmillaryScreen), "Using the middle of the rings should open the map");
        context.takeScreenshot("armillary-map-empty");

        var deepest = onServer(server, minecraft ->
        {
            var chart = armillary(minecraft).getChart();
            for (int difficulty : new int[] { 1, 3, 2, 4 })
                chart.find(ArmillaryTile.rules(), randomForDifficulty(difficulty));
            return chart.find(ArmillaryTile.rules(), randomForDifficulty(5));
        });
        context.waitTicks(60);
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(2);
        context.takeScreenshot("armillary-map-found");

        var center = context.computeOnClient(minecraft -> screen(minecraft).bubbleCenter(deepest.id()));
        expect(center != null, "The screen should have been told about the bubble");
        double scale = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScale());
        context.getInput().setCursorPos(center[0] * scale, center[1] * scale);
        context.waitTicks(3);
        context.takeScreenshot("armillary-map-hover");

        click(context, center[0], center[1], 5);
        expect(onServer(server, minecraft -> armillary(minecraft).getChart().getTargetId()) == -1, "A click should only look at a bubble");
        expect(selected(context) == deepest.id(), "A click should select the bubble");
        expect(tablet(context).contains("5 void pearls"), "The price should show from the first look, the tablet says " + tablet(context));
        expect(String.join(" ", tablet(context)).contains("Nothing comes back from here whole."), "The depth should say how dangerous it is, the tablet says " + tablet(context));
        expect(String.join(" ", tablet(context)).contains("8m 00s to search"), "The search time should show before committing to it, the tablet says " + tablet(context));
        expect(primaryLabel(context).equals("Search for a path"), "A bubble on its own should offer a search, not " + primaryLabel(context));
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(2);
        context.takeScreenshot("armillary-map-tablet");

        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(2);
        expect(selected(context) == -1 && context.computeOnClient(minecraft -> minecraft.gui.screen() instanceof ArmillaryScreen), "Escape should let go of the tablet before the screen");
        context.getInput().pressKey(InputConstants.KEY_DOWN);
        context.waitTicks(2);
        expect(selected(context) != -1, "The down key should walk the bubbles");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(2);

        click(context, center[0], center[1], 5);
        var plate = context.computeOnClient(minecraft -> screen(minecraft).primaryCenter());
        click(context, plate[0], plate[1], 5);
        context.waitTicks(5);
        expect(onServer(server, minecraft -> armillary(minecraft).getChart().getTargetId()) == deepest.id(), "The plate should start the search");
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(25);
        expect(tablet(context).stream().anyMatch(line -> line.endsWith(" left")), "A running search should say how long it has left, the tablet says " + tablet(context));
        context.takeScreenshot("armillary-map-search-tablet");

        int other = onServer(server, minecraft -> armillary(minecraft).getChart().bubbles().stream().filter(bubble -> bubble.id() != deepest.id()).findFirst().orElseThrow().id());
        var otherCenter = context.computeOnClient(minecraft -> screen(minecraft).bubbleCenter(other));
        click(context, otherCenter[0], otherCenter[1], 5);
        expect(onServer(server, minecraft -> armillary(minecraft).getChart().getTargetId()) == deepest.id(), "Looking at another bubble should not lose the search");
        expect(String.join(" ", tablet(context)).contains("The search under way will be lost."), "Another bubble should warn what it would cost, the tablet says " + tablet(context));
        expect(primaryLabel(context).equals("Search here instead"), "Another bubble should offer to search there instead, not " + primaryLabel(context));
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(2);
        context.takeScreenshot("armillary-map-search-other");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(2);

        for (int percent : new int[] { 12, 30, 50, 72, 92 })
        {
            act(server, armillary -> armillary.advanceSearch(percent / 100F));
            context.waitTicks(4);
            context.takeScreenshot("armillary-map-search-" + percent);
            if (percent == 50)
            {
                shootDial(context, server, "armillary-dial-search", 0.5F);
                aimAtHub(context, server);
                rightClick(context);
                context.waitTicks(10);
            }
        }

        act(server, armillary -> armillary.advanceSearch(1));
        context.waitTicks(8);
        expect(onServer(server, minecraft -> armillary(minecraft).getChart().getPhase()) == ArmillaryChart.Phase.REACHED, "A finished search should reach the bubble");
        context.takeScreenshot("armillary-map-reached-bloom");
        context.waitTicks(60);
        expect(selected(context) == deepest.id(), "Reaching a bubble should bring up its tablet");
        int gap = context.computeOnClient(minecraft -> screen(minecraft).plateGap());
        expect(gap >= 8, "Abandoning should stand well apart from opening, they are " + gap + " apart");
        context.takeScreenshot("armillary-map-reached");
        return deepest.id();
    }

    /**
     * The dial from where a player would read it, with the map closed. The server's word can take a few seconds to reach it, and it eases from there.
     */
    private static void shootDial(ClientGameTestContext context, TestServerContext server, String name, float arc)
    {
        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimAt(context, server, 4.5, CORE.getY(), 3.5, 0.5, CORE.getY() + 1.4, 0.5);
        context.waitTicks(DIAL_SETTLE_TICKS);
        float hud = context.computeOnClient(minecraft -> clientArmillary(minecraft).getDisplay().hud(1));
        float shown = context.computeOnClient(minecraft -> clientArmillary(minecraft).getDisplay().arc(1));
        expect(hud == 1, name + " should show the dial, it stands at " + hud);
        expect(Math.abs(shown - arc) < 0.04F, name + " should light " + arc + " of the dial, it shows " + shown);
        context.takeScreenshot(name);
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    /**
     * Full and in the theme's colour once a path is ready. Readable from across a room, and gone from further away.
     */
    private static void checkDialWhenReached(ClientGameTestContext context, TestServerContext server)
    {
        shootDial(context, server, "armillary-dial-reached", 1);
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimAt(context, server, 10.5, CORE.getY(), 7.5, 0.5, CORE.getY() + 1.4, 0.5);
        context.takeScreenshot("armillary-dial-reached-across");
        aimAt(context, server, 26.5, CORE.getY(), 15.5, 0.5, CORE.getY() + 1.4, 0.5);
        context.takeScreenshot("armillary-dial-reached-far");
        aimAt(context, server, 2.5, CORE.getY(), 1.5, 0.5, CORE.getY() + 1.4, 0.5);
        context.takeScreenshot("armillary-dial-reached-too-close");
        context.getInput().pressKey(InputConstants.KEY_F1);

        aimAtHub(context, server);
        rightClick(context);
        context.waitTicks(10);
    }

    /**
     * Drains with the time left, and starts to flicker near the end. The connection is put back afterwards for the rest of the test.
     */
    private static void checkDialDraining(ClientGameTestContext context, TestServerContext server)
    {
        shootDial(context, server, "armillary-dial-connected", 1);
        int duration = onServer(server, minecraft -> (int) ArmillaryConnections.get(minecraft).get(armillary(minecraft).getConnection()).duration());

        act(server, armillary -> armillary.decayIn(duration / 20 / 2));
        shootDial(context, server, "armillary-dial-draining", 0.5F);
        act(server, armillary -> armillary.decayIn(40));
        shootDial(context, server, "armillary-dial-failing", 40F * 20 / duration);
        act(server, armillary -> armillary.decayIn(duration / 20));
        context.waitTicks(20);
    }

    /**
     * A random whose next bubble lies at that depth, to fill the chart the same way on every run.
     */
    private static RandomSource randomForDifficulty(int difficulty)
    {
        for (long seed = 0; ; seed++)
        {
            if (new ArmillaryChart().find(ArmillaryTile.rules(), RandomSource.create(seed)).difficulty() == difficulty)
                return RandomSource.create(seed);
        }
    }

    /**
     * A survival player pays in void pearls; without them nothing opens.
     */
    private static void checkOpening(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode survival @a");
        server.runOnServer(minecraft -> player(minecraft).getAbilities().invulnerable = true);
        context.waitTicks(5);

        context.waitTicks(25);
        expect(!context.computeOnClient(minecraft -> screen(minecraft).primaryActive()), "Opening should not be on offer without the pearls");
        expect(primaryLabel(context).equals("Short by 5 void pearls"), "The plate should say what is missing, not " + primaryLabel(context));
        context.takeScreenshot("armillary-map-no-pearls");

        var button = context.computeOnClient(minecraft -> screen(minecraft).primaryCenter());
        click(context, button[0], button[1], 5);
        context.waitTicks(5);
        expect(onServer(server, minecraft -> armillary(minecraft).getChart().getPhase()) == ArmillaryChart.Phase.REACHED, "Without pearls the bubble should stay shut");

        int cost = onServer(server, minecraft -> armillary(minecraft).getChart().target().openCost());
        server.runOnServer(minecraft -> player(minecraft).getInventory().add(new ItemStack(VoidHeartItems.VOID_PEARL, cost + 2)));
        context.waitTicks(25);
        expect(context.computeOnClient(minecraft -> screen(minecraft).primaryActive()), "With the pearls in hand opening should be on offer");
        expect(primaryLabel(context).equals("Open: 5 void pearls"), "The plate should give the price, not " + primaryLabel(context));
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(2);
        captureSizes(context, "armillary-map-can-open");

        button = context.computeOnClient(minecraft -> screen(minecraft).primaryCenter());
        click(context, button[0], button[1], 5);
        context.waitTicks(10);

        expect(onServer(server, minecraft -> armillary(minecraft).getChart().getPhase()) == ArmillaryChart.Phase.CONNECTED, "Paying should open the bubble");
        int left = onServer(server, minecraft -> player(minecraft).getInventory().countItem(VoidHeartItems.VOID_PEARL));
        expect(left == 2, "Opening should cost exactly " + cost + " pearls, " + left + " are left of " + (cost + 2));
        expect(onServer(server, minecraft -> minecraft.overworld().getBlockState(HUB).getValue(ArmillaryHubBlock.OPEN)), "The middle of the rings should show an open portal");
        context.takeScreenshot("armillary-map-connected-bloom");
        context.waitTicks(45);
        expect(tablet(context).stream().anyMatch(line -> line.endsWith(" left")), "An open connection should say how long it has left, the tablet says " + tablet(context));
        expect(!context.computeOnClient(minecraft -> screen(minecraft).primaryVisible()), "Nothing is left to do on the map once a way stands open");
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(2);
        captureSizes(context, "armillary-map-connected");

        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        context.waitTicks(10);
        server.runCommand("gamemode creative @a");
        context.waitTicks(60);
    }

    /**
     * Across by right clicking the portal, back by right clicking the rift, and across once more.
     */
    private static void checkCrossing(ClientGameTestContext context, TestServerContext server)
    {
        aimAtHub(context, server);
        rightClick(context);
        context.waitTicks(40);
        expect(dimension(context, server).equals(VoidDungeons.WORLD_KEY.identifier().toString()), "The portal should lead into the dungeon");
        context.waitTicks(40);

        var rift = onServer(server, ArmillaryClientTest::riftPosition);
        var feet = onServer(server, minecraft -> player(minecraft).blockPosition());
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimAt(context, server, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, rift.getX() + 0.5, rift.getY() + 0.5, rift.getZ() + 0.5);
        context.takeScreenshot("armillary-rift");
        context.getInput().pressKey(InputConstants.KEY_F1);

        rightClick(context);
        context.waitTicks(20);
        expect(dimension(context, server).equals(Level.OVERWORLD.identifier().toString()), "The rift should lead back");
        double distance = onServer(server, minecraft -> Math.sqrt(player(minecraft).blockPosition().distSqr(CORE)));
        expect(distance <= 8, "The rift should land the player beside the armillary, " + distance + " blocks away");

        aimAtHub(context, server);
        rightClick(context);
        context.waitTicks(40);
        expect(dimension(context, server).equals(VoidDungeons.WORLD_KEY.identifier().toString()), "The portal should still lead into the dungeon");
    }

    private static BlockPos riftPosition(MinecraftServer minecraft)
    {
        var player = player(minecraft);
        var address = DungeonAddress.at(player.getBlockX(), player.getBlockZ());
        var plan = VoidDungeons.plan(player.level().getSeed(), address);
        int[] rift = plan.returnRift();
        var pos = address.origin().offset(rift[0], rift[1], rift[2]);
        var block = player.level().getBlockState(pos).getBlock();
        expect(block == VoidHeartBlocks.ARMILLARY_RIFT, "The plan's rift should be placed in the world, found " + BuiltInRegistries.BLOCK.getKey(block));
        return pos;
    }

    /**
     * Warned at a minute, shaken, counted down, then thrown out beside the armillary with the bubble spent.
     */
    private static void checkWarningsAndCollapse(ClientGameTestContext context, TestServerContext server, int target)
    {
        var address = onServer(server, minecraft -> DungeonAddress.at(player(minecraft).getBlockX(), player(minecraft).getBlockZ()));
        server.runOnServer(minecraft -> ArmillaryConnections.get(minecraft).decayIn(address, ArmillaryConnections.now(minecraft) + 20 * 60 + 5));
        context.waitTicks(15);
        expect(overlay(context).contains("collapsing"), "Expected the last minute's warning, the action bar shows: " + overlay(context));

        server.runOnServer(minecraft -> ArmillaryConnections.get(minecraft).decayIn(address, ArmillaryConnections.now(minecraft) + 20 * 10 + 8));
        context.waitTicks(20);
        expect(overlay(context).contains("closes in 10"), "Expected the countdown, the action bar shows: " + overlay(context));
        expect(context.computeOnClient(minecraft -> ArmillaryTremor.isShaking()), "The ground should shake in the last minute");
        expect(context.computeOnClient(minecraft -> Math.abs(minecraft.gameRenderer.mainCamera().yRot() - minecraft.player.getYRot()) > 0.001F
                                                    || Math.abs(minecraft.gameRenderer.mainCamera().xRot() - minecraft.player.getXRot()) > 0.001F),
                "The view should be shaken away from where the player looks");
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot("armillary-collapse-shaking");
        context.getInput().pressKey(InputConstants.KEY_F1);

        context.waitTicks(20 * 10);
        expect(dimension(context, server).equals(Level.OVERWORLD.identifier().toString()), "The collapse should throw the player out");
        double distance = onServer(server, minecraft -> Math.sqrt(player(minecraft).blockPosition().distSqr(CORE)));
        expect(distance <= 8, "The player should land beside the armillary, " + distance + " blocks away");
        expect(onServer(server, minecraft -> ArmillaryConnections.get(minecraft).get(address)) == null, "The connection should be gone");

        context.waitTicks(30);
        expect(onServer(server, minecraft -> armillary(minecraft).getChart().getPhase()) == ArmillaryChart.Phase.SCANNING, "The armillary should go back to scanning");
        expect(onServer(server, minecraft -> armillary(minecraft).getChart().bubble(target).broken()), "The bubble should be left broken");
        expect(!onServer(server, minecraft -> minecraft.overworld().getBlockState(HUB).getValue(ArmillaryHubBlock.OPEN)), "The portal should close");
        context.waitTicks(20);
        context.takeScreenshot("armillary-after-collapse");

        aimAtHub(context, server);
        rightClick(context);
        context.waitTicks(10);
        expect(context.computeOnClient(minecraft -> screen(minecraft).whisper()).equals("The way has closed."), "Someone thrown back should be told, the map says " + context.computeOnClient(minecraft -> screen(minecraft).whisper()));
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(5);
        context.takeScreenshot("armillary-map-reopened-after-collapse");
        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        context.waitTicks(5);
    }

    /**
     * Watching it happen: the path dies back, and the bubble stays as a broken shell that falls apart slowly and can no longer be chosen.
     */
    private static void checkCollapseOnTheMap(ClientGameTestContext context, TestServerContext server)
    {
        int bubble = onServer(server, minecraft ->
        {
            var chart = armillary(minecraft).getChart();
            var found = chart.find(ArmillaryTile.rules(), randomForDifficulty(3));
            chart.target(found.id(), ArmillaryTile.rules(), RandomSource.create());
            chart.advanceSearch(1);
            return found.id();
        });
        aimAtHub(context, server);
        rightClick(context);
        context.waitTicks(30);

        var plate = context.computeOnClient(minecraft -> screen(minecraft).primaryCenter());
        click(context, plate[0], plate[1], 5);
        context.waitTicks(30);
        expect(onServer(server, minecraft -> armillary(minecraft).getChart().getPhase()) == ArmillaryChart.Phase.CONNECTED, "The plate should open the bubble");

        act(server, armillary -> armillary.decayIn(1));
        context.getInput().setCursorPos(2, 2);
        context.waitFor(minecraft -> screen(minecraft).whisper().equals("The way has closed."), 200);
        expect(selected(context) == -1, "The tablet should let go of a bubble that broke");
        context.takeScreenshot("armillary-map-collapse-start");
        context.waitTicks(20);
        context.takeScreenshot("armillary-map-collapse-root");
        context.waitTicks(60);
        context.takeScreenshot("armillary-map-broken");

        var center = context.computeOnClient(minecraft -> screen(minecraft).bubbleCenter(bubble));
        click(context, center[0], center[1], 5);
        expect(selected(context) == -1, "A broken bubble should not be selectable");
        context.getInput().setCursorPos(2, 2);

        ageChart(server, ArmillaryChart.RUIN_TICKS / 2);
        context.waitTicks(25);
        context.takeScreenshot("armillary-map-broken-half");
        ageChart(server, ArmillaryChart.RUIN_TICKS / 4);
        context.waitTicks(25);
        context.takeScreenshot("armillary-map-broken-late");
        ageChart(server, ArmillaryChart.RUIN_TICKS / 4);
        context.waitTicks(25);
        expect(onServer(server, minecraft -> armillary(minecraft).getChart().bubble(bubble)) == null, "A broken bubble should end up gone");
        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        context.waitTicks(5);
    }

    private static void ageChart(TestServerContext server, int ticks)
    {
        server.runOnServer(minecraft ->
        {
            var random = RandomSource.create(1);
            for (int tick = 0; tick < ticks; tick++)
                armillary(minecraft).getChart().tick(ArmillaryTile.rules(), random);
        });
    }

    /**
     * Someone who logged out inside comes back to a dungeon nothing leads to any more.
     */
    private static void checkRejoin(ClientGameTestContext context, TestServerContext server)
    {
        var closed = onServer(server, minecraft ->
        {
            var address = new DungeonAddress(DungeonSize.SMALL, DungeonTheme.PALACE, 1, 900);
            var connections = ArmillaryConnections.get(minecraft);
            connections.open(address, Level.OVERWORLD, CORE, ArmillaryConnections.now(minecraft), 1);
            VoidDungeons.enter(player(minecraft), address);
            return address;
        });
        context.waitTicks(40);
        expect(dimension(context, server).equals(Level.OVERWORLD.identifier().toString()), "The short connection should already have thrown the player out");

        server.runOnServer(minecraft -> VoidDungeons.enter(player(minecraft), closed));
        context.waitTicks(30);
        expect(dimension(context, server).equals(VoidDungeons.WORLD_KEY.identifier().toString()), "A command can still put a player inside a closed dungeon");
        server.runOnServer(minecraft -> ArmillaryConnections.onJoin(player(minecraft)));
        context.waitTicks(30);
        expect(dimension(context, server).equals(Level.OVERWORLD.identifier().toString()), "Joining inside a closed dungeon should send the player back");
    }

    /**
     * The connection outlives a broken armillary, and those inside land where it stood.
     */
    private static void checkBrokenArmillary(ClientGameTestContext context, TestServerContext server)
    {
        var address = onServer(server, minecraft ->
        {
            var opened = new DungeonAddress(DungeonSize.SMALL, DungeonTheme.CATHEDRAL, 1, 901);
            ArmillaryConnections.get(minecraft).open(opened, Level.OVERWORLD, CORE, ArmillaryConnections.now(minecraft), 20 * 8);
            VoidDungeons.enter(player(minecraft), opened);
            return opened;
        });
        context.waitTicks(20);

        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.destroyBlock(CORE, false);
            for (var part : ArmillaryLayout.base(CORE))
            {
                expect(level.getBlockState(part.pos()).is(part.block()), "Breaking the core should give " + part.pos() + " its block back");
                level.setBlockAndUpdate(part.pos(), Blocks.AIR.defaultBlockState());
            }
        });
        expect(onServer(server, minecraft -> !minecraft.overworld().getBlockState(HUB).is(VoidHeartBlocks.ARMILLARY_HUB)), "Breaking the core should take its middle away");
        expect(onServer(server, minecraft -> ArmillaryConnections.get(minecraft).get(address)) != null, "The connection should outlive the armillary");

        context.waitTicks(20 * 9);
        expect(dimension(context, server).equals(Level.OVERWORLD.identifier().toString()), "The collapse should still throw the player out");
        double distance = onServer(server, minecraft -> Math.sqrt(player(minecraft).blockPosition().distSqr(CORE)));
        expect(distance <= 8, "The player should land where the armillary stood, " + distance + " blocks away");

        var drops = onServer(server, minecraft -> Block.getDrops(VoidHeartBlocks.ARMILLARY_CORE.defaultBlockState(), minecraft.overworld(), CORE, null,
                player(minecraft), new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE)));
        expect(drops.stream().anyMatch(stack -> stack.is(VoidHeartBlocks.ARMILLARY_CORE.asItem())), "A core mined with a pickaxe should drop itself, got " + drops);
    }
}
