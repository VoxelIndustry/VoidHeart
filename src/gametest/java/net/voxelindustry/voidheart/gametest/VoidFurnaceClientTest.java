package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.client.screen.VoidFurnaceScreen;
import net.voxelindustry.voidheart.common.content.crystal.BundleSize;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile;
import net.voxelindustry.voidheart.common.content.hoard.HoardCofferTile;
import net.voxelindustry.voidheart.common.content.hoard.HoardContent;
import net.voxelindustry.voidheart.common.content.hoard.PocketHoard;
import net.voxelindustry.voidheart.common.content.works.OfficiantTile;
import net.voxelindustry.voidheart.common.content.works.WorksContent;
import net.voxelindustry.voidheart.common.content.works.WorksState;
import net.voxelindustry.voidheart.common.energy.creative.CreativeEnergyContent;
import net.voxelindustry.voidheart.common.energy.furnace.VoidFurnaceBalance;
import net.voxelindustry.voidheart.common.energy.furnace.VoidFurnaceBlock;
import net.voxelindustry.voidheart.common.energy.furnace.VoidFurnaceContent;
import net.voxelindustry.voidheart.common.energy.furnace.VoidFurnaceTile;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The Void Furnace smelts four times a furnace's pace on a field and eight times on Fiery brought by a relay, works with hoppers, stays cold with no
 * field, opens its screen, drops what it holds, and an Officiant under it smelts an order from the hoard in batches, taking a broken furnace's batch back.
 */
public class VoidFurnaceClientTest implements FabricClientGameTest
{
    private static final BlockPos WELL      = new BlockPos(0, FLAT_FEET - 3, 0);
    private static final BlockPos PLAIN     = new BlockPos(-6, FLAT_FEET, 10);
    private static final BlockPos FIERY     = new BlockPos(-2, FLAT_FEET, 0);
    private static final BlockPos RELAY     = new BlockPos(-2, FLAT_FEET, -3);
    private static final BlockPos STORAGE   = new BlockPos(-4, FLAT_FEET, -3);
    private static final BlockPos HOPPED    = new BlockPos(2, FLAT_FEET + 1, 0);
    private static final BlockPos OFFICIANT = new BlockPos(6, FLAT_FEET, 0);
    private static final BlockPos SERVED    = OFFICIANT.above();
    private static final BlockPos BROKEN    = new BlockPos(-10, FLAT_FEET, 0);
    private static final BlockPos COLD      = new BlockPos(28, FLAT_FEET, 0);

