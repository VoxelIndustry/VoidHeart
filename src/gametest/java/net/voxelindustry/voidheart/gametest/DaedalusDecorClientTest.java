package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The trim, lamps, panels, rugs and bookshelves the Daedalus is furnished with: what they drop, how they look side by side by day and night, and as items.
 */
public class DaedalusDecorClientTest implements FabricClientGameTest
{
    private static final int FLOOR_Y = -55;
    private static final int CHUNK_TIMEOUT_TICKS = 600;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode spectator @a");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            checkBlocks(server);

            server.runCommand("tp @a 6 " + (FLOOR_Y + 2) + " 7 180 10");
            context.waitTicks(20);
            world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
            buildShowcase(server);
            context.waitTicks(20);

            context.getInput().pressKey(InputConstants.KEY_F1);
            shoot(context, server, "daedalus-decor-wall", 5.5, FLOOR_Y + 1.5, 6.5, 180, 8);
            shoot(context, server, "daedalus-decor-wall-close", 2.5, FLOOR_Y + 1.5, 3, 180, 5);
            shoot(context, server, "daedalus-decor-wall-grazing", 12, FLOOR_Y + 1.5, 1, 120, 5);
            shoot(context, server, "daedalus-decor-floor", 5.5, FLOOR_Y + 4, 6, 180, 60);
            server.runCommand("time set midnight");
            shoot(context, server, "daedalus-decor-wall-night", 5.5, FLOOR_Y + 1.5, 6.5, 180, 8);
            shoot(context, server, "daedalus-decor-floor-night", 5.5, FLOOR_Y + 4, 6, 180, 60);
            server.runCommand("time set noon");
            context.getInput().pressKey(InputConstants.KEY_F1);

