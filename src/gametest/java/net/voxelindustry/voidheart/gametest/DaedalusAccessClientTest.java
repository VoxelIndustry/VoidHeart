package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.client.screen.ArmillaryScreen;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryChart;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryChunkHold;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryHubBlock;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryLayout;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryPayloads;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryRiftTile;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryTile;
import net.voxelindustry.voidheart.common.content.armillary.DaedalusConnections;
import net.voxelindustry.voidheart.common.energy.creative.CreativeEnergyContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;

import java.util.List;
import java.util.function.Function;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The way down to the Daedalus: a shadow until studied, a search and a price like any bubble, a portal onto a bridge that holds through a restart, the rift back, and severing it.
 */
public class DaedalusAccessClientTest implements FabricClientGameTest
{
    private static final BlockPos CORE = new BlockPos(0, 120, 0);
    private static final BlockPos HUB  = ArmillaryLayout.hub(CORE);

    private static final String DAEDALUS = VoidDaedalus.WORLD_KEY.identifier().toString();
    private static final String OVERWORLD = Level.OVERWORLD.identifier().toString();

    private static final int FORMED_TICKS   = 30;
    private static final int VIEW_TICKS     = 25;
    private static final int CROSSING_TICKS = 60;
    private static final int REVEAL_SHOT_TICKS = 14;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        TestWorldSave save;
        BlockPos arrival;
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule spawn_monsters false");
            server.runCommand("gamemode creative @a");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            server.runOnServer(DaedalusAccessClientTest::build);
            context.waitTicks(FORMED_TICKS);
            expect(onServer(server, minecraft -> armillary(minecraft).isFormed()), "The armillary should form");

