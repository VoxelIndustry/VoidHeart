package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceStorage;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.essence.GreatRavenousStorageBlock;
import net.voxelindustry.voidheart.common.content.essence.GreatRavenousStorageTile;
import net.voxelindustry.voidheart.common.content.essence.GreatStorageLayout.Part;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import net.voxelindustry.voidheart.common.content.essence.StorageFill;
import net.voxelindustry.voidheart.common.content.essence.StorageParts;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Eight Great Ravenous Storage blocks joining into one cube, relays filling it through its holder alone, each block
 * keeping its share when broken out, the cube joining again, and blocks of two aspects refusing to join.
 */
public class GreatRavenousStorageClientTest implements FabricClientGameTest
{
    private static final int FEET = -60;

    private static final BlockPos CUBE      = new BlockPos(10, FEET, 0);
    private static final BlockPos COLLECTOR = new BlockPos(4, FEET, 0);
    private static final BlockPos RELAY     = new BlockPos(7, FEET, 0);
    private static final BlockPos REFUSED   = new BlockPos(20, FEET, 0);
    private static final BlockPos LOOSE     = new BlockPos(26, FEET, 0);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode creative @a");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            checkJoinsOnLastBlock(context, server);
            checkRelaysFillTheHolder(context, server);
            showCube(context, server);
            checkBreakingKeepsShares(server);
            checkAspectsRefuse(server);
            showLooseAndItems(context, server);
        }
    }

    private static void checkJoinsOnLastBlock(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            for (var part : Part.CORNERS)
            {
                if (part != Part.UPPER_SE)
                    place(level, at(CUBE, part), null);
            }
            for (var part : Part.CORNERS)
            {
                if (part != Part.UPPER_SE)
                    expect(part(level, at(CUBE, part)) == Part.LOOSE, "seven blocks joined without their eighth");
            }
            var inventory = player(minecraft).getInventory();
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(VoidHeartBlocks.GREAT_RAVENOUS_STORAGE));
        });

        // The eighth by a real click, on top of the lower south-east block
        var lowerSe = at(CUBE, Part.LOWER_SE);
        aimFrom(context, server, lowerSe.getX() + 0.5, FEET + 1, lowerSe.getZ() + 3.5, lowerSe.getX() + 0.5, FEET + 1, lowerSe.getZ() + 0.5, 5);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);

        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            for (var part : Part.CORNERS)
                expect(part(level, at(CUBE, part)) == part, "the " + part + " block did not join, it is " + part(level, at(CUBE, part)));
            expect(tile(minecraft, CUBE).isStore(), "the holder of a whole cube is no store");
            expect(!tile(minecraft, at(CUBE, Part.UPPER_NE)).isStore(), "a block other than the holder answers as a store");
            expect(CUBE.equals(StorageParts.holderOf(level.getBlockState(at(CUBE, Part.UPPER_SE)), at(CUBE, Part.UPPER_SE))),
                    "the cube's blocks do not lead to its holder");
            player(minecraft).getInventory().setItem(0, ItemStack.EMPTY);
        });
    }

    private static void checkRelaysFillTheHolder(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            level.setBlockAndUpdate(COLLECTOR, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            if (!(level.getBlockEntity(COLLECTOR) instanceof RavenousCollectorTile collector))
                throw new AssertionError("No collector at " + COLLECTOR);
            collector.storage().attune(EssenceRegistry.FIERY);
            collector.storage().fill(EssenceRegistry.FIERY, RavenousCollectorTile.CAPACITY);
            level.setBlockAndUpdate(RELAY, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
        });

        waitFor(context, server, 200, "the relay carried no essence into the cube", minecraft -> tile(minecraft, CUBE).amountOf(EssenceRegistry.FIERY) > 0);
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            if (!(level.getBlockEntity(RELAY) instanceof RavenousRelayTile relay))
                throw new AssertionError("No relay at " + RELAY);
            expect(relay.linkedReceivers().equals(List.of(CUBE)), "the relay links to " + relay.linkedReceivers() + " rather than the holder alone");

            var holder = tile(minecraft, CUBE);
            holder.receive(EssenceRegistry.FIERY, GreatRavenousStorageTile.CAPACITY / 2);
            var total = holder.amountOf(EssenceRegistry.FIERY);
            var low = Integer.MAX_VALUE;
            var high = 0;
            var sum = 0;
            for (var part : Part.CORNERS)
            {
                var share = tile(minecraft, at(CUBE, part)).share();
                expect(share.type() == EssenceRegistry.FIERY, "the " + part + " share is attuned to " + share.type().identifier());
                low = Math.min(low, share.amount());
                high = Math.max(high, share.amount());
                sum += share.amount();
            }
            expect(sum == total, "the shares add up to " + sum + " for a cube holding " + total);
            expect(high - low <= 1, "the shares are uneven, from " + low + " to " + high);
            var fill = StorageFill.fillOf(total, GreatRavenousStorageTile.CAPACITY);
            for (var part : Part.CORNERS)
                expect(level.getBlockState(at(CUBE, part)).getValue(GreatRavenousStorageBlock.FILL) == fill, "the " + part + " block does not show fill " + fill);

            // A cube holding one aspect takes no other
            expect(holder.receive(EssenceRegistry.STILLNESS, 100) == 0, "a fiery cube took stillness");
            // Spends the collector so the relay stops filling during the shots
            if (level.getBlockEntity(COLLECTOR) instanceof RavenousCollectorTile collector)
                collector.storage().drain(EssenceRegistry.FIERY, RavenousCollectorTile.CAPACITY);
        });
        context.waitTicks(5);
        expectClient(context, "the client does not see the cube's essence", minecraft ->
                minecraft.level.getBlockEntity(CUBE) instanceof GreatRavenousStorageTile tile && tile.held().amount() > GreatRavenousStorageTile.CAPACITY / 3);
    }

    private static void showCube(ClientGameTestContext context, TestServerContext server)
    {
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, CUBE.getX() + 3, FEET + 1, CUBE.getZ() - 1.5, CUBE.getX() + 1, FEET + 1, CUBE.getZ() + 1, 10);
        context.takeScreenshot(TestScreenshotOptions.of("great-storage-half").withSize(1280, 720));
        aimFrom(context, server, CUBE.getX() - 1.5, FEET, CUBE.getZ() + 3.5, CUBE.getX() + 1, FEET + 1, CUBE.getZ() + 1, 5);
        context.takeScreenshot("great-storage-half-north-west");

        server.runOnServer(minecraft -> tile(minecraft, CUBE).receive(EssenceRegistry.FIERY, GreatRavenousStorageTile.CAPACITY));
        context.waitTicks(5);
        aimFrom(context, server, CUBE.getX() + 3, FEET + 1, CUBE.getZ() - 1.5, CUBE.getX() + 1, FEET + 1, CUBE.getZ() + 1, 5);
        context.takeScreenshot(TestScreenshotOptions.of("great-storage-full").withSize(1280, 720));
        aimFrom(context, server, CUBE.getX() + 1, FEET + 3, CUBE.getZ() - 1, CUBE.getX() + 1, FEET + 1, CUBE.getZ() + 1, 5);
        context.takeScreenshot("great-storage-full-above");
        context.getInput().pressKey(InputConstants.KEY_F1);

        // Looking at an upper block outlines the whole cube
        aimFrom(context, server, CUBE.getX() + 1, FEET, CUBE.getZ() - 3, CUBE.getX() + 1.6, FEET + 1.5, CUBE.getZ() + 0.4, 5);
        context.takeScreenshot(TestScreenshotOptions.of("great-storage-selection").withSize(1280, 720));
    }

    private static void checkBreakingKeepsShares(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            var player = player(minecraft);
            var total = tile(minecraft, CUBE).amountOf(EssenceRegistry.FIERY);
            var broken = at(CUBE, Part.UPPER_NE);
            var share = tile(minecraft, broken).share().amount();
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            player.gameMode.destroyBlock(broken);
            player.setGameMode(GameType.CREATIVE);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            expect(level.getBlockState(broken).isAir(), "the block was not broken");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(broken).inflate(3),
                    item -> item.getItem().is(VoidHeartBlocks.GREAT_RAVENOUS_STORAGE.asItem()));
            expect(drops.size() == 1, "breaking one block dropped " + drops.size() + " items");
            var dropped = drops.getFirst().getItem().get(VoidHeartDataComponents.ESSENCE_STORAGE);
            expect(dropped != null && dropped.type() == EssenceRegistry.FIERY && dropped.amount() == share,
                    "the dropped block carries " + (dropped == null ? "nothing" : dropped.amount()) + " rather than its share of " + share);

            var left = 0;
            for (var part : Part.CORNERS)
            {
                if (part == Part.UPPER_NE)
                    continue;
                expect(part(level, at(CUBE, part)) == Part.LOOSE, "the " + part + " block stayed joined to a broken cube");
                expect(level.getBlockState(at(CUBE, part)).getValue(GreatRavenousStorageBlock.FILL) == 0, "a loose block kept its pillar fill");
                left += tile(minecraft, at(CUBE, part)).share().amount();
            }
            expect(left + share == total, "essence was lost: " + (left + share) + " of " + total);
            expect(!tile(minecraft, CUBE).isStore(), "a broken cube still answers as a store");

            // Put back, it joins again with all its essence
            place(level, broken, dropped.copy());
            drops.getFirst().discard();
            for (var part : Part.CORNERS)
                expect(part(level, at(CUBE, part)) == part, "the cube did not join again");
            expect(tile(minecraft, CUBE).amountOf(EssenceRegistry.FIERY) == total, "the rejoined cube holds "
                    + tile(minecraft, CUBE).amountOf(EssenceRegistry.FIERY) + " of " + total);
        });
    }

    private static void checkAspectsRefuse(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            for (var part : Part.CORNERS)
                place(level, at(REFUSED, part), share(part == Part.UPPER_SW ? EssenceRegistry.STILLNESS : EssenceRegistry.FIERY, 1000));
            for (var part : Part.CORNERS)
                expect(part(level, at(REFUSED, part)) == Part.LOOSE, "blocks of two aspects joined");

            // Emptied, the odd block has no say and the cube joins
            level.removeBlock(at(REFUSED, Part.UPPER_SW), false);
            place(level, at(REFUSED, Part.UPPER_SW), null);
            for (var part : Part.CORNERS)
                expect(part(level, at(REFUSED, part)) == part, "blocks of one aspect and an empty one did not join");
            expect(tile(minecraft, REFUSED).amountOf(EssenceRegistry.FIERY) == 7000, "the joined cube lost essence");
        });
    }

    private static void showLooseAndItems(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            for (var part : Part.CORNERS)
            {
                if (part != Part.UPPER_NW)
                    place(level, at(LOOSE, part), null);
            }
            var inventory = player(minecraft).getInventory();
            inventory.setItem(0, new ItemStack(VoidHeartBlocks.GREAT_RAVENOUS_STORAGE));
            inventory.setItem(1, share(EssenceRegistry.CONTRAVOID, GreatRavenousStorageTile.SHARE_CAPACITY));
            inventory.setSelectedSlot(0);
        });
        aimFrom(context, server, LOOSE.getX() + 3.5, FEET + 2, LOOSE.getZ() - 2.5, LOOSE.getX() + 1, FEET + 1, LOOSE.getZ() + 1, 10);
        context.takeScreenshot("great-storage-loose-in-hand");
        context.getInput().pressKey(InputConstants.KEY_E);
        context.waitTicks(10);
        context.takeScreenshot("great-storage-inventory");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(5);
        server.runOnServer(minecraft -> player(minecraft).getInventory().clearContent());
    }

    private static ItemStack share(EssenceType type, int amount)
    {
        var stored = new EssenceStorage(type, GreatRavenousStorageTile.SHARE_CAPACITY);
        stored.fill(type, amount);
        var stack = new ItemStack(VoidHeartBlocks.GREAT_RAVENOUS_STORAGE);
        stack.set(VoidHeartDataComponents.ESSENCE_STORAGE, stored);
        return stack;
    }

    private static void place(ServerLevel level, BlockPos pos, @Nullable Object stored)
    {
        var block = VoidHeartBlocks.GREAT_RAVENOUS_STORAGE;
        var stack = stored instanceof ItemStack item ? item : new ItemStack(block);
        if (stored instanceof EssenceStorage storage)
            stack.set(VoidHeartDataComponents.ESSENCE_STORAGE, storage);
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        block.setPlacedBy(level, pos, block.defaultBlockState(), null, stack);
    }

    private static BlockPos at(BlockPos origin, Part part)
    {
        return origin.offset(part.dx(), part.dy(), part.dz());
    }

    private static @Nullable Part part(ServerLevel level, BlockPos pos)
    {
        var state = level.getBlockState(pos);
        return state.is(VoidHeartBlocks.GREAT_RAVENOUS_STORAGE) ? state.getValue(GreatRavenousStorageBlock.PART) : null;
    }

    private static GreatRavenousStorageTile tile(MinecraftServer minecraft, BlockPos pos)
    {
        if (level(minecraft).getBlockEntity(pos) instanceof GreatRavenousStorageTile tile)
            return tile;
        throw new AssertionError("No great storage block at " + pos);
    }

    private static ServerLevel level(MinecraftServer minecraft)
    {
        return minecraft.overworld();
    }
}
