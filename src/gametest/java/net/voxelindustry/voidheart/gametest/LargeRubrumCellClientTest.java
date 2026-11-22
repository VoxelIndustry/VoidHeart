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
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.energy.creative.CreativeEnergyContent;
import net.voxelindustry.voidheart.common.energy.storage.CellSocketTile;
import net.voxelindustry.voidheart.common.energy.storage.LargeCellCharge;
import net.voxelindustry.voidheart.common.energy.storage.LargeRubrumCellBlock;
import net.voxelindustry.voidheart.common.energy.storage.LargeRubrumCellTile;
import net.voxelindustry.voidheart.common.energy.storage.RubrumBalance;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;

import java.util.function.Function;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The Large Rubrum Cell in a real world: placed by a real click as two halves, discharged into and charged from a
 * creative well's field at its rate, losing its capacity's 1% an hour while not charging, also over time it spent
 * unloaded, held full while charging, lit a quarter at a time, read by a comparator, never pushed by a piston, kept
 * through a reload, and broken from either half into one empty cell.
 */
public class LargeRubrumCellClientTest implements FabricClientGameTest
{
    private static final BlockPos CELL    = new BlockPos(0, FLAT_FEET, 40);
    private static final BlockPos WELL    = new BlockPos(3, FLAT_FEET, 40);
    /** Out of the well's field, so only decay changes them. */
    private static final BlockPos LONE    = new BlockPos(0, FLAT_FEET, 60);
    private static final BlockPos[] SHOWN = {new BlockPos(-3, FLAT_FEET, 60), new BlockPos(-6, FLAT_FEET, 60)};
    private static final long     CAPACITY = RubrumBalance.LARGE_CELL_CAPACITY;
    private static final long     RATE     = RubrumBalance.LARGE_CELL_RATE;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("large_rubrum_cell");

        // All tests share one client, so the camera goes back as it was
        var camera = context.computeOnClient(minecraft -> minecraft.options.getCameraType());
        try
        {
            TestWorldSave save;
            long kept;
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

                place(context, server);
                flow(context, server);
                decay(context, server);
                looks(context, server);
                piston(context, server);
                kept = server.computeOnServer(minecraft -> cell(minecraft, LONE).charge());
                breaking(context, server);
            }

