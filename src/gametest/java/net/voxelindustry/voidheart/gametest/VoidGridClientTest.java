package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineContent;
import net.voxelindustry.voidheart.common.energy.grid.AzuriumPylonBlock;
import net.voxelindustry.voidheart.common.energy.grid.AzuriumPylonTile;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridContent;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.energy.grid.VoidGrid;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridBalance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The void grid in a real world.
 * <ul>
 *     <li>A chain of three small pylons carries a source's energy to a machine with no loss, and the small cap holds it to 80 a tick.</li>
 *     <li>A small hub fed by a creative well through a medium pylon saturates at 80 a tick across its three spokes.</li>
 *     <li>Taking a bundle off or walling an arc breaks the chain until it is put back.</li>
 *     <li>A broken pylon drops itself and its bundle, and the grid comes back after a reload.</li>
 * </ul>
 * The source and machines are stand-ins that offer and draw every tick from a server tick hook, since W1 has no machine of its own.
 */
public class VoidGridClientTest implements FabricClientGameTest
{
    private static final int Y = FLAT_FEET;

    // Chain: a source, three small pylons 11 apart, a machine only the last one covers
    private static final BlockPos SOURCE   = new BlockPos(0, Y, 10);
    private static final BlockPos CHAIN_1  = new BlockPos(2, Y, 10);
    private static final BlockPos CHAIN_2  = new BlockPos(13, Y, 10);
    private static final BlockPos CHAIN_3  = new BlockPos(24, Y, 10);
    private static final BlockPos MACHINE  = new BlockPos(27, Y, 10);

    // Hub: a creative well in a medium pylon's field, a small hub, three small spokes each covering one machine
    private static final BlockPos WELL      = new BlockPos(38, Y, -10);
    private static final BlockPos HUB_FEED  = new BlockPos(40, Y, -10);
    private static final BlockPos HUB       = new BlockPos(54, Y, -10);
    private static final BlockPos SPOKE_A   = new BlockPos(65, Y, -10);
    private static final BlockPos SPOKE_B   = new BlockPos(54, Y, 1);
    private static final BlockPos SPOKE_C   = new BlockPos(54, Y, -21);
    private static final BlockPos MACHINE_A = new BlockPos(68, Y, -10);
    private static final BlockPos MACHINE_B = new BlockPos(54, Y, 4);
    private static final BlockPos MACHINE_C = new BlockPos(54, Y, -24);

    // Hanging: two pylons under a ceiling, then one standing on the floor, so arcs run between hanging and standing bundles
    private static final int      CEILING   = Y + 6;
    private static final BlockPos HANG_SRC  = new BlockPos(0, Y + 4, -40);
    private static final BlockPos HANG_1    = new BlockPos(2, CEILING - 1, -40);
    private static final BlockPos HANG_2    = new BlockPos(13, CEILING - 1, -40);
    private static final BlockPos HANG_END  = new BlockPos(24, Y, -40);
    private static final BlockPos HANG_MACH = new BlockPos(27, Y, -40);

    private static final Map<BlockPos, Long> ASKS   = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> OFFERS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> DRAWN  = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> LAST   = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> TAKEN  = new ConcurrentHashMap<>();
    private static boolean hooked;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("azurium_pylon");
        hook();

        TestWorldSave save;
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode creative @a");
            context.waitTicks(10);

            buildChain(server);
            checkChain(context, server);
            checkCap(context, server);
            checkSourceWaits(context, server);
            chainScreenshots(context, server);

            buildHub(server);
            checkHub(context, server);
            hubScreenshot(context, server);

            checkBreaks(context, server);
            checkDrops(context, server);
            stopAll(server);

