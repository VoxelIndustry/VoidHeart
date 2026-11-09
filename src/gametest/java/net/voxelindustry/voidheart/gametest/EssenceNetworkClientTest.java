package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceStorage;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageBlock.RavenousStoragePart;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageMatrixBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageMatrixBlock.RavenousStorageMatrixPart;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageMatrixTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile;
import net.voxelindustry.voidheart.common.content.essence.StorageFill;
import net.voxelindustry.voidheart.common.content.essence.StorageParts;
import net.voxelindustry.voidheart.common.content.essence.SufferingHarvest;
import net.voxelindustry.voidheart.common.content.tormenter.TormenterSouls;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Suffering drawn from deaths and from a tormenter, relays linking and beaming essence into a two block storage whose
 * pillars rise with its fill, and the link previews of a hovered or held relay.
 */
public class EssenceNetworkClientTest implements FabricClientGameTest
{
    private static final int FEET = -60;

    private static final BlockPos DEATH_COLLECTOR   = new BlockPos(0, FEET, 0);
    private static final BlockPos CONTRAVOID_COLLECTOR = new BlockPos(-6, FEET, 0);
    private static final BlockPos TORMENTER         = new BlockPos(20, FEET, 0);
    private static final BlockPos TORMENT_COLLECTOR = new BlockPos(23, FEET + 1, 0);

    private static final BlockPos FIERY_COLLECTOR = new BlockPos(40, FEET, 0);
    private static final BlockPos FIRST_RELAY     = new BlockPos(43, FEET, 0);
    private static final BlockPos SECOND_RELAY    = new BlockPos(47, FEET, 0);
    private static final BlockPos STORAGE         = new BlockPos(50, FEET, 0);
    private static final BlockPos WALL            = new BlockPos(44, FEET, 3);
    private static final BlockPos HIDDEN_STORAGE  = new BlockPos(44, FEET, 6);

    private static final int ROW_X = 60;

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

