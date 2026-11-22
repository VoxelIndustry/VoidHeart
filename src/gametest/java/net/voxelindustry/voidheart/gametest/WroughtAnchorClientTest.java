package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.voxelindustry.voidheart.gametest.GameTestKit.ClientSettings;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.client.screen.TravelMapScreen;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.ley.anchor.OldAnchors;
import net.voxelindustry.voidheart.common.ley.anchor.WroughtAnchor;
import net.voxelindustry.voidheart.common.ley.anchor.WroughtAnchorBalance;
import net.voxelindustry.voidheart.common.ley.anchor.WroughtAnchorState;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.Optional;

import static net.voxelindustry.voidheart.gametest.GameTestKit.FLAT_FEET;
import static net.voxelindustry.voidheart.gametest.GameTestKit.FLAT_GROUND;
import static net.voxelindustry.voidheart.gametest.GameTestKit.aimFrom;
import static net.voxelindustry.voidheart.gametest.GameTestKit.click;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expectClient;
import static net.voxelindustry.voidheart.gametest.GameTestKit.freezeTime;
import static net.voxelindustry.voidheart.gametest.GameTestKit.overlay;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;
import static net.voxelindustry.voidheart.gametest.GameTestKit.restoreClient;
import static net.voxelindustry.voidheart.gametest.GameTestKit.rightClick;
import static net.voxelindustry.voidheart.gametest.GameTestKit.useOn;
import static net.voxelindustry.voidheart.gametest.GameTestKit.waitFor;

/**
 * Wrought anchors on the flat world's lattice: refused away from a line, built on one with a real click, renamed and travelled from on the
 * travel map, paid for in void energy, refused without it, and moved keeping their name. Run it alone with -PgameTest=WroughtAnchor.
 */
public class WroughtAnchorClientTest implements FabricClientGameTest
{
    private static final int MARGIN = 28;
    private static final int LIST_WIDTH = 150;
    private static final int LIST_HEADER = 16;

