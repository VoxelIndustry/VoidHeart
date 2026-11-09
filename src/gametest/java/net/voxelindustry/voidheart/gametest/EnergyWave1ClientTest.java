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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.athanor.AthanorTile;
import net.voxelindustry.voidheart.common.content.athanor.CrucibleTile;
import net.voxelindustry.voidheart.common.content.beholder.BeholderTile;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.curse.Corruption;
import net.voxelindustry.voidheart.common.energy.engine.EngineFuel;
import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineContent;
import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineTile;
import net.voxelindustry.voidheart.common.energy.grid.GridLayout;
import net.voxelindustry.voidheart.common.energy.grid.VoidGrid;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridContent;
import net.voxelindustry.voidheart.common.energy.ley.LeySpurs;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneBalance;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneContent;
import net.voxelindustry.voidheart.common.energy.ley.LeyWardTile;
import net.voxelindustry.voidheart.common.energy.ley.SpurTap;
import net.voxelindustry.voidheart.common.energy.ley.WardStrength;
import net.voxelindustry.voidheart.common.energy.machine.MachineEnergy;
import net.voxelindustry.voidheart.common.energy.machine.MachineEnergyBalance;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.Size;
import net.voxelindustry.voidheart.common.energy.storage.RubrumConstructTile;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The first wave of the energy system working together, with real blocks only and no creative source. A collector and a relay feed a Ravenous Engine,
 * whose energy runs down a chain of three pylons to an athanor brewing and a rubrum construct filling at the far end. Elsewhere a standing stone bends a
 * spur off a real ley line to a ward and a beholder, and tainting the line's node with the command weakens the ward.
 * Run it alone with -PgameTest=EnergyWave1.
 */
public class EnergyWave1ClientTest implements FabricClientGameTest
{
    private static final int F = FLAT_FEET;

    private static final BlockPos COLLECTOR = new BlockPos(0, F, 0);
    private static final BlockPos RELAY     = new BlockPos(3, F, 0);
    private static final BlockPos ENGINE    = new BlockPos(6, F, 0);

    private static final BlockPos PYLON_ENGINE = new BlockPos(6, F, 4);
    private static final BlockPos PYLON_MIDDLE = new BlockPos(22, F, 4);
    private static final BlockPos PYLON_BASE   = new BlockPos(38, F, 4);

    private static final BlockPos INTAKE      = new BlockPos(40, F, 10);
    private static final BlockPos ATHANOR     = INTAKE.above();
    private static final BlockPos ALEMBIC     = ATHANOR.above();
    private static final BlockPos SULFUR      = ATHANOR.east();
    private static final BlockPos QUICKSILVER = ATHANOR.west();

    private static final BlockPos CONSTRUCT = new BlockPos(34, F + 1, 10);

    private static final int STILLNESS = RavenousCollectorTile.CAPACITY;
    // What the construct banks before the brew starts: the half of a searing oil's 60 VE a tick a Stillness engine can't give, over its 200 ticks
    private static final long BANKED   = 6_500;
    private static final int MEASURED  = 40;

    private static final int TAP_OUT   = 12;
    private static final int MACHINE_OUT = 4;
    private static final int MACHINE_ALONG = 3;
    private static final int RISES     = 10;
    private static final int RISE      = 2;
    private static final float TAINT   = 50;

    private record LeyScene(BlockPos stone, BlockPos ward, BlockPos beholder, BlockPos upstream, Vec3 along, Vec3 out)
    {
        BlockPos at(BlockPos from, double along, double out)
        {
            return BlockPos.containing(from.getX() + 0.5 + this.along.x * along + this.out.x * out, from.getY(), from.getZ() + 0.5 + this.along.z * along + this.out.z * out);
        }
    }

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("execute as @a run voidheart research grant voidheart:sulfur_rite");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            buildVoidChain(context, server);
            checkEngineFed(context, server);
            checkEngineChargesConstruct(context, server);
            checkChainPowersBrew(context, server);
            voidScreenshots(context, server);
            checkBrewFinishes(context, server);