    private static final int BATCH = 8;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        // Every test shares one client, so the camera and HUD go back as they were, pass or fail
        var saved = context.computeOnClient(minecraft -> new ClientSettings(minecraft.options.getCameraType(), minecraft.gui.hud.isHidden()));
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            try
            {
                world.getConnection().waitForChunksRender();
                freezeTime(server, "noon");
                server.runCommand("gamemode creative @a");
                context.waitTicks(20);
                // The HUD shows, so each shot's F1 hides it
                context.runOnClient(minecraft ->
                {
                    minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                    if (minecraft.gui.hud.isHidden())
                        minecraft.gui.hud.toggle();
                });

                server.runOnServer(VoidFurnaceClientTest::buildScene);
                context.waitTicks(5);

                checkPlainPace(context, server);
                checkFieryPace(context, server);
                checkHoppers(context, server);
                checkCold(context, server);
                checkScreen(context, server);
                checkBreakDrops(context, server);
                checkOfficiant(context, server);
                checkOfficiantFurnaceBroken(context, server);
                checkOfficiantBroken(context, server);
                checkItem(context, server);
            }
            finally
            {
                restoreClient(context, server, saved);
            }
        }
    }

    private static void furnace(MinecraftServer minecraft, BlockPos pos)
    {
        minecraft.overworld().setBlockAndUpdate(pos, VoidFurnaceContent.VOID_FURNACE.defaultBlockState().setValue(VoidFurnaceBlock.FACING, Direction.SOUTH));
    }

    private static VoidFurnaceTile tile(MinecraftServer minecraft, BlockPos pos)
    {
        return (VoidFurnaceTile) minecraft.overworld().getBlockEntity(pos);
    }

    private static VoidFurnaceBlock.Flame flame(MinecraftServer minecraft, BlockPos pos)
    {
        return minecraft.overworld().getBlockState(pos).getValue(VoidFurnaceBlock.FLAME);
    }

    private static PocketHoard.Hoard hoard(MinecraftServer minecraft)
    {
        return PocketHoard.get(minecraft).of(player(minecraft).getUUID());
    }

    private static long held(MinecraftServer minecraft, net.minecraft.world.level.ItemLike item)
    {
        return hoard(minecraft).count(ItemVariant.of(item));
    }

    private static void buildScene(MinecraftServer minecraft)
    {
        var level = minecraft.overworld();
        level.setBlockAndUpdate(WELL, CreativeEnergyContent.CREATIVE_VOID_WELL.defaultBlockState());
        furnace(minecraft, PLAIN);
        furnace(minecraft, FIERY);
        furnace(minecraft, COLD);

        level.setBlockAndUpdate(RELAY, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
        level.setBlockAndUpdate(STORAGE, VoidHeartBlocks.RAVENOUS_STORAGE.defaultBlockState());
        ((RavenousStorageTile) level.getBlockEntity(STORAGE)).receive(EssenceRegistry.FIERY, 20_000);

        // A hopper above feeds it, one below takes its results to a chest
        furnace(minecraft, HOPPED);
        level.setBlockAndUpdate(HOPPED.above(), Blocks.HOPPER.defaultBlockState());
        level.setBlockAndUpdate(HOPPED.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(HOPPED.below().south(), Blocks.CHEST.defaultBlockState());

        // A pocket with a coffer, and an Officiant under a furnace
        var owner = player(minecraft).getUUID();
        var pockets = VoidPocketState.getVoidPocketState(level);
        var voidLevel = minecraft.getLevel(VoidHeart.VOID_WORLD_KEY);
        pockets.createPocket(voidLevel, owner);
        var coffer = pockets.getHeartData(owner).pocketPos().offset(3, 0, 3);
        voidLevel.setBlockAndUpdate(coffer, HoardContent.HOARD_COFFER.defaultBlockState());
        ((HoardCofferTile) voidLevel.getBlockEntity(coffer)).setBundle(new ItemStack(CrystalBundles.bundle(CrystalKind.RUBRUM, BundleSize.LARGE)));
        level.setBlockAndUpdate(OFFICIANT, WorksContent.OFFICIANT.defaultBlockState());
        ((OfficiantTile) level.getBlockEntity(OFFICIANT)).setOwner(owner);
        furnace(minecraft, SERVED);
    }

    /**
     * Times raw iron through a furnace from the moment they are set in it, and checks it against the pace its heat gives.
     */
    private static void timeRun(ClientGameTestContext context, TestServerContext server, BlockPos pos, int count, boolean fiery)
    {
        double[] heat = {0};
        long start = server.computeOnServer(minecraft ->
        {
            heat[0] = tile(minecraft, pos).heat();
            tile(minecraft, pos).setItem(VoidFurnaceTile.INPUT, new ItemStack(Items.RAW_IRON, count));
            return minecraft.overworld().getGameTime();
        });
        var expected = VoidFurnaceBalance.run(count, 200, heat[0], fiery);
        waitFor(context, server, expected.ticks() * 2 + 100, () -> "the furnace at " + pos + " did not smelt " + count + " raw iron",
                minecraft -> tile(minecraft, pos).getItem(VoidFurnaceTile.OUTPUT).getCount() >= count);
        var took = server.computeOnServer(minecraft -> minecraft.overworld().getGameTime()) - start;
        expect(Math.abs(took - expected.ticks()) <= 4, count + " raw iron took " + took + " ticks from heat " + heat[0] + (fiery ? " on Fiery" : "")
                                                       + ", expected " + expected.ticks());
        var left = server.computeOnServer(minecraft -> tile(minecraft, pos).heat());
        expect(Math.abs(left - expected.heat()) < 0.01, "the furnace ended at heat " + left + ", expected " + expected.heat());
    }

    /**
     * On a field alone a cold furnace smelts at four times a furnace's pace, warms as it goes, and cools once idle. The mouth burns violet.
     */
    private static void checkPlainPace(ClientGameTestContext context, TestServerContext server)
    {
        aimFrom(context, server, PLAIN.getX() + 0.5, FLAT_FEET, PLAIN.getZ() + 3.2, PLAIN.getX() + 0.5, FLAT_FEET + 0.5, PLAIN.getZ() + 0.5, 2);
        server.runOnServer(minecraft -> tile(minecraft, PLAIN).setItem(VoidFurnaceTile.INPUT, new ItemStack(Items.RAW_IRON, 1)));
        waitFor(context, server, 60, "the plain furnace never lit violet", minecraft -> flame(minecraft, PLAIN) == VoidFurnaceBlock.Flame.VOID);
        // The block's model changes with its flame, so the chunk is drawn again first
        context.waitTicks(10);
        shot(context, "void-furnace-lit-void");
        waitFor(context, server, 80, "the first raw iron never smelted", minecraft -> tile(minecraft, PLAIN).getItem(VoidFurnaceTile.OUTPUT).getCount() == 1);
        server.runOnServer(minecraft -> tile(minecraft, PLAIN).setItem(VoidFurnaceTile.OUTPUT, ItemStack.EMPTY));

        timeRun(context, server, PLAIN, 8, false);
        server.runOnServer(minecraft ->
        {
            expect(tile(minecraft, PLAIN).getItem(VoidFurnaceTile.OUTPUT).is(Items.IRON_INGOT), "the plain furnace made no iron ingots");
            tile(minecraft, PLAIN).setItem(VoidFurnaceTile.OUTPUT, ItemStack.EMPTY);
        });
        // A whole stack warms it most of the way
        timeRun(context, server, PLAIN, 64, false);
        var warm = server.computeOnServer(minecraft ->
        {
            tile(minecraft, PLAIN).setItem(VoidFurnaceTile.OUTPUT, ItemStack.EMPTY);
            return tile(minecraft, PLAIN).heat();
        });
        expect(warm > 0.5, "a stack only warmed the furnace to " + warm);
        context.waitTicks(240);
        var cooled = server.computeOnServer(minecraft -> tile(minecraft, PLAIN).heat());
        expect(cooled < warm - 0.08 && cooled > warm - 0.12, "idle for 240 ticks, the furnace went from heat " + warm + " to " + cooled);

        waitFor(context, server, 60, "the plain furnace stayed lit with nothing to smelt", minecraft -> flame(minecraft, PLAIN) == VoidFurnaceBlock.Flame.NONE);
        context.waitTicks(10);
        shot(context, "void-furnace-cold");
    }

    /**
     * A relay fills the furnace with Fiery from a storage, and raw iron then cooks twice as fast, the mouth burning embers.
     */
    private static void checkFieryPace(ClientGameTestContext context, TestServerContext server)
    {
        waitFor(context, server, 600, () -> "the relay brought the furnace no Fiery",
                minecraft -> tile(minecraft, FIERY).fiery() >= VoidFurnaceBalance.FIERY_TANK / 2);
        aimFrom(context, server, FIERY.getX() + 0.5, FLAT_FEET, FIERY.getZ() + 3.2, FIERY.getX() + 0.5, FLAT_FEET + 0.5, FIERY.getZ() + 0.5, 2);
        timeRun(context, server, FIERY, 8, true);
        server.runOnServer(minecraft ->
        {
            tile(minecraft, FIERY).setItem(VoidFurnaceTile.OUTPUT, ItemStack.EMPTY);
            tile(minecraft, FIERY).setItem(VoidFurnaceTile.INPUT, new ItemStack(Items.RAW_IRON, 64));
        });
        waitFor(context, server, 60, "the furnace never burned Fiery", minecraft -> flame(minecraft, FIERY) == VoidFurnaceBlock.Flame.FIERY);
        context.waitTicks(10);
        shot(context, "void-furnace-lit-fiery");
        waitFor(context, server, 2000, "the Fiery furnace did not smelt a stack", minecraft -> tile(minecraft, FIERY).getItem(VoidFurnaceTile.INPUT).isEmpty());

        // Hot and on Fiery, its pace gauge burns to the end
        server.runOnServer(minecraft ->
        {
            tile(minecraft, FIERY).setItem(VoidFurnaceTile.OUTPUT, ItemStack.EMPTY);
            tile(minecraft, FIERY).setItem(VoidFurnaceTile.INPUT, new ItemStack(Items.RAW_IRON, 32));
            player(minecraft).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            useOn(player(minecraft), FIERY);
        });
        context.waitFor(minecraft -> minecraft.gui.screen() instanceof VoidFurnaceScreen, 40);
        context.waitTicks(25);
        context.takeScreenshot("void-furnace-screen-fiery");
        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        waitFor(context, server, 600, "the Fiery furnace did not smelt its second run", minecraft -> tile(minecraft, FIERY).getItem(VoidFurnaceTile.INPUT).isEmpty());
        server.runOnServer(minecraft -> tile(minecraft, FIERY).setItem(VoidFurnaceTile.OUTPUT, ItemStack.EMPTY));
    }

    /**
     * A hopper above fills the input, one below empties the output into a chest.
     */
    private static void checkHoppers(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> ((HopperBlockEntity) minecraft.overworld().getBlockEntity(HOPPED.above())).setItem(0, new ItemStack(Items.RAW_GOLD, 4)));
        waitFor(context, server, 600, "the hoppers did not carry four gold ingots through the furnace", minecraft ->
        {
            var chest = (ChestBlockEntity) minecraft.overworld().getBlockEntity(HOPPED.below().south());
            int gold = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++)
                if (chest.getItem(slot).is(Items.GOLD_INGOT))
                    gold += chest.getItem(slot).getCount();
            return gold == 4;
        });
        // A hopper never takes what is still to smelt
        server.runOnServer(minecraft ->
        {
            var furnace = tile(minecraft, HOPPED);
            expect(!furnace.canTakeItemThroughFace(VoidFurnaceTile.INPUT, new ItemStack(Items.RAW_GOLD), Direction.DOWN), "a hopper below can take the input");
            expect(!furnace.canPlaceItemThroughFace(VoidFurnaceTile.INPUT, new ItemStack(Items.STICK), Direction.UP), "a hopper can feed it something it can't smelt");
        });
    }

    /**
     * With no field it never starts, reads as starved, and stays cold.
     */
    private static void checkCold(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> tile(minecraft, COLD).setItem(VoidFurnaceTile.INPUT, new ItemStack(Items.RAW_IRON, 2)));
        context.waitTicks(80);
        server.runOnServer(minecraft ->
        {
            var furnace = tile(minecraft, COLD);
            expect(furnace.getItem(VoidFurnaceTile.OUTPUT).isEmpty(), "a furnace with no field smelted");
            expect(furnace.power() == MachinePower.UNPOWERED, "a furnace with no field reads " + furnace.power());
            expect(flame(minecraft, COLD) == VoidFurnaceBlock.Flame.NONE, "a furnace with no field is lit");
        });
    }

    /**
     * Used with an empty hand it opens its screen, showing what lies in it and how far it has got.
     */
    private static void checkScreen(ClientGameTestContext context, TestServerContext server)
    {
        aimFrom(context, server, PLAIN.getX() + 0.5, FLAT_FEET, PLAIN.getZ() + 2.5, PLAIN.getX() + 0.5, FLAT_FEET + 0.5, PLAIN.getZ() + 0.5, 2);
        server.runOnServer(minecraft ->
        {
            var furnace = tile(minecraft, PLAIN);
            furnace.setItem(VoidFurnaceTile.OUTPUT, new ItemStack(Items.IRON_INGOT, 3));
            furnace.setItem(VoidFurnaceTile.INPUT, new ItemStack(Items.RAW_IRON, 16));
            player(minecraft).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            useOn(player(minecraft), PLAIN);
        });
        context.waitFor(minecraft -> minecraft.gui.screen() instanceof VoidFurnaceScreen, 40);
        context.waitTicks(20);
        context.takeScreenshot("void-furnace-screen");
        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            var furnace = tile(minecraft, PLAIN);
            furnace.setItem(VoidFurnaceTile.INPUT, ItemStack.EMPTY);
            furnace.setItem(VoidFurnaceTile.OUTPUT, ItemStack.EMPTY);
        });
    }

    /**
     * Broken, it drops itself and what it held.
     */
    private static void checkBreakDrops(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            furnace(minecraft, BROKEN);
            var furnace = tile(minecraft, BROKEN);
            furnace.setItem(VoidFurnaceTile.INPUT, new ItemStack(Items.RAW_COPPER, 5));
            furnace.setItem(VoidFurnaceTile.OUTPUT, new ItemStack(Items.COPPER_INGOT, 2));
            minecraft.overworld().destroyBlock(BROKEN, true, player(minecraft));
        });
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            var drops = minecraft.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(BROKEN).inflate(2));
            int furnaces = 0, raw = 0, ingots = 0;
            for (var drop : drops)
            {
                var stack = drop.getItem();
                if (stack.is(VoidFurnaceContent.VOID_FURNACE.asItem()))
                    furnaces += stack.getCount();
                else if (stack.is(Items.RAW_COPPER))
                    raw += stack.getCount();
                else if (stack.is(Items.COPPER_INGOT))
                    ingots += stack.getCount();
            }
            expect(furnaces == 1 && raw == 5 && ingots == 2, "a broken furnace dropped " + furnaces + " furnace, " + raw + " raw copper, " + ingots + " ingots");
            drops.forEach(ItemEntity::discard);
        });
    }

    /**
     * Twenty iron ingots ordered from twenty raw iron in the hoard come from the furnace's Officiant, laid in batches of eight.
     */
    private static void checkOfficiant(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            hoard(minecraft).insert(ItemVariant.of(Items.RAW_IRON), 20);
            var plan = WorksContent.order(minecraft, player(minecraft).getUUID(), ItemVariant.of(Items.IRON_INGOT), 20).plan();
            expect(plan.isComplete(), "the ingots planned " + plan.tasks() + ", lacking " + plan.lacking());
        });
        aimFrom(context, server, SERVED.getX() + 0.5, FLAT_FEET + 1, SERVED.getZ() + 3.2, SERVED.getX() + 0.5, FLAT_FEET + 1.2, SERVED.getZ() + 0.5, 2);
        int[] largest = {0};
        waitFor(context, server, 300, "the Officiant laid nothing in the furnace", minecraft ->
        {
            largest[0] = Math.max(largest[0], tile(minecraft, SERVED).getItem(VoidFurnaceTile.INPUT).getCount());
            return largest[0] > 0;
        });
        context.waitTicks(10);
        shot(context, "void-furnace-officiant");
        waitFor(context, server, 2000, () -> "the Officiant's furnace made no twenty ingots", minecraft ->
        {
            largest[0] = Math.max(largest[0], tile(minecraft, SERVED).getItem(VoidFurnaceTile.INPUT).getCount());
            return held(minecraft, Items.IRON_INGOT) >= 20;
        });
        expect(largest[0] == BATCH, "the Officiant laid at most " + largest[0] + " at once, not a batch of " + BATCH);
        waitFor(context, server, 60, "the order is still queued", minecraft -> WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs().isEmpty());
        server.runOnServer(minecraft ->
        {
            expect(held(minecraft, Items.RAW_IRON) == 0, "raw iron is left in the hoard");
            hoard(minecraft).extract(ItemVariant.of(Items.IRON_INGOT), held(minecraft, Items.IRON_INGOT));
        });
    }

    /**
     * Broken halfway through a batch, the furnace hands what it smelted and what it didn't to the Officiant, the rest of the order counts again, and a
     * new furnace finishes it.
     */
    private static void checkOfficiantFurnaceBroken(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            hoard(minecraft).insert(ItemVariant.of(Items.RAW_IRON), 8);
            WorksContent.order(minecraft, player(minecraft).getUUID(), ItemVariant.of(Items.IRON_INGOT), 8);
        });
        waitFor(context, server, 600, "the furnace never smelted part of the batch", minecraft ->
        {
            var furnace = tile(minecraft, SERVED);
            return furnace.smelted() >= 3 && !furnace.getItem(VoidFurnaceTile.INPUT).isEmpty();
        });
        server.runOnServer(minecraft ->
        {
            minecraft.overworld().destroyBlock(SERVED, false);
            expect(minecraft.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(SERVED).inflate(2)).stream()
                    .noneMatch(drop -> drop.getItem().is(Items.RAW_IRON) || drop.getItem().is(Items.IRON_INGOT)), "the batch fell on the ground");
        });
        waitFor(context, server, 100, "the broken batch never reached the hoard", minecraft -> held(minecraft, Items.IRON_INGOT) + held(minecraft, Items.RAW_IRON) == 8);
        server.runOnServer(minecraft ->
        {
            var made = held(minecraft, Items.IRON_INGOT);
            expect(made >= 3, "only " + made + " ingots came back");
            var task = WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs().getFirst().tasks().getLast();
            expect(task.remaining() == 8 - made && task.working() == 0, "the order counts " + task + " after " + made + " of 8 were made");
            furnace(minecraft, SERVED);
        });
        waitFor(context, server, 900, "a new furnace did not finish the order", minecraft -> held(minecraft, Items.IRON_INGOT) == 8);
        waitFor(context, server, 60, "the order is still queued", minecraft -> WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs().isEmpty());
    }

    /**
     * The Officiant broken halfway through a batch takes the batch back from the furnace, which is free again, and a new Officiant finishes the order.
     */
    private static void checkOfficiantBroken(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            hoard(minecraft).extract(ItemVariant.of(Items.IRON_INGOT), held(minecraft, Items.IRON_INGOT));
            hoard(minecraft).insert(ItemVariant.of(Items.RAW_IRON), 8);
            WorksContent.order(minecraft, player(minecraft).getUUID(), ItemVariant.of(Items.IRON_INGOT), 8);
        });
        waitFor(context, server, 600, "the furnace never smelted part of the batch", minecraft ->
        {
            var furnace = tile(minecraft, SERVED);
            return furnace.smelted() >= 2 && !furnace.getItem(VoidFurnaceTile.INPUT).isEmpty();
        });
        server.runOnServer(minecraft ->
        {
            minecraft.overworld().destroyBlock(OFFICIANT, false);
            var furnace = tile(minecraft, SERVED);
            expect(furnace.getTicket() == null && furnace.isEmptied(), "the furnace still holds the batch of a broken Officiant");
            expect(held(minecraft, Items.IRON_INGOT) + held(minecraft, Items.RAW_IRON) == 8, "the batch did not go back to the hoard: "
                    + held(minecraft, Items.IRON_INGOT) + " ingots, " + held(minecraft, Items.RAW_IRON) + " raw");
            var made = held(minecraft, Items.IRON_INGOT);
            var task = WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs().getFirst().tasks().getLast();
            expect(task.remaining() == 8 - made && task.working() == 0, "the order counts " + task + " after " + made + " of 8 were made");
            minecraft.overworld().setBlockAndUpdate(OFFICIANT, WorksContent.OFFICIANT.defaultBlockState());
            ((OfficiantTile) minecraft.overworld().getBlockEntity(OFFICIANT)).setOwner(player(minecraft).getUUID());
        });
        waitFor(context, server, 900, "a new Officiant did not finish the order", minecraft -> held(minecraft, Items.IRON_INGOT) == 8);
        waitFor(context, server, 60, "the order is still queued", minecraft -> WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs().isEmpty());
        server.runOnServer(minecraft -> hoard(minecraft).extract(ItemVariant.of(Items.IRON_INGOT), 8));
    }

    /**
     * The block in hand and in the inventory, as it stands placed.
     */
    private static void checkItem(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(VoidFurnaceContent.VOID_FURNACE)));
        aimFrom(context, server, 0.5, FLAT_FEET + 4, 8.5, 0.5, FLAT_FEET + 3, 20.5, 5);
        context.takeScreenshot("void-furnace-in-hand");
        context.getInput().pressKey(InputConstants.KEY_E);
        context.waitTicks(5);
        context.takeScreenshot("void-furnace-inventory");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
    }

    private static void shot(ClientGameTestContext context, String name)
    {
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot(name);
        context.getInput().pressKey(InputConstants.KEY_F1);
    }
}
