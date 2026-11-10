package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.client.screen.ResearchScreen;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.PlayerKnowledge;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Drives the repository screen with real clicks and keys, and captures it at each stage of a player's progress and at the sizes it is played at.
 */
public class ResearchScreenClientTest implements FabricClientGameTest
{
    private static final int      GROUND     = -61;
    private static final BlockPos REPOSITORY = new BlockPos(0, GROUND + 1, 3);

    private static final Identifier ALTAR_RITE  = id("altar_rite");
    private static final Identifier VOID_FLESH  = id("void_flesh");
    private static final Identifier ARCHIVE     = id("archive_expansion");
    private static final Identifier PATIENT     = id("patient_study");
    private static final Identifier FORGE       = id("shatter_forge");
    private static final Identifier FAR_SIGHT   = id("far_sight");
    private static final Identifier HUSBANDRY   = id("void_husbandry");
    private static final Identifier VAST        = id("vast_archive");
    private static final Identifier PORTAL      = id("portal_flexion");
    private static final Identifier ARMILLARY   = id("armillary");

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 0 10");
            context.waitTicks(5);

            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                player.level().setBlockAndUpdate(REPOSITORY, Blocks.LECTERN.defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.KNOWLEDGE_TABLET));
                use(player);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                use(player);
                knowledge(minecraft).addKnowledge(4);
            });
            context.waitFor(client -> client.gui.screen() instanceof ResearchScreen);
            context.waitTicks(30);
            captureSizes(context, "fresh");

            if (context.computeOnClient(client -> ResearchScreen.snapshot.insights()) != PlayerKnowledge.STARTING_INSIGHTS)
                throw new AssertionError("A new player should start with one insight");

            click(context, context.computeOnClient(client -> screen(client).insightPosition()));
            context.waitTicks(25);
            if (context.computeOnClient(client -> ResearchScreen.snapshot.insights()) != 1)
                throw new AssertionError("An insight should not be crafted without the knowledge for it");

            click(context, context.computeOnClient(client -> screen(client).eyePosition()));
            context.waitTicks(25);
            if (context.computeOnClient(client -> ResearchScreen.snapshot.insights()) != 1)
                throw new AssertionError("The eye no longer crafts anything");

            var readout = context.computeOnClient(client -> screen(client).readoutPosition());
            double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
            context.getInput().setCursorPos(readout[0] * scale, readout[1] * scale);
            context.takeScreenshot("research-ui-card-growing");
            context.waitTicks(10);
            context.takeScreenshot("research-ui-card");
            hover(context, context.computeOnClient(client -> screen(client).insightPosition()));
            context.waitTicks(10);
            context.takeScreenshot("research-ui-craft-waiting-hover");
            hover(context, new int[] { 5, 5 });
            context.waitTicks(8);

            server.runOnServer(minecraft -> knowledge(minecraft).addKnowledge(21));
            context.waitTicks(25);
            context.takeScreenshot("research-ui-can-craft");
            context.waitTicks(150);
            context.takeScreenshot("research-ui-can-craft-settled");
            hover(context, context.computeOnClient(client -> screen(client).insightPosition()));
            context.waitTicks(10);
            context.takeScreenshot("research-ui-craft-ready-hover");
            hover(context, context.computeOnClient(client -> screen(client).readoutPosition()));
            context.waitTicks(10);
            context.takeScreenshot("research-ui-card-ready");
            hover(context, new int[] { 5, 5 });
            context.waitTicks(8);

            click(context, context.computeOnClient(client -> screen(client).insightPosition()));
            context.waitFor(client -> ResearchScreen.snapshot.insights() == 2, 100);
            context.getInput().setCursorPos(10, 10);
            context.takeScreenshot("research-ui-craft-flight");
            context.waitTicks(20);
            context.takeScreenshot("research-ui-craft-lit");
            context.waitTicks(80);
            context.takeScreenshot("research-ui-craft-out");
            hover(context, new int[] { 5, 5 });
            context.waitTicks(3);
            context.takeScreenshot("research-ui-can-reveal");

            click(context, context.computeOnClient(client -> screen(client).nodePosition(ALTAR_RITE)));
            context.waitTicks(5);
            if (!ALTAR_RITE.equals(context.computeOnClient(client -> screen(client).selected())))
                throw new AssertionError("A click on a node should select it");
            if (context.computeOnClient(client -> ResearchScreen.snapshot.insights()) != 2)
                throw new AssertionError("Selecting must not spend anything");

            click(context, context.computeOnClient(client -> screen(client).actionPosition()));
            context.waitFor(client -> ResearchScreen.snapshot.revealed().contains(ALTAR_RITE), 100);
            context.waitTicks(4);
            context.takeScreenshot("research-ui-reveal-bloom");

            context.waitTicks(30);
            click(context, context.computeOnClient(client -> screen(client).actionPosition()));
            context.waitFor(client -> ResearchScreen.snapshot.active().filter(ALTAR_RITE::equals).isPresent(), 100);
            context.waitTicks(30);
            context.takeScreenshot("research-ui-first-research");

            context.getInput().pressKey(InputConstants.KEY_RETURN);
            context.waitFor(client -> ResearchScreen.snapshot.active().isEmpty(), 100);

            server.runOnServer(minecraft ->
            {
                var data = knowledge(minecraft);
                data.addKnowledge(-data.getKnowledge() + 20);
            });
            context.waitTicks(25);
            context.getInput().pressKey(InputConstants.KEY_R);
            context.waitFor(client -> ResearchScreen.snapshot.insights() == 2, 100);

            server.runOnServer(minecraft ->
            {
                var data = knowledge(minecraft);
                data.grant(ALTAR_RITE);
                data.grant(VOID_FLESH);
                data.grant(ARCHIVE);
                data.grant(PATIENT);
                data.addKnowledge(120);
                data.addInsights(7);
                var tree = Knowledge.tree(minecraft.registryAccess());
                data.reveal(FORGE, tree);
                data.getProgress().put(FORGE, 60D);
                data.addInsights(7);
                data.reveal(FAR_SIGHT, tree);
                data.toggleActive(FAR_SIGHT);
                data.getProgress().put(FAR_SIGHT, 80D);

                for (var item : java.util.List.of("minecraft:ender_pearl", "minecraft:echo_shard", "minecraft:ghast_tear", "minecraft:amethyst_shard", "minecraft:nautilus_shell"))
                {
                    var identifier = Identifier.parse(item);
                    int count = item.contains("pearl") ? 9 : item.contains("echo") ? 6 : item.contains("ghast") ? 2 : 1;
                    for (int fed = 0; fed < count; fed++)
                        data.feed(identifier, 1, 1000);
                }
                data.addInsights(3 - data.getInsights());
            });
            context.waitTicks(30);
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);
            captureSizes(context, "midgame");

            hover(context, context.computeOnClient(client -> screen(client).readoutPosition()));
            context.waitTicks(8);
            context.takeScreenshot("research-ui-card-burning");

            click(context, context.computeOnClient(client -> screen(client).nodePosition(FORGE)));
            hover(context, new int[] { 5, 5 });
            context.takeScreenshot("research-ui-detail-revealed");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);

            click(context, context.computeOnClient(client -> screen(client).nodePosition(FAR_SIGHT)));
            hover(context, new int[] { 5, 5 });
            context.takeScreenshot("research-ui-detail-running");

            for (var node : context.computeOnClient(client -> screen(client).shownNodes()))
            {
                if (context.computeOnClient(client -> screen(client).selected()) != null)
                {
                    context.getInput().pressKey(InputConstants.KEY_ESCAPE);
                    context.waitTicks(3);
                }
                click(context, context.computeOnClient(client -> screen(client).nodePosition(node)));
                if (!node.equals(context.computeOnClient(client -> screen(client).selected())))
                    continue;
                hover(context, context.computeOnClient(client -> screen(client).readoutPosition()));
                context.waitFor(client -> screen(client).cardOpen());
                context.takeScreenshot("research-ui-card-beside-" + node.getPath());
                if (context.computeOnClient(client -> screen(client).cardOverlapsDetail()))
                    throw new AssertionError("The knowledge card should not be drawn over the tablet of " + node);
                hover(context, new int[] { 5, 5 });
                context.waitFor(client -> screen(client).detailShown());
            }
            if (context.computeOnClient(client -> screen(client).selected()) == null)
                click(context, context.computeOnClient(client -> screen(client).nodePosition(FAR_SIGHT)));

            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);
            if (context.computeOnClient(client -> screen(client).selected()) != null)
                throw new AssertionError("Escape should let go of the selection before leaving the screen");

            click(context, context.computeOnClient(client -> screen(client).extractedPosition()));
            hover(context, new int[] { 5, 5 });
            context.takeScreenshot("research-ui-extracted");
            click(context, new int[] { 5, 5 });

            server.runOnServer(minecraft ->
            {
                var data = knowledge(minecraft);
                data.addKnowledge(-data.getKnowledge());
                data.bind(java.util.Optional.of(net.minecraft.core.GlobalPos.of(player(minecraft).level().dimension(), REPOSITORY)));
            });
            context.waitTicks(30);
            context.takeScreenshot("research-ui-starving");

            server.runOnServer(minecraft ->
            {
                var data = knowledge(minecraft);
                data.grant(FAR_SIGHT);
                data.grant(id("ley_sense"));
                data.grant(FORGE);
                data.grant(ARMILLARY);
                data.grant(PORTAL);
                data.grant(HUSBANDRY);
                data.grant(VAST);
                data.addKnowledge(4200);
                data.addInsights(40);
                var tree = Knowledge.tree(minecraft.registryAccess());
                data.reveal(id("beholder"), tree);
                data.toggleActive(id("beholder"));
                data.getProgress().put(id("beholder"), 1495D);
            });
            context.waitFor(client -> ResearchScreen.snapshot.completed().contains(id("beholder")), 400);
            context.waitTicks(6);
            context.takeScreenshot("research-ui-finish-bloom");
            context.waitTicks(40);
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);
            captureSizes(context, "lategame");

            hover(context, context.computeOnClient(client -> screen(client).nodePosition(VOID_FLESH)));
            context.takeScreenshot("research-ui-hover-node");

            server.runOnServer(minecraft ->
            {
                var data = knowledge(minecraft);
                data.addKnowledge(9000);
            });
            context.waitTicks(30);
            hover(context, new int[] { 200, 4 });
            context.takeScreenshot("research-ui-full");
            hover(context, context.computeOnClient(client -> screen(client).readoutPosition()));
            context.waitTicks(10);
            context.takeScreenshot("research-ui-card-full");
            hover(context, context.computeOnClient(client -> screen(client).insightPosition()));
            context.waitTicks(10);
            context.takeScreenshot("research-ui-craft-many-hover");

            // The Deities stay hidden until the Daedalus is done, and Maw comes further through behind the map with each one read
            hover(context, new int[] { 2, 2 });
            if (context.computeOnClient(client -> screen(client).mawStage()) != -1)
                throw new AssertionError("The Deities show before the Daedalus is done");
            server.runOnServer(minecraft -> knowledge(minecraft).grant(id("daedalus")));
            context.waitTicks(20);
            captureMaw(context, 0);
            String[] deities = { "hunger_beneath", "litany_of_teeth", "gullet", "maw" };
            for (int stage = 1; stage <= deities.length; stage++)
            {
                var research = id(deities[stage - 1]);
                server.runOnServer(minecraft -> knowledge(minecraft).grant(research));
                context.waitTicks(20);
                captureMaw(context, stage);
            }
            // A tendril mid-lash, caught in the first second of its cycle at most
            context.waitTicks(17);
            context.takeScreenshot("research-ui-maw-4-later-427x240");

            // Sealed off, the ravine shuts and is stitched across, and the flesh leaves the stone
            server.runOnServer(minecraft -> knowledge(minecraft).grant(id("sealed_mind")));
            context.waitTicks(20);
            captureMaw(context, 5);

            server.runOnServer(minecraft ->
            {
                var data = knowledge(minecraft);
                var tree = Knowledge.tree(minecraft.registryAccess());
                data.addInsights(100);
                tree.keySet().forEach(data::grant);
            });
            context.waitTicks(30);
            context.takeScreenshot("research-ui-all-read");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            hover(context, new int[] { 2, 2 });
            captureSizes(context, "all-read");

            // Every text read on the map or a page, so a stray % or a missing key shows here rather than in play
            context.computeOnClient(client -> screen(client).describedTexts()).forEach((key, text) ->
            {
                if (text.contains("Format error") || text.equals(key) || text.contains("((") || text.contains("[[") || text.contains("{{"))
                    throw new AssertionError(key + " shows as: " + text);
            });

            // The map keeps its spacing on a small screen and pans instead, by dragging the bare dark and by the wheel
            int[] rest = context.computeOnClient(client -> screen(client).eyePosition());
            hover(context, bareDark(context));
            context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(1);
            for (int step = 0; step < 8; step++)
            {
                context.getInput().moveCursor(30, 10);
                context.waitTicks(1);
            }
            context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(3);
            int[] dragged = context.computeOnClient(client -> screen(client).eyePosition());
            if (dragged[0] <= rest[0] || dragged[1] <= rest[1])
                throw new AssertionError("Dragging the map did not pan it: eye at " + rest[0] + "," + rest[1] + " then " + dragged[0] + "," + dragged[1]);
            context.takeScreenshot("research-ui-panned-427x240");

            context.getInput().scroll(-4);
            context.waitTicks(3);
            int[] scrolled = context.computeOnClient(client -> screen(client).eyePosition());
            if (scrolled[1] >= dragged[1])
                throw new AssertionError("The wheel did not pan the map: eye at " + dragged[1] + " then " + scrolled[1]);
            context.takeScreenshot("research-ui-scrolled-427x240");
            context.runOnClient(client -> ResearchScreen.resetPan());

            context.runOnClient(client -> client.player.closeContainer());
            context.waitFor(client -> client.gui.screen() == null);
        }
    }

    /** A point of the map with no research and no eye under or near it, wherever the map happens to put them, so that pressing there starts a drag and nothing else. */
    private static int[] bareDark(ClientGameTestContext context)
    {
        return context.computeOnClient(client ->
        {
            var screen = screen(client);
            var nodes = screen.shownNodes().stream().map(screen::nodePosition).toList();
            int[] eye = screen.eyePosition();
            for (int y = 30; y < screen.height - 90; y += 6)
            {
                for (int x = 60; x < screen.width / 2; x += 6)
                {
                    int atX = x;
                    int atY = y;
                    boolean offEye = Math.abs(atX - eye[0]) > 60 || Math.abs(atY - eye[1]) > 45;
                    if (offEye && nodes.stream().allMatch(node -> Math.abs(node[0] - atX) > 20 || Math.abs(node[1] - atY) > 20))
                        return new int[] { atX, atY };
                }
            }
            throw new AssertionError("No bare dark on the map to drag it by");
        });
    }

    private static void captureMaw(ClientGameTestContext context, int stage)
    {
        context.runOnClient(client -> screen(client).deselect());
        context.waitTicks(2);
        int seen = context.computeOnClient(client -> screen(client).mawStage());
        if (seen != stage)
            throw new AssertionError("Maw should be at stage " + stage + " but is at " + seen);
        resize(context, 1920, 1080, 2);
        context.takeScreenshot("research-ui-maw-" + stage + "-960x540");
        resize(context, 854, 480, 2);
        context.takeScreenshot("research-ui-maw-" + stage + "-427x240");
    }

    private static void captureSizes(ClientGameTestContext context, String name)
    {
        context.getInput().setCursorPos(2, 2);
        resize(context, 854, 480, 2);
        context.takeScreenshot("research-ui-" + name + "-427x240");
        resize(context, 1920, 1080, 4);
        context.takeScreenshot("research-ui-" + name + "-480x270");
        resize(context, 1920, 1080, 3);
        context.takeScreenshot("research-ui-" + name + "-640x360");
        resize(context, 1920, 1080, 2);
        context.takeScreenshot("research-ui-" + name + "-960x540");
        resize(context, 854, 480, 2);
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
