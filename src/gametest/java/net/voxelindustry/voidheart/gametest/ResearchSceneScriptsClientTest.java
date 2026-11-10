package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.client.scene.ResearchScenes;
import net.voxelindustry.voidheart.client.scene.Scene;
import net.voxelindustry.voidheart.client.screen.ResearchScreen;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFormer;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameTile;
import net.voxelindustry.voidheart.common.energy.grid.GridLayout;
import net.voxelindustry.voidheart.common.energy.grid.VoidGrid;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridContent;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneBalance;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneContent;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorTile;
import net.voxelindustry.voidheart.common.energy.storage.RubrumConstructTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Looks at every scene written after the first three, step by step, and holds what they teach to the game: each finished structure is set down in a real world,
 * where its own tile has to accept it, its pylons have to cover what the text says they feed, and its relays have to find what they are shown feeding.
 */
public class ResearchSceneScriptsClientTest implements FabricClientGameTest
{
    private static final BlockPos REPOSITORY = new BlockPos(0, FLAT_FEET, 3);

    // Far enough apart that no pylon of one scene reaches a pylon of another
    private static final BlockPos GRID_AT    = new BlockPos(30, FLAT_FEET, 60);
    private static final BlockPos REACTOR_AT = new BlockPos(-40, FLAT_FEET, 60);
    private static final BlockPos SPIKE_AT   = new BlockPos(-40, FLAT_FEET, -40);
    private static final BlockPos FISSURE_AT = new BlockPos(30, FLAT_FEET, -40);
    private static final BlockPos PORTAL_AT  = new BlockPos(80, FLAT_FEET, 0);

    private static final Identifier GRID    = id("void_energy");
    private static final Identifier REACTOR = id("contravoid_reactor");
    private static final Identifier SPIKE   = id("ley_conversion");
    private static final Identifier FISSURE = id("void_fissure");
    private static final Identifier STONES  = id("ley_sense");
    private static final Identifier PORTAL  = id("portal_flexion");
    private static final Identifier BARRIER = id("permeable_barrier");

    private static final Map<Identifier, String> NAMES = Map.of(GRID, "grid", REACTOR, "reactor", SPIKE, "spike", FISSURE, "fissure",
            STONES, "stones", PORTAL, "portal", BARRIER, "barrier");
    private static final List<Identifier> ORDER = List.of(GRID, REACTOR, SPIKE, FISSURE, STONES, PORTAL, BARRIER);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 0 10");
            server.runCommand("forceload add -64 -64 112 80");
            context.waitTicks(5);

            server.runOnServer(ResearchSceneScriptsClientTest::assertEveryStepHasItsText);
            server.runOnServer(ResearchSceneScriptsClientTest::assertStonesStandInReach);
            server.runOnServer(ResearchSceneScriptsClientTest::assertPortalFrameForms);