            checkSufferingFromDeath(context, server);
            checkSufferingFromTormenter(context, server);
            checkStoragePlacedWithRealClick(context, server);
            checkRelayFacesClickedSide(context, server);
            checkNetworkFillsStorage(context, server);
            showStorageRow(context, server);
            showItems(context, server);
            checkStorageBreaksWhole(server);
            checkMatrix(context, server);
        }
    }

    private static void checkSufferingFromDeath(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            level.setBlockAndUpdate(DEATH_COLLECTOR, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());

            // Rotten flesh attunes through the real interaction
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ROTTEN_FLESH));
            useOn(player, DEATH_COLLECTOR);
            expect(collector(minecraft, DEATH_COLLECTOR).storage().type() == EssenceRegistry.SUFFERING, "rotten flesh did not attune the collector to Suffering");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            // An azurium shard attunes a collector to Contravoid through the same interaction
            level.setBlockAndUpdate(CONTRAVOID_COLLECTOR, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.AZURIUM_SHARD));
            useOn(player, CONTRAVOID_COLLECTOR);
            expect(collector(minecraft, CONTRAVOID_COLLECTOR).storage().type() == EssenceRegistry.CONTRAVOID,
                    "an azurium shard did not attune the collector to Contravoid");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            level.removeBlock(CONTRAVOID_COLLECTOR, false);
        });

        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            var near = spawn(minecraft, EntityTypes.ZOMBIE, DEATH_COLLECTOR.getX() + 3.5, FEET, DEATH_COLLECTOR.getZ() + 0.5);
            near.hurtServer(level, level.damageSources().playerAttack(player(minecraft)), Float.MAX_VALUE);
            var expected = SufferingHarvest.yieldOf(near.getMaxHealth());
            var held = collector(minecraft, DEATH_COLLECTOR).storage().amount();
            expect(held == expected, "a zombie dying nearby gave " + held + " Suffering, expected " + expected);

            var far = spawn(minecraft, EntityTypes.ZOMBIE, DEATH_COLLECTOR.getX() + 12.5, FEET, DEATH_COLLECTOR.getZ() + 0.5);
            far.hurtServer(level, level.damageSources().playerAttack(player(minecraft)), Float.MAX_VALUE);
            expect(collector(minecraft, DEATH_COLLECTOR).storage().amount() == expected, "a zombie dying out of range gave Suffering");
        });

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, DEATH_COLLECTOR.getX() + 2, FEET, DEATH_COLLECTOR.getZ() - 4, DEATH_COLLECTOR.getX() + 2, FEET + 0.5, DEATH_COLLECTOR.getZ() + 0.5, 5);
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            var mob = spawn(minecraft, EntityTypes.ZOMBIE, DEATH_COLLECTOR.getX() + 3.5, FEET, DEATH_COLLECTOR.getZ() + 0.5);
            mob.hurtServer(level, level.damageSources().playerAttack(player(minecraft)), Float.MAX_VALUE);
        });
        // Strands take 20 ticks to reach the collector and start retracting after 30
        context.waitTicks(22);
        context.takeScreenshot("suffering-death-strands");
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runCommand("kill @e[type=!player]");
    }

    private static void checkSufferingFromTormenter(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            var tormenter = VoidHeartBlocks.MALIGNANT_TORMENTER;
            level.setBlockAndUpdate(TORMENTER, tormenter.defaultBlockState());
            var souls = new ItemStack(tormenter);
            souls.set(VoidHeartDataComponents.TORMENTER_SOULS, new TormenterSouls(EntityTypes.ZOMBIE, TormenterSouls.REQUIRED));
            tormenter.setPlacedBy(level, TORMENTER, tormenter.defaultBlockState(), null, souls);

            level.setBlockAndUpdate(TORMENT_COLLECTOR.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(TORMENT_COLLECTOR, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            collector(minecraft, TORMENT_COLLECTOR).storage().attune(EssenceRegistry.SUFFERING);
        });

        waitFor(context, server, 120, "a sated tormenter fed no Suffering to the collector beside it",
                minecraft -> collector(minecraft, TORMENT_COLLECTOR).storage().amount() > 0);
        server.runOnServer(minecraft ->
        {
            var held = collector(minecraft, TORMENT_COLLECTOR).storage().amount();
            expect(held % SufferingHarvest.yieldOf(20) == 0, "a tormented zombie gave " + held + " Suffering, not a zombie's worth");
        });
        server.runCommand("kill @e[type=item]");

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, TORMENTER.getX() + 1.5, FEET + 1, TORMENTER.getZ() - 5, TORMENTER.getX() + 2, FEET + 1, TORMENTER.getZ() + 0.5, 2);
        // Waits for a fresh draw, then for its strands to reach the collector
        waitFor(context, server, 80, "the collector never showed drawing from the tormenter",
                minecraft -> !collector(minecraft, TORMENT_COLLECTOR).isDrawing());
        waitFor(context, server, 80, "the collector never showed drawing from the tormenter",
                minecraft -> collector(minecraft, TORMENT_COLLECTOR).isDrawing());
        context.waitTicks(20);
        context.takeScreenshot("suffering-tormenter-strands");
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runCommand("kill @e[type=item]");
    }

    private static void checkStoragePlacedWithRealClick(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(VoidHeartBlocks.RAVENOUS_STORAGE));
        });
        aimFrom(context, server, STORAGE.getX() + 0.5, FEET + 1, STORAGE.getZ() - 2.5, STORAGE.getX() + 0.5, FEET - 0.5, STORAGE.getZ() + 0.5, 5);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);

        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            expect(level.getBlockState(STORAGE).is(VoidHeartBlocks.RAVENOUS_STORAGE)
                    && level.getBlockState(STORAGE).getValue(RavenousStorageBlock.PART) == RavenousStoragePart.BASE, "the storage base was not placed");
            expect(level.getBlockState(STORAGE.above()).is(VoidHeartBlocks.RAVENOUS_STORAGE)
                    && level.getBlockState(STORAGE.above()).getValue(RavenousStorageBlock.PART) == RavenousStoragePart.PILLAR, "the storage placed no pillars above");
            expect(level.getBlockEntity(STORAGE.above()) == null, "the pillar part has a block entity of its own");
            player(minecraft).getInventory().setItem(0, ItemStack.EMPTY);

            // Without room above, it does not place
            level.setBlockAndUpdate(new BlockPos(STORAGE.getX() + 3, FEET + 1, STORAGE.getZ()), Blocks.STONE.defaultBlockState());
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartBlocks.RAVENOUS_STORAGE));
            useOn(player, new BlockPos(STORAGE.getX() + 3, FEET - 1, STORAGE.getZ()));
            expect(!level.getBlockState(new BlockPos(STORAGE.getX() + 3, FEET, STORAGE.getZ())).is(VoidHeartBlocks.RAVENOUS_STORAGE),
                    "a storage was placed with no room for its pillars");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            level.setBlockAndUpdate(new BlockPos(STORAGE.getX() + 3, FEET + 1, STORAGE.getZ()), Blocks.AIR.defaultBlockState());
        });
    }

    private static void checkRelayFacesClickedSide(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            // Long and high enough to hide a storage behind it from every relay
            for (var x = WALL.getX() - 3; x <= WALL.getX() + 4; x++)
            {
                level.setBlockAndUpdate(new BlockPos(x, FEET, WALL.getZ()), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, FEET + 1, WALL.getZ()), Blocks.STONE.defaultBlockState());
            }
            player(minecraft).getInventory().setItem(0, new ItemStack(VoidHeartBlocks.RAVENOUS_RELAY));
        });
        // Clicks the south side of the wall
        aimFrom(context, server, WALL.getX() + 0.5, FEET, WALL.getZ() + 2.5, WALL.getX() + 0.5, FEET + 0.5, WALL.getZ() + 1, 5);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            var placed = level.getBlockState(WALL.south());
            expect(placed.is(VoidHeartBlocks.RAVENOUS_RELAY) && placed.getValue(BlockStateProperties.FACING) == Direction.SOUTH,
                    "a relay placed on the south of a wall faces " + (placed.is(VoidHeartBlocks.RAVENOUS_RELAY) ? placed.getValue(BlockStateProperties.FACING) : "nothing"));
            level.setBlockAndUpdate(WALL.south(), Blocks.AIR.defaultBlockState());
            player(minecraft).getInventory().setItem(0, ItemStack.EMPTY);
        });
    }

    private static void checkNetworkFillsStorage(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            level.setBlockAndUpdate(FIERY_COLLECTOR, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            var storage = collector(minecraft, FIERY_COLLECTOR).storage();
            storage.attune(EssenceRegistry.FIERY);
            storage.fill(EssenceRegistry.FIERY, RavenousCollectorTile.CAPACITY);
            level.setBlockAndUpdate(FIRST_RELAY, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
            level.setBlockAndUpdate(SECOND_RELAY, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());

            // In range of the first relay, but behind the wall
            placeStorage(level, HIDDEN_STORAGE, null);
        });

        waitFor(context, server, 200, "the relays carried no essence into the storage",
                minecraft -> storageTile(minecraft, STORAGE).storage().amount() > 0);
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            var tile = storageTile(minecraft, STORAGE);
            expect(tile.storage().type() == EssenceRegistry.FIERY, "the storage attuned to " + tile.storage().type().identifier());
            expect(level.getBlockState(STORAGE).getValue(RavenousStorageBlock.FILL) >= 1, "the pillars did not rise with the first essence");
            expect(storageTile(minecraft, HIDDEN_STORAGE).storage().amount() == 0, "a storage out of sight of every relay received essence");

            // Skips most of the filling
            tile.receive(EssenceRegistry.FIERY, RavenousStorageTile.CAPACITY / 2);
            var fill = StorageFill.fillOf(tile.storage().amount(), tile.storage().maximumAmount());
            expect(level.getBlockState(STORAGE).getValue(RavenousStorageBlock.FILL) == fill
                    && level.getBlockState(STORAGE.above()).getValue(RavenousStorageBlock.FILL) == fill, "the pillars of both parts do not show fill " + fill);
        });
        context.waitTicks(5);
        context.runOnClient(minecraft ->
        {
            if (!(minecraft.level.getBlockEntity(STORAGE) instanceof RavenousStorageTile tile) || tile.storage().type() != EssenceRegistry.FIERY
                    || tile.storage().amount() == 0)
                throw new AssertionError("the client does not see the essence the storage holds");
        });

        context.getInput().pressKey(InputConstants.KEY_F1);
        // The beams: the pulse moves along them through a route
        for (var frame = 0; frame < 3; frame++)
        {
            aimFrom(context, server, 45.5, FEET + 1.5, -5, 45.5, FEET + 0.5, 0.5, frame == 0 ? 10 : 3);
            context.takeScreenshot("relay-beams-" + frame);
        }
        server.runCommand("tick freeze");
        for (var step = 0; step < 6; step++)
        {
            aimFrom(context, server, 44 + step * 0.06, FEET + 0.8, -2.5 + step * 0.08, 45.5, FEET + 0.5, 0.5, 2);
            context.takeScreenshot("relay-beams-sweep-" + step);
        }
        server.runCommand("tick unfreeze");

        // Hovering a relay shows its links
        aimFrom(context, server, FIRST_RELAY.getX() + 0.5, FEET + 2.5, FIRST_RELAY.getZ() - 2.5, FIRST_RELAY.getX() + 0.5, FEET + 0.5, FIRST_RELAY.getZ() + 0.5, 5);
        expectClient(context, "the player is not looking at the relay", minecraft -> minecraft.hitResult instanceof BlockHitResult hit
                && hit.getBlockPos().equals(FIRST_RELAY));
        context.takeScreenshot("relay-hover-links");

        // Holding one previews its links, the hidden storage shown as blocked
        server.runOnServer(minecraft -> player(minecraft).getInventory().setItem(0, new ItemStack(VoidHeartBlocks.RAVENOUS_RELAY)));
        aimFrom(context, server, 45.5, FEET + 2.5, 0.5, 45.5, FEET - 0.5, 2.5, 5);
        context.takeScreenshot("relay-placement-preview");
        server.runOnServer(minecraft -> player(minecraft).getInventory().setItem(0, ItemStack.EMPTY));

        // A storage knows the relays that reach it, the same ones whose links include it
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            var reaching = RavenousRelayTile.relaysReaching(level, STORAGE);
            expect(reaching.contains(SECOND_RELAY), "the storage is not reached by the relay that fills it, found " + reaching);
            for (var relay : reaching)
                expect(RavenousRelayTile.scanLinks(level, relay).receivers().contains(STORAGE), "the storage counts " + relay + " which does not link to it");
            expect(STORAGE.equals(StorageParts.holderOf(level.getBlockState(STORAGE.above()), STORAGE.above())), "the pillar does not lead to its base");
            expect(RavenousRelayTile.relaysReaching(level, HIDDEN_STORAGE).isEmpty(), "the hidden storage counts a relay out of its sight");
        });

        // Hovering its pillars shows the relays that reach it, and Jade shows the storage itself
        aimFrom(context, server, STORAGE.getX() + 1.8, FEET + 2.5, STORAGE.getZ() - 1, STORAGE.getX() + 0.84, FEET + 0.9, STORAGE.getZ() + 0.16, 5);
        expectClient(context, "the player is not looking at the storage pillars", minecraft -> minecraft.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(STORAGE.above()));
        context.takeScreenshot("storage-hover-links");
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.waitTicks(20);
        context.takeScreenshot("storage-pillar-jade");
    }

    /**
     * Storages of each aspect, from empty to full, to compare the pillars and the essence between them.
     */
    private static void showStorageRow(ClientGameTestContext context, TestServerContext server)
    {
        EssenceType[] aspects = {EssenceRegistry.STILLNESS, EssenceRegistry.FIERY, EssenceRegistry.SUFFERING, EssenceRegistry.CONTRAVOID, EssenceRegistry.STILLNESS};
        float[] fills = {0.05F, 0.3F, 0.55F, 0.8F, 1F};
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            placeStorage(level, new BlockPos(ROW_X - 2, FEET, 0), null);
            for (var i = 0; i < aspects.length; i++)
            {
                var stored = new EssenceStorage(EssenceRegistry.EMPTY, RavenousStorageTile.CAPACITY);
                stored.attune(aspects[i]);
                stored.fill(aspects[i], Math.round(RavenousStorageTile.CAPACITY * fills[i]));
                placeStorage(level, new BlockPos(ROW_X + i * 2, FEET, 0), stored);
            }
        });
        context.waitTicks(5);
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, ROW_X + 3.5, FEET + 1.5, -6, ROW_X + 3.5, FEET + 1, 0.5, 10);
        context.takeScreenshot("storage-row");
        aimFrom(context, server, ROW_X + 6.5, FEET + 1.2, -2.2, ROW_X + 6.5, FEET + 1, 0.5, 3);
        context.takeScreenshot("storage-close");
        aimFrom(context, server, ROW_X + 3.5, FEET + 4, -3, ROW_X + 3.5, FEET + 1, 0.5, 3);
        context.takeScreenshot("storage-row-above");
        context.getInput().pressKey(InputConstants.KEY_F1);

        // Each half collides as its share of the pillars, caps included, and selects the whole storage
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            for (var i = 0; i < aspects.length; i++)
            {
                var base = new BlockPos(ROW_X + i * 2, FEET, 0);
                var top = StorageFill.pillarTop(level.getBlockState(base).getValue(RavenousStorageBlock.FILL));
                var baseState = level.getBlockState(base);
                var pillarState = level.getBlockState(base.above());
                var baseCollision = baseState.getCollisionShape(level, base);
                var pillarCollision = pillarState.getCollisionShape(level, base.above());
                expect(near(baseCollision.max(Direction.Axis.Y) * 16, Math.min(16, top)), "the base collision of fill " + top + " ends at " + baseCollision.max(Direction.Axis.Y) * 16);
                if (top > 16)
                    expect(!pillarCollision.isEmpty() && near(pillarCollision.max(Direction.Axis.Y) * 16, top - 16) && near(pillarCollision.min(Direction.Axis.X) * 16, 0.5),
                            "the pillar collision of fill " + top + " does not end at its caps");
                else
                    expect(pillarCollision.isEmpty(), "pillars still below the base top collide above it");

                // Both outlines are the same two block box, seen from their own block
                var baseOutline = baseState.getShape(level, base).bounds();
                var pillarOutline = pillarState.getShape(level, base.above()).bounds().move(0, 1, 0);
                expect(near(baseOutline.minY, 0) && near(baseOutline.maxY * 16, top), "the base outline of fill " + top + " spans " + baseOutline);
                expect(baseOutline.equals(pillarOutline), "the two halves outline different boxes: " + baseOutline + " and " + pillarOutline);
            }
        });
        aimFrom(context, server, ROW_X + 8.5, FEET + 1.2, -1.8, ROW_X + 8.2, FEET + 1.6, 0.2, 5);
        expectClient(context, "the player is not looking at the full storage's pillars", minecraft -> minecraft.hitResult instanceof BlockHitResult hit
                && hit.getBlockPos().equals(new BlockPos(ROW_X + 8, FEET + 1, 0)));
        context.takeScreenshot("storage-selection-pillars");
        aimFrom(context, server, ROW_X + 4.5, FEET + 0.8, -1.8, ROW_X + 4.5, FEET + 0.6, 0.5, 5);
        expectClient(context, "the player is not looking at the storage base", minecraft -> minecraft.hitResult instanceof BlockHitResult hit
                && hit.getBlockPos().equals(new BlockPos(ROW_X + 4, FEET, 0)));
        context.takeScreenshot("storage-selection-base");
        // From low down, so the outline around the caps stands against the sky
        aimFrom(context, server, ROW_X + 8.84, FEET, -1.5, ROW_X + 8.84, FEET + 1.75, 0.15, 5);
        expectClient(context, "the player is not looking at the full storage's pillars", minecraft -> minecraft.hitResult instanceof BlockHitResult hit
                && hit.getBlockPos().equals(new BlockPos(ROW_X + 8, FEET + 1, 0)));
        context.takeScreenshot("storage-selection-skyline");
    }

    private static void showItems(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.setItem(0, new ItemStack(VoidHeartBlocks.RAVENOUS_RELAY));
            inventory.setItem(1, new ItemStack(VoidHeartBlocks.RAVENOUS_STORAGE));
            inventory.setItem(2, new ItemStack(VoidHeartBlocks.RAVENOUS_STORAGE_MATRIX));
            inventory.setSelectedSlot(0);
        });
        aimFrom(context, server, ROW_X + 3.5, FEET + 3, -8, ROW_X + 3.5, FEET + 3, 10, 15);
        context.takeScreenshot("essence-relay-in-hand");
        context.getInput().pressKey(InputConstants.KEY_2);
        context.waitTicks(15);
        context.takeScreenshot("essence-storage-in-hand");
        context.getInput().pressKey(InputConstants.KEY_3);
        context.waitTicks(15);
        context.takeScreenshot("essence-matrix-in-hand");
        server.runOnServer(minecraft -> player(minecraft).getInventory().setSelectedSlot(0));
    }

    private static void checkStorageBreaksWhole(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            var player = player(minecraft);
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            var pos = new BlockPos(ROW_X + 2, FEET, 0);
            var held = storageTile(minecraft, pos).storage().amount();

            player.gameMode.destroyBlock(pos.above());
            expect(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir(), "breaking the pillars left part of the storage");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), item -> item.getItem().is(VoidHeartBlocks.RAVENOUS_STORAGE.asItem()));
            expect(drops.size() == 1, "breaking the pillars dropped " + drops.size() + " storages");
            var stored = drops.getFirst().getItem().get(VoidHeartDataComponents.ESSENCE_STORAGE);
            expect(stored != null && stored.amount() == held && stored.type() == EssenceRegistry.FIERY, "the dropped storage lost its essence");
            drops.forEach(ItemEntity::discard);

            var base = new BlockPos(ROW_X + 4, FEET, 0);
            player.gameMode.destroyBlock(base);
            expect(level.getBlockState(base.above()).isAir(), "breaking the base left its pillars standing");
            var baseDrops = level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(2), item -> item.getItem().is(VoidHeartBlocks.RAVENOUS_STORAGE.asItem()));
            expect(baseDrops.size() == 1, "breaking the base dropped " + baseDrops.size() + " storages");

            player.setGameMode(GameType.CREATIVE);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        server.runCommand("kill @e[type=item]");
    }

    private static final BlockPos MATRIX_GROUND = new BlockPos(90, FEET - 1, 0);
    private static final BlockPos MATRIX        = MATRIX_GROUND.above(2);
    private static final BlockPos MATRIX_RELAY  = MATRIX.east(5);
    private static final BlockPos MATRIX_SOURCE = MATRIX.east(8);

    /**
     * A core with a column on each face, placed by a real click, its slots each filling the column of their face.
     */
    private static void checkMatrix(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(VoidHeartBlocks.RAVENOUS_STORAGE_MATRIX));
        });
        aimFrom(context, server, MATRIX.getX() + 0.5, FEET + 1, MATRIX.getZ() - 0.8, MATRIX.getX() + 0.5, FEET, MATRIX.getZ() + 0.5, 5);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);

        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            var core = level.getBlockState(MATRIX);
            expect(core.is(VoidHeartBlocks.RAVENOUS_STORAGE_MATRIX) && core.getValue(RavenousStorageMatrixBlock.PART) == RavenousStorageMatrixPart.BASE,
                    "the matrix core is not one block above the ground clicked, found " + core);
            for (var face : RavenousStorageMatrixBlock.FACES)
            {
                var column = level.getBlockState(MATRIX.relative(face));
                expect(column.is(VoidHeartBlocks.RAVENOUS_STORAGE_MATRIX) && column.getValue(RavenousStorageMatrixBlock.PART) == RavenousStorageMatrixPart.of(face),
                        "no column on the " + face + " face of the matrix, found " + column);
                expect(level.getBlockEntity(MATRIX.relative(face)) == null, "the " + face + " column has a block entity of its own");
            }
            player(minecraft).getInventory().setItem(0, ItemStack.EMPTY);

            // Without room for all six columns, it does not place
            var player = player(minecraft);
            var blocked = MATRIX.west(4);
            level.setBlockAndUpdate(blocked.east(), Blocks.STONE.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartBlocks.RAVENOUS_STORAGE_MATRIX));
            useOn(player, blocked.below(2));
            expect(!level.getBlockState(blocked).is(VoidHeartBlocks.RAVENOUS_STORAGE_MATRIX), "a matrix was placed with no room for a column");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            level.setBlockAndUpdate(blocked.east(), Blocks.AIR.defaultBlockState());

            // Each slot fills the column of its face, in slot order
            var matrix = matrixTile(minecraft);
            matrix.receive(EssenceRegistry.STILLNESS, RavenousStorageMatrixTile.CAPACITY);
            matrix.receive(EssenceRegistry.FIERY, RavenousStorageMatrixTile.CAPACITY / 4);
            matrix.receive(EssenceRegistry.SUFFERING, RavenousStorageMatrixTile.CAPACITY / 2);
            matrix.receive(EssenceRegistry.CONTRAVOID, RavenousStorageMatrixTile.CAPACITY * 3 / 4);
            int[] amounts = {RavenousStorageMatrixTile.CAPACITY, RavenousStorageMatrixTile.CAPACITY / 4, RavenousStorageMatrixTile.CAPACITY / 2,
                    RavenousStorageMatrixTile.CAPACITY * 3 / 4, 0, 0};
            for (int slot = 0; slot < amounts.length; slot++)
            {
                var face = RavenousStorageMatrixBlock.FACES[slot];
                var expected = StorageFill.fillOf(amounts[slot], RavenousStorageMatrixTile.CAPACITY, StorageFill.MATRIX_MAX_FILL);
                var shown = level.getBlockState(MATRIX.relative(face)).getValue(RavenousStorageMatrixBlock.FILL);
                expect(shown == expected, "the " + face + " column shows fill " + shown + ", expected " + expected);
            }

            // Every block of it outlines the whole matrix, reaching as far as the full column on top
            var coreOutline = level.getBlockState(MATRIX).getShape(level, MATRIX).bounds();
            expect(near(coreOutline.maxY, 2), "the core outline spans " + coreOutline);
            for (var face : RavenousStorageMatrixBlock.FACES)
            {
                var beside = MATRIX.relative(face);
                var outline = level.getBlockState(beside).getShape(level, beside).bounds().move(face.getStepX(), face.getStepY(), face.getStepZ());
                expect(outline.equals(coreOutline), "the " + face + " column outlines " + outline + " instead of the whole matrix " + coreOutline);
            }

            // A relay in line with a column still reaches the core behind it
            level.setBlockAndUpdate(MATRIX_RELAY, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
            expect(RavenousRelayTile.scanLinks(level, MATRIX_RELAY).receivers().contains(MATRIX), "a relay does not see the matrix core past its own column");
            level.setBlockAndUpdate(MATRIX_SOURCE, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            var source = collector(minecraft, MATRIX_SOURCE).storage();
            source.attune(EssenceRegistry.FIERY);
            source.fill(EssenceRegistry.FIERY, RavenousCollectorTile.CAPACITY);
        });

        waitFor(context, server, 200, "the relay carried no essence into the matrix",
                minecraft -> matrixTile(minecraft).amountOf(EssenceRegistry.FIERY) > RavenousStorageMatrixTile.CAPACITY / 4);
        context.runOnClient(minecraft ->
        {
            if (!(minecraft.level.getBlockEntity(MATRIX) instanceof RavenousStorageMatrixTile tile) || tile.amountOf(EssenceRegistry.SUFFERING) == 0)
                throw new AssertionError("the client does not see the essence the matrix holds");
        });

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, MATRIX.getX() + 3.5, FEET + 2.5, MATRIX.getZ() + 3.5, MATRIX.getX() + 0.5, MATRIX.getY() + 0.5, MATRIX.getZ() + 0.5, 10);
        context.takeScreenshot("matrix-south-east");
        aimFrom(context, server, MATRIX.getX() - 3, FEET + 0.5, MATRIX.getZ() - 3.5, MATRIX.getX() + 0.5, MATRIX.getY() + 0.5, MATRIX.getZ() + 0.5, 5);
        context.takeScreenshot("matrix-north-west");
        aimFrom(context, server, MATRIX.getX() + 4, FEET + 0.5, MATRIX.getZ() - 1, MATRIX.getX() + 0.5, MATRIX.getY() + 0.5, MATRIX.getZ() + 0.5, 5);
        context.takeScreenshot("matrix-relay");
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, MATRIX.getX() + 0.5, FEET + 0.2, MATRIX.getZ() - 2.6, MATRIX.getX() + 0.5, MATRIX.getY() + 0.4, MATRIX.getZ() - 0.6, 5);
        context.takeScreenshot("matrix-selection");

        // Breaking a column breaks the core, which drops the matrix with its essence
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            var player = player(minecraft);
            var held = matrixTile(minecraft).storage().copy();
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            player.gameMode.destroyBlock(MATRIX.north());
            expect(level.getBlockState(MATRIX).isAir(), "breaking a column left the core");
            for (var face : RavenousStorageMatrixBlock.FACES)
                expect(level.getBlockState(MATRIX.relative(face)).isAir(), "breaking a column left the " + face + " column");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(MATRIX).inflate(3), item -> item.getItem().is(VoidHeartBlocks.RAVENOUS_STORAGE_MATRIX.asItem()));
            expect(drops.size() == 1, "breaking a column dropped " + drops.size() + " matrices");
            var stored = drops.getFirst().getItem().get(VoidHeartDataComponents.MULTI_ESSENCE_STORAGE);
            expect(stored != null && stored.amountOf(EssenceRegistry.SUFFERING) == held.amountOf(EssenceRegistry.SUFFERING)
                    && stored.amountOf(EssenceRegistry.STILLNESS) == held.amountOf(EssenceRegistry.STILLNESS), "the dropped matrix lost its essence");
            player.setGameMode(GameType.CREATIVE);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        server.runCommand("kill @e[type=item]");
    }

    private static RavenousStorageMatrixTile matrixTile(MinecraftServer minecraft)
    {
        if (level(minecraft).getBlockEntity(MATRIX) instanceof RavenousStorageMatrixTile tile)
            return tile;
        throw new AssertionError("No matrix core at " + MATRIX);
    }

    private static void placeStorage(ServerLevel level, BlockPos pos, EssenceStorage stored)
    {
        var block = VoidHeartBlocks.RAVENOUS_STORAGE;
        var stack = new ItemStack(block);
        if (stored != null)
            stack.set(VoidHeartDataComponents.ESSENCE_STORAGE, stored);
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        block.setPlacedBy(level, pos, block.defaultBlockState(), null, stack);
    }

    private static RavenousCollectorTile collector(MinecraftServer minecraft, BlockPos pos)
    {
        if (level(minecraft).getBlockEntity(pos) instanceof RavenousCollectorTile collector)
            return collector;
        throw new AssertionError("No collector at " + pos);
    }

    private static RavenousStorageTile storageTile(MinecraftServer minecraft, BlockPos pos)
    {
        if (level(minecraft).getBlockEntity(pos) instanceof RavenousStorageTile tile)
            return tile;
        throw new AssertionError("No storage at " + pos);
    }

    private static boolean near(double value, double expected)
    {
        return Math.abs(value - expected) < 1.0E-6;
    }

    private static ServerLevel level(MinecraftServer minecraft)
    {
        return minecraft.overworld();
    }
}
