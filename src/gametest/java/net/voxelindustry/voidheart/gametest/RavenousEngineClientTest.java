package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.engine.EngineFuel;
import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineBlock;
import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineContent;
import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * A Ravenous Engine placed with a real click, fed by a relay from a collector, idle while nothing takes its energy, then burning each essence at its own
 * rate into a creative well's field. It goes idle when it runs dry, keeps its essence on the item when broken, and keeps it through a reload.
 * Run it alone with -PgameTest=RavenousEngine.
 */
public class RavenousEngineClientTest implements FabricClientGameTest
{
    private static final BlockPos COLLECTOR = new BlockPos(10, FLAT_FEET, 0);
    private static final BlockPos RELAY     = new BlockPos(13, FLAT_FEET, 0);
    private static final BlockPos ENGINE    = new BlockPos(16, FLAT_FEET, 0);
    private static final BlockPos CHAMBER   = ENGINE.above();
    private static final BlockPos WELL      = new BlockPos(16, FLAT_FEET, 8);

    private static final int FED       = 1_500;
    private static final int MEASURED  = 40;
    private static final int AFTER_BREAK = 500;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("ravenous_engine");

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
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            placeWithRealClick(context, server);
            checkRelayFeedsEngine(context, server);
            checkIdleWithoutTaker(context, server);

            server.runCommand("setblock " + WELL.getX() + " " + WELL.getY() + " " + WELL.getZ() + " voidheart:creative_void_well");
            checkBurnRate(context, server, EngineFuel.STILLNESS, "stillness");
            showBurning(context, server, "stillness");
            showJade(context, server);
            checkRunsDry(context, server);

            fill(server, "fiery", 2_000);
            checkBurnRate(context, server, EngineFuel.FIERY, "fiery");
            showBurning(context, server, "fiery");
            waitForEmpty(context, server);

            fill(server, "suffering", 1_000);
            checkBurnRate(context, server, EngineFuel.SUFFERING, "suffering");
            showBurning(context, server, "suffering");
            waitForEmpty(context, server);

