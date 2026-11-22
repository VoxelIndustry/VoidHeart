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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.client.render.OfficiantFoldRender;
import net.voxelindustry.voidheart.common.content.works.OfficiantFolds;
import net.voxelindustry.voidheart.common.content.crystal.BundleSize;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.hoard.HoardContent;
import net.voxelindustry.voidheart.common.content.hoard.HoardCofferTile;
import net.voxelindustry.voidheart.common.content.hoard.PocketHoard;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeLayout;
import net.voxelindustry.voidheart.common.content.works.OfficiantTile;
import net.voxelindustry.voidheart.common.content.works.WorksContent;
import net.voxelindustry.voidheart.common.content.works.WorksState;
import net.voxelindustry.voidheart.common.energy.creative.CreativeEnergyContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import java.util.List;
import java.util.UUID;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Officiants work stations from the hoard for what is ordered: a crafting table cuts ender pearls into shards, a Void Pearl ordered from raw
 * obsidian and an ender pearl is made through the table and the altar, and a forge pinned by its Officiant makes Seething Steel. Each item comes up
 * through a small fold opened over its pillar, one at a time, the catalyst last, and the result sinks back through one.
 */
public class OfficiantClientTest implements FabricClientGameTest
{
    private static final BlockPos WELL   = new BlockPos(0, FLAT_FEET - 3, 0);
    private static final BlockPos TABLE  = new BlockPos(8, FLAT_FEET, -6);
    private static final BlockPos ALTAR  = new BlockPos(0, FLAT_FEET, 8);
    private static final List<BlockPos> PILLARS = List.of(
            new BlockPos(-1, 0, 3), new BlockPos(1, 0, 3), new BlockPos(1, 0, -3), new BlockPos(-1, 0, -3),
            new BlockPos(3, 0, -1), new BlockPos(3, 0, 1), new BlockPos(-3, 0, -1), new BlockPos(-3, 0, 1));
    private static final BlockPos FORGE  = new BlockPos(-8, FLAT_FEET, 4);
    private static final Direction TRACK = Direction.NORTH;
    private static final int TRACK_PAIRS = 2;
    private static final BlockPos SOURCE = ShatterForgeLayout.endSlot(FORGE, TRACK, TRACK_PAIRS);
    private static final BlockPos ABSENT_TABLE = new BlockPos(8, FLAT_FEET, -10);
    private static final BlockPos ESSENCE_RELAY = new BlockPos(0, FLAT_FEET, 3);
    private static final BlockPos ESSENCE_FOLD  = new BlockPos(2, FLAT_FEET, 3);
    private static final UUID ABSENT_OWNER = UUID.fromString("5a1d0c3e-0000-4000-8000-0000000000f1");

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode creative @a");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            server.runOnServer(OfficiantClientTest::buildBase);
            // Officiants make their stations known on their first ticks
            context.waitTicks(25);
            checkTable(context, server);
            checkAltarChain(context, server);
            checkForge(context, server);
            checkForgeSourceOccupied(context, server);
            checkForgeResultKeptAndSourceTaken(context, server);
            checkForgeResultKeptFromInserter(context, server);
            checkForgeInputTakenByHand(context, server);
            checkFullHoard(context, server);
            checkBrokenMidLaying(context, server);
            checkItemOnPillarMidLaying(context, server);
            checkRiteStartedMidLaying(context, server);
            checkAltarReplaced(context, server);
            checkResultKeptForOfficiant(context, server);
            checkAltarBrokenOnResult(context, server);
            checkOfflineOwner(context, server);
            checkTransform(context, server);
            checkResumesOnceSupplied(context, server);
            checkOnPylonField(context, server);
        }
    }

    private static PocketHoard.Hoard hoard(MinecraftServer minecraft)
    {
        return PocketHoard.get(minecraft).of(player(minecraft).getUUID());
    }

    private static long held(MinecraftServer minecraft, net.minecraft.world.level.ItemLike item)
    {
        return hoard(minecraft).count(ItemVariant.of(item));
    }

    private static void officiant(ServerLevel level, BlockPos pos, MinecraftServer minecraft)
    {
        level.setBlockAndUpdate(pos, WorksContent.OFFICIANT.defaultBlockState());
        ((OfficiantTile) level.getBlockEntity(pos)).setOwner(player(minecraft).getUUID());
    }

    /**
     * A pocket with a large coffer, a field covering every station, and an Officiant under each.
     */
    private static void buildBase(MinecraftServer minecraft)
    {
        var owner = player(minecraft).getUUID();
        var pockets = VoidPocketState.getVoidPocketState(minecraft.overworld());
        pockets.createPocket(minecraft.getLevel(VoidHeart.VOID_WORLD_KEY), owner);
        var voidLevel = minecraft.getLevel(VoidHeart.VOID_WORLD_KEY);
        var coffer = pockets.getHeartData(owner).pocketPos().offset(3, 0, 3);
        voidLevel.setBlockAndUpdate(coffer, HoardContent.HOARD_COFFER.defaultBlockState());
        ((HoardCofferTile) voidLevel.getBlockEntity(coffer)).setBundle(new ItemStack(CrystalBundles.bundle(CrystalKind.RUBRUM, BundleSize.LARGE)));

        var level = minecraft.overworld();
        level.setBlockAndUpdate(WELL, CreativeEnergyContent.CREATIVE_VOID_WELL.defaultBlockState());

        level.setBlockAndUpdate(TABLE, Blocks.CRAFTING_TABLE.defaultBlockState());
        officiant(level, TABLE.below(), minecraft);

        level.setBlockAndUpdate(ALTAR, VoidHeartBlocks.VOID_ALTAR.defaultBlockState());
        for (var offset : PILLARS)
            level.setBlockAndUpdate(ALTAR.offset(offset), VoidHeartBlocks.VOID_PILLAR.defaultBlockState());
        officiant(level, ALTAR.below(), minecraft);

        level.setBlockAndUpdate(FORGE, VoidHeartBlocks.SHATTER_FORGE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.Z));
        for (var expected : ShatterForgeLayout.expected(FORGE, TRACK, TRACK_PAIRS))
        {
            var block = switch (expected.part())
            {
                case CONDUIT -> VoidHeartBlocks.VOID_CONDUIT;
                case PILLAR -> VoidHeartBlocks.VOID_PILLAR;
                default -> VoidHeartBlocks.VOID_MONOLITH;
            };
            level.setBlockAndUpdate(expected.pos(), block.defaultBlockState());
        }
        for (var spot : ShatterForgeLayout.optionalCollectors(FORGE, TRACK, TRACK_PAIRS))
        {
            level.setBlockAndUpdate(spot, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            var collector = (RavenousCollectorTile) level.getBlockEntity(spot);
            collector.storage().attune(EssenceRegistry.FIERY);
            collector.storage().fill(EssenceRegistry.FIERY, RavenousCollectorTile.CAPACITY);
        }
        officiant(level, SOURCE.below(), minecraft);
    }

    /**
     * Four ender shards ordered from one ender pearl in the hoard come from the table's Officiant.
     */
    private static void checkTable(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            hoard(minecraft).insert(ItemVariant.of(Items.ENDER_PEARL), 1);
            var plan = WorksContent.order(minecraft, player(minecraft).getUUID(), ItemVariant.of(VoidHeartItems.ENDER_SHARD), 4).plan();
            expect(plan.isComplete() && plan.tasks().size() == 1, "the ender shards planned " + plan.tasks() + ", lacking " + plan.lacking());
        });
        aimFrom(context, server, TABLE.getX() + 0.5, FLAT_FEET + 1.2, TABLE.getZ() - 2.2, TABLE.getX() + 0.5, FLAT_FEET + 1, TABLE.getZ() + 0.5, 1);
        waitForFold(context, OfficiantFolds.PASS, 200, "the table's Officiant opened no fold");
        context.waitTicks(7);
        shot(context, "officiant-table-pass");
        waitFor(context, server, 200, "the table's Officiant made no ender shards", minecraft -> held(minecraft, VoidHeartItems.ENDER_SHARD) >= 4);
        // The order goes once the Officiant sees its craft finished
        waitFor(context, server, 40, "the order is still queued", minecraft -> WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs().isEmpty());
        server.runOnServer(minecraft ->
        {
            expect(held(minecraft, Items.ENDER_PEARL) == 0, "the ender pearl is still in the hoard");
            hoard(minecraft).extract(ItemVariant.of(VoidHeartItems.ENDER_SHARD), held(minecraft, VoidHeartItems.ENDER_SHARD));
        });

        // A craft of many ingredients: the planks lie on the table's grid, then the chest takes their place, larger, and sinks into the fold
        server.runOnServer(minecraft ->
        {
            hoard(minecraft).insert(ItemVariant.of(Items.OAK_PLANKS), 8);
            WorksContent.order(minecraft, player(minecraft).getUUID(), ItemVariant.of(Items.CHEST), 1);
        });
        aimFrom(context, server, TABLE.getX() + 0.5, FLAT_FEET + 1.3, TABLE.getZ() - 1.2, TABLE.getX() + 0.5, FLAT_FEET + 1, TABLE.getZ() + 0.5, 1);
        context.waitFor(minecraft -> OfficiantFoldRender.ingredientsLaid() >= 8, 300);
        shot(context, "officiant-table-grid");
        context.waitFor(minecraft -> OfficiantFoldRender.resultShown(), 100);
        shot(context, "officiant-table-result");
        waitFor(context, server, 200, "the table's Officiant made no chest", minecraft -> held(minecraft, Items.CHEST) == 1);
        server.runOnServer(minecraft -> hoard(minecraft).extract(ItemVariant.of(Items.CHEST), 1));
    }

    /**
     * A Void Pearl ordered from raw obsidian and an ender pearl: the table cuts both into shards, then the altar lays them out and makes the pearl.
     */
    private static void checkAltarChain(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var pearl = id("altar/void_pearl");
            Knowledge.lockOf(minecraft.registryAccess(), pearl).ifPresent(knowledge(minecraft)::grant);
            hoard(minecraft).insert(ItemVariant.of(Items.ENDER_PEARL), 1);
            hoard(minecraft).insert(ItemVariant.of(Items.OBSIDIAN), 2);
            var plan = WorksContent.order(minecraft, player(minecraft).getUUID(), ItemVariant.of(VoidHeartItems.VOID_PEARL), 1).plan();
            expect(plan.isComplete(), "the Void Pearl plan lacks " + plan.lacking());
        });

        aimFrom(context, server, ALTAR.getX() + 0.5, FLAT_FEET + 2.2, ALTAR.getZ() - 5.5, ALTAR.getX() + 0.5, FLAT_FEET + 0.8, ALTAR.getZ() + 0.5, 5);

        // The pillars fill one at a time, the catalyst last
        waitForFold(context, OfficiantFolds.LAY, 900, "the altar's Officiant opened no fold");
        context.waitTicks(4);
        shot(context, "officiant-fold-first");
        server.runOnServer(minecraft ->
        {
            var filled = pillarsFilled(minecraft);
            expect(filled >= 1 && filled < 8, filled + " pillars filled as the first folds opened");
            expect(altar(minecraft).getStack().isEmpty(), "the catalyst was laid before the pillars");
        });
        context.waitTicks(12);
        shot(context, "officiant-folds-laying");
        waitFor(context, server, 80, "the catalyst never reached the altar", minecraft -> !altar(minecraft).getStack().isEmpty());
        server.runOnServer(minecraft -> expect(pillarsFilled(minecraft) == 8, "the catalyst came before the last pillar"));
        context.waitTicks(6);
        shot(context, "officiant-catalyst-rising");

        waitFor(context, server, 600, "the altar never started the Void Pearl", minecraft -> altar(minecraft).isCrafting());
        shot(context, "officiant-altar-rite");

        waitFor(context, server, 2000, "no Void Pearl reached the hoard", minecraft -> held(minecraft, VoidHeartItems.VOID_PEARL) == 1);
        waitForFold(context, OfficiantFolds.TAKE, 40, "no fold took the Void Pearl back");
        context.waitTicks(7);
        shot(context, "officiant-fold-take");
        server.runOnServer(minecraft ->
        {
            expect(held(minecraft, Items.OBSIDIAN) == 0 && held(minecraft, Items.ENDER_PEARL) == 0, "raw materials left in the hoard: " + hoard(minecraft).view());
            expect(altar(minecraft).isVacant(), "the altar still holds " + altar(minecraft).getStack());
        });
    }

    /**
     * Seething Steel ordered from an iron ingot: the forge's Officiant sets the iron on the source pillar, pinned, and takes the steel.
     */
    private static void checkForge(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            hoard(minecraft).insert(ItemVariant.of(Items.IRON_INGOT), 1);
            var plan = WorksContent.order(minecraft, player(minecraft).getUUID(), ItemVariant.of(VoidHeartItems.SEETHING_STEEL_INGOT), 1).plan();
            expect(plan.isComplete(), "the Seething Steel plan lacks " + plan.lacking() + ", tasks " + plan.tasks());
        });
        waitFor(context, server, 900, "no Seething Steel reached the hoard", minecraft -> held(minecraft, VoidHeartItems.SEETHING_STEEL_INGOT) == 1);
        server.runOnServer(minecraft ->
        {
            expect(held(minecraft, Items.IRON_INGOT) == 0, "the iron is still in the hoard");
            expect(forge(minecraft).getPinned() == null, "the forge is still pinned to " + forge(minecraft).getPinned() + " once its steel was taken");
        });
    }

    private static net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile source(MinecraftServer minecraft)
    {
        return (net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile) minecraft.overworld().getBlockEntity(SOURCE);
    }

    private static net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile forge(MinecraftServer minecraft)
    {
        return (net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile) minecraft.overworld().getBlockEntity(FORGE);
    }

    private static void orderSteel(TestServerContext server)
    {
        orderFromIron(server, VoidHeartItems.SEETHING_STEEL_INGOT);
    }

    private static void orderFromIron(TestServerContext server, net.minecraft.world.item.Item made)
    {
        server.runOnServer(minecraft ->
        {
            // What earlier crafts from iron left would answer the waits for this one
            for (var product : List.of(VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.ARROGANT_IRON_INGOT, made))
                hoard(minecraft).extract(ItemVariant.of(product), held(minecraft, product));
            hoard(minecraft).insert(ItemVariant.of(Items.IRON_INGOT), 1);
            WorksContent.order(minecraft, player(minecraft).getUUID(), ItemVariant.of(made), 1);
        });
    }

    /**
     * With a player's item on the forge's source pillar, its Officiant lays nothing: the item and the iron stay where they are until the item is taken
     * off, and the steel is then made.
     */
    private static void checkForgeSourceOccupied(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> source(minecraft).setStack(new ItemStack(Items.APPLE)));
        orderSteel(server);
        context.waitTicks(80);
        server.runOnServer(minecraft ->
        {
            expect(source(minecraft).getStack().is(Items.APPLE), "the apple on the source pillar became " + source(minecraft).getStack());
            expect(held(minecraft, Items.IRON_INGOT) == 1, "the iron left the hoard while the source pillar was taken: " + held(minecraft, Items.IRON_INGOT));
            expect(held(minecraft, Items.APPLE) == 0, "the apple went into the hoard");
            expect(((OfficiantTile) minecraft.overworld().getBlockEntity(SOURCE.below())).getClaim() == null, "the Officiant claimed a craft on a taken pillar");
            expect(!forge(minecraft).isCrafting(), "the forge started on the apple");
            source(minecraft).setStack(ItemStack.EMPTY);
        });
        waitFor(context, server, 900, "no Seething Steel was made once the apple was taken off", minecraft -> held(minecraft, VoidHeartItems.SEETHING_STEEL_INGOT) == 1);
        server.runOnServer(minecraft -> expect(WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs().isEmpty(), "the steel's order is still queued"));
    }

    private static void dropSteelAndIron(MinecraftServer minecraft)
    {
        var inventory = player(minecraft).getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
            if (inventory.getItem(slot).is(VoidHeartItems.SEETHING_STEEL_INGOT) || inventory.getItem(slot).is(Items.IRON_INGOT))
                inventory.setItem(slot, ItemStack.EMPTY);
    }

    private static int carried(MinecraftServer minecraft, net.minecraft.world.level.ItemLike item)
    {
        return player(minecraft).getInventory().countItem(item.asItem());
    }

    private static void aimAtForge(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.setSelectedSlot(0);
            inventory.setItem(0, ItemStack.EMPTY);
        });
        aimFrom(context, server, FORGE.getX() + 0.5, FLAT_FEET, FORGE.getZ() + 3.5, FORGE.getX() + 0.5, FLAT_FEET + 0.5, FORGE.getZ() + 0.5, 5);
    }

    /**
     * A right-click can't take a finished forge output its Officiant has yet to take back, and the output stays the Officiant's when the source pillar is
     * taken away before it looks: the steel reaches the hoard, is not made again, and leaves the forge unpinned. The same click takes a player's own
     * output.
     */
    private static void checkForgeResultKeptAndSourceTaken(ClientGameTestContext context, TestServerContext server)
    {
        aimAtForge(context, server);
        orderSteel(server);
        var frozen = false;
        for (int tick = 0; tick < 2000 && !frozen; tick++)
        {
            // Frozen in the server step the steel is seen, so the Officiant can't take it before the click lands
            frozen = server.computeOnServer(minecraft ->
            {
                var forge = forge(minecraft);
                if (forge.isCrafting() || !forge.getStack().is(VoidHeartItems.SEETHING_STEEL_INGOT))
                    return false;
                minecraft.tickRateManager().setFrozen(true);
                return true;
            });
            if (!frozen)
                context.waitTick();
        }
        expect(frozen, "the steel was never seen finished in the forge");
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            expect(forge(minecraft).getStack().is(VoidHeartItems.SEETHING_STEEL_INGOT), "a click took the Officiant's steel off the forge");
            expect(carried(minecraft, VoidHeartItems.SEETHING_STEEL_INGOT) == 0, "a click gave the player the Officiant's steel");
            minecraft.overworld().removeBlock(SOURCE, false);
            minecraft.tickRateManager().setFrozen(false);
        });
        waitFor(context, server, 100, "the Officiant never took its steel back once the source pillar was gone",
                minecraft -> held(minecraft, VoidHeartItems.SEETHING_STEEL_INGOT) == 1);
        server.runOnServer(minecraft ->
        {
            expect(forge(minecraft).getStack().isEmpty(), "the steel is still in the forge: " + forge(minecraft).getStack());
            expect(forge(minecraft).getPinned() == null, "the forge is still pinned to " + forge(minecraft).getPinned());
            expect(WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs().isEmpty(),
                    "the steel's order is still queued: " + WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs());
            minecraft.overworld().setBlockAndUpdate(SOURCE, VoidHeartBlocks.VOID_PILLAR.defaultBlockState());
        });
        waitFor(context, server, 400, "the forge never took the new source pillar back", minecraft -> source(minecraft).isForgeSource());

        // The same click takes an output no Officiant waits on
        server.runOnServer(minecraft -> forge(minecraft).setStack(new ItemStack(VoidHeartItems.SEETHING_STEEL_INGOT)));
        aimAtForge(context, server);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            expect(carried(minecraft, VoidHeartItems.SEETHING_STEEL_INGOT) == 1 && forge(minecraft).getStack().isEmpty(),
                    "a click did not take a player's own steel off the forge");
            dropSteelAndIron(minecraft);
        });
    }

    /**
     * A player taking the Officiant's iron off the source pillar while the forge crafts Arrogant Iron from it, before it is used, ends that craft: the
     * craft counts again, and the forge chooses afresh for the player's own iron, making Seething Steel on its Fiery build, which stays in the forge
     * rather than going to the hoard.
     */
    private static void checkForgeInputTakenByHand(ClientGameTestContext context, TestServerContext server)
    {
        orderFromIron(server, VoidHeartItems.ARROGANT_IRON_INGOT);
        var swapped = false;
        for (int tick = 0; tick < 2000 && !swapped; tick++)
        {
            // Taken in the server step the Arrogant Iron craft is seen running on the iron, before the forge can use it
            swapped = server.computeOnServer(minecraft ->
            {
                var officiant = (OfficiantTile) minecraft.overworld().getBlockEntity(SOURCE.below());
                var forge = forge(minecraft);
                if (officiant.getClaim() == null || officiant.isLaying() || !forge.isCrafting() || !source(minecraft).getStack().is(Items.IRON_INGOT))
                    return false;
                expect(forge.getPinned() != null && forge.getPinned().getPath().contains("arrogant_iron"), "the forge runs " + forge.getPinned()
                        + " rather than the Officiant's Arrogant Iron");
                var player = player(minecraft);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                useOn(player, SOURCE);
                expect(carried(minecraft, Items.IRON_INGOT) == 1, "the player did not get the iron off the source pillar");
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
                useOn(player, SOURCE);
                expect(source(minecraft).getStack().is(Items.IRON_INGOT), "the player's own iron was not set on the source pillar");
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                return true;
            });
            if (!swapped)
                context.waitTick();
        }
        expect(swapped, "the forge never crafted Arrogant Iron from the Officiant's iron");
        waitFor(context, server, 60, "the Officiant never let go of the craft whose iron was taken",
                minecraft -> ((OfficiantTile) minecraft.overworld().getBlockEntity(SOURCE.below())).getClaim() == null);
        waitFor(context, server, 900, "the forge never made anything from the player's iron", minecraft -> !forge(minecraft).getStack().isEmpty());
        context.waitTicks(30);
        server.runOnServer(minecraft ->
        {
            expect(forge(minecraft).getStack().is(VoidHeartItems.SEETHING_STEEL_INGOT),
                    "the forge kept to the abandoned craft and made " + forge(minecraft).getStack() + " from the player's iron");
            expect(held(minecraft, VoidHeartItems.SEETHING_STEEL_INGOT) == 0 && held(minecraft, VoidHeartItems.ARROGANT_IRON_INGOT) == 0,
                    "what the forge made from the player's iron went to the hoard");
            var jobs = WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs();
            expect(jobs.size() == 1 && jobs.getFirst().tasks().getFirst().remaining() == 1 && jobs.getFirst().tasks().getFirst().working() == 0,
                    "the craft whose iron was taken was not counted again: " + jobs);
            WorksState.get(minecraft).edit(player(minecraft).getUUID()).cancel(jobs.getFirst().id());
            forge(minecraft).takeOutput();
            dropSteelAndIron(minecraft);
            var inventory = player(minecraft).getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++)
                if (inventory.getItem(slot).is(Items.APPLE))
                    inventory.setItem(slot, ItemStack.EMPTY);
        });
    }

    private static final BlockPos INSERTER = FORGE.south();
    private static final BlockPos INSERTER_CHEST = FORGE.south(2);

    /**
     * An inserter pulling from the forge leaves a finished output its Officiant has yet to take back: the steel reaches the hoard, not the chest, and is
     * not made again. Once the Officiant is done, the inserter still empties the forge of a player's own craft.
     */
    private static void checkForgeResultKeptFromInserter(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(level.getBlockState(INSERTER).isAir() && level.getBlockState(INSERTER_CHEST).isAir(), "the spots behind the forge are taken");
            level.setBlockAndUpdate(INSERTER, VoidHeartBlocks.INVENTORY_INSERTER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
            level.setBlockAndUpdate(INSERTER_CHEST, Blocks.CHEST.defaultBlockState());
        });
        orderSteel(server);
        var tried = false;
        for (int tick = 0; tick < 2000 && !tried; tick++)
        {
            // The inserter is run in the same server step the steel is seen, so the Officiant can't take it first
            tried = server.computeOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                var forge = forge(minecraft);
                if (forge.isCrafting() || !forge.getStack().is(VoidHeartItems.SEETHING_STEEL_INGOT))
                    return false;
                // An apple set on the source pillar and taken back once the steel is done is not the craft's input, so the steel stays the Officiant's
                var player = player(minecraft);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
                useOn(player, SOURCE);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                useOn(player, SOURCE);
                expect(source(minecraft).getStack().isEmpty() && carried(minecraft, Items.APPLE) >= 1, "the apple was not set on the source pillar and taken back");
                expect(forge.isOfficiantsResult(), "taking the apple off the source pillar let the Officiant's steel go");
                net.voxelindustry.voidheart.common.content.inventorymover.InventoryInserterTile.transfer(level, INSERTER, level.getBlockState(INSERTER),
                        (net.voxelindustry.voidheart.common.content.inventorymover.InventoryInserterTile) level.getBlockEntity(INSERTER));
                expect(forge.getStack().is(VoidHeartItems.SEETHING_STEEL_INGOT), "the inserter took the Officiant's steel off the forge");
                return true;
            });
            if (!tried)
                context.waitTick();
        }
        expect(tried, "the steel was never seen finished in the forge");
        waitFor(context, server, 100, "the Officiant never took its steel back", minecraft -> held(minecraft, VoidHeartItems.SEETHING_STEEL_INGOT) == 1);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var chest = (net.minecraft.world.Container) level.getBlockEntity(INSERTER_CHEST);
            expect(chest.countItem(VoidHeartItems.SEETHING_STEEL_INGOT) == 0, "the inserter put the Officiant's steel in the chest");
            expect(WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs().isEmpty(),
                    "the steel's order is still queued: " + WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs());

            // A player's own output is still the inserter's to take
            forge(minecraft).setStack(new ItemStack(VoidHeartItems.SEETHING_STEEL_INGOT));
            net.voxelindustry.voidheart.common.content.inventorymover.InventoryInserterTile.transfer(level, INSERTER, level.getBlockState(INSERTER),
                    (net.voxelindustry.voidheart.common.content.inventorymover.InventoryInserterTile) level.getBlockEntity(INSERTER));
            expect(chest.countItem(VoidHeartItems.SEETHING_STEEL_INGOT) == 1 && forge(minecraft).getStack().isEmpty(),
                    "the inserter left a player's own steel in the forge");
            chest.clearContent();
            level.removeBlock(INSERTER_CHEST, false);
            level.removeBlock(INSERTER, false);
        });
    }

    /**
     * With the hoard full, the table's Officiant holds the shards that do not fit and puts them in once there is room.
     */
    private static void checkFullHoard(ClientGameTestContext context, TestServerContext server)
    {
        var cobble = ItemVariant.of(Items.COBBLESTONE);
        // The Void Pearl before left some shards behind
        server.runOnServer(minecraft ->
        {
            hoard(minecraft).extract(ItemVariant.of(VoidHeartItems.ENDER_SHARD), held(minecraft, VoidHeartItems.ENDER_SHARD));
            hoard(minecraft).insert(ItemVariant.of(Items.ENDER_PEARL), 1);
            hoard(minecraft).insert(cobble, hoard(minecraft).room());
            expect(hoard(minecraft).room() == 0, "the hoard still has room for " + hoard(minecraft).room());
            WorksContent.order(minecraft, player(minecraft).getUUID(), ItemVariant.of(VoidHeartItems.ENDER_SHARD), 4);
        });
        waitFor(context, server, 200, "the table's Officiant never held what did not fit", minecraft -> heldBy(minecraft, TABLE.below()) == 3);
        server.runOnServer(minecraft -> expect(held(minecraft, VoidHeartItems.ENDER_SHARD) == 1, "the hoard took " + held(minecraft, VoidHeartItems.ENDER_SHARD) + " shards"));
        server.runOnServer(minecraft -> hoard(minecraft).extract(cobble, 10));
        waitFor(context, server, 200, "the held shards never went in once there was room", minecraft -> held(minecraft, VoidHeartItems.ENDER_SHARD) == 4);
        // The table rests while its fold takes the result back, and the order goes after
        waitFor(context, server, 100, "the shards' order is still queued", minecraft -> WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs().isEmpty());
        server.runOnServer(minecraft ->
        {
            hoard(minecraft).extract(cobble, held(minecraft, Items.COBBLESTONE));
            hoard(minecraft).extract(ItemVariant.of(VoidHeartItems.ENDER_SHARD), 4);
        });
    }

    private static int heldBy(MinecraftServer minecraft, BlockPos officiant)
    {
        return minecraft.overworld().getBlockEntity(officiant) instanceof OfficiantTile tile
                ? tile.heldItems().stream().mapToInt(ItemStack::getCount).sum() : -1;
    }

    /**
     * Broken while laying a Void Pearl out, the altar's Officiant gives back what it had not laid yet, and the craft counts again.
     */
    private static void checkBrokenMidLaying(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            hoard(minecraft).insert(ItemVariant.of(VoidHeartItems.ENDER_SHARD), 1);
            hoard(minecraft).insert(ItemVariant.of(VoidHeartItems.OBSIDIAN_SHARD), 8);
            var plan = WorksContent.order(minecraft, player(minecraft).getUUID(), ItemVariant.of(VoidHeartItems.VOID_PEARL), 1).plan();
            expect(plan.tasks().size() == 1, "the Void Pearl from shards planned " + plan.tasks());
        });
        waitFor(context, server, 600, "the altar's Officiant never got three pillars in",
                minecraft -> minecraft.overworld().getBlockEntity(ALTAR.below()) instanceof OfficiantTile tile && tile.isLaying() && tile.layingStep() >= 3);
        server.runOnServer(minecraft ->
        {
            minecraft.overworld().destroyBlock(ALTAR.below(), false);
            var onPillars = pillarsFilled(minecraft);
            expect(onPillars >= 3 && onPillars < 8, onPillars + " pillars were filled when the Officiant broke");
            expect(held(minecraft, VoidHeartItems.OBSIDIAN_SHARD) == 8 - onPillars, "the hoard got back " + held(minecraft, VoidHeartItems.OBSIDIAN_SHARD)
                    + " obsidian shards with " + onPillars + " on the pillars");
            expect(held(minecraft, VoidHeartItems.ENDER_SHARD) == 1, "the unlaid catalyst did not come back");
            var jobs = WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs();
            expect(jobs.size() == 1 && jobs.getFirst().tasks().getFirst().remaining() == 1 && jobs.getFirst().tasks().getFirst().working() == 0,
                    "the broken craft was not counted again: " + jobs);

            // Put things back for what follows
            WorksState.get(minecraft).edit(player(minecraft).getUUID()).cancel(jobs.getFirst().id());
            for (var offset : PILLARS)
                if (minecraft.overworld().getBlockEntity(ALTAR.offset(offset)) instanceof net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile pillar)
                    pillar.setStack(ItemStack.EMPTY);
            hoard(minecraft).extract(ItemVariant.of(VoidHeartItems.OBSIDIAN_SHARD), 8);
            hoard(minecraft).extract(ItemVariant.of(VoidHeartItems.ENDER_SHARD), 1);
            officiant(minecraft.overworld(), ALTAR.below(), minecraft);
        });
        context.waitTicks(25);
    }

    private static void orderPearlFromShards(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            // Pearls made before would answer the wait for this one
            hoard(minecraft).extract(ItemVariant.of(VoidHeartItems.VOID_PEARL), held(minecraft, VoidHeartItems.VOID_PEARL));
            hoard(minecraft).insert(ItemVariant.of(VoidHeartItems.ENDER_SHARD), 1);
            hoard(minecraft).insert(ItemVariant.of(VoidHeartItems.OBSIDIAN_SHARD), 8);
            WorksContent.order(minecraft, player(minecraft).getUUID(), ItemVariant.of(VoidHeartItems.VOID_PEARL), 1);
        });
    }

    private static void waitForStep(ClientGameTestContext context, TestServerContext server, int step)
    {
        waitFor(context, server, 600, "the altar's Officiant never reached step " + step,
                minecraft -> minecraft.overworld().getBlockEntity(ALTAR.below()) instanceof OfficiantTile tile && tile.isLaying() && tile.layingStep() >= step);
    }

    private static net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile pillar(MinecraftServer minecraft, int index)
    {
        return altar(minecraft).pillarsAround().get(index);
    }

    /**
     * A player's item set on a pillar not laid yet goes to the hoard when the Officiant gets there, and the rite still gets its ingredient.
     */
    private static void checkItemOnPillarMidLaying(ClientGameTestContext context, TestServerContext server)
    {
        orderPearlFromShards(server);
        waitForStep(context, server, 2);
        server.runOnServer(minecraft ->
        {
            expect(pillar(minecraft, 6).getStack().isEmpty(), "pillar 6 was laid before the apple went on it");
            pillar(minecraft, 6).setStack(new ItemStack(Items.APPLE));
        });
        waitFor(context, server, 2000, "no Void Pearl came of the rite with an apple in the way", minecraft -> held(minecraft, VoidHeartItems.VOID_PEARL) >= 1);
        server.runOnServer(minecraft ->
        {
            expect(held(minecraft, Items.APPLE) == 1, "the apple did not reach the hoard: hoard " + hoard(minecraft).view() + ", officiant holds "
                    + heldBy(minecraft, ALTAR.below()) + ", pillars " + altar(minecraft).pillarsAround().stream().map(p -> p.getStack().toString()).toList()
                    + ", altar " + altar(minecraft).getStack() + ", drops " + minecraft.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(ALTAR).inflate(6)).stream().map(e -> e.getItem().toString()).toList());
            hoard(minecraft).extract(ItemVariant.of(Items.APPLE), 1);
            hoard(minecraft).extract(ItemVariant.of(VoidHeartItems.VOID_PEARL), held(minecraft, VoidHeartItems.VOID_PEARL));
        });
    }

    /**
     * A rite a player starts on what was laid so far stops the laying. Three shards in, the player adds a fourth on a pillar not laid yet and sets a
     * redstone lamp on the altar, starting a Void Lamp: the Officiant leaves the pillars to that rite, gives back what it had not laid, and the craft
     * counts again.
     */
    private static void checkRiteStartedMidLaying(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> Knowledge.lockOf(minecraft.registryAccess(), id("altar/void_lamp_from_redstone_lamp")).ifPresent(knowledge(minecraft)::grant));
        orderPearlFromShards(server);
        waitForStep(context, server, 3);
        var laid = server.computeOnServer(minecraft ->
        {
            var filled = pillarsFilled(minecraft);
            pillar(minecraft, 5).setStack(new ItemStack(VoidHeartItems.OBSIDIAN_SHARD));
            altar(minecraft).setStack(player(minecraft), new ItemStack(net.minecraft.world.item.Items.REDSTONE_LAMP));
            expect(altar(minecraft).isCrafting(), "the Void Lamp did not start on " + filled + " laid shards and the player's");
            return filled;
        });
        waitFor(context, server, 40, "the Officiant kept laying under the player's rite",
                minecraft -> minecraft.overworld().getBlockEntity(ALTAR.below()) instanceof OfficiantTile tile && !tile.isLaying() && tile.getClaim() == null);
        server.runOnServer(minecraft ->
        {
            expect(pillar(minecraft, 5).getStack().is(VoidHeartItems.OBSIDIAN_SHARD) && pillarsFilled(minecraft) == laid + 1,
                    "the pillars the player's rite uses were changed: " + altar(minecraft).pillarsAround().stream().map(p -> p.getStack().toString()).toList());
            expect(held(minecraft, VoidHeartItems.OBSIDIAN_SHARD) == 8 - laid && held(minecraft, VoidHeartItems.ENDER_SHARD) == 1,
                    "the unlaid ingredients did not come back: " + hoard(minecraft).view());
            var jobs = WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs();
            expect(jobs.size() == 1 && jobs.getFirst().tasks().getFirst().remaining() == 1 && jobs.getFirst().tasks().getFirst().working() == 0,
                    "the craft was not counted again: " + jobs);
            WorksState.get(minecraft).edit(player(minecraft).getUUID()).cancel(jobs.getFirst().id());
            hoard(minecraft).extract(ItemVariant.of(VoidHeartItems.OBSIDIAN_SHARD), 8);
            hoard(minecraft).extract(ItemVariant.of(VoidHeartItems.ENDER_SHARD), 1);
        });
        // The lamp is the player's, made from what lay on the pillars
        waitFor(context, server, 600, "the player's Void Lamp never finished", minecraft -> !altar(minecraft).isCrafting());
        server.runOnServer(minecraft ->
        {
            expect(altar(minecraft).getStack().is(VoidHeartBlocks.VOID_LAMP.asItem()), "the player's rite made " + altar(minecraft).getStack());
            altar(minecraft).takeResult();
            for (int index = 0; index < 8; index++)
                pillar(minecraft, index).setStack(ItemStack.EMPTY);
        });
    }

    /**
     * A player can't take the finished result of an Officiant's craft off the altar before the Officiant takes it back: the pearl reaches the hoard,
     * and the craft is not counted again.
     */
    private static void checkResultKeptForOfficiant(ClientGameTestContext context, TestServerContext server)
    {
        orderPearlFromShards(server);
        var tried = false;
        for (int tick = 0; tick < 2000 && !tried; tick++)
        {
            // Checked and tried within one server step, so the Officiant can't take the pearl in between
            tried = server.computeOnServer(minecraft ->
            {
                var altar = altar(minecraft);
                if (altar.isCrafting() || !altar.getStack().is(VoidHeartItems.VOID_PEARL))
                    return false;
                var player = player(minecraft);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                useOn(player, ALTAR);
                expect(altar.getStack().is(VoidHeartItems.VOID_PEARL), "the player took the Officiant's pearl off the altar");
                expect(player.getInventory().countItem(VoidHeartItems.VOID_PEARL) == 0, "the player got the Officiant's pearl");
                return true;
            });
            if (!tried)
                context.waitTick();
        }
        expect(tried, "the pearl was never seen finished on the altar");
        waitFor(context, server, 100, "the Officiant never took its pearl back", minecraft -> held(minecraft, VoidHeartItems.VOID_PEARL) == 1);
        server.runOnServer(minecraft -> expect(WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs().isEmpty(),
                "the pearl's order is still queued: " + WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs()));
    }

    /**
     * An altar broken with a finished Void Pearl on it before its Officiant takes it gives the pearl to the Officiant rather than the ground, and the
     * craft is not made again.
     */
    private static void checkAltarBrokenOnResult(ClientGameTestContext context, TestServerContext server)
    {
        orderPearlFromShards(server);
        var broken = false;
        for (int tick = 0; tick < 2000 && !broken; tick++)
        {
            broken = server.computeOnServer(minecraft ->
            {
                var altar = altar(minecraft);
                if (altar.isCrafting() || !altar.getStack().is(VoidHeartItems.VOID_PEARL))
                    return false;
                minecraft.overworld().destroyBlock(ALTAR, false);
                return true;
            });
            if (!broken)
                context.waitTick();
        }
        expect(broken, "the pearl was never seen finished on the altar");
        waitFor(context, server, 100, "the Officiant never got the pearl from the broken altar", minecraft -> held(minecraft, VoidHeartItems.VOID_PEARL) == 1);
        server.runOnServer(minecraft ->
        {
            var dropped = minecraft.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(ALTAR).inflate(4),
                    entity -> entity.getItem().is(VoidHeartItems.VOID_PEARL));
            expect(dropped.isEmpty(), "the broken altar dropped its pearl as well");
            expect(WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs().isEmpty(),
                    "the pearl's order is still queued: " + WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs());
            minecraft.overworld().setBlockAndUpdate(ALTAR, VoidHeartBlocks.VOID_ALTAR.defaultBlockState());
        });
        context.waitTicks(25);
    }

    /**
     * An altar replaced while its rite runs, with a player's apple set on the new one before the Officiant looks, leaves no result: the craft counts
     * again, and the apple is not taken for its result.
     */
    private static void checkAltarReplaced(ClientGameTestContext context, TestServerContext server)
    {
        orderPearlFromShards(server);
        waitFor(context, server, 900, "the altar never started the Void Pearl", minecraft -> altar(minecraft).isCrafting());
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.removeBlock(ALTAR, false);
            level.setBlockAndUpdate(ALTAR, VoidHeartBlocks.VOID_ALTAR.defaultBlockState());
            altar(minecraft).setStack(player(minecraft), new ItemStack(Items.APPLE));
        });
        waitFor(context, server, 80, "the Officiant never let go of the replaced altar's craft",
                minecraft -> minecraft.overworld().getBlockEntity(ALTAR.below()) instanceof OfficiantTile tile && tile.getClaim() == null);
        server.runOnServer(minecraft ->
        {
            var jobs = WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs();
            expect(jobs.size() == 1 && jobs.getFirst().tasks().getFirst().remaining() == 1 && jobs.getFirst().tasks().getFirst().working() == 0,
                    "the replaced altar's craft finished without a result: " + jobs);
            expect(held(minecraft, VoidHeartItems.VOID_PEARL) == 0, "a Void Pearl came from the replaced altar");
            expect(held(minecraft, Items.APPLE) == 0 && altar(minecraft).getStack().is(Items.APPLE),
                    "the apple on the new altar was taken for the craft's result: " + held(minecraft, Items.APPLE) + " in the hoard");
            altar(minecraft).takeResult();
            WorksState.get(minecraft).edit(player(minecraft).getUUID()).cancel(jobs.getFirst().id());
            for (var entity : minecraft.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(ALTAR).inflate(4)))
                entity.discard();
        });
        context.waitTicks(25);
    }

    /**
     * An Officiant works for its owner while they are not in the game: its own pocket, hoard and orders.
     */
    private static void checkOfflineOwner(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var pockets = VoidPocketState.getVoidPocketState(minecraft.overworld());
            var voidLevel = minecraft.getLevel(VoidHeart.VOID_WORLD_KEY);
            pockets.createPocket(voidLevel, ABSENT_OWNER);
            var coffer = pockets.getHeartData(ABSENT_OWNER).pocketPos().offset(3, 0, 3);
            voidLevel.setBlockAndUpdate(coffer, HoardContent.HOARD_COFFER.defaultBlockState());
            ((HoardCofferTile) voidLevel.getBlockEntity(coffer)).setBundle(new ItemStack(CrystalBundles.bundle(CrystalKind.RUBRUM, BundleSize.SMALL)));

            var level = minecraft.overworld();
            level.setBlockAndUpdate(ABSENT_TABLE, Blocks.CRAFTING_TABLE.defaultBlockState());
            level.setBlockAndUpdate(ABSENT_TABLE.below(), WorksContent.OFFICIANT.defaultBlockState());
            ((OfficiantTile) level.getBlockEntity(ABSENT_TABLE.below())).setOwner(ABSENT_OWNER);
            PocketHoard.get(minecraft).of(ABSENT_OWNER).insert(ItemVariant.of(Items.ENDER_PEARL), 1);
        });
        context.waitTicks(25);
        server.runOnServer(minecraft ->
        {
            expect(minecraft.getPlayerList().getPlayer(ABSENT_OWNER) == null, "the absent owner is in the game");
            var plan = WorksContent.order(minecraft, ABSENT_OWNER, ItemVariant.of(VoidHeartItems.ENDER_SHARD), 4).plan();
            expect(plan.isComplete(), "the absent owner's shards plan lacks " + plan.lacking());
        });
        waitFor(context, server, 200, "the absent owner's Officiant made no shards",
                minecraft -> PocketHoard.get(minecraft).of(ABSENT_OWNER).count(ItemVariant.of(VoidHeartItems.ENDER_SHARD)) == 4);
    }

    /**
     * A transform ordered from the hoard: the lesser apparatus held is laid as the catalyst, the rite drinks its Stillness through an Aspect Fold, and
     * the greater apparatus comes back.
     */
    /**
     * A Beam Focus ordered with nothing in the hoard waits, and is made once its raw ingredients are brought: cobblestone, obsidian, ender pearls and
     * Ravenous Gold ingots.
     */
    private static void checkResumesOnceSupplied(ClientGameTestContext context, TestServerContext server)
    {
        var focus = ItemVariant.of(VoidHeartBlocks.BEAM_FOCUS);
        server.runOnServer(minecraft ->
        {
            Knowledge.lockOf(minecraft.registryAccess(), id("altar/beam_focus")).ifPresent(knowledge(minecraft)::grant);
            Knowledge.lockOf(minecraft.registryAccess(), id("void_conduit")).ifPresent(knowledge(minecraft)::grant);
            for (var entry : List.copyOf(hoard(minecraft).view().entrySet()))
                hoard(minecraft).extract(entry.getKey(), entry.getValue());
            var ordered = WorksContent.order(minecraft, player(minecraft).getUUID(), focus, 1);
            expect(ordered.job() > 0, "the Beam Focus was not ordered: " + ordered.plan().tasks());
        });
        context.waitTicks(60);
        server.runOnServer(minecraft ->
        {
            hoard(minecraft).insert(ItemVariant.of(Items.COBBLESTONE), 64);
            hoard(minecraft).insert(ItemVariant.of(Items.OBSIDIAN), 64);
            hoard(minecraft).insert(ItemVariant.of(Items.ENDER_PEARL), 16);
            hoard(minecraft).insert(ItemVariant.of(VoidHeartItems.RAVENOUS_GOLD_INGOT), 64);
        });
        for (int waited = 0; waited < 3000 && server.computeOnServer(minecraft -> held(minecraft, VoidHeartBlocks.BEAM_FOCUS) == 0); waited += 20)
            context.waitTicks(20);
        server.runOnServer(minecraft ->
        {
            var jobs = WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs();
            expect(held(minecraft, VoidHeartBlocks.BEAM_FOCUS) == 1, "the Beam Focus was never made once supplied; queue " + jobs + ", hoard " + hoard(minecraft).view());
            expect(jobs.isEmpty(), "the Beam Focus order is still queued: " + jobs);
        });
    }

    private static final BlockPos FIELD_TABLE = new BlockPos(40, FLAT_FEET, 40);
    private static final BlockPos FIELD_PYLON = new BlockPos(52, FLAT_FEET, 40);
    // In the pylon's field but too far from the Officiant to reach it directly, so what it draws goes through the pylon
    private static final BlockPos FIELD_WELL  = new BlockPos(64, FLAT_FEET - 2, 40);

    private static String at(BlockPos pos)
    {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /**
     * An Officiant outside every field says it has none and lays nothing out. Under a pylon, which passes far less in a tick than a craft costs, it
     * gathers the energy over time and makes the ender shards ordered.
     */
    private static void checkOnPylonField(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            // The base's table is taken away, so only the table out on the field can make shards
            level.removeBlock(TABLE, false);
            level.setBlockAndUpdate(FIELD_TABLE, Blocks.CRAFTING_TABLE.defaultBlockState());
            officiant(level, FIELD_TABLE.below(), minecraft);
            hoard(minecraft).extract(ItemVariant.of(VoidHeartItems.ENDER_SHARD), held(minecraft, VoidHeartItems.ENDER_SHARD));
            hoard(minecraft).insert(ItemVariant.of(Items.ENDER_PEARL), 1);
            WorksContent.order(minecraft, player(minecraft).getUUID(), ItemVariant.of(VoidHeartItems.ENDER_SHARD), 4);
        });
        context.waitTicks(60);
        server.runOnServer(minecraft ->
        {
            var officiant = (OfficiantTile) minecraft.overworld().getBlockEntity(FIELD_TABLE.below());
            expect(officiant.energy() == OfficiantTile.Energy.NO_FIELD, "an Officiant outside every field says " + officiant.energy());
            expect(officiant.getClaim() == null && held(minecraft, VoidHeartItems.ENDER_SHARD) == 0, "an Officiant with no field made shards");
        });

        server.runCommand("setblock " + at(FIELD_WELL) + " voidheart:creative_void_well");
        server.runCommand("setblock " + at(FIELD_PYLON) + " voidheart:azurium_pylon");
        server.runCommand("setblock " + at(FIELD_PYLON.above()) + " voidheart:large_azurium_bundle[facing=up]");
        waitFor(context, server, 600, "the Officiant under the pylon never made the shards", minecraft -> held(minecraft, VoidHeartItems.ENDER_SHARD) >= 4);
        server.runOnServer(minecraft ->
        {
            var officiant = (OfficiantTile) minecraft.overworld().getBlockEntity(FIELD_TABLE.below());
            expect(officiant.energy() != OfficiantTile.Energy.NO_FIELD, "an Officiant under a pylon says it has no field");
            expect(officiant.getCharge() <= net.voxelindustry.voidheart.common.content.works.WorksBalance.MAX_CHARGE, "the Officiant holds " + officiant.getCharge());
            var level = minecraft.overworld();
            for (var pos : List.of(FIELD_PYLON.above(), FIELD_PYLON, FIELD_WELL, FIELD_TABLE, FIELD_TABLE.below()))
                level.removeBlock(pos, false);
            level.setBlockAndUpdate(TABLE, Blocks.CRAFTING_TABLE.defaultBlockState());
            hoard(minecraft).extract(ItemVariant.of(VoidHeartItems.ENDER_SHARD), held(minecraft, VoidHeartItems.ENDER_SHARD));
        });
        server.runCommand("kill @e[type=item]");
    }

    private static void checkTransform(ClientGameTestContext context, TestServerContext server)
    {
        var greater = new ItemVariant[1];
        server.runOnServer(minecraft ->
        {
            Knowledge.lockOf(minecraft.registryAccess(), id("altar/greater_shielding_apparatus")).ifPresent(knowledge(minecraft)::grant);
            var hoard = hoard(minecraft);
            hoard.insert(ItemVariant.of(VoidHeartItems.LESSER_SHIELDING_APPARATUS), 1);
            hoard.insert(ItemVariant.of(VoidHeartItems.RAVENOUS_GOLD_INGOT), 3);
            hoard.insert(ItemVariant.of(VoidHeartItems.SEETHING_STEEL_INGOT), 2);
            hoard.insert(ItemVariant.of(VoidHeartItems.VOID_PEARL), 2);
            hoard.insert(ItemVariant.of(CrystalBundles.shard(CrystalKind.AZURIUM)), 1);
            // Stillness for the rite, in the reserve, through a Fold beside the altar's relay
            hoard.addVessel(new net.voxelindustry.voidheart.common.content.hoard.PocketReserve.SlotId(0, 0), EssenceRegistry.STILLNESS, 32_000, 4_000);
            var level = minecraft.overworld();
            level.setBlockAndUpdate(ESSENCE_RELAY, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
            level.setBlockAndUpdate(ESSENCE_FOLD, HoardContent.ASPECT_FOLD.defaultBlockState());
            ((net.voxelindustry.voidheart.common.content.hoard.AspectFoldTile) level.getBlockEntity(ESSENCE_FOLD)).setOwner(player(minecraft).getUUID());

            var catalogue = net.voxelindustry.voidheart.common.content.works.WorksCatalogues.of(minecraft, player(minecraft).getUUID(), hoard);
            greater[0] = catalogue.outputs().stream().filter(variant -> variant.getItem() == VoidHeartItems.GREATER_SHIELDING_APPARATUS).findFirst().orElse(null);
            expect(greater[0] != null, "the greater apparatus is not makeable from the lesser one held");
        });
        context.waitTicks(45);
        server.runOnServer(minecraft ->
        {
            var plan = WorksContent.order(minecraft, player(minecraft).getUUID(), greater[0], 1).plan();
            expect(plan.isComplete() && plan.tasks().size() == 1, "the greater apparatus planned " + plan.tasks() + ", lacking " + plan.lacking());
        });
        waitFor(context, server, 2500, () -> "no greater apparatus came back; " + server.computeOnServer(minecraft ->
                        "Stillness left " + hoard(minecraft).reserve().amountOf(EssenceRegistry.STILLNESS)
                        + ", jobs " + WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs()
                        + ", officiant claim " + ((OfficiantTile) minecraft.overworld().getBlockEntity(ALTAR.below())).getClaim()
                        + ", altar crafting " + altar(minecraft).isCrafting() + " vacant " + altar(minecraft).isVacant() + " stack " + altar(minecraft).getStack()
                        + ", suppliers " + net.voxelindustry.voidheart.common.content.essence.RelayNetwork.suppliersOfReceiver(minecraft.overworld(), ALTAR, EssenceRegistry.STILLNESS).size()
                        + ", pillars " + pillarsFilled(minecraft) + ", hoard " + hoard(minecraft).view()),
                minecraft -> hoard(minecraft).view().keySet().stream().anyMatch(variant -> variant.getItem() == VoidHeartItems.GREATER_SHIELDING_APPARATUS));
        server.runOnServer(minecraft ->
        {
            expect(held(minecraft, VoidHeartItems.LESSER_SHIELDING_APPARATUS) == 0, "the lesser apparatus is still in the hoard");
            expect(hoard(minecraft).reserve().amountOf(EssenceRegistry.STILLNESS) == 2_000, "the rite drank "
                    + (4_000 - hoard(minecraft).reserve().amountOf(EssenceRegistry.STILLNESS)) + " Stillness");
        });
    }

    private static int pillarsFilled(MinecraftServer minecraft)
    {
        return (int) PILLARS.stream().filter(offset -> minecraft.overworld().getBlockEntity(ALTAR.offset(offset))
                instanceof net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile pillar && !pillar.getStack().isEmpty()).count();
    }

    private static void waitForFold(ClientGameTestContext context, int kind, int maxTicks, String message)
    {
        for (int tick = 0; tick < maxTicks; tick++)
        {
            if (context.computeOnClient(minecraft -> OfficiantFoldRender.open(kind)) > 0)
                return;
            context.waitTick();
        }
        throw new AssertionError(message);
    }

    private static void shot(ClientGameTestContext context, String name)
    {
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot(name);
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    private static net.voxelindustry.voidheart.common.content.altar.VoidAltarTile altar(MinecraftServer minecraft)
    {
        return (net.voxelindustry.voidheart.common.content.altar.VoidAltarTile) minecraft.overworld().getBlockEntity(ALTAR);
    }
}
