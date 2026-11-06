package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The Seething Steel, Covetous Copper and Listless Lead families: their recipes, drops and translations, with captures
 * of each placed family, the steel's animation, and the ingots in hand and in the inventory.
 */
public class SinMetalsClientTest implements FabricClientGameTest
{
    private static final List<String> METALS = List.of("seething_steel", "covetous_copper", "listless_lead");
    private static final int GROUND_Y = -60;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode creative @a");

            server.runOnServer(SinMetalsClientTest::assertRecipes);
            server.runOnServer(SinMetalsClientTest::assertDrops);
            context.runOnClient(minecraft -> assertTranslations());

            for (int metal = 0; metal < METALS.size(); metal++)
                buildFamily(server, METALS.get(metal), metal * 8);
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);

            for (int metal = 0; metal < METALS.size(); metal++)
            {
                aimFrom(context, server, 0.5, GROUND_Y + 1, metal * 8 - 4.5, 0.5, GROUND_Y + 1, metal * 8 + 0.5, 5);
                context.takeScreenshot(TestScreenshotOptions.of("metal-" + METALS.get(metal)).withSize(1280, 720));
            }

            // Close enough to see the veins, a few moments apart across the loop
            aimFrom(context, server, -0.5, GROUND_Y + 1, -2.5, -0.5, GROUND_Y + 1.5, 0.5, 5);
            for (int moment = 0; moment < 4; moment++)
            {
                context.takeScreenshot(TestScreenshotOptions.of("metal-steel-moment-" + moment).withSize(1280, 720));
                context.waitTicks(16);
            }

            // The glow, with the lead beside it as the unlit comparison
            server.runCommand("time set midnight");
            context.waitTicks(20);
            context.takeScreenshot(TestScreenshotOptions.of("metal-steel-night").withSize(1280, 720));
            aimFrom(context, server, 0.5, GROUND_Y + 1, 2 * 8 - 4.5, 0.5, GROUND_Y + 1, 2 * 8 + 0.5, 20);
            context.takeScreenshot(TestScreenshotOptions.of("metal-lead-night").withSize(1280, 720));
            server.runCommand("time set noon");
            context.waitTicks(5);
            context.getInput().pressKey(InputConstants.KEY_F1);

            // The hand and the inventory, one ingot of each in the hotbar
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0.5 " + GROUND_Y + " -4 0 0");
            for (var metal : METALS)
                server.runCommand("give @a " + MODID + ":" + metal + "_ingot");
            context.waitTicks(20);
            context.takeScreenshot(TestScreenshotOptions.of("metal-ingot-in-hand").withSize(1280, 720));
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitTicks(5);
            context.takeScreenshot(TestScreenshotOptions.of("metal-ingots-in-inventory").withSize(1280, 720));
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(2);
        }
    }

    private static void buildFamily(TestServerContext server, String metal, int z)
    {
        var y = GROUND_Y;
        // A panel shows how neighbouring blocks read together, the shapes stand in front of it
        for (int x = -3; x <= 3; x++)
            for (int up = 0; up < 3; up++)
                server.runCommand("setblock " + x + " " + (y + up) + " " + (z + 2) + " " + MODID + ":" + (x < 0 ? metal + "_block" : "cut_" + metal));
        server.runCommand("setblock -2 " + y + " " + z + " " + MODID + ":" + metal + "_block");
        server.runCommand("setblock -1 " + y + " " + z + " " + MODID + ":cut_" + metal);
        server.runCommand("setblock 0 " + y + " " + z + " " + MODID + ":cut_" + metal + "_stairs");
        server.runCommand("setblock 1 " + y + " " + z + " " + MODID + ":cut_" + metal + "_slab");
        server.runCommand("setblock 2 " + y + " " + z + " " + MODID + ":cut_" + metal + "_wall");
    }

    private static void assertRecipes(MinecraftServer minecraft)
    {
        var fromIron = VoidHeartRecipes.SHATTER_FORGE_CATEGORY.findRecipes(new ItemStack(Items.IRON_INGOT)).toList();
        assertRecipe(fromIron, VoidHeartItems.ARROGANT_IRON_INGOT, 0, 0);
        assertRecipe(fromIron, VoidHeartItems.SEETHING_STEEL_INGOT, 2, 0);
        assertRecipe(fromIron, VoidHeartItems.LISTLESS_LEAD_INGOT, 0, 2);

        var fromCopper = VoidHeartRecipes.SHATTER_FORGE_CATEGORY.findRecipes(new ItemStack(Items.COPPER_INGOT)).toList();
        assertRecipe(fromCopper, VoidHeartItems.COVETOUS_COPPER_INGOT, 0, 0);
    }

    private static void assertRecipe(List<ShatterForgeRecipe> recipes, Item output, int fiery, int stillness)
    {
        var recipe = recipes.stream()
                .filter(candidate -> candidate.getOutput().is(output))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a forge recipe for " + output));
        if (recipe.getAspects().getOrDefault(EssenceRegistry.FIERY, 0) != fiery || recipe.getAspects().getOrDefault(EssenceRegistry.STILLNESS, 0) != stillness)
            throw new AssertionError("Unexpected aspects for " + output + ": " + recipe.getAspects());
    }

    private static void assertDrops(MinecraftServer minecraft)
    {
        var level = minecraft.overworld();
        var pos = new BlockPos(0, GROUND_Y + 40, 0);
        for (var metal : METALS)
        {
            for (var id : List.of(metal + "_block", "cut_" + metal, "cut_" + metal + "_stairs", "cut_" + metal + "_slab", "cut_" + metal + "_wall"))
            {
                var block = block(id);
                var light = metal.equals("seething_steel") ? 4 : 0;
                if (block.defaultBlockState().getLightEmission() != light)
                    throw new AssertionError("Expected " + id + " to emit light " + light + ", got " + block.defaultBlockState().getLightEmission());

                var drops = Block.getDrops(block.defaultBlockState(), level, pos, null);
                if (drops.size() != 1 || !drops.getFirst().is(block.asItem()) || drops.getFirst().getCount() != 1)
                    throw new AssertionError("Expected " + id + " to drop itself, got " + drops);
            }

            var slab = block("cut_" + metal + "_slab");
            var drops = Block.getDrops(slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), level, pos, null);
            if (drops.size() != 1 || drops.getFirst().getCount() != 2)
                throw new AssertionError("Expected a double slab of " + metal + " to drop two, got " + drops);
        }
    }

    private static void assertTranslations()
    {
        for (var metal : METALS)
        {
            for (var key : List.of("item." + MODID + "." + metal + "_ingot",
                    "block." + MODID + "." + metal + "_block",
                    "block." + MODID + ".cut_" + metal,
                    "block." + MODID + ".cut_" + metal + "_stairs",
                    "block." + MODID + ".cut_" + metal + "_slab",
                    "block." + MODID + ".cut_" + metal + "_wall"))
            {
                if (!Language.getInstance().has(key))
                    throw new AssertionError("Missing translation " + key);
            }
        }
    }

    private static Block block(String id)
    {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(MODID, id));
    }
}
