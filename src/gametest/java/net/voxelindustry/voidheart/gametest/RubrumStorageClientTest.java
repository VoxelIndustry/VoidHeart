package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.energy.storage.CellSocketTile;
import net.voxelindustry.voidheart.common.energy.storage.ConstructFrameBlock;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.FramePart;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.Size;
import net.voxelindustry.voidheart.common.energy.storage.RubrumBalance;
import net.voxelindustry.voidheart.common.energy.storage.RubrumBudding;
import net.voxelindustry.voidheart.common.energy.storage.RubrumCellItem;
import net.voxelindustry.voidheart.common.energy.storage.RubrumConstructTile;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.energy.storage.SocketBundle;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.function.Function;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * W2 Rubrum storage in a real world: a small construct shown by its preview, formed from its frame, given bundles by a
 * real click and by use, charged by a creative well at its rate, grown a bud, broken and set back with its charge and
 * bundles, and a cell charged then emptied through a socket. A large construct forms too, and both survive a reload.
 */
public class RubrumStorageClientTest implements FabricClientGameTest
{
    private static final BlockPos SMALL  = new BlockPos(0, FLAT_FEET + 1, 10);
    private static final BlockPos LARGE  = new BlockPos(-14, FLAT_FEET + 2, 10);
    private static final BlockPos WELL   = new BlockPos(7, FLAT_FEET, 10);
    private static final BlockPos SOCKET = new BlockPos(4, FLAT_FEET, 5);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("rubrum_construct_core", "rubrum_construct_frame", "rubrum_cell", "cell_socket");

        TestWorldSave save;
        long largeStored;
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode creative @a");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(10);