            checkShadow(context, server);
            checkStudied(context, server);
            checkOpening(context, server);
            arrival = checkCrossing(context, server);
        }

        try (var world = save.open())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender(false, 1200);
            checkAfterReload(context, server, arrival);
            checkSevering(context, server);
            checkBreakingSevers(context, server);
        }
    }

    private static void build(MinecraftServer server)
    {
        var level = server.overworld();
        for (var pos : BlockPos.betweenClosed(CORE.offset(-12, -1, -12), CORE.offset(12, -1, 12)))
            level.setBlockAndUpdate(pos, Blocks.DEEPSLATE_TILES.defaultBlockState());
        level.setBlockAndUpdate(CORE, VoidHeartBlocks.ARMILLARY_CORE.defaultBlockState());
        for (var part : ArmillaryLayout.base(CORE))
            level.setBlockAndUpdate(part.pos(), part.block().defaultBlockState());
        // Out of sight under the floor, it powers the search, the opening and the hold
        level.setBlockAndUpdate(CORE.below(8), CreativeEnergyContent.CREATIVE_VOID_WELL.defaultBlockState());
    }

    /**
     * The way gathers its void energy once the root reaches the strata, at most ARMILLARY_INTAKE a tick even from the creative well.
     */
    private static void waitForCharge(ClientGameTestContext context, TestServerContext server)
    {
        for (int wait = 0; wait < 80 && !onServer(server, minecraft -> armillary(minecraft).getCharge() >= armillary(minecraft).openingCost()); wait++)
            context.waitTicks(20);
        expect(onServer(server, minecraft -> armillary(minecraft).getCharge() >= armillary(minecraft).openingCost()), "The way should gather its void energy from the well");
    }

    /**
     * Before it is studied the strata is only a shadow: a look says something is there and what would make it out, and nothing reaches for it, even asked directly.
     */
    private static void checkShadow(ClientGameTestContext context, TestServerContext server)
    {
        openChart(context, server);
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(VIEW_TICKS);
        context.takeScreenshot("daedalus-map-shadow");

        clickStrata(context);
        var text = String.join(" ", tablet(context));
        expect(text.contains("A shadow below"), "An unstudied strata should only show as a shadow, the tablet says " + text);
        expect(text.contains("The Floor of the Void would bring it into focus."), "The tablet should say what to study, it says " + text);
        expect(!context.computeOnClient(minecraft -> screen(minecraft).primaryVisible()), "Nothing should be on offer before it is studied");
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(2);
        context.takeScreenshot("daedalus-map-shadow-tablet");

        context.runOnClient(minecraft -> ClientPlayNetworking.send(new ArmillaryPayloads.Action(ArmillaryPayloads.ActionKind.TARGET, ArmillaryChart.STRATA)));
        context.waitTicks(10);
        expect(!onServer(server, minecraft -> armillary(minecraft).getChart().targetsStrata()), "The server should refuse a search for the strata from someone who has not studied it");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(2);
    }

    /**
     * Studied, it comes into focus, and a search reaches for it by a real click like for any bubble. The district it comes down on is named once the root gets there.
     */
    private static void checkStudied(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("execute as @a at @a run voidheart research grant voidheart:daedalus");
        for (int tick = 0; tick < VIEW_TICKS * 2 && !context.computeOnClient(minecraft -> screen(minecraft).isRevealing()); tick++)
            context.waitTicks(1);
        expect(context.computeOnClient(minecraft -> screen(minecraft).isRevealing()), "Studying it should bring the strata into focus on the open chart");
        context.waitTicks(REVEAL_SHOT_TICKS);
        context.takeScreenshot("daedalus-map-reveal");
        context.waitTicks(VIEW_TICKS * 2);
        context.takeScreenshot("daedalus-map-known");

        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        context.waitTicks(5);
        openChart(context, server);
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(VIEW_TICKS);
        expect(!context.computeOnClient(minecraft -> screen(minecraft).isRevealing()), "The strata should only come into focus once");

        clickStrata(context);
        var text = String.join(" ", tablet(context));
        expect(text.contains("The Daedalus"), "A studied strata should be named, the tablet says " + text);
        expect(text.contains("10m 00s to search"), "The search time should show before committing to it, the tablet says " + text);
        expect(text.contains("8 void pearls"), "The price should show from the first look, the tablet says " + text);
        expect(primaryLabel(context).equals("Search for a path"), "The strata should offer a search, not " + primaryLabel(context));
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(2);
        context.takeScreenshot("daedalus-map-known-tablet");

        var plate = context.computeOnClient(minecraft -> screen(minecraft).primaryCenter());
        click(context, plate[0], plate[1], 5);
        context.waitTicks(5);
        expect(onServer(server, minecraft -> armillary(minecraft).getChart().targetsStrata()), "The plate should start a search for the strata");
        context.getInput().setCursorPos(2, 2);

        for (int percent : new int[] { 25, 60, 90 })
        {
            act(server, armillary -> armillary.advanceSearch(percent / 100F));
            context.waitTicks(VIEW_TICKS);
            context.takeScreenshot("daedalus-map-search-" + percent);
        }

        act(server, armillary -> armillary.advanceSearch(1));
        context.waitTicks(8);
        expect(onServer(server, minecraft -> armillary(minecraft).getChart().getPhase()) == ArmillaryChart.Phase.REACHED, "A finished search should reach the strata");
        context.takeScreenshot("daedalus-map-reached-bloom");
        context.waitTicks(60);
        expect(context.computeOnClient(minecraft -> screen(minecraft).selected()) == ArmillaryChart.STRATA, "Reaching the strata should bring up its tablet");
        text = String.join(" ", tablet(context));
        expect(text.contains("Opens on "), "The district the root came down on should be named, the tablet says " + text);
        context.takeScreenshot("daedalus-map-reached");
    }

    /**
     * Paid in void pearls by a survival player, and then it holds: no time left, only a way to sever it.
     */
    private static void checkOpening(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode survival @a");
        server.runOnServer(minecraft ->
        {
            player(minecraft).getAbilities().invulnerable = true;
            player(minecraft).getInventory().add(new ItemStack(VoidHeartItems.VOID_PEARL, ArmillaryTile.daedalusCost() + 2));
        });
        waitForCharge(context, server);
        context.waitTicks(VIEW_TICKS);
        expect(primaryLabel(context).equals("Open: 8 void pearls"), "The plate should give the price, not " + primaryLabel(context));

        var plate = context.computeOnClient(minecraft -> screen(minecraft).primaryCenter());
        click(context, plate[0], plate[1], 5);
        context.waitTicks(10);
        expect(onServer(server, minecraft -> armillary(minecraft).getChart().getPhase()) == ArmillaryChart.Phase.CONNECTED, "Paying should open the way");
        int left = onServer(server, minecraft -> player(minecraft).getInventory().countItem(VoidHeartItems.VOID_PEARL));
        expect(left == 2, "Opening should cost exactly 8 pearls, " + left + " are left of 10");
        expect(onServer(server, minecraft -> minecraft.overworld().getBlockState(HUB).getValue(ArmillaryHubBlock.OPEN)), "The middle of the rings should show an open portal");
        context.takeScreenshot("daedalus-map-connected-bloom");

        context.waitTicks(45);
        var text = String.join(" ", tablet(context));
        expect(text.contains("Holds until it is severed"), "The way into the Daedalus should hold, the tablet says " + text);
        expect(context.computeOnClient(minecraft -> screen(minecraft).quietLabel()).equals("Sever the way"), "The tablet should offer to sever the way");
        expect(context.computeOnClient(minecraft -> screen(minecraft).whisper()).equals("The way into the Daedalus stands open."), "The map should say the way stands open");
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(2);
        captureSizes(context, "daedalus-map-connected");

        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        server.runCommand("gamemode creative @a");
        context.waitTicks(60);
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimAt(context, server, 3.5, CORE.getY(), 2.5, 0.5, HUB.getY() + 0.5, 0.5);
        context.takeScreenshot("daedalus-portal");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    /**
     * Across onto a bridge, back through the rift standing behind, and across once more onto the same bridge.
     *
     * @return where the player lands
     */
    private static BlockPos checkCrossing(ClientGameTestContext context, TestServerContext server)
    {
        enterThroughHub(context, server);
        expect(dimension(server).equals(DAEDALUS), "The portal should lead into the Daedalus, the player is in " + dimension(server));
        var feet = onServer(server, minecraft -> player(minecraft).blockPosition());
        expect(onServer(server, minecraft -> !player(minecraft).level().getBlockState(feet.below()).isAir()), "The player should stand on a bridge");
        int id = onServer(server, minecraft -> armillary(minecraft).getDaedalusConnection());
        expect(onServer(server, minecraft -> DaedalusConnections.get(minecraft).entryOf(player(minecraft).getUUID())) == id, "The player should be known to have come in by this way");
        expect(onServer(server, DaedalusAccessClientTest::holdsChunks), "An armillary holding the Daedalus should keep its chunks loaded");

        var rift = onServer(server, DaedalusAccessClientTest::findRift);
        expect(rift != null, "A rift should stand on the bridge near where the player arrives");
        expect(onServer(server, minecraft -> ((ArmillaryRiftTile) VoidDaedalus.level(minecraft).getBlockEntity(rift)).getConnection()) == id, "The rift should belong to the way it was opened by");

        context.waitTicks(40);
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot("daedalus-arrival");
        aimAt(context, server, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, rift.getX() + 0.5, rift.getY() + 0.5, rift.getZ() + 0.5);
        context.takeScreenshot("daedalus-rift");
        context.getInput().pressKey(InputConstants.KEY_F1);

        rightClick(context);
        context.waitTicks(20);
        expect(dimension(server).equals(OVERWORLD), "The rift should lead back");
        double distance = onServer(server, minecraft -> Math.sqrt(player(minecraft).blockPosition().distSqr(CORE)));
        expect(distance <= 8, "The rift should land the player beside the armillary, " + distance + " blocks away");

        enterThroughHub(context, server);
        var again = onServer(server, minecraft -> player(minecraft).blockPosition());
        expect(dimension(server).equals(DAEDALUS) && again.equals(feet), "The portal should keep leading onto the same bridge, " + again + " rather than " + feet);
        return feet;
    }

    /**
     * Closing the world changes nothing: the player is still in the Daedalus, the way still holds, and the rift still leads back.
     */
    private static void checkAfterReload(ClientGameTestContext context, TestServerContext server, BlockPos arrival)
    {
        expect(dimension(server).equals(DAEDALUS), "The player should come back inside the Daedalus, not in " + dimension(server));
        int id = onServer(server, minecraft -> armillary(minecraft).getDaedalusConnection());
        expect(id != ArmillaryRiftTile.NONE && onServer(server, minecraft -> DaedalusConnections.get(minecraft).get(id)) != null, "The way should survive a reload");
        expect(onServer(server, minecraft -> armillary(minecraft).getChart().getPhase()) == ArmillaryChart.Phase.CONNECTED, "The armillary should still hold the way");
        checkChunksHeld(context, server);

        var rift = onServer(server, DaedalusAccessClientTest::findRift);
        expect(rift != null, "The rift should still stand");
        server.runCommand("execute in " + DAEDALUS + " run tp @a " + (arrival.getX() + 0.5) + " " + arrival.getY() + " " + (arrival.getZ() + 0.5));
        context.waitTicks(20);
        aimAt(context, server, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, rift.getX() + 0.5, rift.getY() + 0.5, rift.getZ() + 0.5);
        rightClick(context);
        context.waitTicks(20);
        expect(dimension(server).equals(OVERWORLD), "The rift should still lead back after a reload");
    }

    /**
     * Severed by a real click, the way gives its last two minutes' warnings to whoever is inside, throws them out, and takes its rift with it.
     */
    private static void checkSevering(ClientGameTestContext context, TestServerContext server)
    {
        openChart(context, server);
        context.waitTicks(VIEW_TICKS);
        click(context, strataDoor(context)[0], strataDoor(context)[1], 5);
        expect(context.computeOnClient(minecraft -> screen(minecraft).quietLabel()).equals("Sever the way"), "The strata's tablet should offer to sever the way");
        var quiet = context.computeOnClient(minecraft -> screen(minecraft).quietCenter());
        click(context, quiet[0], quiet[1], 5);
        context.waitTicks(VIEW_TICKS);

        int id = onServer(server, minecraft -> armillary(minecraft).getDaedalusConnection());
        expect(onServer(server, minecraft -> DaedalusConnections.get(minecraft).get(id).isSevered()), "The quiet plate should sever the way");
        expect(tablet(context).stream().anyMatch(line -> line.endsWith(" left")), "A severed way should say how long it has left, the tablet says " + tablet(context));
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(2);
        context.takeScreenshot("daedalus-map-severed");
        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        context.waitTicks(5);

        enterThroughHub(context, server);
        expect(dimension(server).equals(DAEDALUS), "A severed way should still let players through until it closes");
        var rift = onServer(server, DaedalusAccessClientTest::findRift);
        server.runCommand("execute in " + DAEDALUS + " run forceload add " + rift.getX() + " " + rift.getZ());

        server.runCommand("execute as @a at @a run voidheart armillary decay 65");
        context.waitTicks(5 * 20 + 15);
        expect(overlay(context).contains("collapsing"), "Expected the last minute's warning, the action bar shows: " + overlay(context));

        server.runCommand("execute as @a at @a run voidheart armillary decay 3");
        context.waitTicks(3 * 20 + 20);
        expect(dimension(server).equals(OVERWORLD), "The end of the way should throw the player out, they are in " + dimension(server));
        double distance = onServer(server, minecraft -> Math.sqrt(player(minecraft).blockPosition().distSqr(CORE)));
        expect(distance <= 8, "The player should land beside the armillary, " + distance + " blocks away");
        expect(onServer(server, minecraft -> DaedalusConnections.get(minecraft).get(id)) == null, "The way should be gone");

        context.waitTicks(40);
        expect(!onServer(server, DaedalusAccessClientTest::holdsChunks), "The armillary should let its chunks go once the way is gone");
        expect(onServer(server, minecraft -> armillary(minecraft).getChart().getPhase()) == ArmillaryChart.Phase.SCANNING, "The armillary should go back to scanning");
        expect(!onServer(server, minecraft -> minecraft.overworld().getBlockState(HUB).getValue(ArmillaryHubBlock.OPEN)), "The portal should close");
        expect(onServer(server, minecraft -> VoidDaedalus.level(minecraft).getBlockState(rift).isAir()), "The rift should go with its way");
        server.runCommand("execute in " + DAEDALUS + " run forceload remove " + rift.getX() + " " + rift.getZ());

        openChart(context, server);
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(VIEW_TICKS);
        context.takeScreenshot("daedalus-map-after-close");
        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        context.waitTicks(5);
    }

    /**
     * The Daedalus is held by the armillary itself: breaking its core severs the way.
     */
    private static void checkBreakingSevers(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var chart = armillary(minecraft).getChart();
            chart.target(ArmillaryChart.STRATA, ArmillaryTile.rules(), player(minecraft).getRandom());
        });
        act(server, armillary -> armillary.advanceSearch(1));
        context.waitTicks(5);
        waitForCharge(context, server);
        openChart(context, server);
        context.waitTicks(VIEW_TICKS);
        click(context, strataDoor(context)[0], strataDoor(context)[1], 5);
        var plate = context.computeOnClient(minecraft -> screen(minecraft).primaryCenter());
        click(context, plate[0], plate[1], 5);
        context.waitTicks(10);
        int id = onServer(server, minecraft -> armillary(minecraft).getDaedalusConnection());
        expect(id != ArmillaryRiftTile.NONE, "A creative player should open the way for free");
        context.runOnClient(minecraft -> minecraft.player.closeContainer());

        server.runOnServer(minecraft -> minecraft.overworld().destroyBlock(CORE, true));
        context.waitTicks(5);
        expect(onServer(server, minecraft -> DaedalusConnections.get(minecraft).get(id).isSevered()), "Breaking the core should sever the way");
    }

    /**
     * With nobody left in the overworld, the base around the armillary keeps ticking ArmillaryChunkHold.TICKING_RADIUS chunks each way, and no further,
     * and the hold stays paid.
     */
    private static void checkChunksHeld(ClientGameTestContext context, TestServerContext server)
    {
        expect(onServer(server, minecraft -> minecraft.overworld().players().isEmpty()), "Nobody should be left in the overworld");
        expect(onServer(server, DaedalusAccessClientTest::holdsChunks), "The chunk hold should survive a reload");
        context.waitTicks(40);
        int reach = ArmillaryChunkHold.TICKING_RADIUS * 16;
        expect(onServer(server, minecraft -> ticks(minecraft, CORE) && ticks(minecraft, CORE.offset(reach, 0, -reach))), "The armillary and the base around it should keep ticking");
        expect(!onServer(server, minecraft -> ticks(minecraft, CORE.offset(reach + 32, 0, 0))), "The hold should not keep chunks ticking beyond its radius");
        expect(onServer(server, minecraft -> armillary(minecraft).secondsBeforeSevered()) == -1, "The hold should stay paid with nobody in the overworld");
    }

    private static boolean holdsChunks(MinecraftServer minecraft)
    {
        var tickets = minecraft.overworld().getDataStorage().computeIfAbsent(TicketStorage.TYPE).getTickets(ChunkPos.containing(CORE).pack());
        return tickets.stream().anyMatch(ticket -> ticket.getType() == ArmillaryChunkHold.TICKET);
    }

    private static boolean ticks(MinecraftServer minecraft, BlockPos pos)
    {
        return minecraft.overworld().shouldTickBlocksAt(ChunkPos.containing(pos).pack());
    }

    private static BlockPos findRift(MinecraftServer minecraft)
    {
        var player = player(minecraft);
        var level = VoidDaedalus.level(minecraft);
        for (var pos : BlockPos.betweenClosed(player.blockPosition().offset(-6, -3, -6), player.blockPosition().offset(6, 4, 6)))
        {
            if (level.getBlockState(pos).is(VoidHeartBlocks.ARMILLARY_RIFT))
                return pos.immutable();
        }
        return null;
    }

    private static void enterThroughHub(ClientGameTestContext context, TestServerContext server)
    {
        aimAt(context, server, 3.5, CORE.getY(), 0.5, 0.5, HUB.getY() + 0.5, 0.5);
        rightClick(context);
        context.waitTicks(CROSSING_TICKS);
    }

    private static void openChart(ClientGameTestContext context, TestServerContext server)
    {
        aimAt(context, server, 3.5, CORE.getY(), 0.5, 0.5, CORE.getY() + 0.9, 0.5);
        rightClick(context);
        context.waitTicks(5);
        expect(context.computeOnClient(minecraft -> minecraft.gui.screen() instanceof ArmillaryScreen), "Using the core should open the map");
    }

    private static void clickStrata(ClientGameTestContext context)
    {
        var spot = context.computeOnClient(minecraft -> screen(minecraft).strataSpot());
        click(context, spot[0], spot[1], 5);
        expect(context.computeOnClient(minecraft -> screen(minecraft).selected()) == ArmillaryChart.STRATA, "A click on the strata should select it");
    }

    private static float[] strataDoor(ClientGameTestContext context)
    {
        return context.computeOnClient(minecraft -> screen(minecraft).strataDoor());
    }

    private static ArmillaryTile armillary(MinecraftServer server)
    {
        return (ArmillaryTile) server.overworld().getBlockEntity(CORE);
    }

    private static void act(TestServerContext server, java.util.function.Consumer<ArmillaryTile> action)
    {
        server.runOnServer(minecraft -> action.accept(armillary(minecraft)));
    }

    private static <T> T onServer(TestServerContext server, Function<MinecraftServer, T> function)
    {
        return server.computeOnServer(function::apply);
    }

    private static String dimension(TestServerContext server)
    {
        return onServer(server, minecraft -> player(minecraft).level().dimension().identifier().toString());
    }

    private static void aimAt(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z)
    {
        server.runCommand("execute in " + dimension(server) + " run tp @a " + aimedPose(feetX, feetY, feetZ, x, y, z));
        context.waitTicks(10);
    }

    private static ArmillaryScreen screen(Minecraft minecraft)
    {
        return (ArmillaryScreen) minecraft.gui.screen();
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
        resize(context, 1920, 1080, 2);
        context.takeScreenshot(name + "-960x540");
        resize(context, 854, 480, 2);
        context.takeScreenshot(name);
    }
}