            // The fissure stays out of the world: nothing breaks one, and what it gives is read from the game's own numbers
            server.runOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                build(level, GRID_AT, scene(GRID));
                build(level, REACTOR_AT, scene(REACTOR));
                build(level, SPIKE_AT, scene(SPIKE));
            });
            waitFor(context, server, 80, "The grid scene's construct should form", minecraft ->
                    minecraft.overworld().getBlockEntity(construct()) instanceof RubrumConstructTile tile && tile.isFormed());
            waitFor(context, server, 80, "The reactor scene's shell should complete its reactor", minecraft ->
                    minecraft.overworld().getBlockEntity(REACTOR_AT.above()) instanceof ContravoidReactorTile tile && tile.isComplete());
            waitFor(context, server, 120, "The grid scene's two pylons should arc", minecraft ->
            {
                var grid = VoidGrid.get(minecraft.overworld());
                return grid != null && grid.layout().arcs().length == 1;
            });
            waitFor(context, server, 120, "Every relay of the scenes should find what it is shown feeding",
                    minecraft -> relaysLinked(minecraft.overworld()));
            server.runOnServer(ResearchSceneScriptsClientTest::assertFieldsCover);

            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                player.level().setBlockAndUpdate(REPOSITORY, Blocks.LECTERN.defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.KNOWLEDGE_TABLET));
                use(player);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                use(player);
                var data = knowledge(minecraft);
                ResearchScenes.researches().forEach(data::grant);
            });
            context.waitFor(client -> client.gui.screen() instanceof ResearchScreen);
            context.waitTicks(30);
            resize(context, 854, 480, 2);

            for (var research : ORDER)
            {
                context.runOnClient(client -> screen(client).showScene(research));
                context.waitTicks(3);
                if (!context.computeOnClient(client -> screen(client).sceneOpen()))
                    throw new AssertionError("The research " + research + " should have a scene to open");
                hover(context, new int[] { 2, 2 });
                captureSteps(context, NAMES.get(research));
                if (research.equals(GRID))
                    assertGridMoments(context);
                if (research.equals(SPIKE))
                    captureMoment(context, "spike-sneak-click", 2, 118);
                if (research.equals(FISSURE))
                    captureMoment(context, "fissure-tearing", 1, 132);
                if (research.equals(REACTOR))
                    captureMoment(context, "reactor-igniting", 4, 75);
                if (research.equals(BARRIER))
                    captureMoment(context, "barrier-first-corner", 1, 34);
                context.getInput().pressKey(InputConstants.KEY_ESCAPE);
                context.waitTicks(3);
            }

            // The widest stages are the ones a small window squeezes most, and a large one draws the most of
            context.runOnClient(client -> screen(client).showScene(STONES));
            context.waitTicks(3);
            resize(context, 640, 480, 2);
            context.runOnClient(client -> screen(client).holdScene(4));
            context.waitTicks(3);
            context.takeScreenshot("research-scripts-stones-window-320x240-ward");
            context.getInput().pressKey(InputConstants.KEY_F);
            context.waitTicks(3);
            resize(context, 1920, 1080, 2);
            context.runOnClient(client -> screen(client).holdScene(4));
            context.waitTicks(3);
            context.takeScreenshot("research-scripts-stones-full-960x540-ward");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);
            context.runOnClient(client -> screen(client).showScene(GRID));
            context.waitTicks(3);
            context.getInput().pressKey(InputConstants.KEY_F);
            context.waitTicks(3);
            context.runOnClient(client -> screen(client).holdScene(3));
            context.waitTicks(3);
            context.takeScreenshot("research-scripts-grid-full-960x540-arc");
            context.getInput().pressKey(InputConstants.KEY_F);
            context.waitTicks(3);
            resize(context, 854, 480, 2);
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);

            context.runOnClient(client -> client.player.closeContainer());
            context.waitFor(client -> client.gui.screen() == null);
            server.runCommand("forceload remove all");
        }
    }

    /**
     * The grid's scene is open: a click shows where it lands, and what the pointer rests on is named.
     */
    private static void assertGridMoments(ClientGameTestContext context)
    {
        captureMoment(context, "grid-click", 1, 24);
        captureMoment(context, "grid-count", 5, 40);

        context.runOnClient(client -> screen(client).holdScene(0));
        context.waitTicks(3);
        var pylon = context.computeOnClient(client -> screen(client).scenePoint(0.5, 0.5, 0.5));
        hover(context, pylon);
        var named = context.computeOnClient(client -> screen(client).sceneNameAt(pylon));
        var expected = context.computeOnClient(client -> VoidGridContent.AZURIUM_PYLON.getName().getString());
        if (!expected.equals(named))
            throw new AssertionError("Pointing at the pylon should name it, got " + named);
        context.takeScreenshot("research-scripts-grid-hover-name");

        var floor = context.computeOnClient(client -> screen(client).scenePoint(4.5, 0, 2.5));
        var nothing = context.computeOnClient(client -> screen(client).sceneNameAt(floor));
        if (nothing != null)
            throw new AssertionError("The floor is nothing to name, got " + nothing);
        hover(context, new int[] { 2, 2 });
    }

    private static void captureSteps(ClientGameTestContext context, String name)
    {
        int steps = context.computeOnClient(client -> screen(client).sceneSteps());
        for (int step = 0; step < steps; step++)
        {
            int held = step;
            context.runOnClient(client -> screen(client).holdScene(held));
            context.waitTicks(2);
            context.takeScreenshot("research-scripts-" + name + "-step" + step);
        }
    }

    private static void captureMoment(ClientGameTestContext context, String name, int step, int offset)
    {
        context.runOnClient(client -> screen(client).holdScene(step, offset));
        context.waitTicks(2);
        context.takeScreenshot("research-scripts-" + name);
    }

    private static Scene scene(Identifier research)
    {
        return ResearchScenes.of(research).orElseThrow();
    }

    /**
     * A scene stands a bundle in a construct's socket as a block, where the game keeps it in the construct: those stay out of the world.
     */
    private static void build(ServerLevel level, BlockPos origin, Scene scene)
    {
        var finished = scene.finished();
        var pylons = new HashSet<BlockPos>();
        finished.stream().filter(place -> place.state().is(VoidGridContent.AZURIUM_PYLON)).forEach(place -> pylons.add(place.pos()));
        for (var place : finished)
        {
            boolean bundle = net.voxelindustry.voidheart.common.energy.storage.ConstructBundles.bundleOf(new ItemStack(place.state().getBlock())) != null;
            if (!bundle || pylons.contains(place.pos().below()))
                level.setBlockAndUpdate(origin.offset(place.pos()), place.state());
        }
    }

    private static BlockPos construct()
    {
        return scene(GRID).finished().stream()
                .filter(place -> place.state().is(net.voxelindustry.voidheart.common.energy.storage.RubrumContent.RUBRUM_CONSTRUCT_CORE))
                .map(place -> GRID_AT.offset(place.pos())).findFirst().orElseThrow();
    }

    private static List<BlockPos> blocksOf(Scene scene, BlockPos origin, Predicate<Scene.Place> which)
    {
        return scene.finished().stream().filter(which).map(place -> origin.offset(place.pos())).toList();
    }

    /**
     * The engine's relay has to reach the engine, and each of the reactor's relays the intake on its side.
     */
    private static boolean relaysLinked(ServerLevel level)
    {
        var gridRelays = blocksOf(scene(GRID), GRID_AT, place -> place.state().is(VoidHeartBlocks.RAVENOUS_RELAY));
        var engines = blocksOf(scene(GRID), GRID_AT, place -> place.state().is(net.voxelindustry.voidheart.common.energy.engine.RavenousEngineContent.RAVENOUS_ENGINE));
        for (var relay : gridRelays)
        {
            if (!(level.getBlockEntity(relay) instanceof RavenousRelayTile tile) || tile.linkedReceivers().stream().noneMatch(engines::contains))
                return false;
        }

        var reactorRelays = blocksOf(scene(REACTOR), REACTOR_AT, place -> place.state().is(VoidHeartBlocks.RAVENOUS_RELAY));
        var intakes = new HashSet<>(blocksOf(scene(REACTOR), REACTOR_AT, place -> place.state().is(net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorContent.REACTOR_INTAKE)));
        var fed = new HashSet<BlockPos>();
        for (var relay : reactorRelays)
        {
            if (level.getBlockEntity(relay) instanceof RavenousRelayTile tile)
                tile.linkedReceivers().stream().filter(intakes::contains).forEach(fed::add);
        }
        return gridRelays.size() == 1 && reactorRelays.size() == 2 && fed.size() == 2;
    }

    /**
     * What a scene shows inside a field has to stand inside one.
     */
    private static void assertFieldsCover(MinecraftServer minecraft)
    {
        var level = minecraft.overworld();
        expect(covered(level, construct()), "The grid scene's construct should stand in a pylon's field");
        for (var engine : blocksOf(scene(GRID), GRID_AT, place -> place.state().is(net.voxelindustry.voidheart.common.energy.engine.RavenousEngineContent.RAVENOUS_ENGINE)))
            expect(covered(level, engine), "The grid scene's engine should stand in a pylon's field");
        expect(covered(level, REACTOR_AT.above()), "The reactor scene's core should stand in the field it lights from");
        expect(covered(level, SPIKE_AT), "The spike scene's spike should stand in a pylon's field");

        // The fissure and the portal are not built, so their pylons are held to the same rule by hand
        for (var research : List.of(FISSURE, PORTAL))
        {
            var finished = scene(research).finished();
            var bundles = finished.stream().filter(place -> place.state().is(VoidHeartBlocks.SMALL_AZURIUM_BUNDLE)).map(Scene.Place::pos).toList();
            expect(bundles.size() == 1, "The scene of " + research + " should show one small bundle, it shows " + bundles.size());
            var small = net.voxelindustry.voidheart.common.energy.grid.PylonBundle.SMALL;
            var pylon = new GridLayout.Pylon(bundles.getFirst().below(), bundles.getFirst(), small.radius(), small.cap());
            var fed = research.equals(FISSURE) ? BlockPos.ZERO
                    : finished.stream().filter(place -> place.state().is(VoidHeartBlocks.PORTAL_FRAME_CORE)).map(Scene.Place::pos)
                    .filter(pylon::covers).findFirst().orElse(null);
            expect(fed != null && pylon.covers(fed), "The scene of " + research + " should stand what it feeds inside its pylon's field");
        }
    }

    private static boolean covered(ServerLevel level, BlockPos pos)
    {
        return !VoidGrid.pylonsCovering(level, pos).isEmpty();
    }

    /**
     * The stones scene draws its distances shorter than the most the game allows, never longer.
     */
    private static void assertStonesStandInReach(MinecraftServer minecraft)
    {
        var finished = scene(STONES).finished();
        var stones = finished.stream().filter(place -> place.state().is(LeyStoneContent.LEY_STONE)
                        && place.state().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
                .map(Scene.Place::pos).sorted(java.util.Comparator.comparingInt(BlockPos::getX)).toList();
        expect(stones.size() == 2, "The stones scene should raise two stones, it raises " + stones.size());

        // Its ley line runs along Z through X 0
        expect(Math.abs(stones.get(0).getX()) <= LeyStoneBalance.STONE_REACH, "The first stone should stand in reach of the line");
        expect(stones.get(1).getX() > LeyStoneBalance.STONE_REACH, "The second stone should stand out of the line's reach, or it shows no chain");
        expect(stones.get(1).getX() - stones.get(0).getX() <= LeyStoneBalance.STONE_REACH, "The second stone should stand in reach of the first");

        int supply = LeyStoneBalance.STONE_SUPPLY_RADIUS * LeyStoneBalance.STONE_SUPPLY_RADIUS;
        for (var machine : List.of(LeyStoneContent.LEY_WARD, LeyStoneContent.LEY_GROWER))
        {
            var pos = finished.stream().filter(place -> place.state().is(machine)).map(Scene.Place::pos).findFirst().orElseThrow();
            expect(stones.stream().anyMatch(stone -> stone.distSqr(pos) <= supply), "The " + machine.getName().getString() + " should stand in a stone's supply");
        }
    }

    /**
     * The bricks the portal scene raises have to flood into the opening it then fills.
     */
    private static void assertPortalFrameForms(MinecraftServer minecraft)
    {
        var finished = scene(PORTAL).finished();
        var cores = finished.stream().filter(place -> place.state().is(VoidHeartBlocks.PORTAL_FRAME_CORE)).toList();
        expect(cores.size() == 2, "The portal scene should form two portals, it forms " + cores.size());

        var frame = new HashSet<BlockPos>();
        var way = new HashSet<BlockPos>();
        for (var place : finished)
        {
            if (place.state().is(VoidHeartBlocks.PORTAL_FRAME) || place.state().is(VoidHeartBlocks.PORTAL_FRAME_CORE))
                frame.add(place.pos());
            else if (place.state().is(VoidHeartBlocks.PORTAL_INTERIOR))
                way.add(place.pos());
        }

        for (var core : cores)
        {
            var facing = core.state().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
            var found = PortalFormer.tryFloodFill(core.pos(), 16, frame::contains, pos -> !frame.contains(pos), facing, PortalFrameTile.getAdjacentDirection(facing));
            expect(!found.getLeft().equals(found.getRight()), "The frame around " + core.pos() + " should form a portal");
            var inside = new HashSet<BlockPos>();
            BlockPos.betweenClosed(found.getLeft(), found.getRight()).forEach(pos ->
            {
                if (!frame.contains(pos))
                    inside.add(pos.immutable());
            });
            expect(way.containsAll(inside) && !inside.isEmpty(), "The way shown inside " + core.pos() + " should be the opening its frame floods to, " + inside);
            expect(facing == Direction.SOUTH, "The core should face the side the scene clicks it from");
        }
    }

    /**
     * A step without its line would show its key on the tablet.
     */
    private static void assertEveryStepHasItsText(MinecraftServer minecraft)
    {
        var language = net.minecraft.locale.Language.getInstance();
        for (var research : ResearchScenes.researches())
        {
            var scene = scene(research);
            expect(language.has(scene.titleKey()), "The scene of " + research + " has no title, " + scene.titleKey());
            for (int step = 0; step < scene.steps().size(); step++)
                expect(language.has(scene.textKey(step)), "A step has no text, " + scene.textKey(step));
        }
    }

    private static ResearchScreen screen(Minecraft client)
    {
        return (ResearchScreen) client.gui.screen();
    }

    private static void use(ServerPlayer player)
    {
        useOn(player, REPOSITORY);
    }
}
