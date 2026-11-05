package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.client.ArmillaryTremor;
import net.voxelindustry.voidheart.client.screen.ArmillaryScreen;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryChart;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryConnections;
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

/**
 * The whole way into a dungeon: building the instrument, finding and reaching a bubble, paying to open it, crossing both ways, the warnings, and being thrown out.
 */
public class ArmillaryClientTest implements FabricClientGameTest
{
    private static final BlockPos CORE = new BlockPos(0, 120, 0);
    private static final BlockPos HUB  = ArmillaryLayout.hub(CORE);

    private static final int FORMED_TICKS = 30;
    private static final int RISEN_TICKS  = 80;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        TestWorldSave save;
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
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
            server.runCommand("time set noon");
            context.getInput().pressKey(InputConstants.KEY_F1);

            int target = checkChart(context, server);
            checkOpening(context, server);
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
        check(onServer(server, minecraft -> armillary(minecraft).isFormed()), "A rebuilt armillary should form");
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
        check(overlay(context).equals("A force is interfering with your attempt"), "A core used inside a dungeon should be refused, the action bar shows: " + overlay(context));

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
            chart.target(chart.bubbles().getFirst().id(), ArmillaryTile.rules());
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
        check(dimension(context, server).equals(VoidDungeons.WORLD_KEY.identifier().toString()), "The player should come back inside the dungeon");
        check(onServer(server, minecraft -> ArmillaryConnections.get(minecraft).get(DungeonAddress.at(player(minecraft).getBlockX(), player(minecraft).getBlockZ()))) != null,
                "The connection should survive a reload");

        context.waitTicks(20 * 25);
        check(dimension(context, server).equals(Level.OVERWORLD.identifier().toString()), "The connection should still end after a reload");
        context.waitTicks(30);

        check(onServer(server, minecraft -> armillary(minecraft).isFormed()), "The armillary should still be formed after a reload");
        var phase = onServer(server, minecraft -> armillary(minecraft).getChart().getPhase());
        check(phase == ArmillaryChart.Phase.SEARCHING, "The search should survive a reload, the chart is " + phase);
        var ticks = onServer(server, minecraft -> armillary(minecraft).getChart().getSearchTicks());
        check(ticks > 0, "The search should keep its progress, it is at " + ticks + " ticks");
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
            check(found, "Nothing makes " + BuiltInRegistries.BLOCK.getKey(part));
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

    private static ServerPlayer player(MinecraftServer server)
    {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static <T> T onServer(TestServerContext server, Function<MinecraftServer, T> function)
    {
        return server.computeOnServer(function::apply);
    }

    private static void act(TestServerContext server, Consumer<ArmillaryTile> action)
    {
        server.runOnServer(minecraft -> action.accept(armillary(minecraft)));
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }

    private static void aimAt(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z)
    {
        double dx = x - feetX;
        double dy = y - (feetY + 1.62);
        double dz = z - feetZ;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        server.runCommand("execute in " + dimension(context, server) + " run tp @a " + feetX + " " + feetY + " " + feetZ + " " + yaw + " " + pitch);
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

    private static void rightClick(ClientGameTestContext context)
    {
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);
    }

    private static void guiClick(ClientGameTestContext context, float x, float y)
    {
        double scale = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScale());
        context.getInput().setCursorPos(x * scale, y * scale);
        context.waitTicks(2);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(5);
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
        size(context, 640, 480, 2);
        context.takeScreenshot(name + "-320x240");
        size(context, 1920, 1080, 4);
        context.takeScreenshot(name + "-480x270");
        size(context, 1920, 1080, 3);
        context.takeScreenshot(name + "-640x360");
        size(context, 1920, 1080, 2);
        context.takeScreenshot(name + "-960x540");
        size(context, 854, 480, 2);
        context.takeScreenshot(name);
    }

    private static void size(ClientGameTestContext context, int width, int height, int scale)
    {
        context.getInput().resizeWindow(width, height);
        context.runOnClient(minecraft ->
        {
            minecraft.options.guiScale().set(scale);
            minecraft.resizeGui();
        });
        context.waitTicks(4);
    }

    private static String overlay(ClientGameTestContext context)
    {
        return context.computeOnClient(minecraft ->
        {
            try
            {
                var field = Hud.class.getDeclaredField("overlayMessageString");
                field.setAccessible(true);
                var message = (Component) field.get(minecraft.gui.hud);
                return message == null ? "" : message.getString();
            } catch (ReflectiveOperationException e)
            {
                throw new AssertionError(e);
            }
        });
    }