            try (var world = save.open())
            {
                var server = world.getServer();
                world.getConnection().waitForChunksRender();
                context.waitTicks(5);
                var after = server.computeOnServer(minecraft -> cell(minecraft, LONE).charge());
                // The reload itself passes a few ticks, each losing about four
                expect(after <= kept && after >= kept - 1_000, "A large cell should keep its charge through a reload, had " + kept + " and holds " + after);
            }
        }
        finally
        {
            context.runOnClient(minecraft -> minecraft.options.setCameraType(camera));
        }
    }

    private static void place(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getInventory().setSelectedSlot(0);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RubrumContent.LARGE_RUBRUM_CELL));
        });
        aimFrom(context, server, CELL.getX() + 0.5, FLAT_FEET + 1, CELL.getZ() - 2.5, CELL.getX() + 0.5, FLAT_FEET, CELL.getZ() + 0.5, 5);
        rightClick(context);
        var halves = server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var lower = level.getBlockState(CELL);
            var upper = level.getBlockState(CELL.above());
            return lower.is(RubrumContent.LARGE_RUBRUM_CELL) && lower.getValue(LargeRubrumCellBlock.HALF) == DoubleBlockHalf.LOWER
                    && upper.is(RubrumContent.LARGE_RUBRUM_CELL) && upper.getValue(LargeRubrumCellBlock.HALF) == DoubleBlockHalf.UPPER
                    && level.getBlockEntity(CELL) instanceof LargeRubrumCellTile && level.getBlockEntity(CELL.above()) == null;
        });
        expect(halves, "A right click on the ground should raise a large cell of two halves, the tile in the lower one");
        expect(read(server, CELL, LargeRubrumCellTile::charge) == 0, "A new large cell should hold nothing");

        server.runOnServer(minecraft -> minecraft.overworld().setBlock(LONE, RubrumContent.LARGE_RUBRUM_CELL.defaultBlockState(), Block.UPDATE_ALL));
        server.runOnServer(minecraft -> RubrumContent.LARGE_RUBRUM_CELL.setPlacedBy(minecraft.overworld(), LONE,
                minecraft.overworld().getBlockState(LONE), null, ItemStack.EMPTY));
        for (var shown : SHOWN)
        {
            server.runOnServer(minecraft ->
            {
                minecraft.overworld().setBlock(shown, RubrumContent.LARGE_RUBRUM_CELL.defaultBlockState(), Block.UPDATE_ALL);
                RubrumContent.LARGE_RUBRUM_CELL.setPlacedBy(minecraft.overworld(), shown, minecraft.overworld().getBlockState(shown), null, ItemStack.EMPTY);
            });
        }
    }

    private static void flow(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> minecraft.overworld().setBlock(WELL, CreativeEnergyContent.CREATIVE_VOID_WELL.defaultBlockState(), Block.UPDATE_ALL));
        context.waitTicks(3);

        // Half full through the review command, then unpowered it empties into the field at its rate
        server.runCommand("execute positioned " + CELL.getX() + " " + CELL.getY() + " " + CELL.getZ() + " run voidheart energy large_cell fill 50");
        expect(read(server, CELL, LargeRubrumCellTile::charge) == CAPACITY / 2, "The fill command should set the nearest large cell half full");
        var before = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), cell(minecraft, CELL).charge()});
        context.waitTicks(40);
        var after = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), cell(minecraft, CELL).charge()});
        var ticks = after[0] - before[0];
        var lost = before[1] - after[1];
        expect(lost >= RATE * (ticks - 2) && lost <= (RATE + 2) * (ticks + 1),
                "An unpowered large cell should discharge into the field at " + RATE + " a tick: lost " + lost + " over " + ticks + " ticks");
        expect(read(server, CELL, LargeRubrumCellTile::activity) == CellSocketTile.Activity.DISCHARGING, "It should show itself discharging");

        // A signal on the upper half is enough to charge it
        server.runOnServer(minecraft -> minecraft.overworld().setBlock(CELL.above().west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL));
        context.waitTicks(2);
        before = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), cell(minecraft, CELL).charge()});
        context.waitTicks(40);
        after = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), cell(minecraft, CELL).charge()});
        ticks = after[0] - before[0];
        var gained = after[1] - before[1];
        expect(gained >= RATE * (ticks - 2) && gained <= RATE * (ticks + 1),
                "A powered large cell should charge from the field at " + RATE + " a tick: gained " + gained + " over " + ticks + " ticks");
        expect(read(server, CELL, LargeRubrumCellTile::activity) == CellSocketTile.Activity.CHARGING, "It should show itself charging");

        // Kept on charge, a full cell stays full on every tick, drawing just what it would fade
        server.runOnServer(minecraft -> cell(minecraft, CELL).setCharge(CAPACITY));
        for (var tick = 0; tick < 20; tick++)
        {
            context.waitTick();
            var held = server.computeOnServer(minecraft -> new long[]{cell(minecraft, CELL).charge(), cell(minecraft, CELL).comparatorSignal()});
            expect(held[0] == CAPACITY && held[1] == 15, "A full cell on charge should stay full each tick, holds " + held[0] + " and reads " + held[1]);
        }
        expect(read(server, CELL, LargeRubrumCellTile::activity) == CellSocketTile.Activity.CHARGING, "A full cell held full should show itself charging");

        // An hour spent unloaded fades before it charges again, so what it draws on its first tick back is kept
        server.runOnServer(minecraft ->
        {
            cell(minecraft, CELL).setCharge(0);
            minecraft.getCommands().performPrefixedCommand(minecraft.createCommandSourceStack(), "data merge block " + CELL.getX() + " " + CELL.getY() + " "
                    + CELL.getZ() + " {lastTick:" + (minecraft.overworld().getGameTime() - LargeCellCharge.TICKS_PER_HOUR) + "L}");
        });
        context.waitTick();
        var resumed = read(server, CELL, LargeRubrumCellTile::charge);
        expect(resumed >= RATE, "A cell back from an hour unloaded should keep what it draws, holds " + resumed);

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, CELL.getX() + 2.5, FLAT_FEET, CELL.getZ() - 3.5, CELL.getX() + 0.5, FLAT_FEET + 1, CELL.getZ() + 0.5, 5);
        context.takeScreenshot("large-rubrum-cell-charging");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    private static void decay(ClientGameTestContext context, TestServerContext server)
    {
        // Out of any field, it loses its capacity's 1% an hour, about 4.4 a tick
        server.runOnServer(minecraft -> cell(minecraft, LONE).setCharge(CAPACITY / 2));
        var before = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), cell(minecraft, LONE).charge()});
        context.waitTicks(200);
        var after = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), cell(minecraft, LONE).charge()});
        var expected = (after[0] - before[0]) * CAPACITY / 100 / LargeCellCharge.TICKS_PER_HOUR;
        var lost = before[1] - after[1];
        expect(Math.abs(lost - expected) <= 10, "An idle large cell should lose about " + expected + ", lost " + lost);

        // An hour passed while unloaded counts as an hour idle
        var charge = server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            minecraft.getCommands().performPrefixedCommand(minecraft.createCommandSourceStack(),
                    "data merge block " + LONE.getX() + " " + LONE.getY() + " " + LONE.getZ() + " {lastTick:" + (level.getGameTime() - LargeCellCharge.TICKS_PER_HOUR) + "L}");
            return cell(minecraft, LONE).charge();
        });
        context.waitTicks(2);
        lost = charge - read(server, LONE, LargeRubrumCellTile::charge);
        expect(lost >= CAPACITY / 100 && lost <= CAPACITY / 100 + 50,
                "A large cell should lose 1% of its capacity for an hour spent unloaded, lost " + lost);

        // The age command does the same
        charge = read(server, LONE, LargeRubrumCellTile::charge);
        server.runCommand("execute positioned " + LONE.getX() + " " + LONE.getY() + " " + LONE.getZ() + " run voidheart energy large_cell age 10");
        lost = charge - read(server, LONE, LargeRubrumCellTile::charge);
        expect(lost >= CAPACITY / 10 && lost <= CAPACITY / 10 + 50, "Ten hours aged should lose 10% of its capacity, lost " + lost);
    }

    private static void looks(ClientGameTestContext context, TestServerContext server)
    {
        long[] charges = {CAPACITY * 3 / 10, CAPACITY, 0};
        BlockPos[] cells = {LONE, SHOWN[0], SHOWN[1]};
        int[] segments = {2, 4, 0};
        for (var i = 0; i < cells.length; i++)
        {
            var at = cells[i];
            var charge = charges[i];
            server.runOnServer(minecraft -> cell(minecraft, at).setCharge(charge));
        }
        context.waitTicks(3);
        for (var i = 0; i < cells.length; i++)
        {
            var at = cells[i];
            var shows = server.computeOnServer(minecraft -> new int[]{
                    minecraft.overworld().getBlockState(at).getValue(LargeRubrumCellBlock.CHARGE),
                    minecraft.overworld().getBlockState(at.above()).getValue(LargeRubrumCellBlock.CHARGE),
                    minecraft.overworld().getBlockState(at.above()).getAnalogOutputSignal(minecraft.overworld(), at.above(), Direction.NORTH),
                    // It has faded a little since it was set, which takes a full cell just under 15
                    LargeCellCharge.comparatorSignal(cell(minecraft, at).charge(), CAPACITY)});
            expect(shows[0] == segments[i] && shows[1] == segments[i],
                    "Both halves should light " + segments[i] + " segments, lit " + shows[0] + " and " + shows[1]);
            var signal = shows[3];
            expect(signal >= LargeCellCharge.comparatorSignal(charges[i], CAPACITY) - 1, "The cell should still read about as full as it was set");
            expect(shows[2] == signal, "A comparator on the upper half should read " + signal + ", read " + shows[2]);
        }

        // A real comparator beside the lower half, taking its input from the west
        var comparator = LONE.offset(1, 0, 0);
        server.runOnServer(minecraft -> minecraft.overworld().setBlock(comparator, Blocks.COMPARATOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ComparatorBlock.FACING, Direction.WEST), Block.UPDATE_ALL));
        context.waitTicks(4);
        var power = server.computeOnServer(minecraft -> minecraft.overworld().getBlockEntity(comparator)
                instanceof net.minecraft.world.level.block.entity.ComparatorBlockEntity entity ? entity.getOutputSignal() : -1);
        expect(power == LargeCellCharge.comparatorSignal(charges[0], CAPACITY), "A comparator beside the cell should output its fill, gave " + power);

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, LONE.getX() - 2.5, FLAT_FEET + 0.5, LONE.getZ() - 5.5, LONE.getX() - 2.5, FLAT_FEET + 1, LONE.getZ() + 0.5, 5);
        context.takeScreenshot("large-rubrum-cell-fills");
        aimFrom(context, server, LONE.getX() - 1, FLAT_FEET + 0.2, LONE.getZ() - 1.6, LONE.getX() + 0.5, FLAT_FEET + 1, LONE.getZ() + 0.5, 5);
        context.takeScreenshot("large-rubrum-cell-close");
        context.getInput().pressKey(InputConstants.KEY_F1);

        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.setItem(0, new ItemStack(RubrumContent.LARGE_RUBRUM_CELL));
            inventory.setItem(1, new ItemStack(RubrumContent.RUBRUM_CELL));
        });
        context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(0));
        aimFrom(context, server, LONE.getX() + 6.5, FLAT_FEET, LONE.getZ() - 3.5, LONE.getX() + 10, FLAT_FEET, LONE.getZ() + 5, 10);
        context.takeScreenshot("large-rubrum-cell-in-hand");
        context.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
        context.waitTicks(5);
        context.takeScreenshot("large-rubrum-cell-inventory");
        context.setScreen(() -> null);
        server.runOnServer(minecraft -> player(minecraft).getInventory().clearContent());
    }

    private static void piston(ClientGameTestContext context, TestServerContext server)
    {
        var piston = CELL.west().west();
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlock(CELL.above().west(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(CELL.west(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        });
        context.waitTicks(2);
        // The cell sits one block past the piston's head, so it pushes once the gap is filled
        server.runOnServer(minecraft ->
        {
            minecraft.overworld().setBlock(CELL.west(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            minecraft.overworld().setBlock(piston.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        });
        context.waitTicks(10);
        var stays = server.computeOnServer(minecraft -> minecraft.overworld().getBlockState(CELL).is(RubrumContent.LARGE_RUBRUM_CELL)
                && minecraft.overworld().getBlockState(CELL.above()).is(RubrumContent.LARGE_RUBRUM_CELL)
                && !minecraft.overworld().getBlockState(CELL.east()).is(RubrumContent.LARGE_RUBRUM_CELL)
                && minecraft.overworld().getBlockState(CELL.west()).is(Blocks.STONE));
        expect(stays, "A piston should not push a large cell, nor the stone against it");
        server.runOnServer(minecraft ->
        {
            for (var pos : new BlockPos[]{piston.west(), piston, CELL.west()})
                minecraft.overworld().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        });
    }

    private static void breaking(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> cell(minecraft, CELL).setCharge(CAPACITY));
        server.runCommand("kill @e[type=item]");
        // Broken by the lower half: one cell, empty, and the upper half goes with it
        server.runCommand("setblock " + CELL.getX() + " " + CELL.getY() + " " + CELL.getZ() + " air destroy");
        context.waitTicks(5);
        expectBrokenInto(server, CELL);

        // Broken by the upper half
        var shown = SHOWN[0];
        server.runCommand("setblock " + shown.getX() + " " + (shown.getY() + 1) + " " + shown.getZ() + " air destroy");
        context.waitTicks(5);
        expectBrokenInto(server, shown);
    }

    private static void expectBrokenInto(TestServerContext server, BlockPos lower)
    {
        var result = server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(lower).inflate(2, 3, 2));
            var count = drops.stream().mapToInt(item -> item.getItem().getCount()).sum();
            var plain = drops.stream().allMatch(item -> item.getItem().is(RubrumContent.LARGE_RUBRUM_CELL.asItem())
                    && item.getItem().getComponentsPatch().isEmpty());
            drops.forEach(ItemEntity::discard);
            return new boolean[]{count == 1 && plain, level.getBlockState(lower).isAir() && level.getBlockState(lower.above()).isAir()};
        });
        expect(result[1], "Both halves should be gone");
        expect(result[0], "A broken large cell should drop exactly one empty large cell");
    }

    private static LargeRubrumCellTile cell(MinecraftServer minecraft, BlockPos pos)
    {
        if (minecraft.overworld().getBlockEntity(pos) instanceof LargeRubrumCellTile tile)
            return tile;
        throw new AssertionError("No large cell at " + pos);
    }

    private static <T> T read(TestServerContext server, BlockPos pos, Function<LargeRubrumCellTile, T> read)
    {
        return server.computeOnServer(minecraft -> read.apply(cell(minecraft, pos)));
    }
}
