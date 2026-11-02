package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.screen.ResearchScreen;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.PlayerKnowledge;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

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

    private static Identifier id(String path)
    {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 0 10");
            context.waitTicks(5);

            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                player.level().setBlockAndUpdate(REPOSITORY, VoidHeartBlocks.VOID_PILLAR.defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.KNOWLEDGE_TABLET));
                use(player);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                use(player);
                data(minecraft).addKnowledge(4);
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
            hover(context, new int[] { 5, 5 });
            context.waitTicks(8);

            server.runOnServer(minecraft -> data(minecraft).addKnowledge(21));
            context.waitTicks(25);
            context.takeScreenshot("research-ui-can-craft");

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
                var data = data(minecraft);
                data.addKnowledge(-data.getKnowledge() + 20);
            });
            context.waitTicks(25);
            context.getInput().pressKey(InputConstants.KEY_R);
            context.waitFor(client -> ResearchScreen.snapshot.insights() == 2, 100);

            server.runOnServer(minecraft ->
            {
                var data = data(minecraft);
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
                var data = data(minecraft);
                data.addKnowledge(-data.getKnowledge());
                data.bind(java.util.Optional.of(net.minecraft.core.GlobalPos.of(player(minecraft).level().dimension(), REPOSITORY)));
            });
            context.waitTicks(30);
            context.takeScreenshot("research-ui-starving");

            server.runOnServer(minecraft ->
            {
                var data = data(minecraft);
                data.grant(FAR_SIGHT);
                data.grant(FORGE);
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
                var data = data(minecraft);
                data.addKnowledge(9000);
            });
            context.waitTicks(30);
            hover(context, new int[] { 200, 4 });
            context.takeScreenshot("research-ui-full");

            server.runOnServer(minecraft ->
            {
                var data = data(minecraft);
                var tree = Knowledge.tree(minecraft.registryAccess());
                data.addInsights(100);
                tree.keySet().forEach(data::grant);
            });
            context.waitTicks(30);
            context.takeScreenshot("research-ui-all-read");

            context.runOnClient(client -> client.player.closeContainer());
            context.waitFor(client -> client.gui.screen() == null);
        }
    }

    private static void captureSizes(ClientGameTestContext context, String name)
    {
        context.getInput().setCursorPos(2, 2);
        size(context, 854, 480, 2);
        context.takeScreenshot("research-ui-" + name + "-427x240");
        size(context, 1920, 1080, 4);
        context.takeScreenshot("research-ui-" + name + "-480x270");
        size(context, 1920, 1080, 3);
        context.takeScreenshot("research-ui-" + name + "-640x360");
        size(context, 1920, 1080, 2);
        context.takeScreenshot("research-ui-" + name + "-960x540");
        size(context, 854, 480, 2);
    }

    private static void size(ClientGameTestContext context, int width, int height, int scale)
    {
        context.getInput().resizeWindow(width, height);
        context.runOnClient(client ->
        {
            client.options.guiScale().set(scale);
            client.resizeGui();
        });
        context.waitTicks(4);
    }

    private static void click(ClientGameTestContext context, int[] guiPosition)
    {
        double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        context.getInput().setCursorPos(guiPosition[0] * scale, guiPosition[1] * scale);
        context.waitTicks(2);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    private static void hover(ClientGameTestContext context, int[] guiPosition)
    {
        double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        context.getInput().setCursorPos(guiPosition[0] * scale, guiPosition[1] * scale);
        context.waitTicks(3);
    }

    private static ResearchScreen screen(Minecraft client)
    {
        return (ResearchScreen) client.gui.screen();
    }

    private static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
    }

    private static PlayerKnowledge data(MinecraftServer minecraft)
    {
        return Knowledge.of(minecraft, player(minecraft).getUUID());
    }

    private static void use(ServerPlayer player)
    {
        var hit = new BlockHitResult(Vec3.atCenterOf(REPOSITORY), Direction.UP, REPOSITORY, false);
        player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }
}