    /**
     * A lone core shows what is missing, rings form only over a whole base, and anything in their way brings them down.
     */
    private static void checkAssembly(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(CORE, VoidHeartBlocks.ARMILLARY_CORE.defaultBlockState()));
        context.waitTicks(FORMED_TICKS);
        check(!onServer(server, minecraft -> armillary(minecraft).isFormed()), "A lone core should not form");

        aimAt(context, server, 2.5, CORE.getY(), 2.5, 0.5, CORE.getY() + 0.9, 0.5);
        rightClick(context);
        check(onServer(server, minecraft -> armillary(minecraft).isPreviewActive()), "Using a lone core should show the base it needs");
        check(overlay(context).contains("chiseled voidstone"), "Using a lone core should say what it needs, the action bar shows: " + overlay(context));
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.waitTicks(5);
        aimAt(context, server, 4.5, CORE.getY() + 1.5, 4.5, 0.5, CORE.getY() + 1, 0.5);
        context.takeScreenshot("armillary-preview");
        context.getInput().pressKey(InputConstants.KEY_F1);

        server.runOnServer(minecraft -> placeBase(minecraft, true));
        context.waitTicks(FORMED_TICKS);
        check(!onServer(server, minecraft -> armillary(minecraft).isFormed()), "A base missing a block should not form");

        server.runOnServer(minecraft -> placeBase(minecraft, false));
        context.waitTicks(FORMED_TICKS);
        check(onServer(server, minecraft -> armillary(minecraft).isFormed()), "A whole base should form");
        check(onServer(server, minecraft -> minecraft.overworld().getBlockState(HUB).is(VoidHeartBlocks.ARMILLARY_HUB)), "A formed armillary should keep its middle clickable");
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot("armillary-rising");
        context.waitTicks(RISEN_TICKS);
        context.getInput().pressKey(InputConstants.KEY_F1);