    // Waits for the chunks around the player to draw, after a teleport far from where they were
    private static Runnable waitForChunks;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        var saved = context.computeOnClient(minecraft -> new ClientSettings(minecraft.options.getCameraType(), minecraft.gui.hud.isHidden()));
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            try
            {
                runScene(context, world, server);
            }
            finally
            {
                restoreClient(context, server, saved);
            }
        }
    }

    private static void runScene(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server)
    {
        waitForChunks = () -> world.getConnection().waitForChunksRender();
        world.getConnection().waitForChunksRender();
        freezeTime(server, "noon");
        server.runCommand("gamemode creative @a");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

        var spots = server.computeOnServer(WroughtAnchorClientTest::findSpots);
        expect(spots != null, "Expected two lines far apart and a spot away from every line near the origin");
        for (var spot : new BlockPos[] { spots.home(), spots.far(), spots.moved(), spots.off() })
            server.runCommand("forceload add " + (spot.getX() - 16) + " " + (spot.getZ() - 16) + " " + (spot.getX() + 16) + " " + (spot.getZ() + 16));

        server.runOnServer(minecraft -> player(minecraft).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(VoidHeartBlocks.WROUGHT_ANCHOR, 4)));

        refusedAwayFromLine(context, server, spots.off());
        long home = builtByClick(context, server, spots.home());
        long far = builtNamed(context, server, spots.far(), "Far Haven");
        looks(context, server, spots.home());

        // An endless creative well beside home, none at the far anchor
        server.runCommand("setblock " + (spots.home().getX() + 3) + " " + FLAT_FEET + " " + spots.home().getZ() + " voidheart:creative_void_well");

        renameOnMap(context, server, spots.home(), home);
        travelOnMap(context, server, spots.home(), spots.far(), far);
        refusedWithoutVoid(context, server, spots.far(), home);
        movedKeepsName(context, server, spots.far(), spots.moved(), far);
    }

    private record Spots(BlockPos home, BlockPos far, BlockPos moved, BlockPos off)
    {
    }

    /**
     * Two spots on lines at least 200 blocks apart, a third on the far one's line, and a spot well away from every line.
     */
    private static Spots findSpots(MinecraftServer minecraft)
    {
        var level = minecraft.overworld();
        var lattice = LeyLattice.get(level).orElseThrow();
        var network = OldAnchors.get(level).orElseThrow().network();

        BlockPos home = null, far = null, moved = null, off = null;
        for (var region : lattice.regionsAround(0, 0, 600, true))
        {
            for (var edge : region.edges())
            {
                if (edge.length() < 60)
                    continue;
                var at = spotAlong(edge, 0.4);
                if (home == null && Math.hypot(at.getX(), at.getZ()) < 300)
                    home = at;
                else if (home != null && far == null && Math.hypot(at.getX() - home.getX(), at.getZ() - home.getZ()) > 200)
                {
                    far = at;
                    moved = spotAlong(edge, 0.6);
                }
            }
        }
        for (int x = -200; x <= 200 && off == null && home != null; x += 3)
        {
            for (int z = -200; z <= 200 && off == null; z += 3)
            {
                if (!network.nearLine(x, z, WroughtAnchorBalance.LINE_REACH + 6))
                    off = new BlockPos(x, FLAT_FEET, z);
            }
        }
        return home == null || far == null || off == null ? null : new Spots(home, far, moved, off);
    }

    private static BlockPos spotAlong(net.voxelindustry.voidheart.common.ley.LeyEdge edge, double t)
    {
        return new BlockPos((int) Math.round(edge.fromX() + (edge.toX() - edge.fromX()) * t), FLAT_FEET,
                (int) Math.round(edge.fromZ() + (edge.toZ() - edge.fromZ()) * t));
    }

    private static Optional<WroughtAnchor> anchorAt(MinecraftServer minecraft, BlockPos pos)
    {
        return WroughtAnchorState.get(minecraft.overworld()).all().stream().filter(anchor -> anchor.pos().equals(pos)).findFirst();
    }

    /**
     * Aims at the grass under pos from a little way off, so a right click places on top of it.
     */
    private static void aimAtGround(ClientGameTestContext context, TestServerContext server, BlockPos pos)
    {
        aimFrom(context, server, pos.getX() + 3.5, FLAT_FEET + 1, pos.getZ() + 0.5, pos.getX() + 0.5, FLAT_GROUND + 0.98, pos.getZ() + 0.5, 6);
        waitForChunks.run();
    }

    private static void refusedAwayFromLine(ClientGameTestContext context, TestServerContext server, BlockPos off)
    {
        aimAtGround(context, server, off);
        rightClick(context);
        context.waitTicks(4);
        boolean placed = server.computeOnServer(minecraft -> minecraft.overworld().getBlockState(off).is(VoidHeartBlocks.WROUGHT_ANCHOR));
        expect(!placed, "A wrought anchor should not hold away from every line");
        expect(overlay(context).equals(Component.translatable("voidheart.wrought_anchor.far_from_line").getString()),
                "Expected the far-from-line message, got '" + overlay(context) + "'");
        expectClient(context, "The refused anchor should not linger on the client", minecraft -> !minecraft.level.getBlockState(off).is(VoidHeartBlocks.WROUGHT_ANCHOR));
    }

    private static long builtByClick(ClientGameTestContext context, TestServerContext server, BlockPos home)
    {
        aimAtGround(context, server, home);
        rightClick(context);
        waitFor(context, server, 40, "the home anchor to be recorded", minecraft -> anchorAt(minecraft, home).isPresent());
        var anchor = server.computeOnServer(minecraft -> anchorAt(minecraft, home).orElseThrow());
        expect(anchor.id() < 0 && !anchor.name().isBlank(), "Expected a negative id and a default name, got " + anchor);
        return anchor.id();
    }

    private static long builtNamed(ClientGameTestContext context, TestServerContext server, BlockPos far, String name)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var stack = new ItemStack(VoidHeartBlocks.WROUGHT_ANCHOR);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
            var held = player.getMainHandItem().copy();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            player.teleportTo(far.getX() + 2.5, FLAT_FEET, far.getZ() + 0.5);
            useOn(player, far.below());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
        });
        waitFor(context, server, 40, "the far anchor to be recorded", minecraft -> anchorAt(minecraft, far).isPresent());
        var anchor = server.computeOnServer(minecraft -> anchorAt(minecraft, far).orElseThrow());
        expect(anchor.name().equals(name), "A named item should give the anchor its name, got " + anchor.name());
        return anchor.id();
    }

    private static void looks(ClientGameTestContext context, TestServerContext server, BlockPos home)
    {
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, home.getX() + 2.5, FLAT_FEET + 0.4, home.getZ() + 2.5, home.getX() + 0.5, FLAT_FEET + 0.7, home.getZ() + 0.5, 10);
        waitForChunks.run();
        context.takeScreenshot("wrought-anchor-placed");
        aimFrom(context, server, home.getX() - 1.5, FLAT_FEET + 1.2, home.getZ() - 1.5, home.getX() + 0.5, FLAT_FEET + 0.9, home.getZ() + 0.5, 10);
        context.takeScreenshot("wrought-anchor-placed-close");
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, home.getX() + 5.5, FLAT_FEET + 1, home.getZ() + 0.5, home.getX() + 12.5, FLAT_FEET + 1, home.getZ() + 0.5, 6);
        context.takeScreenshot("wrought-anchor-in-hand");
    }

    private static void openMap(ClientGameTestContext context, TestServerContext server, BlockPos anchor)
    {
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        aimFrom(context, server, anchor.getX() + 2.5, FLAT_FEET, anchor.getZ() + 0.5, anchor.getX() + 0.5, FLAT_FEET + 0.5, anchor.getZ() + 0.5, 6);
        rightClick(context);
        context.waitFor(minecraft -> minecraft.gui.screen() instanceof TravelMapScreen, 40);
        context.waitTicks(20);
    }

    private static void renameOnMap(ClientGameTestContext context, TestServerContext server, BlockPos home, long homeId)
    {
        openMap(context, server, home);
        context.takeScreenshot("wrought-anchor-map");
        int[] size = context.computeOnClient(minecraft -> new int[] { minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight() });
        click(context, size[0] / 2F, MARGIN + 6 - 11, 2);
        context.getInput().pressKey(InputConstants.KEY_END);
        for (int i = 0; i < WroughtAnchorBalance.NAME_MAX_LENGTH + 4; i++)
            context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
        context.getInput().typeChars("Home Stake");
        context.waitTicks(2);
        context.takeScreenshot("wrought-anchor-map-typing");
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        waitFor(context, server, 40, "the rename to reach the server",
                minecraft -> WroughtAnchorState.get(minecraft.overworld()).get(homeId).map(anchor -> anchor.name().equals("Home Stake")).orElse(false));
        context.waitTicks(4);
        context.takeScreenshot("wrought-anchor-map-renamed");
        boolean tileRenamed = server.computeOnServer(minecraft -> minecraft.overworld().getBlockEntity(home, VoidHeartTiles.WROUGHT_ANCHOR)
                .map(tile -> tile.getName().equals("Home Stake")).orElse(false));
        expect(tileRenamed, "The block should carry the new name too");
    }

    private static void clickFirstRow(ClientGameTestContext context)
    {
        int width = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScaledWidth());
        int listLeft = width - MARGIN - LIST_WIDTH;
        click(context, listLeft + 40, MARGIN + 6 + LIST_HEADER + 6, 2);
    }

    private static void travelOnMap(ClientGameTestContext context, TestServerContext server, BlockPos home, BlockPos far, long farId)
    {
        // The map is still open after the rename
        clickFirstRow(context);
        expectClient(context, "Clicking a destination should close the map", minecraft -> !(minecraft.gui.screen() instanceof TravelMapScreen));
        context.waitTicks(2);
        String gathering = overlay(context);
        expect(gathering.startsWith("Gathering void toward Far Haven"), "Expected the gathering message, got '" + gathering + "'");
        context.waitTicks(16);
        context.takeScreenshot("wrought-anchor-gathering");

        long cost = WroughtAnchorBalance.travelCost(Math.hypot(far.getX() - home.getX(), far.getZ() - home.getZ()));
        int expectedTicks = (int) Math.ceil(cost / (double) WroughtAnchorBalance.INTAKE);
        waitFor(context, server, expectedTicks + 40, "the player to arrive at the far anchor",
                minecraft -> player(minecraft).position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(far)) < 4);
        expect(overlay(context).contains("Far Haven"), "Expected the arrival message, got '" + overlay(context) + "'");
        aimFrom(context, server, far.getX() + 3.5, FLAT_FEET + 0.5, far.getZ() + 1.5, far.getX() + 0.5, FLAT_FEET + 0.8, far.getZ() + 0.5, 10);
        waitForChunks.run();
        context.takeScreenshot("wrought-anchor-arrived");
    }

    private static void refusedWithoutVoid(ClientGameTestContext context, TestServerContext server, BlockPos far, long homeId)
    {
        context.waitTicks(45);
        openMap(context, server, far);
        clickFirstRow(context);
        context.waitTicks(4);
        expect(overlay(context).equals(Component.translatable("voidheart.wrought_anchor.no_void").getString()),
                "Expected the no-void message, got '" + overlay(context) + "'");
        boolean stayed = server.computeOnServer(minecraft -> player(minecraft).position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(far)) < 8);
        expect(stayed, "Without void energy the player should stay put");
    }

    private static void movedKeepsName(ClientGameTestContext context, TestServerContext server, BlockPos far, BlockPos moved, long farId)
    {
        server.runCommand("setblock " + far.getX() + " " + far.getY() + " " + far.getZ() + " air destroy");
        context.waitTicks(4);
        var dropped = server.computeOnServer(minecraft -> minecraft.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(far).inflate(3))
                .stream().map(ItemEntity::getItem).filter(stack -> stack.is(VoidHeartBlocks.WROUGHT_ANCHOR.asItem())).findFirst().map(ItemStack::copy).orElse(null));
        expect(dropped != null, "Breaking the anchor should drop it");
        expect(dropped.getHoverName().getString().equals("Far Haven"), "The dropped anchor should keep its name, got " + dropped.getHoverName().getString());
        boolean forgotten = server.computeOnServer(minecraft -> WroughtAnchorState.get(minecraft.overworld()).get(farId).isEmpty());
        expect(forgotten, "A broken anchor should be forgotten");

        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, dropped);
            player.teleportTo(moved.getX() + 2.5, FLAT_FEET, moved.getZ() + 0.5);
            useOn(player, moved.below());
        });
        waitFor(context, server, 40, "the moved anchor to be recorded", minecraft -> anchorAt(minecraft, moved).isPresent());
        var anchor = server.computeOnServer(minecraft -> anchorAt(minecraft, moved).orElseThrow());
        expect(anchor.name().equals("Far Haven") && anchor.id() != farId, "The moved anchor should keep its name under a new id, got " + anchor);
    }
}
