package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.voidheart.client.screen.ArmillaryScreen;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryChart;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryLayout;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryPayloads;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryRiftTile;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryTile;
import net.voxelindustry.voidheart.common.content.armillary.DaedalusConnections;
import net.voxelindustry.voidheart.common.content.athanor.AthanorTile;
import net.voxelindustry.voidheart.common.content.athanor.CrucibleTile;
import net.voxelindustry.voidheart.common.content.beholder.BeholderTile;
import net.voxelindustry.voidheart.common.content.oculus.OculusSight;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameCoreTile;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameTile;
import net.voxelindustry.voidheart.common.content.portalframe.PortalLinker;
import net.voxelindustry.voidheart.common.energy.LeyPower;
import net.voxelindustry.voidheart.common.energy.LeyPowerProvider;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.VoidNetworkProvider;
import net.voxelindustry.voidheart.common.energy.machine.MachineEnergyBalance;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Each machine unlocked after energy runs on a creative source, pauses and dims once it is taken away, and picks up where it stopped once it is back:
 * the armillary's search, opening and hold of the Daedalus, an athanor's brew, a portal pair, and a beholder's sight.
 */
public class MachineRetrofitClientTest implements FabricClientGameTest
{
    private static final int FEET   = FLAT_FEET;
    /** Sources go in the dirt under the grass, out of sight. */
    private static final int BURIED = FLAT_GROUND - 2;
    /** Long enough for a machine's power meter to judge a whole second, and its state to reach the client. */
    private static final int SETTLE = 45;

    private static final BlockPos ARMILLARY      = new BlockPos(0, FEET, 0);
    private static final BlockPos ARMILLARY_WELL = new BlockPos(0, BURIED, 0);

    private static final BlockPos INTAKE       = new BlockPos(40, FEET, 0);
    private static final BlockPos ATHANOR      = INTAKE.above();
    private static final BlockPos ALEMBIC      = ATHANOR.above();
    private static final BlockPos SULFUR       = ATHANOR.east();
    private static final BlockPos QUICKSILVER  = ATHANOR.west();
    private static final BlockPos ATHANOR_WELL = new BlockPos(40, BURIED, 0);

    private static final BlockPos PORTAL_A    = new BlockPos(80, FEET, 0);
    private static final BlockPos PORTAL_B    = new BlockPos(80, FEET, 40);
    private static final BlockPos CORE_A      = PORTAL_A.offset(2, 0, 0);
    private static final BlockPos CORE_B      = PORTAL_B.offset(2, 0, 0);
    private static final BlockPos PORTAL_WELL = new BlockPos(82, BURIED, 40);