            server.runCommand("setblock " + WELL.getX() + " " + WELL.getY() + " " + WELL.getZ() + " air");
            context.waitTicks(5);
            checkBreakingKeepsEssence(context, server);
        }

        try (var world = save.open())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            server.runOnServer(minecraft ->
            {
                var held = engine(minecraft).amounts()[EngineFuel.STILLNESS.ordinal()];
                expect(held == AFTER_BREAK, "The engine should keep its essence through a reload, holds " + held);
            });
        }
    }

    private static void placeWithRealClick(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(RavenousEngineContent.RAVENOUS_ENGINE));
        });
        aimFrom(context, server, ENGINE.getX() + 0.5, FLAT_FEET + 1, ENGINE.getZ() - 2.5, ENGINE.getX() + 0.5, FLAT_FEET - 0.5, ENGINE.getZ() + 0.5, 5);
        context.takeScreenshot("engine-item-hand");
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);

        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(level.getBlockState(ENGINE).is(RavenousEngineContent.RAVENOUS_ENGINE)
                    && level.getBlockState(ENGINE).getValue(RavenousEngineBlock.PART) == RavenousEngineBlock.Part.MAW, "The engine's maw was not placed");
            expect(level.getBlockState(CHAMBER).is(RavenousEngineContent.RAVENOUS_ENGINE)
                    && level.getBlockState(CHAMBER).getValue(RavenousEngineBlock.PART) == RavenousEngineBlock.Part.CHAMBER, "The engine placed no chamber above");
            expect(level.getBlockEntity(CHAMBER) == null, "The chamber has a block entity of its own");
            player(minecraft).getInventory().setItem(0, ItemStack.EMPTY);

            level.setBlockAndUpdate(COLLECTOR, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            if (!(level.getBlockEntity(COLLECTOR) instanceof RavenousCollectorTile collector))
                throw new AssertionError("No collector");
            collector.storage().attune(EssenceRegistry.STILLNESS);
            collector.storage().fill(EssenceRegistry.STILLNESS, FED);
            level.setBlockAndUpdate(RELAY, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
        });
    }

    /**
     * The relay links the engine like any receiver and carries the collector's whole content into it. While no field covers it, nothing is burned.
     */
    private static void checkRelayFeedsEngine(ClientGameTestContext context, TestServerContext server)
    {
        waitFor(context, server, 400, () -> "The relay did not empty the collector into the engine",
                minecraft -> collector(minecraft).storage().amount() == 0);
        server.runOnServer(minecraft ->
        {
            var held = engine(minecraft).amounts()[EngineFuel.STILLNESS.ordinal()];
            expect(held == FED, "The engine should hold all " + FED + " Stillness the relay carried, holds " + held);
            expect(minecraft.overworld().getBlockEntity(RELAY) instanceof RavenousRelayTile relay && relay.linkedReceivers().contains(ENGINE),
                    "The relay does not link to the engine");
            expect(engine(minecraft).amountOf(EssenceRegistry.STILLNESS) == 0, "The engine should give no essence back to the relays");
        });
    }

    private static void checkIdleWithoutTaker(ClientGameTestContext context, TestServerContext server)
    {
        context.waitTicks(30);
        server.runOnServer(minecraft ->
        {
            var engine = engine(minecraft);
            expect(VoidEnergy.offer(minecraft.overworld(), ENGINE, 10) == 0, "Something takes energy at the engine before any field covers it");
            expect(engine.amounts()[EngineFuel.STILLNESS.ordinal()] == FED, "The engine burned essence while nothing took its energy, holds "
                    + engine.amounts()[EngineFuel.STILLNESS.ordinal()]);
            expect(engine.isIdle() && engine.lastOutput() == 0, "The engine should be idle while nothing takes its energy");
        });

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, ENGINE.getX() + 2.5, FLAT_FEET + 2.2, ENGINE.getZ() - 2, ENGINE.getX() + 0.5, FLAT_FEET + 1, ENGINE.getZ() + 0.5, 5);
        context.takeScreenshot("engine-idle-closed");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    /**
     * Inside the well's field everything offered is taken, so the tank drains at exactly the fuel's burn per tick.
     */
    private static void checkBurnRate(ClientGameTestContext context, TestServerContext server, EngineFuel fuel, String name)
    {
        waitFor(context, server, 40, () -> "The engine did not start burning " + name, minecraft -> engine(minecraft).lastOutput() > 0);
        long[] start = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), engine(minecraft).amounts()[fuel.ordinal()]});
        context.waitTicks(MEASURED);
        long[] end = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), engine(minecraft).amounts()[fuel.ordinal()]});

        long ticks = end[0] - start[0];
        long burned = start[1] - end[1];
        expect(end[1] > 0, "The tank ran dry while measuring " + name);
        expect(burned == ticks * fuel.burn(), name + " should burn " + fuel.burn() + " essence a tick, burned " + burned + " in " + ticks + " ticks");

        server.runOnServer(minecraft ->
        {
            var engine = engine(minecraft);
            expect(engine.lastOutput() == fuel.fullOutput(), name + " should give " + fuel.fullOutput() + " VE a tick, gave " + engine.lastOutput());
            expect(engine.shownFuel() == fuel, "The engine shows " + engine.shownFuel() + " burning instead of " + name);
            expect(WELL.equals(engine.outputTarget()), "The output arc should land on the well, it lands on " + engine.outputTarget());
        });
        expectClient(context, "The client does not see the engine burning " + name + " toward the well", minecraft ->
                minecraft.level.getBlockEntity(ENGINE) instanceof RavenousEngineTile engine && engine.shownFuel() == fuel
                        && engine.activity() == RavenousEngineTile.ACTIVITY_STEPS && WELL.equals(engine.outputTarget()));
    }

    private static void showBurning(ClientGameTestContext context, TestServerContext server, String name)
    {
        context.getInput().pressKey(InputConstants.KEY_F1);
        // Close on the maw and chamber, the column moving through a few moments of its rhythm
        for (int frame = 0; frame < 3; frame++)
        {
            // From the middle of a side, between two posts
            aimFrom(context, server, ENGINE.getX() + 0.5, FLAT_FEET + 0.9, ENGINE.getZ() - 1.7, ENGINE.getX() + 0.5, FLAT_FEET + 1.2, ENGINE.getZ() + 0.5, frame == 0 ? 15 : 7);
            context.takeScreenshot("engine-" + name + "-" + frame);
        }
        // The arc from the chamber to the well
        aimFrom(context, server, ENGINE.getX() + 6, FLAT_FEET + 3, ENGINE.getZ() + 3, ENGINE.getX() + 0.5, FLAT_FEET + 1.5, ENGINE.getZ() + 4.5, 5);
        context.takeScreenshot("engine-" + name + "-arc");

        if (name.equals("stillness"))
        {
            server.runCommand("tick freeze");
            for (int step = 0; step < 6; step++)
            {
                aimFrom(context, server, ENGINE.getX() + 2.2 + step * 0.05, FLAT_FEET + 2, ENGINE.getZ() - 1.6 + step * 0.08,
                        ENGINE.getX() + 0.5, FLAT_FEET + 1.1, ENGINE.getZ() + 0.5, 2);
                context.takeScreenshot("engine-column-sweep-" + step);
            }
            server.runCommand("tick unfreeze");
        }
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    private static void showJade(ClientGameTestContext context, TestServerContext server)
    {
        // Looking at a post of the chamber shows the maw's line
        double postX = ENGINE.getX() + 2.5 / 16;
        double postZ = ENGINE.getZ() + 2.5 / 16;
        aimFrom(context, server, postX, FLAT_FEET + 0.2, ENGINE.getZ() - 2, postX, FLAT_FEET + 1.6, postZ, 30);
        expectClient(context, "The player is not looking at the chamber", minecraft -> minecraft.hitResult instanceof BlockHitResult hit
                && hit.getBlockPos().equals(CHAMBER));
        context.takeScreenshot("engine-jade-chamber");
        aimFrom(context, server, ENGINE.getX() + 0.5, FLAT_FEET + 0.2, ENGINE.getZ() - 2, ENGINE.getX() + 0.5, FLAT_FEET + 0.5, ENGINE.getZ(), 30);
        context.takeScreenshot("engine-jade-maw");
    }

    /**
     * Once the tank is dry, nothing more is offered and the maw closes.
     */
    private static void checkRunsDry(ClientGameTestContext context, TestServerContext server)
    {
        waitForEmpty(context, server);
        context.waitTicks(RavenousEngineTile.IDLE_GRACE_TICKS + RavenousEngineTile.OPEN_TICKS + 5);
        server.runOnServer(minecraft -> expect(engine(minecraft).isIdle(), "The engine should go idle once its tank is dry"));
        expectClient(context, "The client still sees the dry engine burning", minecraft ->
                minecraft.level.getBlockEntity(ENGINE) instanceof RavenousEngineTile engine && engine.isIdle());
    }

    private static void waitForEmpty(ClientGameTestContext context, TestServerContext server)
    {
        waitFor(context, server, 400, () -> "The engine never burned its tank dry", minecraft ->
        {
            var amounts = engine(minecraft).amounts();
            return amounts[0] == 0 && amounts[1] == 0 && amounts[2] == 0;
        });
    }

    private static void fill(TestServerContext server, String aspect, int amount)
    {
        server.runCommand("voidheart energy engine fill " + CHAMBER.getX() + " " + CHAMBER.getY() + " " + CHAMBER.getZ() + " " + aspect + " " + amount);
    }

    /**
     * Breaking the chamber with a pickaxe breaks the maw too, and the dropped engine carries its essence into the next one placed.
     */
    private static void checkBreakingKeepsEssence(ClientGameTestContext context, TestServerContext server)
    {
        fill(server, "stillness", AFTER_BREAK);
        context.waitTicks(20);
        server.runOnServer(minecraft ->
        {
            var held = engine(minecraft).amounts()[EngineFuel.STILLNESS.ordinal()];
            expect(held == AFTER_BREAK, "With the well gone the engine should keep its " + AFTER_BREAK + " Stillness, holds " + held);

            var player = player(minecraft);
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
            player.gameMode.destroyBlock(CHAMBER);

            var level = minecraft.overworld();
            expect(level.getBlockState(ENGINE).isAir() && level.getBlockState(CHAMBER).isAir(), "Breaking the chamber should break the whole engine");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(ENGINE).inflate(3));
            expect(drops.size() == 1 && drops.getFirst().getItem().is(RavenousEngineContent.RAVENOUS_ENGINE.asItem()),
                    "Breaking the engine should drop one engine, dropped " + drops.stream().map(drop -> drop.getItem().toString()).toList());

            var stack = drops.getFirst().getItem().copy();
            var tank = stack.get(VoidHeartDataComponents.MULTI_ESSENCE_STORAGE);
            expect(tank != null && tank.amountOf(EssenceRegistry.STILLNESS) == AFTER_BREAK, "The dropped engine should carry its " + AFTER_BREAK + " Stillness");
            drops.forEach(ItemEntity::discard);

            player.setGameMode(GameType.CREATIVE);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            useOn(player, ENGINE.below());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var restored = engine(minecraft).amounts()[EngineFuel.STILLNESS.ordinal()];
            expect(restored == AFTER_BREAK, "The placed engine should hold the " + AFTER_BREAK + " Stillness it carried, holds " + restored);
        });
    }

    private static RavenousEngineTile engine(MinecraftServer minecraft)
    {
        if (minecraft.overworld().getBlockEntity(ENGINE) instanceof RavenousEngineTile engine)
            return engine;
        throw new AssertionError("No Ravenous Engine at " + ENGINE.toShortString());
    }

    private static RavenousCollectorTile collector(MinecraftServer minecraft)
    {
        if (minecraft.overworld().getBlockEntity(COLLECTOR) instanceof RavenousCollectorTile collector)
            return collector;
        throw new AssertionError("No collector at " + COLLECTOR.toShortString());
    }
}
