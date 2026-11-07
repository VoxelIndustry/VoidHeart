package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The void door closed and open, the mending altar unlit and lit, what each drops, and the door, altar and void key as items.
 */
public class VoidDoorAndMendingAltarClientTest implements FabricClientGameTest
{
    private static final int FLOOR_Y = -60;

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

            checkDrops(server);

            int y = FLOOR_Y;
            server.runCommand("fill -3 " + y + " -2 8 " + (y + 4) + " 4 air");
            server.runCommand("fill -3 " + (y - 1) + " -2 8 " + (y - 1) + " 4 voidheart:voidstone_tile");
            server.runCommand("setblock 0 " + y + " 0 voidheart:void_door[facing=south,half=lower]");
            server.runCommand("setblock 0 " + (y + 1) + " 0 voidheart:void_door[facing=south,half=upper]");
            server.runCommand("setblock 2 " + y + " 0 voidheart:void_door[facing=south,half=lower,open=true]");
            server.runCommand("setblock 2 " + (y + 1) + " 0 voidheart:void_door[facing=south,half=upper,open=true]");
            server.runCommand("setblock 4 " + y + " 0 voidheart:mending_altar[lit=false]");
            server.runCommand("setblock 6 " + y + " 0 voidheart:mending_altar[lit=true]");
            context.waitTicks(10);
            server.runOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                expect(level.getBlockState(new BlockPos(0, y + 1, 0)).is(VoidHeartBlocks.VOID_DOOR), "The void door's upper half should be placed");
                expect(level.getBlockState(new BlockPos(6, y, 0)).getValue(BlockStateProperties.LIT), "The second mending altar should be lit");
            });

            context.getInput().pressKey(InputConstants.KEY_F1);
            server.runCommand("tp @a 3 " + (y + 0.6) + " 4.5 180 8");
            context.waitTicks(10);
            world.getConnection().waitForChunksRender(false, 600);
            context.takeScreenshot("void-door-mending-altar-front");
            server.runCommand("tp @a 5 " + (y + 0.8) + " 2 180 35");
            context.waitTicks(10);
            context.takeScreenshot("mending-altar-close");
            server.runCommand("tp @a 1 " + (y + 0.6) + " -2.5 0 5");
            context.waitTicks(10);
            context.takeScreenshot("void-door-back");
            context.getInput().pressKey(InputConstants.KEY_F1);

            checkItems(context, server);
        }
    }

    private static void checkDrops(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var pickaxe = new ItemStack(Items.IRON_PICKAXE);
            var lower = VoidHeartBlocks.VOID_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            var doorDrops = Block.getDrops(lower, level, BlockPos.ZERO, null, null, ItemStack.EMPTY);
            expect(doorDrops.size() == 1 && doorDrops.getFirst().is(VoidHeartBlocks.VOID_DOOR.asItem()), "The void door should drop itself, it drops " + doorDrops);
            var upperDrops = Block.getDrops(lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), level, BlockPos.ZERO, null, null, ItemStack.EMPTY);
            expect(upperDrops.isEmpty(), "The void door's upper half should drop nothing, it drops " + upperDrops);
            var altarDrops = Block.getDrops(VoidHeartBlocks.MENDING_ALTAR.defaultBlockState(), level, BlockPos.ZERO, null, null, pickaxe);
            expect(altarDrops.size() == 1 && altarDrops.getFirst().is(VoidHeartBlocks.MENDING_ALTAR.asItem()), "The mending altar should drop itself, it drops " + altarDrops);
        });
    }

    private static void checkItems(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode creative @a");
        server.runCommand("tp @a 3 " + FLOOR_Y + " 4.5 180 20");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(VoidHeartBlocks.VOID_DOOR));
            player.getInventory().add(new ItemStack(VoidHeartBlocks.MENDING_ALTAR));
            player.getInventory().add(new ItemStack(VoidHeartItems.VOID_KEY));
            player.getInventory().setSelectedSlot(1);
        });
        context.waitTicks(20);
        context.takeScreenshot("void-door-mending-altar-in-hand");
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitTicks(10);
        context.takeScreenshot("void-door-mending-altar-inventory");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(5);
        server.runOnServer(minecraft -> player(minecraft).getInventory().clearContent());
    }
}