    private static final BlockPos TABLE     = new BlockPos(120, FEET, 0);
    private static final BlockPos BEHOLDER  = new BlockPos(140, FEET, 0);
    private static final BlockPos LEY_STONE = new BlockPos(140, BURIED, 0);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("forceload add -16 -16 160 56");
            // Jade only shows with the HUD, which an earlier test may have left hidden
            boolean hudWasHidden = context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden());
            var camera = context.computeOnClient(minecraft -> minecraft.options.getCameraType());
            context.runOnClient(minecraft ->
            {
                if (hudWasHidden)
                    minecraft.gui.hud.toggle();
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            });
            context.waitTicks(10);

            checkArmillarySearchAndOpening(context, server);
            checkDaedalusHold(context, server);
            checkAthanor(context, server);
            checkPortal(context, server);
            checkBeholder(context, server);

            context.runOnClient(minecraft ->
            {
                if (hudWasHidden)
                    minecraft.gui.hud.toggle();
                minecraft.options.setCameraType(camera);
            });
        }
    }

    // The armillary

    private static void checkArmillarySearchAndOpening(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(ARMILLARY, VoidHeartBlocks.ARMILLARY_CORE.defaultBlockState());
            for (var part : ArmillaryLayout.base(ARMILLARY))
                level.setBlockAndUpdate(part.pos(), part.block().defaultBlockState());
        });
        setSource(server, ARMILLARY_WELL, true);
        waitFor(context, server, 100, "The armillary should form", minecraft -> armillary(minecraft).isFormed());

        server.runOnServer(minecraft ->
        {
            var armillary = armillary(minecraft);
            armillary.revealBubble();
            var chart = armillary.getChart();
            chart.target(chart.bubbles().getFirst().id(), ArmillaryTile.rules(), RandomSource.create(7));
        });
        context.waitTicks(SETTLE);
        int searched = onServer(server, minecraft -> armillary(minecraft).getChart().getSearchTicks());
        expect(searched > 0, "A powered search should advance, it is at " + searched + " ticks");
        expect(armillaryPower(server) == MachinePower.POWERED, "A powered search should be POWERED, it is " + armillaryPower(server));
        expectWarmth(context, 1, "A paid armillary's inner ring should glow fully");
        ringShot(context, server, "retrofit-armillary-rings-powered");
        jadeShot(context, server, ARMILLARY, 2.5, 1, 0.5, 0.6, "retrofit-armillary-search-powered");

        // Cut: the search waits where it is, the rings slow, the screen and Jade say why
        setSource(server, ARMILLARY_WELL, false);
        context.waitTicks(SETTLE);
        expect(armillaryPower(server) == MachinePower.UNPOWERED, "A search without a source should be UNPOWERED, it is " + armillaryPower(server));
        int stalled = onServer(server, minecraft -> armillary(minecraft).getChart().getSearchTicks());
        context.waitTicks(60);
        int still = onServer(server, minecraft -> armillary(minecraft).getChart().getSearchTicks());
        expect(still == stalled, "An unpowered search should wait, it went from " + stalled + " to " + still + " ticks");
        expectClient(context, "The client should know the armillary is unpowered", minecraft -> clientArmillary(minecraft).getPower() == MachinePower.UNPOWERED);
        expectWarmth(context, 0, "An unpowered armillary's inner ring should have gone cold");
        ringShot(context, server, "retrofit-armillary-rings-unpowered");

        openChart(context, server);
        String whisper = context.computeOnClient(minecraft -> ((ArmillaryScreen) minecraft.gui.screen()).whisper());
        expect(whisper.equals("The roots wait for void energy."), "The map should say the search waits, it says: " + whisper);
        context.takeScreenshot("retrofit-armillary-search-unpowered-map");
        closeScreen(context);
        jadeShot(context, server, ARMILLARY, 2.5, 1, 0.5, 0.6, "retrofit-armillary-search-unpowered");
        // Its base shows the armillary's state too, not just its own name
        jadeShot(context, server, ARMILLARY.south().east(), 2, 0.5, 2, 0.5, "retrofit-armillary-base-jade");

        // Weak: half of what it asks for, so the inner ring warms halfway and the rings turn at half speed
        var network = server.computeOnServer(minecraft -> VoidEnergy.provider());
        try
        {
            server.runOnServer(minecraft -> VoidEnergy.install(halfSupply()));
            waitFor(context, server, 100, "A search on half its draw should be WEAK", minecraft -> armillary(minecraft).getPower() == MachinePower.WEAK);
            context.waitTicks(30);
            expectClient(context, "The client should know the armillary is weak", minecraft -> clientArmillary(minecraft).getPower() == MachinePower.WEAK);
            expectWarmth(context, ArmillaryTile.warmthOf(MachinePower.WEAK), "A weak armillary's inner ring should glow halfway");
            ringShot(context, server, "retrofit-armillary-rings-weak");
        }
        finally
        {
            server.runOnServer(minecraft -> VoidEnergy.install(network));
        }

        // Restored: it picks up from where it waited
        setSource(server, ARMILLARY_WELL, true);
        context.waitTicks(SETTLE);
        int resumed = onServer(server, minecraft -> armillary(minecraft).getChart().getSearchTicks());
        expect(resumed > still, "A search powered again should go on from " + still + ", it is at " + resumed);
        expect(armillaryPower(server) == MachinePower.POWERED, "The restored search should be POWERED, it is " + armillaryPower(server));

        // Reached: the opening gathers its charge, and waits for it without power
        server.runOnServer(minecraft -> armillary(minecraft).advanceSearch(1));
        context.waitTicks(SETTLE);
        long gathered = onServer(server, minecraft -> armillary(minecraft).getCharge());
        long cost = onServer(server, minecraft -> armillary(minecraft).openingCost());
        expect(gathered > 0 && gathered < cost, "The opening should be gathering its charge, it holds " + gathered + " of " + cost);

        setSource(server, ARMILLARY_WELL, false);
        context.waitTicks(SETTLE);
        long held = onServer(server, minecraft -> armillary(minecraft).getCharge());
        context.waitTicks(40);
        expect(onServer(server, minecraft -> armillary(minecraft).getCharge()) == held, "An unpowered opening should hold its charge without gaining");
        expect(armillaryPower(server) == MachinePower.UNPOWERED, "A charge without a source should be UNPOWERED, it is " + armillaryPower(server));

        openChart(context, server);
        context.waitTicks(30);
        String label = context.computeOnClient(minecraft -> ((ArmillaryScreen) minecraft.gui.screen()).primaryLabel());
        expect(label.startsWith("Unpowered: ") && label.endsWith("%"), "The plate should show the charge as unpowered, not " + label);
        String reachedWhisper = context.computeOnClient(minecraft -> ((ArmillaryScreen) minecraft.gui.screen()).whisper());
        expect(reachedWhisper.equals("A path holds, but no void energy reaches it."), "The map should say the charge waits, it says: " + reachedWhisper);
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(2);
        context.takeScreenshot("retrofit-armillary-charge-unpowered-map");
        sendAction(context, ArmillaryPayloads.ActionKind.OPEN);
        context.waitTicks(10);
        expect(onServer(server, minecraft -> armillary(minecraft).getChart().getPhase()) == ArmillaryChart.Phase.REACHED, "Opening should be refused before the charge is gathered");
        expect(overlay(context).startsWith("Gathering void energy to open the way"), "The action bar should say the charge is missing, it shows: " + overlay(context));
        closeScreen(context);
        jadeShot(context, server, ARMILLARY, 2.5, 1, 0.5, 0.6, "retrofit-armillary-charge-unpowered");

        setSource(server, ARMILLARY_WELL, true);
        waitFor(context, server, 600, "The opening should finish gathering once powered again", minecraft -> armillary(minecraft).getCharge() >= armillary(minecraft).openingCost());
        openChart(context, server);
        context.waitTicks(30);
        sendAction(context, ArmillaryPayloads.ActionKind.OPEN);
        context.waitTicks(10);
        expect(onServer(server, minecraft -> armillary(minecraft).getChart().getPhase()) == ArmillaryChart.Phase.CONNECTED, "A charged opening should open");
        expect(onServer(server, minecraft -> armillary(minecraft).getCharge()) < cost, "Opening should spend the charge");
        closeScreen(context);

        // Let the dungeon's connection go, for the Daedalus next
        server.runOnServer(minecraft -> armillary(minecraft).decayIn(1));
        waitFor(context, server, 200, "The dungeon's connection should close", minecraft -> armillary(minecraft).getChart().getPhase() == ArmillaryChart.Phase.SCANNING);
    }

    /**
     * Held open, the Daedalus draws steadily. A short cut is caught up once power is back; a long one severs the way.
     */
    private static void checkDaedalusHold(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("execute as @a at @a run voidheart research grant voidheart:daedalus");
        server.runOnServer(minecraft -> armillary(minecraft).getChart().target(ArmillaryChart.STRATA, ArmillaryTile.rules(), RandomSource.create(11)));
        context.waitTicks(5);
        server.runOnServer(minecraft -> armillary(minecraft).advanceSearch(1));
        waitFor(context, server, 1200, "The way into the Daedalus should gather its charge", minecraft -> armillary(minecraft).getCharge() >= armillary(minecraft).openingCost());
        openChart(context, server);
        context.waitTicks(30);
        sendAction(context, ArmillaryPayloads.ActionKind.OPEN);
        context.waitTicks(10);
        closeScreen(context);
        int id = onServer(server, minecraft -> armillary(minecraft).getDaedalusConnection());
        expect(id != ArmillaryRiftTile.NONE, "A charged armillary should open the way into the Daedalus");

        context.waitTicks(SETTLE);
        expect(armillaryPower(server) == MachinePower.POWERED, "A paid hold should be POWERED, it is " + armillaryPower(server));
        expect(onServer(server, minecraft -> armillary(minecraft).getHoldDebt()) == 0, "A paid hold should owe nothing");

        setSource(server, ARMILLARY_WELL, false);
        context.waitTicks(10 * 20);
        long debt = onServer(server, minecraft -> armillary(minecraft).getHoldDebt());
        expect(debt > 0, "A starved hold should fall behind");
        expect(!severed(server, id), "Ten seconds without power should not sever the way");
        expect(armillaryPower(server) == MachinePower.UNPOWERED, "A starved hold should be UNPOWERED, it is " + armillaryPower(server));
        jadeShot(context, server, ARMILLARY, 2.5, 1, 0.5, 0.6, "retrofit-armillary-hold-starved");

        setSource(server, ARMILLARY_WELL, true);
        waitFor(context, server, 20 * 20, "A hold powered again should catch up what it owes", minecraft -> armillary(minecraft).getHoldDebt() == 0);
        expect(!severed(server, id), "A hold caught up should still hold");

        setSource(server, ARMILLARY_WELL, false);
        waitFor(context, server, (MachineEnergyBalance.HOLD_STARVED_TICKS + 10 * 20), "A hold starved for too long should be severed", minecraft -> severed(minecraft, id));
        expect(onServer(server, minecraft -> armillary(minecraft).getDaedalusConnection()) == id, "A severed way still lets its players out until it closes");

        setSource(server, ARMILLARY_WELL, true);
        server.runOnServer(minecraft -> armillary(minecraft).decayIn(1));
        waitFor(context, server, 200, "The severed way should close", minecraft -> armillary(minecraft).getDaedalusConnection() == ArmillaryRiftTile.NONE);
    }

    // The athanor

    private static void checkAthanor(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("execute as @a run voidheart research grant voidheart:sulfur_rite");
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(INTAKE, VoidHeartBlocks.ASPECT_INTAKE.defaultBlockState());
            level.setBlockAndUpdate(ATHANOR, VoidHeartBlocks.ATHANOR.defaultBlockState());
            level.setBlockAndUpdate(ALEMBIC, VoidHeartBlocks.ALEMBIC.defaultBlockState());
            for (var crucible : new BlockPos[] { SULFUR, QUICKSILVER })
            {
                level.setBlockAndUpdate(crucible.below(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(crucible, VoidHeartBlocks.CRUCIBLE.defaultBlockState());
            }
        });
        setSource(server, ATHANOR_WELL, true);
        context.waitTicks(5);
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

        context.waitTicks(SETTLE);
        int brewed = onServer(server, minecraft -> athanor(minecraft).getProgress());
        expect(brewed > 0, "A powered brew should advance");
        expect(athanorPower(server) == MachinePower.POWERED, "A powered brew should be POWERED, it is " + athanorPower(server));
        expect(athanorLit(server), "A powered brew should be lit");
        long rate = onServer(server, minecraft -> athanor(minecraft).drawRate());
        expect(rate == 60, "Searing oil should draw 500 aspects x 24 over 200 ticks, 60 a tick, not " + rate);
        jadeShot(context, server, ATHANOR, 0.5, 0, -2.5, 0.5, "retrofit-athanor-powered");

        setSource(server, ATHANOR_WELL, false);
        context.waitTicks(SETTLE);
        expect(athanorPower(server) == MachinePower.UNPOWERED, "A brew without a source should be UNPOWERED, it is " + athanorPower(server));
        expect(!athanorLit(server), "An unpowered brew should go dark");
        int waiting = onServer(server, minecraft -> athanor(minecraft).getProgress());
        context.waitTicks(40);
        int stillWaiting = onServer(server, minecraft -> athanor(minecraft).getProgress());
        expect(stillWaiting == waiting, "An unpowered brew should wait, it went from " + waiting + " to " + stillWaiting);
        expect(onServer(server, minecraft -> athanor(minecraft).isBrewing()), "An unpowered brew should wait rather than stop");
        expectClient(context, "The client should know the athanor is unpowered",
                minecraft -> ((AthanorTile) minecraft.level.getBlockEntity(ATHANOR)).getPower() == MachinePower.UNPOWERED);
        jadeShot(context, server, ATHANOR, 0.5, 0, -2.5, 0.5, "retrofit-athanor-unpowered");

        // Weak: it brews on, slower, still lit, its fire guttering
        var network = server.computeOnServer(minecraft -> VoidEnergy.provider());
        try
        {
            server.runOnServer(minecraft -> VoidEnergy.install(halfSupply()));
            waitFor(context, server, 100, "A brew on half its draw should be WEAK", minecraft -> athanor(minecraft).getPower() == MachinePower.WEAK);
            expect(athanorLit(server), "A weak brew should stay lit");
            int slow = onServer(server, minecraft -> athanor(minecraft).getProgress());
            context.waitTicks(40);
            expect(onServer(server, minecraft -> athanor(minecraft).getProgress()) > slow, "A weak brew should still advance");
            expectClient(context, "The client should know the athanor is weak",
                    minecraft -> ((AthanorTile) minecraft.level.getBlockEntity(ATHANOR)).getPower() == MachinePower.WEAK);
            jadeShot(context, server, ATHANOR, 0.5, 0, -2.5, 0.5, "retrofit-athanor-weak");
        }
        finally
        {
            server.runOnServer(minecraft -> VoidEnergy.install(network));
        }

        setSource(server, ATHANOR_WELL, true);
        context.waitTicks(SETTLE);
        expect(onServer(server, minecraft -> athanor(minecraft).getProgress()) > stillWaiting, "A brew powered again should go on");
        expect(athanorLit(server), "A brew powered again should light up");
        waitFor(context, server, 400, "The brew should finish", minecraft -> !athanor(minecraft).isBrewing());
        expect(onServer(server, minecraft -> athanor(minecraft).getStack().is(VoidHeartItems.SEARING_OIL)), "The brew should leave searing oil");
    }

    // A portal pair

    private static void checkPortal(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            for (var corner : new BlockPos[] { PORTAL_A, PORTAL_B })
            {
                for (int y = 0; y < 5; y++)
                {
                    for (int x = 0; x < 5; x++)
                    {
                        if (x == 0 || y == 0 || x == 4 || y == 4)
                            level.setBlockAndUpdate(corner.offset(x, y, 0), VoidHeartBlocks.PORTAL_FRAME.defaultBlockState());
                    }
                }
            }
        });
        // Only the far side stands in a field: the pair still opens, paid from there
        setSource(server, PORTAL_WELL, true);
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var player = player(minecraft);
            var eye = new ItemStack(VoidHeartItems.PORTAL_CORE, 2);
            expect(PortalLinker.voidIronEyeInteract((PortalFrameTile) level.getBlockEntity(CORE_A), level, CORE_A, Direction.NORTH, player, eye), "The first portal should form");
            expect(PortalLinker.voidIronEyeInteract((PortalFrameTile) level.getBlockEntity(CORE_B), level, CORE_B, Direction.NORTH, player, eye), "The second portal should link");
        });
        context.waitTicks(SETTLE + MachineEnergyBalance.PORTAL_STARVED_TICKS);
        expectPortalOpen(server, true, "A pair with a field on one side");
        expect(onServer(server, minecraft -> core(minecraft, CORE_A).getPower() == MachinePower.POWERED || core(minecraft, CORE_B).getPower() == MachinePower.POWERED),
                "One core of the pair should pay for it");
        jadeShot(context, server, CORE_A, 2.5, 0, -2.5, 0.5, "retrofit-portal-open");

        crossAt(server, CORE_A);
        context.waitTicks(10);
        expect(onServer(server, minecraft -> player(minecraft).getZ()) > 30, "An open portal should let the player through");

        setSource(server, PORTAL_WELL, false);
        context.waitTicks(MachineEnergyBalance.PORTAL_STARVED_TICKS + 20);
        expectPortalOpen(server, false, "A pair with no field left");
        expect(onServer(server, minecraft -> core(minecraft, CORE_A).isLinked() && core(minecraft, CORE_B).isLinked()), "A dark pair should keep its link");
        jadeShot(context, server, CORE_A, 2.5, 0, -2.5, 0.5, "retrofit-portal-dark");
        crossAt(server, CORE_A);
        context.waitTicks(10);
        expect(onServer(server, minecraft -> player(minecraft).getZ()) < 10, "A dark portal should let nobody through");

        setSource(server, PORTAL_WELL, true);
        context.waitTicks(10);
        expectPortalOpen(server, true, "A pair powered again");
        aimFrom(context, server, CORE_A.getX() + 0.5, FEET, CORE_A.getZ() - 6.5, CORE_A.getX() + 0.5, FEET + 2, CORE_A.getZ() + 0.5, 10);
        context.takeScreenshot("retrofit-portal-relit");
    }

    private static void expectPortalOpen(TestServerContext server, boolean open, String what)
    {
        server.runOnServer(minecraft ->
        {
            for (var pos : new BlockPos[] { CORE_A, CORE_B })
            {
                var core = core(minecraft, pos);
                var state = minecraft.overworld().getBlockState(pos);
                expect(core.isDark() != open, what + " should be " + (open ? "open" : "dark") + " at " + pos.toShortString());
                expect(state.getValue(BlockStateProperties.LIT) == open, what + " should " + (open ? "light" : "darken") + " its core at " + pos.toShortString());
                expect(core.getLinkedInteriors().size() == (open ? 9 : 0), what + " should have " + (open ? 9 : 0) + " interiors at " + pos.toShortString() + ", has "
                        + core.getLinkedInteriors().size());
                expect(minecraft.overworld().getBlockState(pos.above(2)).is(VoidHeartBlocks.PORTAL_INTERIOR) == open, what + " should " + (open ? "fill" : "empty") + " its middle");
            }
        });
    }

    private static void crossAt(TestServerContext server, BlockPos core)
    {
        server.runCommand("tp @a " + (core.getX() + 0.5) + " " + (core.getY() + 1) + " " + (core.getZ() + 0.5) + " 0 0");
    }

    // The beholder

    private static void checkBeholder(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("fill " + (TABLE.getX() - 2) + " " + TABLE.getY() + " -2 " + (TABLE.getX() + 2) + " " + TABLE.getY() + " 2 voidheart:oculus_table");
        setSource(server, LEY_STONE, true);
        server.runCommand("setblock " + BEHOLDER.getX() + " " + BEHOLDER.getY() + " " + BEHOLDER.getZ() + " voidheart:beholder");
        context.waitTicks(SETTLE);
        int full = VoidHeartConfig.get().beholderLowRange;
        expect(beholderRange(server) == full, "A beholder with all its ley should see its whole range, " + full + ", it sees " + beholderRange(server));
        jadeShot(context, server, BEHOLDER, 0.5, 0, -2.5, 0.6, "retrofit-beholder-powered");
        expectEye(context, 1, false, "A beholder with all its ley");
        eyeShot(context, server, "retrofit-beholder-eye-powered");

        var previous = LeyPower.provider();
        try
        {
            setSource(server, LEY_STONE, false);
            LeyPower.install(fixedLey(0.5F));
            context.waitTicks(SETTLE);
            int half = Math.round(full * 0.5F);
            expect(beholderRange(server) == half, "A beholder on half its ley should see half its range, " + half + ", it sees " + beholderRange(server));
            jadeShot(context, server, BEHOLDER, 0.5, 0, -2.5, 0.6, "retrofit-beholder-half");
            expectEye(context, 0.5F, false, "A beholder on half its ley");
            eyeShot(context, server, "retrofit-beholder-eye-half");

            LeyPower.install(fixedLey(0.05F));
            context.waitTicks(SETTLE);
            expect(beholderRange(server) == 0, "A beholder under a tenth of its ley should be blind, it sees " + beholderRange(server));
            expect(onServer(server, minecraft -> minecraft.overworld().getBlockEntity(BEHOLDER) instanceof BeholderTile beholder && beholder.sight() == 0),
                    "A starved beholder should be blind");
            jadeShot(context, server, BEHOLDER, 0.5, 0, -2.5, 0.6, "retrofit-beholder-blind");
            expectEye(context, 0, true, "A blind beholder");
            eyeShot(context, server, "retrofit-beholder-eye-blind");
        }
        finally
        {
            LeyPower.install(previous);
        }

        setSource(server, LEY_STONE, true);
        context.waitTicks(SETTLE);
        expect(beholderRange(server) == full, "A beholder with its ley back should see its whole range again, it sees " + beholderRange(server));
        jadeShot(context, server, BEHOLDER, 0.5, 0, -2.5, 0.6, "retrofit-beholder-woken");
        expectEye(context, 1, false, "A beholder with its ley back");
    }

    /**
     * The eye on the client: its iris as bright as the share it sees, and drowsed off, looking down at its pedestal, only when blind.
     */
    private static void expectEye(ClientGameTestContext context, float glow, boolean drowsing, String what)
    {
        String seen = context.computeOnClient(minecraft ->
        {
            var eye = (BeholderTile) minecraft.level.getBlockEntity(BEHOLDER);
            return String.format(java.util.Locale.ROOT, "glow %.2f, drowse %.2f, pitch %.2f", eye.glow, eye.drowse, eye.eyePitch);
        });
        expectClient(context, what + " should shine at " + glow + " and " + (drowsing ? "" : "not ") + "drowse, it has " + seen, minecraft ->
        {
            var eye = (BeholderTile) minecraft.level.getBlockEntity(BEHOLDER);
            return Math.abs(eye.glow - glow) < 0.02F && (drowsing ? eye.drowse > 0.98F && eye.eyePitch < -0.4F : eye.drowse < 0.02F);
        });
    }

    /**
     * The eye at night, close up and without the HUD, where its iris shows how much it sees.
     */
    private static void eyeShot(ClientGameTestContext context, TestServerContext server, String name)
    {
        freezeTime(server, "midnight");
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, BEHOLDER.getX() + 1.6, FEET, BEHOLDER.getZ() - 1.4, BEHOLDER.getX() + 0.5, FEET + 0.75, BEHOLDER.getZ() + 0.5, 20);
        context.takeScreenshot(name);
        context.getInput().pressKey(InputConstants.KEY_F1);
        freezeTime(server, "noon");
    }

    private static LeyPowerProvider fixedLey(float supply)
    {
        return (level, pos, need) -> supply;
    }

    /**
     * How far the table sees through the beholder, 0 if the beholder isn't one of its sources.
     */
    private static int beholderRange(TestServerContext server)
    {
        return onServer(server, minecraft -> OculusSight.of(minecraft.overworld(), TABLE).sources().stream()
                .filter(source -> source.beholder() && source.pos().equals(BEHOLDER))
                .mapToInt(OculusSight.Source::lowRange)
                .findFirst().orElse(0));
    }

    // Helpers

    /**
     * Gives every machine half of what it draws, rounded up so a last unit still comes, from nowhere, so it runs WEAK.
     */
    private static VoidNetworkProvider halfSupply()
    {
        return new VoidNetworkProvider()
        {
            @Override
            public long available(ServerLevel level, BlockPos pos)
            {
                return Long.MAX_VALUE / 2;
            }

            @Override
            public long draw(ServerLevel level, BlockPos pos, long amount)
            {
                return (amount + 1) / 2;
            }

            @Override
            public long offer(ServerLevel level, BlockPos pos, long amount)
            {
                return 0;
            }
        };
    }

    private static void expectWarmth(ClientGameTestContext context, float warmth, String message)
    {
        float seen = context.computeOnClient(minecraft -> clientArmillary(minecraft).warmth(1));
        expect(Math.abs(seen - warmth) < 0.02F, message + ": its warmth is " + seen + ", not " + warmth);
    }

    /**
     * The rings at night, close up and without the HUD, where the inner ring's glow or its absence shows.
     */
    private static void ringShot(ClientGameTestContext context, TestServerContext server, String name)
    {
        freezeTime(server, "midnight");
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, ARMILLARY.getX() + 4.5, FEET + 1, ARMILLARY.getZ() + 0.5, ARMILLARY.getX() + 0.5, FEET + 2.5, ARMILLARY.getZ() + 0.5, 20);
        context.takeScreenshot(name);
        context.getInput().pressKey(InputConstants.KEY_F1);
        freezeTime(server, "noon");
    }

    private static void setSource(TestServerContext server, BlockPos pos, boolean present)
    {
        String block = !present ? "dirt" : pos.equals(LEY_STONE) ? "voidheart:creative_ley_stone" : "voidheart:creative_void_well";
        server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block);
    }

    /**
     * Looks at a machine from close by, so Jade shows its line, and takes a screenshot.
     */
    private static void jadeShot(ClientGameTestContext context, TestServerContext server, BlockPos target, double dx, double dy, double dz, double aimY, String name)
    {
        aimFrom(context, server, target.getX() + dx, target.getY() + dy, target.getZ() + dz, target.getX() + 0.5, target.getY() + aimY, target.getZ() + 0.5, 20);
        context.takeScreenshot(name);
    }

    private static void openChart(ClientGameTestContext context, TestServerContext server)
    {
        aimFrom(context, server, 3.5, FEET, 1.5, 0.5, FEET + 0.9, 0.5, 5);
        server.runOnServer(minecraft -> armillary(minecraft).use(player(minecraft), false));
        context.waitTicks(10);
        expectClient(context, "The armillary's map should open", minecraft -> minecraft.gui.screen() instanceof ArmillaryScreen);
    }

    private static void closeScreen(ClientGameTestContext context)
    {
        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        context.waitTicks(5);
    }

    private static void sendAction(ClientGameTestContext context, ArmillaryPayloads.ActionKind kind)
    {
        context.runOnClient(minecraft -> ClientPlayNetworking.send(new ArmillaryPayloads.Action(kind, -1)));
    }

    private static boolean severed(TestServerContext server, int id)
    {
        return onServer(server, minecraft -> severed(minecraft, id));
    }

    private static boolean severed(MinecraftServer minecraft, int id)
    {
        var open = DaedalusConnections.get(minecraft).get(id);
        return open != null && open.isSevered();
    }

    private static ArmillaryTile armillary(MinecraftServer minecraft)
    {
        return (ArmillaryTile) minecraft.overworld().getBlockEntity(ARMILLARY);
    }

    private static ArmillaryTile clientArmillary(Minecraft minecraft)
    {
        return (ArmillaryTile) minecraft.level.getBlockEntity(ARMILLARY);
    }

    private static MachinePower armillaryPower(TestServerContext server)
    {
        return onServer(server, minecraft -> armillary(minecraft).getPower());
    }

    private static AthanorTile athanor(MinecraftServer minecraft)
    {
        return (AthanorTile) minecraft.overworld().getBlockEntity(ATHANOR);
    }

    private static MachinePower athanorPower(TestServerContext server)
    {
        return onServer(server, minecraft -> athanor(minecraft).getPower());
    }

    private static boolean athanorLit(TestServerContext server)
    {
        return onServer(server, minecraft -> minecraft.overworld().getBlockState(ATHANOR).getValue(BlockStateProperties.LIT));
    }

    private static PortalFrameCoreTile core(MinecraftServer minecraft, BlockPos pos)
    {
        return (PortalFrameCoreTile) minecraft.overworld().getBlockEntity(pos);
    }

    private static <T> T onServer(TestServerContext server, java.util.function.Function<MinecraftServer, T> function)
    {
        return server.computeOnServer(function::apply);
    }
}