            checkHanging(context, server);
            checkLore(context);
        }

        try (var world = save.open())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            server.runCommand("setblock " + at(CHAIN_3) + " voidheart:azurium_pylon");
            server.runCommand("setblock " + at(CHAIN_3.above()) + " voidheart:small_azurium_bundle[facing=up]");
            context.waitTicks(5);

            OFFERS.put(SOURCE, 50L);
            ASKS.put(MACHINE, 50L);
            context.waitTicks(5);
            DRAWN.clear();
            context.waitTicks(20);
            expectAbout(server, MACHINE, 50, 20, "After a reload");
            stopAll(server);
        }
    }

    /**
     * Offers, then draws, after everything else in the overworld ticked, as a source and machine block entity would.
     */
    private static void hook()
    {
        if (hooked)
            return;
        hooked = true;
        ServerTickEvents.END_LEVEL_TICK.register(level ->
        {
            if (level.dimension() != Level.OVERWORLD)
                return;
            OFFERS.forEach((pos, amount) -> TAKEN.merge(pos, VoidEnergy.offer(level, pos, amount), Long::sum));
            ASKS.forEach((pos, amount) ->
            {
                long got = VoidEnergy.draw(level, pos, amount);
                DRAWN.merge(pos, got, Long::sum);
                LAST.put(pos, got);
            });
        });
    }

    private static void stopAll(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            ASKS.clear();
            OFFERS.clear();
            DRAWN.clear();
            LAST.clear();
            TAKEN.clear();
        });
    }

    private static String at(BlockPos pos)
    {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void pylon(TestServerContext server, BlockPos pos, String bundle)
    {
        server.runCommand("setblock " + at(pos) + " voidheart:azurium_pylon");
        if (bundle != null)
            server.runCommand("setblock " + at(pos.above()) + " voidheart:" + bundle + "_azurium_bundle[facing=up]");
    }

    private static void buildChain(TestServerContext server)
    {
        pylon(server, CHAIN_1, "small");
        pylon(server, CHAIN_2, "small");
        pylon(server, CHAIN_3, "small");
        // Something to look at where the stand-ins stand
        server.runCommand("setblock " + at(SOURCE) + " voidheart:voidstone_chiseled");
        server.runCommand("setblock " + at(MACHINE) + " voidheart:voidstone_chiseled");
    }

    private static void checkChain(ClientGameTestContext context, TestServerContext server)
    {
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var layout = VoidGrid.get(level).layout();
            expect(layout.pylons().size() == 3, "Three pylons with bundles should be in the grid, found " + layout.pylons().size());
            expect(layout.arcs().length == 2, "The chain should have two arcs, not one from end to end, found " + layout.arcs().length);
            expect(VoidGrid.pylonsCovering(level, MACHINE).size() == 1, "Only the last pylon should cover the machine");
            expect(VoidEnergy.available(level, MACHINE) == 0, "Nothing should be available before a source offers");
        });

        server.runOnServer(minecraft ->
        {
            OFFERS.put(SOURCE, 50L);
            ASKS.put(MACHINE, 60L);
        });
        context.waitTicks(5);
        server.runOnServer(minecraft -> DRAWN.clear());
        context.waitTicks(20);
        expectAbout(server, MACHINE, 50, 20, "A source offering 50 a tick three pylons away");
    }

    /**
     * Exactly so much on the last tick, and about so much a tick over a window, give or take one tick where the server and client clocks meet.
     */
    private static void expectAbout(TestServerContext server, BlockPos machine, long perTick, int ticks, String what)
    {
        server.runOnServer(minecraft ->
        {
            long last = LAST.getOrDefault(machine, 0L);
            long drawn = DRAWN.getOrDefault(machine, 0L);
            expect(last == perTick, what + ", the machine should get " + perTick + " a tick, it got " + last + " on the last one");
            expect(Math.abs(drawn - perTick * ticks) <= perTick, what + ", the machine should get about " + perTick * ticks + " over " + ticks + " ticks, it got " + drawn);
        });
    }

    private static void checkCap(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            OFFERS.put(SOURCE, 500L);
            ASKS.put(MACHINE, 500L);
        });
        context.waitTicks(2 * VoidGridBalance.DISPLAY_TICKS + 5);
        server.runOnServer(minecraft ->
        {
            long last = LAST.getOrDefault(MACHINE, 0L);
            expect(last == VoidGridBalance.SMALL_CAP, "Small pylons should hold the chain to " + VoidGridBalance.SMALL_CAP + " a tick, it carried " + last);
            for (var pos : new BlockPos[] { CHAIN_1, CHAIN_2, CHAIN_3 })
            {
                var pylon = (AzuriumPylonTile) minecraft.overworld().getBlockEntity(pos);
                expect(pylon.throughput() == VoidGridBalance.SMALL_CAP, "The pylon at " + pos.toShortString() + " should run at cap, it passes " + pylon.throughput());
            }
        });

        context.waitTicks(5);
        context.runOnClient(minecraft ->
        {
            var middle = (AzuriumPylonTile) minecraft.level.getBlockEntity(CHAIN_2);
            expect(middle != null && middle.throughput() == VoidGridBalance.SMALL_CAP, "The client should see the middle pylon at cap");
            expect(middle.arcs().size() == 2, "The client should see both arcs of the middle pylon, sees " + middle.arcs().size());
            boolean towardMachine = middle.arcs().stream().anyMatch(flow -> flow.pos().equals(CHAIN_3) && flow.rate() == VoidGridBalance.SMALL_CAP);
            expect(towardMachine, "The middle pylon's arc should flow toward the last one, it shows " + middle.arcs());
            var last = (AzuriumPylonTile) minecraft.level.getBlockEntity(CHAIN_3);
            expect(last.fed().stream().anyMatch(flow -> flow.pos().equals(MACHINE)), "The last pylon should show a thread to the machine, it shows " + last.fed());
        });
    }

    /**
     * With nothing drawing, a source fills what its pylon passes in a tick and then has its offers refused, so it burns nothing more.
     */
    private static void checkSourceWaits(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            ASKS.clear();
            TAKEN.clear();
        });
        context.waitTicks(20);
        long taken = server.computeOnServer(minecraft -> TAKEN.getOrDefault(SOURCE, 0L));
        expect(taken <= VoidGridBalance.SMALL_CAP, "A source with nothing drawing should stop once a tick's worth waits, it gave " + taken + " over 20 ticks");
    }

    private static void chainScreenshots(ClientGameTestContext context, TestServerContext server)
    {
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

        // Idle links: connected, nothing flowing
        context.waitTicks(2 * VoidGridBalance.DISPLAY_TICKS + 5);
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 13.5, Y + 4, -2, 13.5, Y + 1.5, 10.5, 5);
        context.takeScreenshot("void-grid-chain-idle");

        // At cap: leaping arcs, forks and bright bundles
        server.runOnServer(minecraft ->
        {
            OFFERS.put(SOURCE, 500L);
            ASKS.put(MACHINE, 500L);
        });
        context.waitTicks(2 * VoidGridBalance.DISPLAY_TICKS + 15);
        context.takeScreenshot("void-grid-chain-at-cap");
        context.waitTicks(3);
        context.takeScreenshot("void-grid-chain-at-cap-2");

        // Additive and layered, so a frozen sweep close to an arc shows whether it flickers
        server.runCommand("tick freeze");
        for (int step = 0; step < 8; step++)
        {
            aimFrom(context, server, 9 + step * 0.06, Y + 3.5, 6 + step * 0.09, 18, Y + 1.6, 10.5, 2);
            context.takeScreenshot("void-grid-arc-sweep-" + step);
        }
        server.runCommand("tick unfreeze");

        // Forks last 2 to 3 ticks every 10 to 20, so a burst one tick apart beside an arc at cap catches some
        aimFrom(context, server, 18.5, Y + 2, 6, 18.5, Y + 1.6, 10.5, 3);
        for (int frame = 0; frame < 12; frame++)
        {
            context.takeScreenshot("void-grid-forks-" + frame);
            context.waitTick();
        }

        // Holding a bundle shows every field nearby: rings on the ground, threads to the machine
        server.runCommand("item replace entity @a weapon.mainhand with voidheart:small_azurium_bundle");
        aimFrom(context, server, 20, Y + 9, 1, 25, Y, 10, 10);
        context.takeScreenshot("void-grid-chain-fields");
        server.runCommand("item replace entity @a weapon.mainhand with air");

        // Close: claws holding the bundle, and an empty pylon
        server.runCommand("setblock 6 " + Y + " 13 voidheart:azurium_pylon");
        aimFrom(context, server, 6.5, Y, 15.5, 4.5, Y + 1.2, 11.5, 15);
        context.takeScreenshot("void-grid-pylon-close");

        // Each bundle size in the claws, from the south and from the north-east; taken down at once so they never join the chain
        var sizes = new BlockPos[] { new BlockPos(5, Y, 16), new BlockPos(7, Y, 16), new BlockPos(9, Y, 16) };
        pylon(server, sizes[0], "small");
        pylon(server, sizes[1], "medium");
        pylon(server, sizes[2], "large");
        aimFrom(context, server, 7.5, Y, 19.5, 7.5, Y + 1.3, 16.5, 5);
        context.takeScreenshot("void-grid-bundle-sizes-south");
        aimFrom(context, server, 11.5, Y + 1, 13.5, 7.5, Y + 1.3, 16.5, 3);
        context.takeScreenshot("void-grid-bundle-sizes-northeast");
        for (var pos : sizes)
        {
            server.runCommand("setblock " + at(pos.above()) + " air");
            server.runCommand("setblock " + at(pos) + " air");
        }
        server.runCommand("kill @e[type=item]");
        context.getInput().pressKey(InputConstants.KEY_F1);

        // Jade, looking straight at the middle pylon with the HUD on
        server.runCommand("item replace entity @a weapon.mainhand with voidheart:azurium_pylon");
        aimFrom(context, server, 13.5, Y, 7.5, 13.5, Y + 0.4, 10.5, 10);
        context.takeScreenshot("void-grid-jade");
        server.runCommand("item replace entity @a weapon.mainhand with air");
        server.runCommand("setblock 6 " + Y + " 13 air");
    }

    private static void buildHub(TestServerContext server)
    {
        server.runCommand("setblock " + at(WELL) + " voidheart:creative_void_well");
        pylon(server, HUB_FEED, "medium");
        pylon(server, HUB, "small");
        pylon(server, SPOKE_A, "small");
        pylon(server, SPOKE_B, "small");
        pylon(server, SPOKE_C, "small");
        for (var machine : new BlockPos[] { MACHINE_A, MACHINE_B, MACHINE_C })
            server.runCommand("setblock " + at(machine) + " voidheart:voidstone_chiseled");
    }

    private static void checkHub(ClientGameTestContext context, TestServerContext server)
    {
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            for (var machine : new BlockPos[] { MACHINE_A, MACHINE_B, MACHINE_C })
            {
                var covering = VoidGrid.pylonsCovering(level, machine);
                expect(covering.size() == 1, "Each hub machine should sit in one spoke's field only, " + machine.toShortString() + " is in " + covering.size());
            }
            var layout = VoidGrid.get(level).layout();
            int hub = layout.indexOf(HUB);
            expect(hub >= 0 && layout.arcsOf(hub).size() == 4, "The hub should arc to its feed and its three spokes");
            int feed = layout.indexOf(HUB_FEED);
            expect(layout.arcsOf(feed).size() == 1, "The feed should only arc to the hub, so everything passes through it");
            ASKS.put(MACHINE_A, 60L);
            ASKS.put(MACHINE_B, 60L);
            ASKS.put(MACHINE_C, 60L);
        });

        context.waitTicks(2 * VoidGridBalance.DISPLAY_TICKS + 5);
        server.runOnServer(minecraft ->
        {
            long total = LAST.getOrDefault(MACHINE_A, 0L) + LAST.getOrDefault(MACHINE_B, 0L) + LAST.getOrDefault(MACHINE_C, 0L);
            expect(total == VoidGridBalance.SMALL_CAP, "The small hub should pass " + VoidGridBalance.SMALL_CAP + " a tick to its three spokes, it passed " + total
                    + " (" + LAST + ")");
            var hub = (AzuriumPylonTile) minecraft.overworld().getBlockEntity(HUB);
            expect(hub.throughput() == VoidGridBalance.SMALL_CAP, "The hub should show itself at cap, it shows " + hub.throughput());
            var feed = (AzuriumPylonTile) minecraft.overworld().getBlockEntity(HUB_FEED);
            expect(feed.throughput() == VoidGridBalance.SMALL_CAP, "The feed should pass only what the hub takes, it passes " + feed.throughput());
        });
    }

    private static void hubScreenshot(ClientGameTestContext context, TestServerContext server)
    {
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 46, Y + 14, -34, 54.5, Y, -10, 10);
        context.takeScreenshot("void-grid-hub-at-cap");
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runOnServer(minecraft ->
        {
            ASKS.remove(MACHINE_A);
            ASKS.remove(MACHINE_B);
            ASKS.remove(MACHINE_C);
        });
    }

    private static void checkBreaks(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            OFFERS.put(SOURCE, 50L);
            ASKS.put(MACHINE, 50L);
        });
        context.waitTicks(5);

        // A bundle taken off
        server.runCommand("setblock " + at(CHAIN_2.above()) + " air");
        context.waitTicks(3);
        expectCarried(context, server, 0, "With the middle bundle gone");
        server.runCommand("setblock " + at(CHAIN_2.above()) + " voidheart:small_azurium_bundle[facing=up]");
        context.waitTicks(5);
        expectCarried(context, server, 50, "With the middle bundle back");

        // A wall across the first arc, found at the next rebuild
        server.runCommand("fill 7 " + Y + " 8 8 " + (Y + 4) + " 12 voidheart:voidstone");
        context.waitTicks(VoidGridBalance.REBUILD_TICKS + 3);
        expectCarried(context, server, 0, "With a wall across the first arc");
        server.runCommand("fill 7 " + Y + " 8 8 " + (Y + 4) + " 12 air");
        context.waitTicks(VoidGridBalance.REBUILD_TICKS + 5);
        expectCarried(context, server, 50, "With the wall gone");
    }

    private static void expectCarried(ClientGameTestContext context, TestServerContext server, long perTick, String what)
    {
        server.runOnServer(minecraft -> DRAWN.clear());
        context.waitTicks(10);
        expectAbout(server, MACHINE, perTick, 10, what);
    }

    private static void checkDrops(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("kill @e[type=item]");
        server.runCommand("setblock " + at(CHAIN_3) + " air destroy");
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(CHAIN_3).inflate(3));
            boolean pylon = items.stream().anyMatch(item -> BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).getPath().equals("azurium_pylon"));
            boolean bundle = items.stream().anyMatch(item -> BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).getPath().equals("small_azurium_bundle"));
            expect(pylon, "A broken pylon should drop itself");
            expect(bundle, "A pylon's bundle should drop when the pylon under it breaks");
            expect(level.getBlockState(CHAIN_3.above()).isAir(), "The bundle should not float once its pylon is gone");
            expect(VoidGrid.pylonsCovering(level, MACHINE).isEmpty(), "No pylon should cover the machine once the last one broke");
        });
        server.runCommand("kill @e[type=item]");

        // Mined off the claws in survival with a bare hand, a bundle comes back whole rather than as a shard
        var spare = new BlockPos(6, Y, 13);
        pylon(server, spare, "small");
        server.runCommand("gamemode survival @a");
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            player(minecraft).gameMode.destroyBlock(spare.above());
            expect(level.getBlockState(spare.above()).isAir(), "The bundle should come off the claws");
            var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(spare).inflate(3));
            boolean bundle = items.stream().anyMatch(item -> BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).getPath().equals("small_azurium_bundle"));
            boolean shard = items.stream().anyMatch(item -> BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).getPath().equals("azurium_shard"));
            expect(bundle && !shard, "A bundle mined off a pylon should drop whole, dropped " + items.stream().map(item -> item.getItem().toString()).toList());
        });
        server.runCommand("gamemode creative @a");
        server.runCommand("setblock " + at(spare) + " air");
        server.runCommand("kill @e[type=item]");
    }

    /**
     * A pylon set against a ceiling with a real click hangs upside down, holds its bundle hanging below, and joins the grid like a standing one.
     */
    private static void checkHanging(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("fill -2 " + CEILING + " -44 18 " + CEILING + " -36 voidheart:voidstone");
        server.runCommand("setblock " + at(HANG_SRC) + " voidheart:voidstone_chiseled");
        server.runCommand("setblock " + at(HANG_MACH) + " voidheart:voidstone_chiseled");
        context.waitTicks(3);

        // Real clicks: the pylon on the ceiling's underside, then the bundle on the pylon's underside
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(VoidGridContent.AZURIUM_PYLON));
        });
        aimFrom(context, server, HANG_1.getX() + 0.5, Y + 2, HANG_1.getZ() - 2.5, HANG_1.getX() + 0.5, CEILING, HANG_1.getZ() + 0.5, 5);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var state = minecraft.overworld().getBlockState(HANG_1);
            expect(state.is(VoidGridContent.AZURIUM_PYLON), "A pylon should be placed against the ceiling, found " + state);
            expect(state.getValue(AzuriumPylonBlock.HANGING), "A pylon placed against a ceiling should hang, found " + state);
            player(minecraft).getInventory().setItem(0, new ItemStack(VoidHeartBlocks.SMALL_AZURIUM_BUNDLE));
        });
        context.waitTicks(2);
        aimFrom(context, server, HANG_1.getX() + 0.5, Y + 1, HANG_1.getZ() - 2.5, HANG_1.getX() + 0.5, HANG_1.getY(), HANG_1.getZ() + 0.5, 5);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var state = minecraft.overworld().getBlockState(HANG_1.below());
            expect(state.is(VoidHeartBlocks.SMALL_AZURIUM_BUNDLE) && state.getValue(AmethystClusterBlock.FACING) == Direction.DOWN,
                    "A bundle set on a hanging pylon should hang from its claws, found " + state);
            player(minecraft).getInventory().setItem(0, ItemStack.EMPTY);
        });

        server.runCommand("setblock " + at(HANG_2) + " voidheart:azurium_pylon[hanging=true]");
        server.runCommand("setblock " + at(HANG_2.below()) + " voidheart:small_azurium_bundle[facing=down]");
        pylon(server, HANG_END, "small");
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var layout = VoidGrid.get(level).layout();
            var hanging = layout.pylons().get(layout.indexOf(HANG_2));
            expect(hanging.bundle().equals(HANG_2.below()), "A hanging pylon's field should centre on the bundle below it, it centres on " + hanging.bundle());
            expect(layout.arcsOf(layout.indexOf(HANG_1)).size() == 1 && layout.arcsOf(layout.indexOf(HANG_2)).size() == 2,
                    "The hanging pylons should arc to each other and the standing one");
            expect(VoidGrid.pylonsCovering(level, HANG_SRC).size() == 1, "The first hanging pylon's field should reach the source");
            OFFERS.put(HANG_SRC, 50L);
            ASKS.put(HANG_MACH, 50L);
        });
        context.waitTicks(5);
        server.runOnServer(minecraft -> DRAWN.clear());
        context.waitTicks(20);
        expectAbout(server, HANG_MACH, 50, 20, "Through two hanging pylons and a standing one");

        // At cap, from the side and from below
        server.runOnServer(minecraft ->
        {
            OFFERS.put(HANG_SRC, 500L);
            ASKS.put(HANG_MACH, 500L);
        });
        context.waitTicks(2 * VoidGridBalance.DISPLAY_TICKS + 15);
        context.runOnClient(minecraft ->
        {
            var pylon = (AzuriumPylonTile) minecraft.level.getBlockEntity(HANG_2);
            expect(pylon != null && pylon.hanging() && pylon.tip().y < HANG_2.getY(), "The client should draw the hanging pylon's arcs from below it");
        });
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 12.5, Y + 2, -49, 12.5, Y + 3.5, -40, 10);
        context.takeScreenshot("void-grid-hanging-side");
        aimFrom(context, server, 4.5, Y, -43, 2.5, CEILING - 1, -39.5, 3);
        context.takeScreenshot("void-grid-hanging-close");
        server.runCommand("item replace entity @a weapon.mainhand with voidheart:small_azurium_bundle");
        aimFrom(context, server, 8, Y + 1, -47, 13, Y + 2, -40, 10);
        context.takeScreenshot("void-grid-hanging-fields");
        server.runCommand("item replace entity @a weapon.mainhand with air");
        context.getInput().pressKey(InputConstants.KEY_F1);
        stopAll(server);

        // Mined off a hanging pylon in survival, the bundle comes back whole; breaking the pylon drops its bundle
        server.runCommand("kill @e[type=item]");
        server.runCommand("gamemode survival @a");
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            player(minecraft).gameMode.destroyBlock(HANG_1.below());
            expect(level.getBlockState(HANG_1.below()).isAir(), "The bundle should come off the hanging claws");
            var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(HANG_1).inflate(3));
            boolean whole = items.stream().anyMatch(item -> item.getItem().is(VoidHeartBlocks.SMALL_AZURIUM_BUNDLE.asItem()));
            boolean shard = items.stream().anyMatch(item -> BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).getPath().equals("azurium_shard"));
            expect(whole && !shard, "A bundle mined off a hanging pylon should drop whole, dropped " + items.stream().map(item -> item.getItem().toString()).toList());
        });
        server.runCommand("gamemode creative @a");
        server.runCommand("kill @e[type=item]");
        server.runCommand("setblock " + at(HANG_2) + " air destroy");
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(HANG_2).inflate(3));
            expect(items.stream().anyMatch(item -> item.getItem().is(VoidHeartBlocks.SMALL_AZURIUM_BUNDLE.asItem())),
                    "A hanging pylon's bundle should drop when the pylon breaks");
            expect(level.getBlockState(HANG_2.below()).isAir(), "The bundle should not float once its hanging pylon is gone");
        });

        server.runCommand("fill -2 " + Y + " -44 30 " + CEILING + " -36 air");
        server.runCommand("kill @e[type=item]");
    }

    private static void checkLore(ClientGameTestContext context)
    {
        context.runOnClient(minecraft ->
        {
            var tooltip = Item.TooltipContext.of(minecraft.level);
            for (var expected : Map.of(
                    VoidGridContent.AZURIUM_PYLON, "Relay and provide void energy around it",
                    RavenousEngineContent.RAVENOUS_ENGINE, "Burn fiery essence to produce void energy",
                    RubrumContent.CELL_SOCKET, "Store energy into rubrum cells").entrySet())
            {
                var lines = new ItemStack(expected.getKey()).getTooltipLines(tooltip, minecraft.player, TooltipFlag.NORMAL).stream().map(line -> line.getString()).toList();
                expect(lines.stream().anyMatch(line -> line.contains(expected.getValue())), expected.getKey() + " should show its lore, shows " + lines);
            }
        });
    }
}