            checkItems(context, server);
        }
    }

    private static List<Block> decor()
    {
        return List.of(VoidHeartBlocks.BRONZE_INLAID_VOIDSTONE, VoidHeartBlocks.IRON_INLAID_VOIDSTONE,
                VoidHeartBlocks.VOIDSTONE_ENGRAVED_GEAR, VoidHeartBlocks.VOIDSTONE_ENGRAVED_EYE,
                VoidHeartBlocks.CAGED_LAMP_AMBER, VoidHeartBlocks.CAGED_LAMP_TEAL, VoidHeartBlocks.CAGED_LAMP_PALE,
                VoidHeartBlocks.VOIDSTONE_BOOKSHELF, VoidHeartBlocks.RUINED_VOIDSTONE_BOOKSHELF,
                VoidHeartBlocks.UMBRAL_RUG, VoidHeartBlocks.VIOLET_RUG, VoidHeartBlocks.TEAL_RUG);
    }

    private static void checkBlocks(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var pickaxe = new ItemStack(Items.IRON_PICKAXE);
            for (var block : decor())
            {
                var drops = Block.getDrops(block.defaultBlockState(), level, BlockPos.ZERO, null, null, pickaxe);
                if (block == VoidHeartBlocks.VOIDSTONE_BOOKSHELF || block == VoidHeartBlocks.RUINED_VOIDSTONE_BOOKSHELF)
                {
                    int books = block == VoidHeartBlocks.VOIDSTONE_BOOKSHELF ? 3 : 1;
                    if (drops.size() != 1 || !drops.getFirst().is(Items.BOOK) || drops.getFirst().getCount() != books)
                        throw new AssertionError(block + " should drop " + books + " books without silk touch, it drops " + drops);
                    if (!block.defaultBlockState().is(BlockTags.ENCHANTMENT_POWER_PROVIDER))
                        throw new AssertionError(block + " should power an enchanting table");
                }
                else if (drops.size() != 1 || !drops.getFirst().is(block.asItem()))
                    throw new AssertionError(block + " should drop itself, it drops " + drops);
            }
            for (var lamp : List.of(VoidHeartBlocks.CAGED_LAMP_AMBER, VoidHeartBlocks.CAGED_LAMP_TEAL, VoidHeartBlocks.CAGED_LAMP_PALE))
                if (lamp.defaultBlockState().getLightEmission() != 15)
                    throw new AssertionError(lamp + " should give light 15, it gives " + lamp.defaultBlockState().getLightEmission());
            for (var block : decor())
            {
                var state = block.defaultBlockState();
                boolean rug = state.is(VoidHeartBlocks.UMBRAL_RUG) || state.is(VoidHeartBlocks.VIOLET_RUG) || state.is(VoidHeartBlocks.TEAL_RUG);
                if (!rug && (!state.requiresCorrectToolForDrops() || !pickaxe.isCorrectToolForDrops(state)))
                    throw new AssertionError(block + " should need a pickaxe to drop");
            }
        });
    }

    /**
     * A wall of each trim, panel, lamp and shelf two high over a floor laid with each rug and inlay, as a room would put them.
     */
    private static void buildShowcase(TestServerContext server)
    {
        int y = FLOOR_Y;
        server.runCommand("fill -2 " + (y - 1) + " -3 14 " + (y - 1) + " 8 voidheart:voidstone_tile");
        server.runCommand("fill -2 " + y + " -3 14 " + (y + 4) + " -2 voidheart:voidstone_bricks");
        server.runCommand("fill -2 " + y + " -1 14 " + (y + 4) + " 8 air");
        var wall = decor().subList(0, 9);
        for (int i = 0; i < wall.size(); i++)
        {
            var id = BuiltInRegistries.BLOCK.getKey(wall.get(i));
            server.runCommand("fill " + i + " " + y + " -2 " + i + " " + (y + 1) + " -2 " + id);
        }
        server.runCommand("fill 9 " + y + " -2 11 " + (y + 1) + " -2 voidheart:voidstone_bookshelf");
        server.runCommand("setblock 10 " + y + " -2 voidheart:ruined_voidstone_bookshelf");

        // Rug runners in front of the wall, each between inlaid borders
        String[] rugs = { "voidheart:umbral_rug", "voidheart:violet_rug", "voidheart:teal_rug" };
        for (int r = 0; r < rugs.length; r++)
        {
            int x0 = r * 4;
            server.runCommand("fill " + x0 + " " + y + " 0 " + (x0 + 2) + " " + y + " 3 " + rugs[r]);
            server.runCommand("fill " + (x0 + 3) + " " + (y - 1) + " 0 " + (x0 + 3) + " " + (y - 1) + " 3 voidheart:" + (r % 2 == 0 ? "bronze" : "iron") + "_inlaid_voidstone");
        }
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            for (int i = 0; i < wall.size(); i++)
                if (!level.getBlockState(new BlockPos(i, y, -2)).is(wall.get(i)))
                    throw new AssertionError(wall.get(i) + " was not placed, found " + level.getBlockState(new BlockPos(i, y, -2)));
            if (!level.getBlockState(new BlockPos(1, y, 1)).is(VoidHeartBlocks.UMBRAL_RUG))
                throw new AssertionError("The umbral rug was not laid, found " + level.getBlockState(new BlockPos(1, y, 1)));
        });
    }

    private static void shoot(ClientGameTestContext context, TestServerContext server, String name, double x, double y, double z, float yaw, float pitch)
    {
        server.runCommand("tp @a " + x + " " + y + " " + z + " " + yaw + " " + pitch);
        context.waitTicks(15);
        context.takeScreenshot(name);
    }

    private static void checkItems(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode creative @a");
        server.runCommand("tp @a 6 " + (FLOOR_Y + 1) + " 5 180 20");
        server.runOnServer(minecraft ->
        {
            var player = minecraft.getPlayerList().getPlayers().getFirst();
            player.getInventory().clearContent();
            for (var block : decor())
                player.getInventory().add(new ItemStack(block));
            player.getInventory().setSelectedSlot(7);
        });
        context.waitTicks(20);
        context.takeScreenshot("daedalus-decor-in-hand");
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitTicks(10);
        context.takeScreenshot("daedalus-decor-inventory");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(5);
        server.runOnServer(minecraft -> minecraft.getPlayerList().getPlayers().getFirst().getInventory().clearContent());
    }
}
