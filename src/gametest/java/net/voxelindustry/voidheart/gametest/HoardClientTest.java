package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.client.screen.HoardScreen;
import net.voxelindustry.voidheart.common.content.crystal.BundleSize;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.content.hoard.FoldTile;
import net.voxelindustry.voidheart.common.content.hoard.HoardBalance;
import net.voxelindustry.voidheart.common.content.hoard.HoardCofferTile;
import net.voxelindustry.voidheart.common.content.hoard.HoardContent;
import net.voxelindustry.voidheart.common.content.hoard.HoardMenu;
import net.voxelindustry.voidheart.common.content.hoard.PocketHoard;
import net.voxelindustry.voidheart.common.energy.creative.CreativeEnergyContent;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Items kept in the pocket's hoard: a coffer gives room only inside the pocket, hoppers fill the hoard through a Fold in the overworld and an attuned
 * Fold gives its item back, an unpowered Fold refuses, a hoard over its room after a coffer breaks keeps everything, the hoard view lists it and gives
 * a stack, and the hoard outlives a save.
 */
public class HoardClientTest implements FabricClientGameTest
{
    private static final BlockPos FOLD         = new BlockPos(4, FLAT_FEET + 1, 8);
    private static final BlockPos HOPPER_IN    = FOLD.above();
    private static final BlockPos HOPPER_OUT   = FOLD.below();
    private static final BlockPos WELL         = FOLD.offset(4, 0, 0);
    private static final BlockPos OUTER_COFFER = FOLD.offset(-3, -1, 0);