        server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(CORE.offset(1, 3, 1), Blocks.STONE.defaultBlockState()));
        context.waitTicks(FORMED_TICKS);
        check(!onServer(server, minecraft -> armillary(minecraft).isFormed()), "A block in the way of the rings should stop them");
        check(onServer(server, minecraft -> !minecraft.overworld().getBlockState(HUB).is(VoidHeartBlocks.ARMILLARY_HUB)), "A stopped armillary should take its middle away");

        aimAt(context, server, 2.5, CORE.getY(), 2.5, 0.5, CORE.getY() + 0.9, 0.5);
        rightClick(context);
        check(overlay(context).contains("left free"), "Using a blocked core should say why, the action bar shows: " + overlay(context));

        server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(CORE.offset(1, 3, 1), Blocks.AIR.defaultBlockState()));
        context.waitTicks(FORMED_TICKS + RISEN_TICKS);
        check(onServer(server, minecraft -> armillary(minecraft).isFormed()), "Clearing the way should bring the rings back");

        server.runOnServer(minecraft -> player(minecraft).setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(VoidHeartBlocks.ARMILLARY_CORE)));
        context.waitTicks(5);
        context.takeScreenshot("armillary-core-held");
        server.runOnServer(minecraft -> player(minecraft).setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY));
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
        check(context.computeOnClient(minecraft -> minecraft.gui.screen() instanceof ArmillaryScreen), "Using the middle of the rings should open the map");
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
        check(center != null, "The screen should have been told about the bubble");
        double scale = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScale());
        context.getInput().setCursorPos(center[0] * scale, center[1] * scale);
        context.waitTicks(3);
        context.takeScreenshot("armillary-map-hover");

        guiClick(context, center[0], center[1]);
        check(onServer(server, minecraft -> armillary(minecraft).getChart().getTargetId()) == -1, "A click should only look at a bubble");
        check(selected(context) == deepest.id(), "A click should select the bubble");
        check(tablet(context).contains("5 void pearls"), "The price should show from the first look, the tablet says " + tablet(context));
        check(String.join(" ", tablet(context)).contains("Nothing comes back from here whole."), "The depth should say how dangerous it is, the tablet says " + tablet(context));
        check(String.join(" ", tablet(context)).contains("8m 00s to search"), "The search time should show before committing to it, the tablet says " + tablet(context));
        check(primaryLabel(context).equals("Search for a path"), "A bubble on its own should offer a search, not " + primaryLabel(context));
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(2);
        context.takeScreenshot("armillary-map-tablet");

        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(2);
        check(selected(context) == -1 && context.computeOnClient(minecraft -> minecraft.gui.screen() instanceof ArmillaryScreen), "Escape should let go of the tablet before the screen");
        context.getInput().pressKey(InputConstants.KEY_DOWN);
        context.waitTicks(2);
        check(selected(context) != -1, "The down key should walk the bubbles");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(2);

        guiClick(context, center[0], center[1]);
        var plate = context.computeOnClient(minecraft -> screen(minecraft).primaryCenter());
        guiClick(context, plate[0], plate[1]);
        context.waitTicks(5);
        check(onServer(server, minecraft -> armillary(minecraft).getChart().getTargetId()) == deepest.id(), "The plate should start the search");
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(25);
        check(tablet(context).stream().anyMatch(line -> line.endsWith(" left")), "A running search should say how long it has left, the tablet says " + tablet(context));
        context.takeScreenshot("armillary-map-search-tablet");

        int other = onServer(server, minecraft -> armillary(minecraft).getChart().bubbles().stream().filter(bubble -> bubble.id() != deepest.id()).findFirst().orElseThrow().id());
        var otherCenter = context.computeOnClient(minecraft -> screen(minecraft).bubbleCenter(other));
        guiClick(context, otherCenter[0], otherCenter[1]);
        check(onServer(server, minecraft -> armillary(minecraft).getChart().getTargetId()) == deepest.id(), "Looking at another bubble should not lose the search");
        check(String.join(" ", tablet(context)).contains("The search under way will be lost."), "Another bubble should warn what it would cost, the tablet says " + tablet(context));
        check(primaryLabel(context).equals("Search here instead"), "Another bubble should offer to search there instead, not " + primaryLabel(context));
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
        }

        act(server, armillary -> armillary.advanceSearch(1));
        context.waitTicks(8);
        check(onServer(server, minecraft -> armillary(minecraft).getChart().getPhase()) == ArmillaryChart.Phase.REACHED, "A finished search should reach the bubble");
        context.takeScreenshot("armillary-map-reached-bloom");
        context.waitTicks(60);
        check(selected(context) == deepest.id(), "Reaching a bubble should bring up its tablet");
        int gap = context.computeOnClient(minecraft -> screen(minecraft).plateGap());
        check(gap >= 8, "Abandoning should stand well apart from opening, they are " + gap + " apart");
        context.takeScreenshot("armillary-map-reached");
        return deepest.id();
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
        check(!context.computeOnClient(minecraft -> screen(minecraft).primaryActive()), "Opening should not be on offer without the pearls");
        check(primaryLabel(context).equals("Short by 5 void pearls"), "The plate should say what is missing, not " + primaryLabel(context));
        context.takeScreenshot("armillary-map-no-pearls");

        var button = context.computeOnClient(minecraft -> screen(minecraft).primaryCenter());
        guiClick(context, button[0], button[1]);
        context.waitTicks(5);
        check(onServer(server, minecraft -> armillary(minecraft).getChart().getPhase()) == ArmillaryChart.Phase.REACHED, "Without pearls the bubble should stay shut");

        int cost = onServer(server, minecraft -> armillary(minecraft).getChart().target().openCost());
        server.runOnServer(minecraft -> player(minecraft).getInventory().add(new ItemStack(VoidHeartItems.VOID_PEARL, cost + 2)));
        context.waitTicks(25);
        check(context.computeOnClient(minecraft -> screen(minecraft).primaryActive()), "With the pearls in hand opening should be on offer");
        check(primaryLabel(context).equals("Open: 5 void pearls"), "The plate should give the price, not " + primaryLabel(context));
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(2);
        captureSizes(context, "armillary-map-can-open");

        button = context.computeOnClient(minecraft -> screen(minecraft).primaryCenter());
        guiClick(context, button[0], button[1]);
        context.waitTicks(10);

        check(onServer(server, minecraft -> armillary(minecraft).getChart().getPhase()) == ArmillaryChart.Phase.CONNECTED, "Paying should open the bubble");
        int left = onServer(server, minecraft -> player(minecraft).getInventory().countItem(VoidHeartItems.VOID_PEARL));
        check(left == 2, "Opening should cost exactly " + cost + " pearls, " + left + " are left of " + (cost + 2));
        check(onServer(server, minecraft -> minecraft.overworld().getBlockState(HUB).getValue(ArmillaryHubBlock.OPEN)), "The middle of the rings should show an open portal");
        context.takeScreenshot("armillary-map-connected-bloom");
        context.waitTicks(45);
        check(tablet(context).stream().anyMatch(line -> line.endsWith(" left")), "An open connection should say how long it has left, the tablet says " + tablet(context));
        check(!context.computeOnClient(minecraft -> screen(minecraft).primaryVisible()), "Nothing is left to do on the map once a way stands open");
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
        check(dimension(context, server).equals(VoidDungeons.WORLD_KEY.identifier().toString()), "The portal should lead into the dungeon");
        context.waitTicks(40);

        var rift = onServer(server, ArmillaryClientTest::riftPosition);
        var feet = onServer(server, minecraft -> player(minecraft).blockPosition());
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimAt(context, server, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, rift.getX() + 0.5, rift.getY() + 0.5, rift.getZ() + 0.5);
        context.takeScreenshot("armillary-rift");
        context.getInput().pressKey(InputConstants.KEY_F1);

        rightClick(context);
        context.waitTicks(20);
        check(dimension(context, server).equals(Level.OVERWORLD.identifier().toString()), "The rift should lead back");
        double distance = onServer(server, minecraft -> Math.sqrt(player(minecraft).blockPosition().distSqr(CORE)));
        check(distance <= 8, "The rift should land the player beside the armillary, " + distance + " blocks away");

        aimAtHub(context, server);
        rightClick(context);
        context.waitTicks(40);
        check(dimension(context, server).equals(VoidDungeons.WORLD_KEY.identifier().toString()), "The portal should still lead into the dungeon");
    }

    private static BlockPos riftPosition(MinecraftServer minecraft)
    {
        var player = player(minecraft);
        var address = DungeonAddress.at(player.getBlockX(), player.getBlockZ());
        var plan = VoidDungeons.plan(player.level().getSeed(), address);
        int[] rift = plan.returnRift();
        var pos = address.origin().offset(rift[0], rift[1], rift[2]);
        var block = player.level().getBlockState(pos).getBlock();
        check(block == VoidHeartBlocks.ARMILLARY_RIFT, "The plan's rift should be placed in the world, found " + BuiltInRegistries.BLOCK.getKey(block));
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
        check(overlay(context).contains("collapsing"), "Expected the last minute's warning, the action bar shows: " + overlay(context));

        server.runOnServer(minecraft -> ArmillaryConnections.get(minecraft).decayIn(address, ArmillaryConnections.now(minecraft) + 20 * 10 + 8));
        context.waitTicks(20);
        check(overlay(context).contains("closes in 10"), "Expected the countdown, the action bar shows: " + overlay(context));
        check(context.computeOnClient(minecraft -> ArmillaryTremor.isShaking()), "The ground should shake in the last minute");
        check(context.computeOnClient(minecraft -> Math.abs(minecraft.gameRenderer.mainCamera().yRot() - minecraft.player.getYRot()) > 0.001F
                                                    || Math.abs(minecraft.gameRenderer.mainCamera().xRot() - minecraft.player.getXRot()) > 0.001F),
                "The view should be shaken away from where the player looks");
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot("armillary-collapse-shaking");
        context.getInput().pressKey(InputConstants.KEY_F1);

        context.waitTicks(20 * 10);
        check(dimension(context, server).equals(Level.OVERWORLD.identifier().toString()), "The collapse should throw the player out");
        double distance = onServer(server, minecraft -> Math.sqrt(player(minecraft).blockPosition().distSqr(CORE)));
        check(distance <= 8, "The player should land beside the armillary, " + distance + " blocks away");
        check(onServer(server, minecraft -> ArmillaryConnections.get(minecraft).get(address)) == null, "The connection should be gone");

        context.waitTicks(30);
        check(onServer(server, minecraft -> armillary(minecraft).getChart().getPhase()) == ArmillaryChart.Phase.SCANNING, "The armillary should go back to scanning");
        check(onServer(server, minecraft -> armillary(minecraft).getChart().bubble(target).broken()), "The bubble should be left broken");
        check(!onServer(server, minecraft -> minecraft.overworld().getBlockState(HUB).getValue(ArmillaryHubBlock.OPEN)), "The portal should close");
        context.waitTicks(20);
        context.takeScreenshot("armillary-after-collapse");

        aimAtHub(context, server);
        rightClick(context);
        context.waitTicks(10);
        check(context.computeOnClient(minecraft -> screen(minecraft).whisper()).equals("The way has closed."), "Someone thrown back should be told, the map says " + context.computeOnClient(minecraft -> screen(minecraft).whisper()));
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
            chart.target(found.id(), ArmillaryTile.rules());
            chart.advanceSearch(1);
            return found.id();
        });
        aimAtHub(context, server);
        rightClick(context);
        context.waitTicks(30);

        var plate = context.computeOnClient(minecraft -> screen(minecraft).primaryCenter());
        guiClick(context, plate[0], plate[1]);
        context.waitTicks(30);
        check(onServer(server, minecraft -> armillary(minecraft).getChart().getPhase()) == ArmillaryChart.Phase.CONNECTED, "The plate should open the bubble");

        act(server, armillary -> armillary.decayIn(1));
        context.getInput().setCursorPos(2, 2);
        context.waitFor(minecraft -> screen(minecraft).whisper().equals("The way has closed."), 200);
        check(selected(context) == -1, "The tablet should let go of a bubble that broke");
        context.takeScreenshot("armillary-map-collapse-start");
        context.waitTicks(20);
        context.takeScreenshot("armillary-map-collapse-root");
        context.waitTicks(60);
        context.takeScreenshot("armillary-map-broken");

        var center = context.computeOnClient(minecraft -> screen(minecraft).bubbleCenter(bubble));
        guiClick(context, center[0], center[1]);
        check(selected(context) == -1, "A broken bubble should not be selectable");
        context.getInput().setCursorPos(2, 2);

        ageChart(server, ArmillaryChart.RUIN_TICKS / 2);
        context.waitTicks(25);
        context.takeScreenshot("armillary-map-broken-half");
        ageChart(server, ArmillaryChart.RUIN_TICKS / 4);
        context.waitTicks(25);
        context.takeScreenshot("armillary-map-broken-late");
        ageChart(server, ArmillaryChart.RUIN_TICKS / 4);
        context.waitTicks(25);
        check(onServer(server, minecraft -> armillary(minecraft).getChart().bubble(bubble)) == null, "A broken bubble should end up gone");
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
        check(dimension(context, server).equals(Level.OVERWORLD.identifier().toString()), "The short connection should already have thrown the player out");

        server.runOnServer(minecraft -> VoidDungeons.enter(player(minecraft), closed));
        context.waitTicks(30);
        check(dimension(context, server).equals(VoidDungeons.WORLD_KEY.identifier().toString()), "A command can still put a player inside a closed dungeon");
        server.runOnServer(minecraft -> ArmillaryConnections.onJoin(player(minecraft)));
        context.waitTicks(30);
        check(dimension(context, server).equals(Level.OVERWORLD.identifier().toString()), "Joining inside a closed dungeon should send the player back");
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
                level.setBlockAndUpdate(part.pos(), Blocks.AIR.defaultBlockState());
        });
        check(onServer(server, minecraft -> !minecraft.overworld().getBlockState(HUB).is(VoidHeartBlocks.ARMILLARY_HUB)), "Breaking the core should take its middle away");
        check(onServer(server, minecraft -> ArmillaryConnections.get(minecraft).get(address)) != null, "The connection should outlive the armillary");

        context.waitTicks(20 * 9);
        check(dimension(context, server).equals(Level.OVERWORLD.identifier().toString()), "The collapse should still throw the player out");
        double distance = onServer(server, minecraft -> Math.sqrt(player(minecraft).blockPosition().distSqr(CORE)));
        check(distance <= 8, "The player should land where the armillary stood, " + distance + " blocks away");

        var drops = onServer(server, minecraft -> Block.getDrops(VoidHeartBlocks.ARMILLARY_CORE.defaultBlockState(), minecraft.overworld(), CORE, null,
                player(minecraft), new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE)));
        check(drops.stream().anyMatch(stack -> stack.is(VoidHeartBlocks.ARMILLARY_CORE.asItem())), "A core mined with a pickaxe should drop itself, got " + drops);
    }
}
