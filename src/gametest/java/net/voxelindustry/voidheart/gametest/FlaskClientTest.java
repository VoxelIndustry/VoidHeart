package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.flask.PlacedFlasksBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.athanor.AthanorTile;
import net.voxelindustry.voidheart.common.content.flask.PlacedFlasksTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;

/**
 * Flasks set down by sneaking with them in hand, up to four on a block, taken back one at a time and dropped when their block goes. Their 3D models
 * are captured standing, in the athanor's mouth, in hand, on the ground and in a frame.
 */
public class FlaskClientTest implements FabricClientGameTest
{
    private static final int      GROUND_Y = -60;
    private static final BlockPos CLUSTER  = new BlockPos(0, GROUND_Y, 0);
    private static final BlockPos LONE     = new BlockPos(3, GROUND_Y, 0);
    private static final BlockPos PAIR     = new BlockPos(5, GROUND_Y, 0);
    private static final BlockPos FULL     = new BlockPos(7, GROUND_Y, 0);
    private static final BlockPos ATHANOR  = new BlockPos(-4, GROUND_Y, 0);
    private static final BlockPos FRAME    = new BlockPos(10, GROUND_Y, 0);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);

            // Four flasks set down one by one with real sneaking clicks on the ground and then on the flasks
            standAndAim(context, server, 0.5, -1.6, CLUSTER.getX() + 0.5, GROUND_Y, CLUSTER.getZ() + 0.5);
            Item[] placed = {VoidHeartItems.SEARING_OIL, VoidHeartItems.BULWARK_ELIXIR, VoidHeartItems.SEETHING_STEEL_FLASK, Items.GLASS_BOTTLE};
            for (int i = 0; i < placed.length; i++)
            {
                sneakUse(context, server, placed[i]);
                int expected = i + 1;
                var flasks = flasks(server, CLUSTER);
                if (flasks.size() != expected || !flasks.getLast().is(placed[i]))
                    throw new AssertionError("Expected " + expected + " flasks ending with " + placed[i] + ", got " + flasks);
            }

            // A fifth clicked on top finds no room, since nothing stands on flasks, but one clicked on a side goes beside them
            sneakUse(context, server, VoidHeartItems.ARCANE_OIL);
            server.runOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                if (flasks(minecraft, CLUSTER).size() != 4 || !level.getBlockState(CLUSTER.above()).isAir())
                    throw new AssertionError("Expected the full block to keep four flasks and nothing on top, got " + flasks(minecraft, CLUSTER)
                                             + " under " + level.getBlockState(CLUSTER.above()));

                var player = player(minecraft);
                var side = new BlockHitResult(Vec3.atCenterOf(CLUSTER).add(0, -0.2, -0.375), Direction.NORTH, CLUSTER, false);
                player.setShiftKeyDown(true);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.ARCANE_OIL));
                PlacedFlasksBlock.place(player, level, InteractionHand.MAIN_HAND, side);
                player.setShiftKeyDown(false);
                var beside = flasks(minecraft, CLUSTER.north());
                if (beside.size() != 1 || !beside.getFirst().is(VoidHeartItems.ARCANE_OIL))
                    throw new AssertionError("Expected the fifth flask beside the full block, got " + beside);
            });

            // A plain use takes back the last one
            standAndAim(context, server, 0.5, -1.6, CLUSTER.getX() + 0.5, GROUND_Y + 0.25, CLUSTER.getZ() + 0.5);
            server.runCommand("item replace entity @a weapon.mainhand with air");
            context.getInput().pressKey(options -> options.keyUse);
            context.waitTicks(5);
            server.runOnServer(minecraft ->
            {
                var flasks = flasks(minecraft, CLUSTER);
                if (flasks.size() != 3 || flasks.stream().anyMatch(flask -> flask.is(Items.GLASS_BOTTLE)))
                    throw new AssertionError("Expected the glass bottle taken back, leaving three, got " + flasks);
                if (!player(minecraft).getInventory().contains(new ItemStack(Items.GLASS_BOTTLE)))
                    throw new AssertionError("Expected the glass bottle back in the inventory");
            });

            // The flasks survive a save and load
            server.runOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                var tile = level.getBlockEntity(CLUSTER);
                var tag = tile.saveWithFullMetadata(level.registryAccess());
                if (!(BlockEntity.loadStatic(CLUSTER, tile.getBlockState(), tag, level.registryAccess()) instanceof PlacedFlasksTile loaded)
                        || loaded.getFlasks().size() != 3 || !loaded.getFlasks().getFirst().is(VoidHeartItems.SEARING_OIL))
                    throw new AssertionError("Expected the three flasks after a reload, saved " + tag);
            });

            server.runOnServer(FlaskClientTest::buildDisplays);
            context.waitTicks(10);

            standAndAim(context, server, 4, -2.6, 4, GROUND_Y + 0.3, 0.5);
            context.takeScreenshot("flasks-row");
            for (var pos : new BlockPos[]{CLUSTER, LONE, PAIR, FULL})
            {
                standAndAim(context, server, pos.getX() + 0.5, pos.getZ() - 0.9, pos.getX() + 0.5, GROUND_Y + 0.25, pos.getZ() + 0.5);
                context.takeScreenshot(TestScreenshotOptions.of("flasks-close-" + flasks(server, pos).size() + "-front").withSize(1280, 720));
                standAndAim(context, server, pos.getX() + 1.4, pos.getZ() + 1.4, pos.getX() + 0.5, GROUND_Y + 0.25, pos.getZ() + 0.5);
                context.takeScreenshot(TestScreenshotOptions.of("flasks-close-" + flasks(server, pos).size() + "-back").withSize(1280, 720));
            }

            // In the athanor's mouth, the vanilla glass bottle and then an oil
            standAndAim(context, server, ATHANOR.getX() + 0.5, ATHANOR.getZ() - 1.3, ATHANOR.getX() + 0.5, GROUND_Y + 7 / 16D, ATHANOR.getZ() + 0.5);
            context.takeScreenshot(TestScreenshotOptions.of("flasks-athanor-glass-bottle").withSize(1280, 720));
            server.runOnServer(minecraft -> athanor(minecraft).setStack(null, new ItemStack(VoidHeartItems.KERNACIAN_OIL)));
            context.waitTicks(5);
            context.takeScreenshot(TestScreenshotOptions.of("flasks-athanor-oil").withSize(1280, 720));

            // On the ground and in a frame
            standAndAim(context, server, FRAME.getX() + 0.5, FRAME.getZ() - 2.2, FRAME.getX() + 1, GROUND_Y + 0.4, FRAME.getZ() + 0.5);
            context.waitTicks(10);
            context.takeScreenshot(TestScreenshotOptions.of("flasks-ground-and-frame").withSize(1280, 720));

            // In hand, first and third person
            context.getInput().pressKey(InputConstants.KEY_F1);
            for (var item : new String[]{"insight_elixir", "searing_oil", "ravenous_gold_flask"})
            {
                server.runCommand("item replace entity @a weapon.mainhand with voidheart:" + item);
                context.waitTicks(15);
                context.takeScreenshot("flasks-hand-" + item);
            }
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(10);
            context.takeScreenshot("flasks-hand-third-person");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);

            // The flasks drop when their block breaks or loses what it stands on
            server.runOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                level.destroyBlock(CLUSTER, true);
                level.setBlockAndUpdate(LONE.below(), Blocks.AIR.defaultBlockState());
            });
            context.waitTicks(5);
            server.runOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                if (!level.getBlockState(LONE).isAir())
                    throw new AssertionError("Expected the lone flask to fall with its support, found " + level.getBlockState(LONE));
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(CLUSTER.getX() - 1, GROUND_Y - 3, -1, LONE.getX() + 2, GROUND_Y + 2, 2))
                        .stream().map(ItemEntity::getItem).toList();
                for (var expected : new Item[]{VoidHeartItems.SEARING_OIL, VoidHeartItems.BULWARK_ELIXIR, VoidHeartItems.SEETHING_STEEL_FLASK, VoidHeartItems.KERNACIAN_OIL})
                {
                    if (drops.stream().noneMatch(drop -> drop.is(expected)))
                        throw new AssertionError("Expected " + expected + " among the drops, got " + drops);
                }
            });
        }
    }

    private static void buildDisplays(MinecraftServer minecraft)
    {
        var level = minecraft.overworld();
        place(minecraft, LONE, VoidHeartItems.KERNACIAN_OIL);
        place(minecraft, PAIR, Items.GLASS_BOTTLE, VoidHeartItems.INSIGHT_ELIXIR);
        place(minecraft, FULL, VoidHeartItems.EMBER_DRAUGHT, VoidHeartItems.STILLWATER, VoidHeartItems.CONCUSSIVE_OIL, VoidHeartItems.RAVENOUS_GOLD_FLASK);

        level.setBlockAndUpdate(ATHANOR, VoidHeartBlocks.ATHANOR.defaultBlockState());
        athanor(minecraft).setStack(null, new ItemStack(Items.GLASS_BOTTLE));

        level.setBlockAndUpdate(FRAME.south(), Blocks.STONE.defaultBlockState());
        minecraft.getCommands().performPrefixedCommand(minecraft.createCommandSourceStack(),
                "summon item_frame " + FRAME.getX() + " " + FRAME.getY() + " " + FRAME.getZ() + " {Facing:2b,Fixed:1b,Item:{id:\"voidheart:bulwark_elixir\",count:1}}");
        minecraft.getCommands().performPrefixedCommand(minecraft.createCommandSourceStack(),
                "summon item " + (FRAME.getX() + 1.5) + " " + FRAME.getY() + " " + (FRAME.getZ() + 0.5) + " {Item:{id:\"voidheart:arcane_oil\",count:1},PickupDelay:32767}");
    }

    private static void place(MinecraftServer minecraft, BlockPos pos, Item... items)
    {
        var level = minecraft.overworld();
        level.setBlockAndUpdate(pos, VoidHeartBlocks.PLACED_FLASKS.defaultBlockState());
        var tile = (PlacedFlasksTile) level.getBlockEntity(pos);
        for (var item : items)
            tile.add(new ItemStack(item));
    }

    private static void sneakUse(ClientGameTestContext context, TestServerContext server, Item item)
    {
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item)));
        context.getInput().holdKey(options -> options.keyShift);
        context.waitTicks(3);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(3);
        context.getInput().releaseKey(options -> options.keyShift);
        context.waitTicks(3);
    }

    /**
     * Stands the player on the ground there and turns their eyes toward the point.
     */
    private static void standAndAim(ClientGameTestContext context, TestServerContext server, double feetX, double feetZ, double x, double y, double z)
    {
        double dx = x - feetX;
        double dy = y - (GROUND_Y + 1.62);
        double dz = z - feetZ;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        server.runCommand("tp @a " + feetX + " " + GROUND_Y + " " + feetZ + " " + yaw + " " + pitch);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        });
        context.waitTicks(8);
    }

    private static List<ItemStack> flasks(TestServerContext server, BlockPos pos)
    {
        return server.computeOnServer(minecraft -> flasks(minecraft, pos));
    }

    private static List<ItemStack> flasks(MinecraftServer minecraft, BlockPos pos)
    {
        return minecraft.overworld().getBlockEntity(pos) instanceof PlacedFlasksTile tile ? List.copyOf(tile.getFlasks()) : List.of();
    }

    private static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
    }

    private static AthanorTile athanor(MinecraftServer minecraft)
    {
        return (AthanorTile) minecraft.overworld().getBlockEntity(ATHANOR);
    }
}