    private static final ItemVariant COBBLE = ItemVariant.of(Items.COBBLESTONE);
    private static final ItemVariant DIRT   = ItemVariant.of(Items.DIRT);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        TestWorldSave save;
        long kept;
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode creative @a");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            server.runOnServer(minecraft -> VoidPocketState.getVoidPocketState(minecraft.overworld())
                    .createPocket(minecraft.getLevel(VoidHeart.VOID_WORLD_KEY), player(minecraft).getUUID()));

            checkCofferRoom(server);
            checkHoppersThroughFold(context, server);
            checkUnpoweredFoldRefuses(context, server);
            checkInserterThroughFold(context, server);
            context.getInput().pressKey(InputConstants.KEY_F1);
            aimFrom(context, server, FOLD.getX() + 3.5, FLAT_FEET + 1, FOLD.getZ() - 3.5, FOLD.getX() - 0.5, FLAT_FEET + 0.8, FOLD.getZ() + 0.5, 5);
            context.takeScreenshot("hoard-fold-and-coffer");
            context.getInput().pressKey(InputConstants.KEY_F1);
            checkHoardView(context, server);
            checkCofferPool(context, server);
            kept = server.computeOnServer(minecraft -> hoard(minecraft).count(COBBLE));
            checkOverCapacity(server);
        }

        try (var world = save.open())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            server.runOnServer(minecraft ->
            {
                var hoard = hoard(minecraft);
                expect(hoard.count(COBBLE) == kept, "the hoard holds " + hoard.count(COBBLE) + " cobblestone after reopening, expected " + kept);
                expect(hoard.capacity() == 0, "the broken coffer's room came back after reopening: " + hoard.capacity());
            });
        }
    }

    private static PocketHoard.Hoard hoard(MinecraftServer minecraft)
    {
        return PocketHoard.get(minecraft).of(player(minecraft).getUUID());
    }

    /**
     * The pocket's coffer, its hoard half full, shows a pool of rubrum in its lid's socket.
     */
    private static void checkCofferPool(ClientGameTestContext context, TestServerContext server)
    {
        var added = server.computeOnServer(minecraft ->
        {
            var hoard = hoard(minecraft);
            return hoard.insert(DIRT, hoard.capacity() / 2 - hoard.total());
        });
        var coffer = server.computeOnServer(HoardClientTest::pocketCoffer);
        server.runCommand("gamemode spectator @a");
        server.runCommand("execute in voidheart:void run tp @a " + (coffer.getX() + 0.5) + " " + (coffer.getY() + 1.6) + " " + (coffer.getZ() - 0.9)
                + " 0 60");
        context.waitTicks(30);
        var fill = context.computeOnClient(minecraft -> minecraft.level.getBlockEntity(coffer) instanceof net.voxelindustry.voidheart.common.content.hoard.HoardCofferTile tile
                ? tile.getFill() : -1F);
        expect(fill > 0.4F && fill < 0.6F, "the coffer shows a fill of " + fill + " with its hoard half full");
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot("hoard-coffer-pool");
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runOnServer(minecraft -> hoard(minecraft).extract(DIRT, added));
        server.runCommand("gamemode creative @a");
        server.runCommand("execute in minecraft:overworld run tp @a " + (FOLD.getX() + 0.5) + " " + FLAT_FEET + " " + (FOLD.getZ() - 2.5));
        context.waitTicks(20);
    }

    private static BlockPos pocketCoffer(MinecraftServer minecraft)
    {
        var pocket = VoidPocketState.getVoidPocketState(minecraft.overworld()).getHeartData(player(minecraft).getUUID()).pocketPos();
        return pocket.offset(3, 0, 3);
    }

    private static ItemStack largeRubrum()
    {
        return new ItemStack(CrystalBundles.bundle(CrystalKind.RUBRUM, BundleSize.LARGE));
    }

    /**
     * A coffer in the pocket gives the hoard a large bundle's room; one in the overworld gives none.
     */
    private static void checkCofferRoom(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var voidLevel = minecraft.getLevel(VoidHeart.VOID_WORLD_KEY);
            var inPocket = pocketCoffer(minecraft);
            voidLevel.setBlockAndUpdate(inPocket, HoardContent.HOARD_COFFER.defaultBlockState());
            ((HoardCofferTile) voidLevel.getBlockEntity(inPocket)).setBundle(largeRubrum());
            var room = HoardBalance.cofferCapacity(BundleSize.LARGE);
            expect(hoard(minecraft).capacity() == room, "a large bundle in the pocket gave " + hoard(minecraft).capacity() + " room, expected " + room);

            var overworld = minecraft.overworld();
            overworld.setBlockAndUpdate(OUTER_COFFER, HoardContent.HOARD_COFFER.defaultBlockState());
            var outer = (HoardCofferTile) overworld.getBlockEntity(OUTER_COFFER);
            outer.setBundle(largeRubrum());
            expect(outer.pocketOwner().isEmpty(), "a coffer in the overworld claims a pocket");
            expect(hoard(minecraft).capacity() == room, "a coffer outside the pocket added room: " + hoard(minecraft).capacity());
        });
    }

    /**
     * A hopper fills the hoard through the Fold; shown cobblestone, the Fold lets the hopper under it draw cobblestone back.
     */
    private static void checkHoppersThroughFold(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(WELL, CreativeEnergyContent.CREATIVE_VOID_WELL.defaultBlockState());
            level.setBlockAndUpdate(FOLD, HoardContent.FOLD.defaultBlockState());
            fold(level).setOwner(player(minecraft).getUUID());
        });
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var storage = ItemStorage.SIDED.find(level, FOLD, Direction.UP);
            expect(storage != null, "the transfer API finds no storage on the Fold");
            try (var transaction = Transaction.openOuter())
            {
                var inserted = storage.insert(DIRT, 2, transaction);
                expect(inserted == 2, "the Fold took " + inserted + " dirt through the transfer API; owner " + fold(level).getOwner()
                        + ", hoard " + (fold(level).hoard() != null) + ", energy " + VoidEnergy.available(level, FOLD));
                transaction.abort();
            }
            expect(hoard(minecraft).count(DIRT) == 0, "an aborted insert left dirt in the hoard");

            level.setBlockAndUpdate(HOPPER_IN, Blocks.HOPPER.defaultBlockState());
            ((HopperBlockEntity) level.getBlockEntity(HOPPER_IN)).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        });

        waitFor(context, server, 300, "the hopper put no cobblestone into the hoard through the Fold", minecraft -> hoard(minecraft).count(COBBLE) >= 8);
        server.runOnServer(minecraft ->
        {
            expect(fold(minecraft.overworld()).getAttuned() == null, "a new Fold gives something back");
            var level = minecraft.overworld();
            level.setBlockAndUpdate(HOPPER_OUT, Blocks.HOPPER.defaultBlockState());
            fold(level).attune(COBBLE);
        });

        waitFor(context, server, 300, "the hopper under the attuned Fold drew no cobblestone", minecraft ->
                ((HopperBlockEntity) minecraft.overworld().getBlockEntity(HOPPER_OUT)).getItem(0).is(Items.COBBLESTONE));
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            removeHopper(level, HOPPER_OUT);
            removeHopper(level, HOPPER_IN);
            expect(hoard(minecraft).insert(DIRT, 5) == 5, "the hoard refused dirt with room left");
        });
    }

    /**
     * Without a field around it the Fold moves nothing, though the hoard has room.
     */
    private static void checkUnpoweredFoldRefuses(ClientGameTestContext context, TestServerContext server)
    {
        var before = server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.removeBlock(WELL, false);
            level.setBlockAndUpdate(HOPPER_IN, Blocks.HOPPER.defaultBlockState());
            ((HopperBlockEntity) level.getBlockEntity(HOPPER_IN)).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
            return hoard(minecraft).count(COBBLE);
        });
        context.waitTicks(80);
        server.runOnServer(minecraft ->
        {
            expect(hoard(minecraft).count(COBBLE) == before, "an unpowered Fold let " + (hoard(minecraft).count(COBBLE) - before) + " cobblestone in");
            var level = minecraft.overworld();
            var left = ((HopperBlockEntity) level.getBlockEntity(HOPPER_IN)).getItem(0).getCount();
            expect(left == 16, "the hopper above an unpowered Fold holds " + left + " cobblestone, expected 16");
            removeHopper(level, HOPPER_IN);
        });
    }

    /**
     * An Inventory Inserter carries iron from a chest into the Fold, through the transfer API since a Fold is no inventory.
     */
    private static void checkInserterThroughFold(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(WELL, CreativeEnergyContent.CREATIVE_VOID_WELL.defaultBlockState());
            level.setBlockAndUpdate(FOLD.west(2), Blocks.CHEST.defaultBlockState());
            ((Container) level.getBlockEntity(FOLD.west(2))).setItem(0, new ItemStack(Items.IRON_INGOT, 4));
            level.setBlockAndUpdate(FOLD.west(), VoidHeartBlocks.INVENTORY_INSERTER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        });
        waitFor(context, server, 200, "the inserter carried no iron into the Fold", minecraft -> hoard(minecraft).count(ItemVariant.of(Items.IRON_INGOT)) >= 2);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.removeBlock(FOLD.west(), false);
            ((Container) level.getBlockEntity(FOLD.west(2))).clearContent();
            level.removeBlock(FOLD.west(2), false);
        });
    }

    /**
     * The hoard view lists what it holds, most first: a click on the first plate puts a stack of it on the cursor, and a click on an empty spot of the hole
     * puts the stack back.
     */
    private static void checkHoardView(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            player(minecraft).teleportTo(FOLD.getX() + 0.5, FLAT_FEET, FOLD.getZ() - 2.5);
            hoard(minecraft).insert(ItemVariant.of(Items.DIAMOND), 3);
            hoard(minecraft).insert(COBBLE, 200);
            HoardMenu.open(player(minecraft), player(minecraft).getUUID(), FOLD);
        });
        context.waitTicks(10);
        var seen = context.computeOnClient(minecraft -> minecraft.gui.screen() instanceof HoardScreen screen
                ? "hoard screen listing " + screen.getMenu().getEntries().size() + " kinds, " + screen.getMenu().getTotal() + " items"
                : "screen " + minecraft.gui.screen());
        expect(seen.startsWith("hoard screen listing 4 kinds"), "expected the hoard view listing 4 kinds, found " + seen);
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot("hoard-view");
        context.getInput().pressKey(InputConstants.KEY_F1);

        var held = server.computeOnServer(minecraft -> hoard(minecraft).count(COBBLE));
        var plate = context.computeOnClient(minecraft -> ((HoardScreen) minecraft.gui.screen()).plateCenter(0, 0));
        click(context, plate);
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var carried = player(minecraft).containerMenu.getCarried();
            expect(carried.is(Items.COBBLESTONE) && carried.getCount() == 64, "the first plate gave " + carried + ", expected 64 cobblestone");
            expect(hoard(minecraft).count(COBBLE) == held - 64, "the hoard still holds " + hoard(minecraft).count(COBBLE) + " cobblestone");
        });

        click(context, context.computeOnClient(minecraft -> ((HoardScreen) minecraft.gui.screen()).plateCenter(8, 3)));
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            expect(player(minecraft).containerMenu.getCarried().isEmpty(), "the carried stack did not go back into the hoard");
            expect(hoard(minecraft).count(COBBLE) == held, "the hoard holds " + hoard(minecraft).count(COBBLE) + " cobblestone, expected " + held);
        });
        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        context.waitTicks(3);
    }

    /**
     * Breaking the pocket's only coffer leaves the hoard with no room: it keeps everything and refuses more.
     */
    private static void checkOverCapacity(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var before = hoard(minecraft).total();
            minecraft.getLevel(VoidHeart.VOID_WORLD_KEY).removeBlock(pocketCoffer(minecraft), false);
            var hoard = hoard(minecraft);
            expect(hoard.capacity() == 0, "a broken coffer left " + hoard.capacity() + " room");
            expect(hoard.total() == before, "breaking the coffer lost " + (before - hoard.total()) + " items");
            expect(hoard.insert(DIRT, 1) == 0, "a hoard over its room took more");
        });
    }

    /**
     * Empties a hopper before taking it away, so it drops nothing a later hopper would pick up.
     */
    private static void removeHopper(ServerLevel level, BlockPos pos)
    {
        ((HopperBlockEntity) level.getBlockEntity(pos)).clearContent();
        level.removeBlock(pos, false);
    }

    private static FoldTile fold(ServerLevel level)
    {
        return (FoldTile) level.getBlockEntity(FOLD);
    }
}
