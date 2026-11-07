package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.client.scene.ResearchScenes;
import net.voxelindustry.voidheart.client.screen.ResearchScreen;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.content.beam.BeamResolver;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.RecipeSheet;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Opens the scenes of finished researches with real clicks and keys, and holds them still on each step to look at. Also holds the scripts to the game's own rules.
 */
public class ResearchSceneClientTest implements FabricClientGameTest
{
    private static final int      GROUND     = -61;
    private static final BlockPos REPOSITORY = new BlockPos(0, GROUND + 1, 3);
    private static final BlockPos ALTAR      = new BlockPos(20, GROUND + 1, 20);

    private static final Identifier ALTAR_RITE = id("altar_rite");
    private static final Identifier VOID_FLESH = id("void_flesh");
    private static final Identifier FORGE      = id("shatter_forge");
    private static final Identifier ARCHIVE    = id("archive_expansion");
    private static final Identifier HUSBANDRY  = id("void_husbandry");

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

            server.runOnServer(ResearchSceneClientTest::assertScriptsFollowTheRules);

            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                player.level().setBlockAndUpdate(REPOSITORY, Blocks.LECTERN.defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.KNOWLEDGE_TABLET));
                use(player);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                use(player);
                var data = knowledge(minecraft);
                data.grant(ALTAR_RITE);
                data.addKnowledge(20);
            });
            context.waitFor(client -> client.gui.screen() instanceof ResearchScreen);
            context.waitTicks(30);

            resize(context, 854, 480, 2);
            assertHiddenMention(context);
            server.runOnServer(minecraft ->
            {
                var data = knowledge(minecraft);
                data.grant(VOID_FLESH);
                data.grant(FORGE);
                data.grant(ARCHIVE);
            });
            context.waitTicks(30);

            // What was just learned opens by itself, and its tablet would take the next click
            click(context, new int[] { 5, 5 });
            click(context, position(context, ARCHIVE));
            hover(context, new int[] { 5, 5 });
            context.takeScreenshot("research-scene-panel-no-unlocks");
            if (context.computeOnClient(client -> screen(client).actionVisible()))
                throw new AssertionError("A finished research with no scene has nothing for its button to do");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);

            click(context, position(context, VOID_FLESH));
            hover(context, new int[] { 5, 5 });
            context.takeScreenshot("research-scene-panel-many-unlocks");
            assertPages(context, server);
            assertMentions(context);
            click(context, position(context, VOID_FLESH));
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);

            click(context, position(context, ALTAR_RITE));
            hover(context, new int[] { 5, 5 });
            context.takeScreenshot("research-scene-panel-altar");
            assertSceneFromPage(context);
            click(context, context.computeOnClient(client -> screen(client).actionPosition()));
            context.waitTicks(3);
            if (!context.computeOnClient(client -> screen(client).sceneOpen()))
                throw new AssertionError("The button of a finished research with a scene should open it");

            hover(context, new int[] { 2, 2 });
            captureSteps(context, "altar");
            assertControls(context);

            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);
            if (context.computeOnClient(client -> screen(client).sceneOpen()))
                throw new AssertionError("Escape should close the scene");
            if (!(context.computeOnClient(client -> client.gui.screen()) instanceof ResearchScreen))
                throw new AssertionError("Escape should close only the scene, not the screen");
            if (!ALTAR_RITE.equals(context.computeOnClient(client -> screen(client).selected())))
                throw new AssertionError("The research should stay selected behind its scene");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);

            click(context, position(context, FORGE));
            hover(context, new int[] { 5, 5 });
            context.takeScreenshot("research-scene-panel-forge");
            click(context, context.computeOnClient(client -> screen(client).actionPosition()));
            context.waitTicks(3);
            hover(context, new int[] { 2, 2 });
            captureSteps(context, "forge");

            context.getInput().pressKey(InputConstants.KEY_F);
            context.waitTicks(3);
            resize(context, 1920, 1080, 2);
            context.runOnClient(client -> screen(client).holdScene(5));
            context.waitTicks(40);
            context.takeScreenshot("research-scene-forge-full-960x540-beam");
            resize(context, 854, 480, 2);
            context.waitTicks(3);
            context.getInput().pressKey(InputConstants.KEY_F);
            context.waitTicks(3);
            resize(context, 640, 480, 2);
            context.runOnClient(client -> screen(client).holdScene(1));
            context.waitTicks(3);
            context.takeScreenshot("research-scene-forge-window-320x240-preview");
            resize(context, 854, 480, 2);

            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);
            context.runOnClient(client -> client.player.closeContainer());
            context.waitFor(client -> client.gui.screen() == null);
        }
    }

    /**
     * Flesh of the Void is selected: its unlocks open pages, a page leads to others, and Escape walks back through them.
     */
    private static void assertPages(ClientGameTestContext context, TestServerContext server)
    {
        click(context, context.computeOnClient(client -> screen(client).unlockPosition(0)));
        hover(context, new int[] { 2, 2 });
        if (!id("void_amalgam").equals(context.computeOnClient(client -> screen(client).pageItem())))
            throw new AssertionError("A click on an unlock should open its page");
        if (context.computeOnClient(client -> ResearchScreen.recipeSheets.isEmpty()))
            throw new AssertionError("Opening the repository should bring the recipes its pages show");
        if (context.computeOnClient(client -> RecipeSheet.making(ResearchScreen.recipeSheets, id("void_amalgam"), ResearchScreen.snapshot.completed()).isEmpty()))
            throw new AssertionError("The page of something researched should know the altar recipe that makes it");
        context.takeScreenshot("research-page-amalgam");

        var riteIcons = context.computeOnClient(client -> screen(client).pageRiteIconPositions());
        if (riteIcons.size() != 2)
            throw new AssertionError("An altar recipe should mark the altar and the pillars, got " + riteIcons.size() + " icons");
        hover(context, riteIcons.get(0));
        context.takeScreenshot("research-page-amalgam-altar-tooltip");
        hover(context, riteIcons.get(1));
        context.takeScreenshot("research-page-amalgam-pillars-tooltip");
        hover(context, new int[] { 2, 2 });
        resize(context, 640, 480, 2);
        context.waitTicks(3);
        context.takeScreenshot("research-page-amalgam-320x240");
        resize(context, 854, 480, 2);
        context.waitTicks(3);

        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);

        click(context, context.computeOnClient(client -> screen(client).unlockPosition(3)));
        hover(context, new int[] { 2, 2 });
        context.takeScreenshot("research-page-eye-bottle");
        click(context, context.computeOnClient(client -> screen(client).pageSlotPosition(id("void_pearl"))));
        hover(context, new int[] { 2, 2 });
        if (!id("void_pearl").equals(context.computeOnClient(client -> screen(client).pageItem())))
            throw new AssertionError("A click on something with a page of its own should open it");
        context.takeScreenshot("research-page-pearl");

        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
        if (!id("eye_bottle").equals(context.computeOnClient(client -> screen(client).pageItem())))
            throw new AssertionError("Escape should go back one page");
        click(context, context.computeOnClient(client -> screen(client).pageClosePosition()));
        if (context.computeOnClient(client -> screen(client).pageItem()) != null)
            throw new AssertionError("The close button should go back to the panel");
        if (!VOID_FLESH.equals(context.computeOnClient(client -> screen(client).selected())))
            throw new AssertionError("The research should stay selected behind its pages");

        click(context, context.computeOnClient(client -> screen(client).unlockPosition(5)));
        hover(context, new int[] { 2, 2 });
        context.takeScreenshot("research-page-voidstone-many-recipes");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);

        server.runOnServer(minecraft ->
        {
            var data = knowledge(minecraft);
            data.addInsights(20);
            data.reveal(HUSBANDRY, Knowledge.tree(minecraft.registryAccess()));
        });
        context.waitTicks(30);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
        click(context, position(context, HUSBANDRY));
        click(context, context.computeOnClient(client -> screen(client).unlockPosition(0)));
        hover(context, new int[] { 2, 2 });
        if (!id("experience_skull").equals(context.computeOnClient(client -> screen(client).pageItem())))
            throw new AssertionError("An unlock of a research still to finish should open its page too");
        context.takeScreenshot("research-page-locked-recipe");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
        click(context, position(context, VOID_FLESH));
    }

    /**
     * The altar's page mentions the forge's research, which nothing has revealed yet: the page may only name its constellation, and offers nothing to click.
     */
    private static void assertHiddenMention(ClientGameTestContext context)
    {
        click(context, new int[] { 5, 5 });
        click(context, position(context, ALTAR_RITE));
        click(context, context.computeOnClient(client -> screen(client).unlockPosition(0)));
        hover(context, new int[] { 2, 2 });
        context.takeScreenshot("research-page-mention-hidden");
        if (context.computeOnClient(client -> screen(client).pageMentionPosition(FORGE).isPresent()))
            throw new AssertionError("A hidden research should not be a link");
        if (context.computeOnClient(client -> screen(client).pageItemMentionPosition(FORGE).isPresent()))
            throw new AssertionError("An item of a hidden research should not be a link");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
    }

    /**
     * A page's mention of a research leads back to the map, on that research. Void Husbandry is revealed but unfinished here, so it shows with its lock.
     */
    private static void assertMentions(ClientGameTestContext context)
    {
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
        click(context, position(context, FORGE));
        click(context, context.computeOnClient(client -> screen(client).unlockPosition(0)));
        hover(context, new int[] { 2, 2 });
        context.takeScreenshot("research-page-mentions-locked");
        click(context, context.computeOnClient(client -> screen(client).pageItemMentionPosition(id("void_conduit")).orElseThrow()));
        hover(context, new int[] { 2, 2 });
        if (!id("void_conduit").equals(context.computeOnClient(client -> screen(client).pageItem())))
            throw new AssertionError("A click on a mentioned item should open its page");
        context.takeScreenshot("research-page-item-link");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
        click(context, context.computeOnClient(client -> screen(client).pageMentionPosition(HUSBANDRY).orElseThrow()));
        hover(context, new int[] { 2, 2 });
        if (context.computeOnClient(client -> screen(client).pageItem()) != null)
            throw new AssertionError("A click on a mention should leave the pages for the map");
        if (!HUSBANDRY.equals(context.computeOnClient(client -> screen(client).selected())))
            throw new AssertionError("A click on a mention should select the research it names");
        context.takeScreenshot("research-page-mention-highlight");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
    }

    /**
     * Rite of the Altar is selected: the altar's page offers its scene, and closing the scene returns to the page.
     */
    private static void assertSceneFromPage(ClientGameTestContext context)
    {
        click(context, context.computeOnClient(client -> screen(client).unlockPosition(0)));
        hover(context, new int[] { 2, 2 });
        context.takeScreenshot("research-page-altar");
        click(context, context.computeOnClient(client -> screen(client).actionPosition()));
        context.waitTicks(3);
        if (!context.computeOnClient(client -> screen(client).sceneOpen()))
            throw new AssertionError("The altar's page should open its scene");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
        if (!id("void_altar").equals(context.computeOnClient(client -> screen(client).pageItem())))
            throw new AssertionError("Closing a scene should return to the page it came from");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);

        click(context, context.computeOnClient(client -> screen(client).unlockPosition(0)));
        click(context, context.computeOnClient(client -> screen(client).actionPosition()));
        context.waitTicks(3);
        back(context);
        if (context.computeOnClient(client -> screen(client).sceneOpen()) || !id("void_altar").equals(context.computeOnClient(client -> screen(client).pageItem())))
            throw new AssertionError("The mouse's back button should leave the scene for its page");
        back(context);
        if (context.computeOnClient(client -> screen(client).pageItem()) != null || !ALTAR_RITE.equals(context.computeOnClient(client -> screen(client).selected())))
            throw new AssertionError("The mouse's back button should leave the page for its research");
        back(context);
        if (context.computeOnClient(client -> screen(client).selected()) != null)
            throw new AssertionError("The mouse's back button should let go of the selected research");
        back(context);
        if (!(context.computeOnClient(client -> client.gui.screen()) instanceof ResearchScreen))
            throw new AssertionError("The mouse's back button should not leave the repository");
        click(context, position(context, ALTAR_RITE));
    }

    private static void back(ClientGameTestContext context)
    {
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_4);
        context.waitTicks(3);
    }

    /**
     * What a scene teaches must be something that would work: its forge has to resolve into a beam with the pairs its recipe needs, and its altar has to find its pillars in a real world.
     */
    private static void assertScriptsFollowTheRules(MinecraftServer minecraft)
    {
        var forge = ResearchScenes.of(FORGE).orElseThrow();
        var resolution = BeamResolver.resolve(forge.beam().view(), BlockPos.ZERO);
        if (resolution.error().isPresent())
            throw new AssertionError("The forge scene's structure would not form: " + resolution.error().get());
        if (resolution.segments().isEmpty())
            throw new AssertionError("The forge scene has no beam to show");
        if (resolution.totalPairs() + 1 < 3)
            throw new AssertionError("Gold takes three monolith pairs, the scene shows " + (resolution.totalPairs() + 1));

        var level = minecraft.overworld();
        var altar = ResearchScenes.of(ALTAR_RITE).orElseThrow();
        for (var place : altar.finished())
            level.setBlockAndUpdate(ALTAR.offset(place.pos()), place.state());

        var tile = (VoidAltarTile) level.getBlockEntity(ALTAR);
        tile.searchPillars();
        if (tile.foundPillars() != 8)
            throw new AssertionError("The altar scene's pillars should all count for a real altar, found " + tile.foundPillars());
    }

    private static void captureSteps(ClientGameTestContext context, String name)
    {
        int steps = context.computeOnClient(client -> screen(client).sceneSteps());
        for (int step = 0; step < steps; step++)
        {
            int held = step;
            context.runOnClient(client -> screen(client).holdScene(held));
            context.waitTicks(2);
            context.takeScreenshot("research-scene-" + name + "-step" + step);
        }
    }

    private static void assertControls(ClientGameTestContext context)
    {
        context.getInput().pressKey(InputConstants.KEY_SPACE);
        context.waitTicks(2);
        if (context.computeOnClient(client -> screen(client).scenePaused()))
            throw new AssertionError("Space should start a held scene again");

        click(context, context.computeOnClient(client -> screen(client).scenePosition("mark", 2)));
        if (context.computeOnClient(client -> screen(client).sceneStep()) != 2)
            throw new AssertionError("A click on a mark should jump to its step");

        context.getInput().pressKey(InputConstants.KEY_RIGHT);
        context.waitTicks(2);
        if (context.computeOnClient(client -> screen(client).sceneStep()) != 3)
            throw new AssertionError("The right arrow should go to the next step");

        click(context, context.computeOnClient(client -> screen(client).scenePosition("play", 0)));
        if (!context.computeOnClient(client -> screen(client).scenePaused()))
            throw new AssertionError("The play button should pause");

        click(context, context.computeOnClient(client -> screen(client).scenePosition("expand", 0)));
        if (!context.computeOnClient(client -> screen(client).sceneFullScreen()))
            throw new AssertionError("The button should put the scene on the whole screen");
        hover(context, new int[] { 2, 2 });
        context.takeScreenshot("research-scene-altar-full-427x240");

        int stageBefore = context.computeOnClient(client -> screen(client).sceneStep());
        click(context, context.computeOnClient(client -> screen(client).scenePosition("stage", 0)));
        if (context.computeOnClient(client -> screen(client).sceneStep()) != stageBefore || !context.computeOnClient(client -> screen(client).sceneOpen()))
            throw new AssertionError("Nothing on the stage should answer to a click");

        context.getInput().pressKey(InputConstants.KEY_F);
        context.waitTicks(2);
        if (context.computeOnClient(client -> screen(client).sceneFullScreen()))
            throw new AssertionError("F should bring the scene back to the map");
    }

    private static int[] position(ClientGameTestContext context, Identifier research)
    {
        return context.computeOnClient(client -> screen(client).nodePosition(research));
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