            formSmall(context, server);
            charge(context, server);
            bud(context, server);
            breakAndReplace(context, server);
            cellRoundTrip(context, server);
            largeStored = formLarge(context, server);
            items(context, server);
        }

        try (var world = save.open())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            context.waitTicks(30);

            var small = core(server, SMALL, tile -> new long[]{tile.isFormed() ? 1 : 0, tile.stored(), tile.capacity(), tile.maxRate()});
            expect(small[0] == 1, "The small construct should still be formed after a reload");
            expect(small[1] > 0 && small[2] == 750_000 && small[3] == 64,
                    "The small construct should keep its charge and bundles after a reload, holds " + small[1] + " / " + small[2] + " at " + small[3]);
            var large = core(server, LARGE, tile -> new long[]{tile.isFormed() ? 1 : 0, tile.stored(), tile.capacity(), tile.maxRate()});
            expect(large[0] == 1 && large[1] == largeStored && large[2] == 8_000_000 && large[3] == 640,
                    "The large construct should come back as it was, holds " + large[1] + " / " + large[2] + " at " + large[3]);
        }
    }

    //
    // Small construct
    //

    private static void formSmall(ClientGameTestContext context, TestServerContext server)
    {
        setBlock(server, SMALL, RubrumContent.RUBRUM_CONSTRUCT_CORE);
        context.waitTicks(2);

        // An empty hand on a loose core shows the small frame
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            useAt(minecraft, SMALL, Vec3.atCenterOf(SMALL).add(0, 0.5, 0));
        });
        expect(core(server, SMALL, RubrumConstructTile::previewSize) == Size.SMALL, "The first use on a loose core should preview the small frame");

        // Half the frame up, so the ghosts show only what is missing
        server.runOnServer(minecraft ->
        {
            for (var offset : ConstructLayout.frame(Size.SMALL))
            {
                if (offset.getY() < 0)
                    minecraft.overworld().setBlock(SMALL.offset(offset), RubrumContent.RUBRUM_CONSTRUCT_FRAME.defaultBlockState(), Block.UPDATE_ALL);
            }
        });
        context.waitTicks(5);
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, SMALL.getX() + 4.5, FLAT_FEET, SMALL.getZ() - 3.5, SMALL.getX() + 0.5, SMALL.getY() + 0.5, SMALL.getZ() + 0.5, 5);
        context.takeScreenshot("rubrum-construct-preview");
        context.getInput().pressKey(InputConstants.KEY_F1);
        expect(!core(server, SMALL, RubrumConstructTile::isFormed), "Half a frame should not form a construct");

        buildFrame(server, SMALL, Size.SMALL);
        waitFor(context, server, 40, "The small construct should form once its frame is whole",
                minecraft -> minecraft.overworld().getBlockEntity(SMALL) instanceof RubrumConstructTile tile && tile.isFormed() && tile.layout() == Size.SMALL);
        expectPart(server, SMALL.offset(1, 0, 1), FramePart.PILLAR_PP);
        expectPart(server, SMALL.offset(-1, -1, -1), FramePart.CORNER_NNN);
        expectPart(server, SMALL.offset(1, 1, -1), FramePart.CORNER_PPN);
        expectPart(server, SMALL.offset(0, 1, 1), FramePart.BEAM_X_PP);

        // A real right click through the east face sets the azurium in the east socket
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getInventory().setSelectedSlot(0);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartBlocks.SMALL_AZURIUM_BUNDLE));
        });
        aimFrom(context, server, SMALL.getX() + 4.5, FLAT_FEET, SMALL.getZ() + 0.5, SMALL.getX() + 0.5, SMALL.getY() + 0.5, SMALL.getZ() + 0.5, 5);
        rightClick(context);
        var east = ConstructLayout.socketIndex(Size.SMALL, new BlockPos(1, 0, 0));
        expect(core(server, SMALL, tile -> tile.sockets().get(east)) == SocketBundle.SMALL_AZURIUM,
                "A right click on the core with an azurium bundle should set it in the socket facing the player");

        // Three small rubrum by use, aimed at the sockets on the other sides
        for (var socket : new BlockPos[]{new BlockPos(-1, 0, 0), new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)})
        {
            server.runOnServer(minecraft ->
            {
                player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartBlocks.SMALL_RUBRUM_BUNDLE));
                useAt(minecraft, SMALL, Vec3.atCenterOf(SMALL.offset(socket)));
            });
        }
        var stats = core(server, SMALL, tile -> new long[]{tile.capacity(), tile.maxRate(), tile.sockets().count()});
        expect(stats[0] == 750_000 && stats[1] == 64 && stats[2] == 4,
                "Three small rubrum and a small azurium should hold 750,000 at 64 a tick, got " + stats[0] + " at " + stats[1] + " from " + stats[2] + " bundles");
    }

    private static void charge(ClientGameTestContext context, TestServerContext server)
    {
        expect(core(server, SMALL, RubrumConstructTile::stored) == 0, "A new construct should hold nothing");
        setBlock(server, WELL, net.voxelindustry.voidheart.common.energy.creative.CreativeEnergyContent.CREATIVE_VOID_WELL);
        context.waitTicks(3);

        var before = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), storedAt(minecraft, SMALL)});
        context.waitTicks(40);
        var after = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), storedAt(minecraft, SMALL)});
        var ticks = after[0] - before[0];
        var gained = after[1] - before[1];
        expect(gained > 0, "A creative well should charge the construct in its field");
        expect(gained <= 64 * (ticks + 1) && gained >= 64 * (ticks - 2),
                "The construct should charge at its azurium rate, 64 a tick: gained " + gained + " over " + ticks + " ticks");
        expect(core(server, SMALL, RubrumConstructTile::flow) > 0.5F, "A charging construct should show its flow as charging");

        // The network moves energy through it as a holder, capped per tick however often it asks
        server.runOnServer(minecraft ->
        {
            var tile = (RubrumConstructTile) minecraft.overworld().getBlockEntity(SMALL);
            var first = tile.extract(50, false);
            var second = tile.extract(50, false);
            expect(first == 50 && second == 14, "Extracting twice in a tick should stop at the rate, took " + first + " then " + second);
        });

        server.runCommand("execute positioned " + SMALL.getX() + " " + SMALL.getY() + " " + SMALL.getZ() + " run voidheart energy construct fill 60");
        context.waitTicks(30);
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, SMALL.getX() + 4.5, FLAT_FEET + 1, SMALL.getZ() - 2.0, SMALL.getX() + 0.5, SMALL.getY() + 0.3, SMALL.getZ() + 0.5, 5);
        context.takeScreenshot("rubrum-construct-charging");
        aimFrom(context, server, SMALL.getX() - 3.5, FLAT_FEET, SMALL.getZ() + 4.5, SMALL.getX() + 0.5, SMALL.getY() + 0.3, SMALL.getZ() + 0.5, 5);
        context.takeScreenshot("rubrum-construct-charging-back");

        // Close to an azurium bundle, frozen, to look for flicker in the veins and arcs
        server.runCommand("tick freeze");
        for (var step = 0; step < 6; step++)
        {
            aimFrom(context, server, SMALL.getX() + 3.2 + step * 0.05, FLAT_FEET, SMALL.getZ() - 1.2 + step * 0.08,
                    SMALL.getX() + 1.5, SMALL.getY() + 0.5, SMALL.getZ() + 0.5, 2);
            context.takeScreenshot("rubrum-construct-sweep-" + step);
        }
        server.runCommand("tick unfreeze");

        // Arcs come and go every 10 to 30 ticks, so a few frames apart should catch some
        aimFrom(context, server, SMALL.getX() + 2.9, FLAT_FEET, SMALL.getZ() - 0.9, SMALL.getX() + 1.4, SMALL.getY() + 0.5, SMALL.getZ() + 0.5, 2);
        for (var frame = 0; frame < 6; frame++)
        {
            context.takeScreenshot("rubrum-construct-arcs-" + frame);
            context.waitTicks(3);
        }
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    private static void bud(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("execute positioned " + SMALL.getX() + " " + SMALL.getY() + " " + SMALL.getZ() + " run voidheart energy construct fill 95");
        server.runCommand("execute positioned " + SMALL.getX() + " " + SMALL.getY() + " " + SMALL.getZ() + " run voidheart energy construct bud 60");
        waitFor(context, server, 45, "A construct above 90% should pick a spot for its bud",
                minecraft -> minecraft.overworld().getBlockEntity(SMALL) instanceof RubrumConstructTile tile && tile.budPos() != null);
        expect(core(server, SMALL, tile -> tile.budding().stage()) == RubrumBudding.Stage.SHARD, "A bud past halfway should show its shard");

        var spot = core(server, SMALL, RubrumConstructTile::budPos);
        var face = core(server, SMALL, RubrumConstructTile::budFace);
        expect(face != Direction.DOWN && !ConstructLayout.isInside(Size.SMALL, spot.subtract(SMALL)),
                "The bud should grow on an outer face of the frame, not under it, got " + spot.subtract(SMALL) + " facing " + face);
        expect(server.computeOnServer(minecraft -> minecraft.overworld().getBlockState(spot.relative(face.getOpposite())).is(RubrumContent.RUBRUM_CONSTRUCT_FRAME)),
                "The bud should sit against a frame block");

        context.getInput().pressKey(InputConstants.KEY_F1);
        // From the side of the bud, since a crystal seen tip on is only a thin cross
        var look = Vec3.atCenterOf(spot).subtract(new Vec3(face.getUnitVec3i()).scale(0.3));
        var side = face.getAxis() == Direction.Axis.Y ? Direction.EAST : face.getClockWise();
        var from = look.add(new Vec3(face.getUnitVec3i()).scale(1.6)).add(new Vec3(side.getUnitVec3i()).scale(1.6));
        aimFrom(context, server, from.x, Math.max(FLAT_FEET, from.y - 1.6), from.z, look.x, look.y, look.z, 5);
        context.takeScreenshot("rubrum-construct-bud-shard");
        context.getInput().pressKey(InputConstants.KEY_F1);

        var storedBefore = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), storedAt(minecraft, SMALL)});
        server.runCommand("execute positioned " + SMALL.getX() + " " + SMALL.getY() + " " + SMALL.getZ() + " run voidheart energy construct bud 100");
        waitFor(context, server, 45, "A ready bud should grow into a small rubrum bundle",
                minecraft -> minecraft.overworld().getBlockState(spot).is(VoidHeartBlocks.SMALL_RUBRUM_BUNDLE));
        var storedAfter = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), storedAt(minecraft, SMALL)});
        var spent = storedBefore[1] - storedAfter[1] + 64 * (storedAfter[0] - storedBefore[0] + 1);
        expect(spent >= RubrumBalance.BUD_COST, "Growing the bud should spend " + RubrumBalance.BUD_COST + ", the construct went from "
                + storedBefore[1] + " to " + storedAfter[1]);
        expect(core(server, SMALL, tile -> tile.budding().stage()) == RubrumBudding.Stage.NONE, "The bud should start over once grown");
    }

    private static void breakAndReplace(ClientGameTestContext context, TestServerContext server)
    {
        var held = server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var tile = (RubrumConstructTile) level.getBlockEntity(SMALL);
            var stored = tile.stored();
            level.destroyBlock(SMALL, true);
            return stored;
        });
        context.waitTicks(5);
        expectPart(server, SMALL.offset(1, 0, 1), FramePart.LOOSE);

        var dropped = server.computeOnServer(minecraft ->
        {
            var items = minecraft.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(SMALL).inflate(3),
                    item -> item.getItem().is(RubrumContent.RUBRUM_CONSTRUCT_CORE.asItem()));
            expect(items.size() == 1, "Breaking the core should drop it once, dropped " + items.size());
            var stack = items.getFirst().getItem().copy();
            items.getFirst().discard();
            return stack;
        });
        var contents = dropped.get(RubrumContent.CONSTRUCT_CONTENTS);
        expect(contents != null && contents.stored() == held, "The dropped core should keep its " + held + " energy, keeps " + (contents == null ? "nothing" : contents.stored()));
        expect(contents.sockets().size() == 4, "The dropped core should keep its four bundles, keeps " + contents.sockets().size());

        // Used on the standing frame, the core goes back in its middle
        server.runOnServer(minecraft ->
        {
            player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, dropped);
            var pillar = SMALL.offset(1, 0, 1);
            useAt(minecraft, pillar, Vec3.atCenterOf(pillar).add(0.5, 0, 0));
        });
        expect(server.computeOnServer(minecraft -> minecraft.overworld().getBlockState(SMALL).is(RubrumContent.RUBRUM_CONSTRUCT_CORE)),
                "Using the core on its frame should set it back in the middle");
        waitFor(context, server, 40, "The replaced core should form again",
                minecraft -> minecraft.overworld().getBlockEntity(SMALL) instanceof RubrumConstructTile tile && tile.isFormed());
        var restored = core(server, SMALL, tile -> new long[]{tile.capacity(), tile.maxRate(), tile.sockets().count()});
        expect(restored[0] == 750_000 && restored[1] == 64 && restored[2] == 4, "The replaced core should bring its bundles back");
        expect(core(server, SMALL, RubrumConstructTile::stored) >= held, "The replaced core should bring its charge back");

        // Rubrum holding the charge cannot be taken out: 750,000 would drop to 500,000 under what is stored
        server.runCommand("execute positioned " + SMALL.getX() + " " + SMALL.getY() + " " + SMALL.getZ() + " run voidheart energy construct fill 90");
        var taken = server.computeOnServer(minecraft ->
        {
            var tile = (RubrumConstructTile) minecraft.overworld().getBlockEntity(SMALL);
            return tile.removeBundle(Vec3.atCenterOf(SMALL.west()));
        });
        expect(taken instanceof RubrumConstructTile.Removal.Held, "Rubrum the rest could not hold the charge without should stay, got " + taken);
    }

    //
    // Cell
    //

    private static void cellRoundTrip(ClientGameTestContext context, TestServerContext server)
    {
        setBlock(server, SOCKET, RubrumContent.CELL_SOCKET);
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RubrumContent.RUBRUM_CELL));
            useAt(minecraft, SOCKET, Vec3.atCenterOf(SOCKET));
        });
        expect(cellCharge(server) == 0 && server.computeOnServer(minecraft -> !socket(minecraft).getCell().isEmpty()), "Using a cell on a socket should set it there");

        // Powered, the socket charges its cell from the well's field
        setBlock(server, SOCKET.east(), Blocks.REDSTONE_BLOCK);
        waitFor(context, server, 80, "A powered socket should charge its cell", minecraft -> RubrumCellItem.charge(socket(minecraft).getCell()) >= 3_000);
        var charged = cellCharge(server);
        expect(charged <= RubrumBalance.CELL_SOCKET_RATE * 80, "The socket should charge at its rate");

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, SOCKET.getX() - 1.5, FLAT_FEET, SOCKET.getZ() - 2.5, SOCKET.getX() + 0.5, SOCKET.getY() + 0.7, SOCKET.getZ() + 0.5, 5);
        context.takeScreenshot("cell-socket-charging");
        context.getInput().pressKey(InputConstants.KEY_F1);

        // Taken out, the cell carries its charge
        var carried = server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            minecraft.overworld().setBlock(SOCKET.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            useAt(minecraft, SOCKET, Vec3.atCenterOf(SOCKET));
            return player.getMainHandItem().copy();
        });
        expect(carried.is(RubrumContent.RUBRUM_CELL) && RubrumCellItem.charge(carried) >= charged,
                "A cell taken out should carry its charge, carries " + RubrumCellItem.charge(carried));

        // Back in and unpowered, it empties into the field
        server.runOnServer(minecraft -> useAt(minecraft, SOCKET, Vec3.atCenterOf(SOCKET)));
        waitFor(context, server, 120, "An unpowered socket should discharge its cell into the field", minecraft -> RubrumCellItem.charge(socket(minecraft).getCell()) == 0);

        server.runCommand("setblock " + SOCKET.getX() + " " + SOCKET.getY() + " " + SOCKET.getZ() + " air destroy");
        context.waitTicks(5);
        var drops = server.computeOnServer(minecraft -> minecraft.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(SOCKET).inflate(2)).stream()
                .map(item -> item.getItem().getItem()).toList());
        expect(drops.contains(RubrumContent.RUBRUM_CELL) && drops.contains(RubrumContent.CELL_SOCKET.asItem()),
                "A broken socket should drop itself and its cell, dropped " + drops);
        server.runCommand("kill @e[type=item]");
    }

    //
    // Large construct
    //

    private static long formLarge(ClientGameTestContext context, TestServerContext server)
    {
        setBlock(server, LARGE, RubrumContent.RUBRUM_CONSTRUCT_CORE);
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            useAt(minecraft, LARGE, Vec3.atCenterOf(LARGE).add(0, 0.5, 0));
            useAt(minecraft, LARGE, Vec3.atCenterOf(LARGE).add(0, 0.5, 0));
        });
        expect(core(server, LARGE, RubrumConstructTile::previewSize) == Size.LARGE, "A second use should preview the large frame");
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, LARGE.getX() + 6.5, FLAT_FEET + 2, LARGE.getZ() - 5.5, LARGE.getX() + 0.5, LARGE.getY() + 0.5, LARGE.getZ() + 0.5, 5);
        context.takeScreenshot("rubrum-construct-preview-large");
        context.getInput().pressKey(InputConstants.KEY_F1);

        buildFrame(server, LARGE, Size.LARGE);
        waitFor(context, server, 40, "The large construct should form once its frame is whole",
                minecraft -> minecraft.overworld().getBlockEntity(LARGE) instanceof RubrumConstructTile tile && tile.isFormed() && tile.layout() == Size.LARGE);

        // N0's advanced construct: 8 medium rubrum and 4 medium azurium
        var sockets = ConstructLayout.sockets(Size.LARGE);
        for (var index = 0; index < 12; index++)
        {
            var offset = sockets.get(index);
            var bundle = index < 8 ? VoidHeartBlocks.MEDIUM_RUBRUM_BUNDLE : VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE;
            server.runOnServer(minecraft ->
            {
                player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(bundle));
                useAt(minecraft, LARGE, Vec3.atCenterOf(LARGE.offset(offset)));
            });
        }
        var stats = core(server, LARGE, tile -> new long[]{tile.capacity(), tile.maxRate()});
        expect(stats[0] == 8_000_000 && stats[1] == 640, "N0's advanced construct should hold 8,000,000 at 640 a tick, got " + stats[0] + " at " + stats[1]);

        server.runCommand("execute positioned " + LARGE.getX() + " " + LARGE.getY() + " " + LARGE.getZ() + " run voidheart energy construct fill 70");
        context.waitTicks(30);
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, LARGE.getX() + 7.5, FLAT_FEET + 1, LARGE.getZ() - 3.5, LARGE.getX() + 0.5, LARGE.getY() + 0.5, LARGE.getZ() + 0.5, 5);
        context.takeScreenshot("rubrum-construct-large");
        // Straight at a face, then grazing along it, for the brace and its plates against the frame
        aimFrom(context, server, LARGE.getX() + 5.5, FLAT_FEET + 0.4, LARGE.getZ() + 0.5, LARGE.getX() + 0.5, LARGE.getY() + 0.5, LARGE.getZ() + 0.5, 5);
        context.takeScreenshot("rubrum-construct-large-face");
        aimFrom(context, server, LARGE.getX() + 3.6, FLAT_FEET + 0.4, LARGE.getZ() - 3.5, LARGE.getX() + 3.0, LARGE.getY() + 0.5, LARGE.getZ() + 0.5, 5);
        context.takeScreenshot("rubrum-construct-large-grazing");
        context.getInput().pressKey(InputConstants.KEY_F1);
        return core(server, LARGE, RubrumConstructTile::stored);
    }

    /**
     * The hotbar: cells at each fill, a charged core, a frame and a socket, in hand and in their slots.
     */
    private static void items(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            var fills = new long[]{0, 100_000, 250_000, RubrumBalance.CELL_CAPACITY};
            for (var slot = 0; slot < fills.length; slot++)
            {
                var cell = new ItemStack(RubrumContent.RUBRUM_CELL);
                RubrumCellItem.setCharge(cell, fills[slot]);
                inventory.setItem(slot, cell);
            }
            var core = new ItemStack(RubrumContent.RUBRUM_CONSTRUCT_CORE);
            core.set(RubrumContent.CONSTRUCT_CONTENTS, new net.voxelindustry.voidheart.common.energy.storage.ConstructContents(500_000,
                    java.util.List.of(new net.voxelindustry.voidheart.common.energy.storage.SocketSet.Entry(0, SocketBundle.SMALL_RUBRUM),
                            new net.voxelindustry.voidheart.common.energy.storage.SocketSet.Entry(1, SocketBundle.SMALL_RUBRUM),
                            new net.voxelindustry.voidheart.common.energy.storage.SocketSet.Entry(2, SocketBundle.SMALL_AZURIUM))));
            inventory.setItem(4, core);
            inventory.setItem(5, new ItemStack(RubrumContent.RUBRUM_CONSTRUCT_FRAME));
            inventory.setItem(6, new ItemStack(RubrumContent.CELL_SOCKET));
            inventory.setSelectedSlot(3);
        });
        aimFrom(context, server, LARGE.getX() + 7.5, FLAT_FEET, LARGE.getZ() - 3.5, LARGE.getX() + 0.5, LARGE.getY(), LARGE.getZ() + 0.5, 10);
        context.takeScreenshot("rubrum-items-hotbar");
        server.runOnServer(minecraft -> player(minecraft).getInventory().clearContent());
    }

    //
    // Helpers
    //

    private static <T> T core(TestServerContext server, BlockPos pos, Function<RubrumConstructTile, T> read)
    {
        return server.computeOnServer(minecraft ->
        {
            if (!(minecraft.overworld().getBlockEntity(pos) instanceof RubrumConstructTile tile))
                throw new AssertionError("Expected a rubrum construct core at " + pos);
            return read.apply(tile);
        });
    }

    private static long storedAt(MinecraftServer minecraft, BlockPos pos)
    {
        return ((RubrumConstructTile) minecraft.overworld().getBlockEntity(pos)).stored();
    }

    private static CellSocketTile socket(MinecraftServer minecraft)
    {
        return (CellSocketTile) minecraft.overworld().getBlockEntity(SOCKET);
    }

    private static long cellCharge(TestServerContext server)
    {
        return server.computeOnServer(minecraft -> RubrumCellItem.charge(socket(minecraft).getCell()));
    }

    private static void setBlock(TestServerContext server, BlockPos pos, Block block)
    {
        server.runOnServer(minecraft -> minecraft.overworld().setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL));
    }

    private static void buildFrame(TestServerContext server, BlockPos core, Size size)
    {
        server.runOnServer(minecraft ->
        {
            for (var offset : ConstructLayout.frame(size))
                minecraft.overworld().setBlock(core.offset(offset), RubrumContent.RUBRUM_CONSTRUCT_FRAME.defaultBlockState(), Block.UPDATE_ALL);
        });
    }

    private static void expectPart(TestServerContext server, BlockPos pos, FramePart part)
    {
        var actual = server.computeOnServer(minecraft ->
        {
            var state = minecraft.overworld().getBlockState(pos);
            return state.is(RubrumContent.RUBRUM_CONSTRUCT_FRAME) ? state.getValue(ConstructFrameBlock.PART) : null;
        });
        expect(actual == part, "The frame block at " + pos + " should show " + part + ", shows " + actual);
    }

    /**
     * Uses the main hand item on a block as if clicked at a point, which picks the socket nearest to it.
     */
    private static void useAt(MinecraftServer minecraft, BlockPos pos, Vec3 hit)
    {
        var player = player(minecraft);
        var result = new BlockHitResult(hit, Direction.UP, pos, false);
        player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, result);
    }
}