            var scene = server.computeOnServer(minecraft -> findLeyScene(minecraft.overworld()));
            var middle = scene.stone();
            server.runCommand("forceload add " + (middle.getX() - 32) + " " + (middle.getZ() - 32) + " " + (middle.getX() + 32) + " " + (middle.getZ() + 32));
            tp(server, middle.getX() + 0.5, F, middle.getZ() + 0.5);
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();

            buildSpur(context, server, scene);
            float clean = checkSpurPowersWardAndBeholder(context, server, scene);
            checkTaintWeakensWard(context, server, scene, clean);
        }
    }

    // The void side

    private static void buildVoidChain(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var player = player(minecraft);

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RavenousEngineContent.RAVENOUS_ENGINE));
            useOn(player, ENGINE.below());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            expect(level.getBlockEntity(ENGINE) instanceof RavenousEngineTile, "The engine should stand at " + ENGINE.toShortString());

            for (var pylon : List.of(PYLON_ENGINE, PYLON_MIDDLE, PYLON_BASE))
            {
                level.setBlockAndUpdate(pylon, VoidGridContent.AZURIUM_PYLON.defaultBlockState());
                level.setBlockAndUpdate(pylon.above(), VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
            }

            level.setBlockAndUpdate(INTAKE, VoidHeartBlocks.ASPECT_INTAKE.defaultBlockState());
            level.setBlockAndUpdate(ATHANOR, VoidHeartBlocks.ATHANOR.defaultBlockState());
            level.setBlockAndUpdate(ALEMBIC, VoidHeartBlocks.ALEMBIC.defaultBlockState());
            for (var crucible : new BlockPos[] { SULFUR, QUICKSILVER })
            {
                level.setBlockAndUpdate(crucible.below(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(crucible, VoidHeartBlocks.CRUCIBLE.defaultBlockState());
            }

            level.setBlockAndUpdate(CONSTRUCT, RubrumContent.RUBRUM_CONSTRUCT_CORE.defaultBlockState());
            for (var offset : ConstructLayout.frame(Size.SMALL))
                level.setBlock(CONSTRUCT.offset(offset), RubrumContent.RUBRUM_CONSTRUCT_FRAME.defaultBlockState(), Block.UPDATE_ALL);
        });
        waitFor(context, server, 40, "The small construct should form", minecraft -> construct(minecraft).isFormed());
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartBlocks.SMALL_AZURIUM_BUNDLE));
            useAt(minecraft, CONSTRUCT, Vec3.atCenterOf(CONSTRUCT.east()));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartBlocks.SMALL_RUBRUM_BUNDLE));
            useAt(minecraft, CONSTRUCT, Vec3.atCenterOf(CONSTRUCT.west()));
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            expect(construct(minecraft).maxRate() > 0 && construct(minecraft).capacity() > 0, "The construct should take a rubrum and an azurium bundle");

            var level = minecraft.overworld();
            level.setBlockAndUpdate(COLLECTOR, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            if (!(level.getBlockEntity(COLLECTOR) instanceof RavenousCollectorTile collector))
                throw new AssertionError("No collector at " + COLLECTOR.toShortString());
            collector.storage().attune(EssenceRegistry.STILLNESS);
            collector.storage().fill(EssenceRegistry.STILLNESS, STILLNESS);
            level.setBlockAndUpdate(RELAY, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
        });
    }

    private static void checkEngineFed(ClientGameTestContext context, TestServerContext server)
    {
        waitFor(context, server, 400, "The relay should carry the collector's Stillness into the engine", minecraft -> engineStillness(minecraft) > 0);
        waitFor(context, server, 80, "The engine should burn once the construct has room", minecraft -> engine(minecraft).lastOutput() > 0);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(onlyPylon(level, ENGINE, PYLON_ENGINE), "Only the first pylon's field should cover the engine, covered by " + covering(level, ENGINE));
            expect(onlyPylon(level, ATHANOR, PYLON_BASE), "Only the last pylon's field should cover the athanor, covered by " + covering(level, ATHANOR));
            expect(onlyPylon(level, CONSTRUCT, PYLON_BASE), "Only the last pylon's field should cover the construct, covered by " + covering(level, CONSTRUCT));
            var grid = VoidGrid.get(level);
            expect(grid != null && grid.layout().arcs().length == 2, "The three pylons should arc into one chain");
            expect(PYLON_ENGINE.above().equals(engine(minecraft).outputTarget()), "The engine's arc should land on the bundle of the pylon taking its energy, it lands on "
                    + engine(minecraft).outputTarget());
        });
    }

    /**
     * With nothing else drawing, everything the engine burns crosses the chain into the construct at the far end.
     */
    private static void checkEngineChargesConstruct(ClientGameTestContext context, TestServerContext server)
    {
        long[] start = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), construct(minecraft).stored(), stillnessLeft(minecraft)});
        context.waitTicks(MEASURED);
        long[] end = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), construct(minecraft).stored(), stillnessLeft(minecraft)});
        long ticks = end[0] - start[0];
        long full = EngineFuel.STILLNESS.fullOutput();
        long charged = end[1] - start[1];
        expect(end[2] < start[2], "The engine should burn Stillness while the construct has room, it went from " + start[2] + " to " + end[2]);
        expect(charged >= full * (ticks - 2) && charged <= full * (ticks + 2), "The construct should take all " + full + " VE a tick the engine gives, took "
                + charged + " in " + ticks + " ticks");
        waitFor(context, server, 400, "The construct should bank " + BANKED + " VE before the brew", minecraft -> construct(minecraft).stored() >= BANKED);
    }

    /**
     * The athanor brews at full speed at the far end of the chain. It draws more than a Stillness engine gives, so the construct makes up the rest.
     */
    private static void checkChainPowersBrew(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            ((CrucibleTile) minecraft.overworld().getBlockEntity(SULFUR)).setStack(new ItemStack(Items.SULFUR));
            ((CrucibleTile) minecraft.overworld().getBlockEntity(QUICKSILVER)).setStack(new ItemStack(VoidHeartItems.QUICKSILVER));
            athanor(minecraft).setStack(player(minecraft), new ItemStack(Items.GLASS_BOTTLE));
        });
        waitFor(context, server, 60, "The athanor should find its recipe", minecraft -> athanor(minecraft).getCurrentRecipe() != null);
        server.runOnServer(minecraft ->
        {
            var athanor = athanor(minecraft);
            athanor.getCurrentRecipe().getAspects().forEach(athanor::receiveAspect);
        });
        waitFor(context, server, 60, "The athanor should start brewing once its aspects are in", minecraft -> athanor(minecraft).isBrewing());
        context.waitTicks(25);

        long[] start = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), athanor(minecraft).getProgress(), construct(minecraft).stored(),
                stillnessLeft(minecraft)});
        context.waitTicks(MEASURED);
        long[] end = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), athanor(minecraft).getProgress(), construct(minecraft).stored(),
                stillnessLeft(minecraft), athanor(minecraft).drawRate(), engine(minecraft).lastOutput()});
        long ticks = end[0] - start[0];
        long brewed = end[1] - start[1];
        long stored = end[2] - start[2];
        long draw = end[4];
        long given = end[5];
        expect(brewed >= ticks - 1, "The brew should advance every tick on the chain's energy, it advanced " + brewed + " in " + ticks + " ticks");
        expect(end[3] < start[3] && given == EngineFuel.STILLNESS.fullOutput(), "The engine should burn at full while the brew draws, it gives " + given);
        long expected = (given - draw) * ticks;
        expect(Math.abs(stored - expected) <= Math.max(draw, given) * 3, "The construct should change by what the engine gives less what the brew draws, "
                + expected + ", it changed by " + stored + " (" + given + " given, " + draw + " drawn a tick)");
        server.runOnServer(minecraft ->
        {
            expect(athanor(minecraft).getPower() == MachinePower.POWERED, "The brew should be POWERED, it is " + athanor(minecraft).getPower());
            expect(minecraft.overworld().getBlockState(ATHANOR).getValue(BlockStateProperties.LIT), "A powered brew should be lit");
            expect(engine(minecraft).shownFuel() == EngineFuel.STILLNESS, "The engine should show Stillness burning");
        });
    }

    private static void voidScreenshots(ClientGameTestContext context, TestServerContext server)
    {
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 22, F + 16, -14, 22, F, 6, 20);
        context.takeScreenshot("wave1-void-chain");
        aimFrom(context, server, 1.5, F + 3, -5, 6.5, F + 1, 3, 10);
        context.takeScreenshot("wave1-engine-arc");
        aimFrom(context, server, 31, F + 5, -3, 37, F + 1, 9, 10);
        context.takeScreenshot("wave1-athanor-construct");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    private static void checkBrewFinishes(ClientGameTestContext context, TestServerContext server)
    {
        waitFor(context, server, 600, "The brew should finish on the engine's energy", minecraft -> !athanor(minecraft).isBrewing());
        expect(server.computeOnServer(minecraft -> athanor(minecraft).getStack().is(VoidHeartItems.SEARING_OIL)), "The brew should leave searing oil");
    }

    // The ley side

    /**
     * A line long enough to stand a stone off its middle, where the stone taps it and the line carries some flow.
     */
    private static LeyScene findLeyScene(ServerLevel level)
    {
        var lattice = LeyLattice.get(level).orElseThrow(() -> new AssertionError("Expected a ley lattice in the overworld"));
        for (int probe = 0; probe < 25; probe++)
        {
            var edge = lattice.nearestEdge((probe % 5 - 2) * 400, (probe / 5 - 2) * 400).orElse(null);
            if (edge == null || edge.length() < 48)
                continue;
            var along = new Vec3(edge.toX() - edge.fromX(), 0, edge.toZ() - edge.fromZ()).normalize();
            var out = new Vec3(-along.z, 0, along.x);
            var onLine = BlockPos.containing(edge.fromX() + (edge.toX() - edge.fromX()) * 0.5, F, edge.fromZ() + (edge.toZ() - edge.fromZ()) * 0.5);
            var probeScene = new LeyScene(null, null, null, null, along, out);
            var stone = probeScene.at(onLine, 0, TAP_OUT);
            var line = Ley.lineNear(level, stone, LeyStoneBalance.STONE_REACH);
            if (line.isEmpty())
                continue;
            var tap = SpurTap.of(line.get(), stone.getX() + 0.5, stone.getZ() + 0.5);
            if (Ley.lineFlowAt(level, tap.claimant(stone.getY())) <= 0)
                continue;
            var node = lattice.node(tap.fromNode()).orElseThrow();
            return new LeyScene(stone, probeScene.at(stone, MACHINE_ALONG, MACHINE_OUT), probeScene.at(stone, -MACHINE_ALONG, MACHINE_OUT),
                    new BlockPos((int) Math.floor(node.x()), F, (int) Math.floor(node.z())), along, out);
        }
        throw new AssertionError("Found no ley line with flow near the origin");
    }

    private static void buildSpur(ClientGameTestContext context, TestServerContext server, LeyScene scene)
    {
        // Placed the way a player does, standing off to the side toward the line so nothing stands in the way
        var stand = scene.at(scene.stone(), 0, -3);
        aimCamera(server, stand.getX() + 0.5, F, stand.getZ() + 0.5, scene.stone().getX() + 0.5, F, scene.stone().getZ() + 0.5);
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LeyStoneContent.LEY_STONE));
            useOn(player, scene.stone().below());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LeyStoneContent.LEY_WARD));
            useOn(player, scene.ward().below());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartBlocks.BEHOLDER));
            useOn(player, scene.beholder().below());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            var level = minecraft.overworld();
            expect(level.getBlockState(scene.stone()).is(LeyStoneContent.LEY_STONE) && level.getBlockState(scene.stone().above()).is(LeyStoneContent.LEY_STONE),
                    "The standing stone should rise two blocks");
            expect(level.getBlockEntity(scene.ward()) instanceof LeyWardTile, "The ward should stand at " + scene.ward().toShortString());
            expect(level.getBlockEntity(scene.beholder()) instanceof BeholderTile, "The beholder should stand at " + scene.beholder().toShortString());
        });
        context.waitTicks(60);
    }

    /**
     * The stone taps the line, and the ward and the beholder near it share what the line grants the spur, each the same share of its need.
     *
     * @return the ward's strength on a clean line
     */
    private static float checkSpurPowersWardAndBeholder(ClientGameTestContext context, TestServerContext server, LeyScene scene)
    {
        float strength = server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var link = LeySpurs.get(level).plan().link(scene.stone());
            expect(link != null && link.isRoot(), "The stone " + TAP_OUT + " blocks from the line should tap it, got " + link);

            var tap = SpurTap.of(Ley.lineNear(level, scene.stone(), LeyStoneBalance.STONE_REACH).orElseThrow(), scene.stone().getX() + 0.5, scene.stone().getZ() + 0.5);
            double flow = Ley.lineFlowAt(level, tap.claimant(scene.stone().getY()));
            float expected = (float) Math.min(1, flow / (LeyStoneBalance.WARD_NEED + MachineEnergyBalance.BEHOLDER_NEED));
            var ward = ward(minecraft, scene);
            expect(Math.abs(ward.supply() - expected) < 0.02F, "The ward should get " + expected + " of its need from a " + flow + " line, got " + ward.supply());

            var beholder = (BeholderTile) level.getBlockEntity(scene.beholder());
            float sight = MachineEnergy.beholderSight(expected);
            expect(Math.abs(beholder.sight() - sight) < 0.05F, "The beholder should see " + sight + " of its range on the same share, sees " + beholder.sight());
            expect(beholder.sight() > 0, "The beholder should not be blind on a real spur");
            expect(ward.health() == 1, "A clean line should leave the ward whole, it holds " + ward.health());
            return ward.strength();
        });
        expect(strength > 0, "The ward should hold the air on a real spur");

        var inside = scene.at(scene.ward(), 0, 3);
        var outside = scene.at(scene.ward(), 0, LeyStoneBalance.WARD_RADIUS + 8);
        int unwarded = raiseAt(server, outside);
        int warded = raiseAt(server, inside);
        expect(unwarded == RISES * RISE, "Outside the ward corruption should rise in full, rose " + unwarded);
        expect(warded < unwarded, "Corruption should rise slower inside the ward (" + warded + ") than outside (" + unwarded + ")");

        context.getInput().pressKey(InputConstants.KEY_F1);
        var view = scene.at(scene.stone(), 0, MACHINE_OUT + 10);
        aimFrom(context, server, view.getX() + 0.5, F + 4, view.getZ() + 0.5, scene.stone().getX() + 0.5, F + 1, scene.stone().getZ() + 0.5, 20);
        context.takeScreenshot("wave1-ley-spur");
        context.getInput().pressKey(InputConstants.KEY_F1);
        return strength;
    }

    /**
     * Taint set on the line's upstream node with the command weakens the ward drawing on that line.
     */
    private static void checkTaintWeakensWard(ClientGameTestContext context, TestServerContext server, LeyScene scene, float clean)
    {
        wardShot(context, server, scene, "wave1-ley-ward-clean");
        int warded = raiseAt(server, scene.at(scene.ward(), 0, 3));
        var node = scene.upstream();
        server.runCommand("execute positioned " + (node.getX() + 0.5) + " " + F + " " + (node.getZ() + 0.5) + " run voidheart ley taint set " + TAINT);
        context.waitTicks(60);

        float[] after = server.computeOnServer(minecraft ->
        {
            var ward = ward(minecraft, scene);
            return new float[]{ward.taint(), ward.health(), ward.strength()};
        });
        expect(Math.abs(after[0] - TAINT) < 1, "The ward should read its line's taint of " + TAINT + ", reads " + after[0]);
        expect(Math.abs(after[1] - WardStrength.health(after[0], 0)) < 0.02F, "A line tainted to " + after[0] + " should leave the ward " + WardStrength.health(after[0], 0)
                + ", left " + after[1]);
        expect(after[2] < clean, "Taint should weaken the ward, its strength went from " + clean + " to " + after[2]);
        int tainted = raiseAt(server, scene.at(scene.ward(), 0, 3));
        expect(tainted > warded, "Corruption should rise faster beside a weakened ward: " + tainted + " now, " + warded + " on a clean line");

        wardShot(context, server, scene, "wave1-ley-ward-tainted");
    }

    private static void wardShot(ClientGameTestContext context, TestServerContext server, LeyScene scene, String name)
    {
        context.getInput().pressKey(InputConstants.KEY_F1);
        var side = scene.at(scene.ward(), -3, 2);
        aimFrom(context, server, side.getX() + 0.5, F + 0.5, side.getZ() + 0.5, scene.ward().getX() + 0.5, F + 0.5, scene.ward().getZ() + 0.5, 20);
        context.takeScreenshot(name);
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    private static int raiseAt(TestServerContext server, BlockPos pos)
    {
        tp(server, pos.getX() + 0.5, F, pos.getZ() + 0.5);
        return server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            Corruption.set(player, 0);
            for (int i = 0; i < RISES; i++)
                Corruption.raise(player, RISE);
            int risen = Corruption.get(player);
            Corruption.set(player, 0);
            return risen;
        });
    }

    // Helpers

    private static boolean onlyPylon(ServerLevel level, BlockPos pos, BlockPos pylon)
    {
        var covering = VoidGrid.pylonsCovering(level, pos);
        return covering.size() == 1 && covering.getFirst().pos().equals(pylon);
    }

    private static List<BlockPos> covering(ServerLevel level, BlockPos pos)
    {
        return VoidGrid.pylonsCovering(level, pos).stream().map(GridLayout.Pylon::pos).toList();
    }

    private static void useAt(MinecraftServer minecraft, BlockPos pos, Vec3 hit)
    {
        var player = player(minecraft);
        player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, pos, false));
    }

    private static RavenousEngineTile engine(MinecraftServer minecraft)
    {
        if (minecraft.overworld().getBlockEntity(ENGINE) instanceof RavenousEngineTile engine)
            return engine;
        throw new AssertionError("No Ravenous Engine at " + ENGINE.toShortString());
    }

    private static long engineStillness(MinecraftServer minecraft)
    {
        return engine(minecraft).amounts()[EngineFuel.STILLNESS.ordinal()];
    }

    /**
     * Stillness still to burn, in the engine and in the collector the relay keeps topping it up from.
     */
    private static long stillnessLeft(MinecraftServer minecraft)
    {
        var collector = (RavenousCollectorTile) minecraft.overworld().getBlockEntity(COLLECTOR);
        return engineStillness(minecraft) + collector.storage().amount();
    }

    private static AthanorTile athanor(MinecraftServer minecraft)
    {
        return (AthanorTile) minecraft.overworld().getBlockEntity(ATHANOR);
    }

    private static RubrumConstructTile construct(MinecraftServer minecraft)
    {
        if (minecraft.overworld().getBlockEntity(CONSTRUCT) instanceof RubrumConstructTile construct)
            return construct;
        throw new AssertionError("No rubrum construct at " + CONSTRUCT.toShortString());
    }

    private static LeyWardTile ward(MinecraftServer minecraft, LeyScene scene)
    {
        if (minecraft.overworld().getBlockEntity(scene.ward()) instanceof LeyWardTile ward)
            return ward;
        throw new AssertionError("Expected a ward at " + scene.ward().toShortString());
    }
}
