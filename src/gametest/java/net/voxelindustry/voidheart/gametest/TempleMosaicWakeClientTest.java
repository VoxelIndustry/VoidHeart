package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.voxelindustry.voidheart.client.render.temple.MosaicStare;
import net.voxelindustry.voidheart.client.render.temple.MosaicWake;
import net.voxelindustry.voidheart.client.render.temple.MosaicWall;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.underway.UnderwayFort;
import net.voxelindustry.voidheart.common.world.underway.UnderwayPlans;
import net.voxelindustry.voidheart.common.world.underway.UnderwayTemple;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Stares at a temple's mosaic until it wakes: that the stare is seen and counted, that it notices the player, stirs and reaches out, and withdraws when they look
 * away while still watching them. Captured at rest, once noticed, stirring, halfway out, fully out from the aisle, both sides and the doorway, and as it withdraws, with its mouth's eye close up before it notices the player and after.
 */
public class TempleMosaicWakeClientTest implements FabricClientGameTest
{
    private static final int CHUNK_TIMEOUT_TICKS = 1200;
    private static final int MOVE_TIMEOUT_TICKS = 200;
    /** The columns of the picture the angel's wheels and the veiled one's head are in. */
    private static final int ANGEL_COLUMN = 3;
    private static final int VEILED_COLUMN = 11;
    /** The frozen sweep's steps, and how far each moves across the temple and in toward the mosaic, in blocks. */
    private static final int SWEEP_STEPS = 8;
    private static final double SWEEP_ACROSS = 0.05;
    private static final double SWEEP_INWARD = 0.08;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode spectator @a");
            var cameraBefore = context.computeOnClient(minecraft -> minecraft.options.getCameraType());
            boolean hudWasHidden = context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden());
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);
            try
            {
                var fort = visit(context, world);
                expect(fort != null, "No dry temple was found near the start");
                server.runCommand("effect give @a minecraft:night_vision infinite 0 true");
                int left = UnderwayTemple.AISLE - 3;
                int middle = UnderwayTemple.ROWS / 2;

                look(context, world, fort, left, 3, middle);
                world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
                context.waitTicks(3);
                expect(!context.computeOnClient(minecraft -> MosaicStare.wake().noticed()),
                        "The mosaic noticed the player while chunks rendered, so its rest frame would show it awake: stare " + context.computeOnClient(minecraft -> MosaicStare.wake().stareTicks()));
                shot(context, "rest");
                expect(context.computeOnClient(minecraft -> MosaicStare.staring() && MosaicStare.wall() != null),
                        "Looking at the mosaic from the aisle did not count as staring at it: " + context.computeOnClient(TempleMosaicWakeClientTest::seen));
                // The great mouth's eye, from the left, before and after it turns to the player
                look(context, world, fort, left + 1, 5, 1);
                shot(context, "rest-eye");

                waitForStare(context, MosaicWake.NOTICE + 30);
                expectClient(context, "The mosaic did not notice the player after " + MosaicWake.NOTICE + " ticks", minecraft -> MosaicStare.wake().noticed());
                shot(context, "noticed-eye");
                look(context, world, fort, left, 3, middle);
                shot(context, "noticed");
                waitForStare(context, (MosaicWake.STIR_START + MosaicWake.STIR_FULL) / 2 + 20);
                shot(context, "stirring");
                // The angel's wheels turning on the wall, its brood dripping and the faithful looking up, then the veiled one's halo
                int angel = columnAt(fort, ANGEL_COLUMN);
                int veiled = columnAt(fort, VEILED_COLUMN);
                lookAt(context, world, fort, angel, 6, ANGEL_COLUMN, middle);
                shot(context, "stirring-angel");
                lookAt(context, world, fort, angel, 7, ANGEL_COLUMN, UnderwayTemple.ROWS - 2);
                shot(context, "stirring-faithful");
                lookAt(context, world, fort, veiled, 6, VEILED_COLUMN, middle);
                shot(context, "stirring-veiled");
                look(context, world, fort, left, 3, middle);
                waitForStare(context, (MosaicWake.REACH_START + MosaicWake.REACH_FULL) / 2);
                shot(context, "reaching");
                waitForStare(context, MosaicWake.MOST);
                shot(context, "reached");
                // It was heard, and it draws the view in
                expectClient(context, "The mosaic made no sound while it woke", minecraft -> MosaicStare.sounds().played() >= 3);
                float narrowed = context.computeOnClient(minecraft -> minecraft.player.getFieldOfViewModifier(true, 1));
                sweep(context, world, fort);
                lookAt(context, world, fort, angel, 5, ANGEL_COLUMN, middle);
                shot(context, "reached-angel");
                lookAt(context, world, fort, veiled, 5, VEILED_COLUMN, middle);
                shot(context, "reached-veiled");
                lookAt(context, world, fort, UnderwayTemple.AISLE, 5, VEILED_COLUMN, middle);
                shot(context, "reached-veiled-aside");

                look(context, world, fort, 1, 8, middle);
                shot(context, "reached-side-left");
                look(context, world, fort, UnderwayTemple.WIDTH - 2, 7, middle);
                shot(context, "reached-side-right");
                look(context, world, fort, UnderwayTemple.AISLE, 0, middle);
                shot(context, "reached-doorway");

                // Looking at the wall beside it, so it stays in view as it withdraws
                look(context, world, fort, left, 6, -1);
                expectClient(context, "Looking beside the mosaic still counted as staring", minecraft -> !MosaicStare.staring());
                shot(context, "withdrawing");
                context.waitTicks(MosaicWake.MOST / MosaicWake.WITHDRAW);
                expectClient(context, "The mosaic did not withdraw once the player looked away", minecraft -> MosaicStare.wake().stareTicks() == 0);
                expectClient(context, "The mosaic forgot the player while they were still in the temple", minecraft -> MosaicStare.wake().noticed());
                // Flying sets its own field of view, so the narrowing shows against the same flight once it withdrew
                float view = context.computeOnClient(minecraft -> minecraft.player.getFieldOfViewModifier(true, 1));
                expect(narrowed < view * 0.93F, "The view did not narrow at full reach: " + narrowed + " against " + view + " once withdrawn");
                look(context, world, fort, left + 1, 5, 1);
                shot(context, "withdrawn-eye");
                server.runCommand("effect clear @a");
            } finally
            {
                context.runOnClient(minecraft -> minecraft.options.setCameraType(cameraBefore));
                if (!hudWasHidden)
                    context.getInput().pressKey(InputConstants.KEY_F1);
            }
        }
    }

    private static UnderwayFort visit(ClientGameTestContext context, TestSingleplayerContext world)
    {
        var server = world.getServer();
        var before = server.computeOnServer(minecraft -> player(minecraft).blockPosition());
        server.runCommand("execute as @a run voidheart underway temple dry");
        for (int tick = 0; tick < MOVE_TIMEOUT_TICKS && server.computeOnServer(minecraft -> player(minecraft).blockPosition()).equals(before); tick++)
            context.waitTick();
        var fort = server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            if (player.level().dimension() != VoidDaedalus.WORLD_KEY || player.blockPosition().equals(before))
                return null;
            return UnderwayPlans.fort(VoidDaedalus.level(minecraft).getSeed(), DistrictPos.at(player.getBlockX(), player.getBlockZ()));
        });
        if (fort != null)
            world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
        return fort;
    }

    /**
     * Stands at a place in the temple, {@code w} across it and {@code d} in from its doorway, and looks at the middle of a row of the mosaic, or with a row of
     * -1 at the side wall beside it.
     */
    private static void look(ClientGameTestContext context, TestSingleplayerContext world, UnderwayFort fort, int w, int d, int row)
    {
        var temple = fort.temple();
        var to = row < 0 ? blockAt(fort, 0, temple.rowY(UnderwayTemple.ROWS / 2), UnderwayTemple.DEPTH - 1)
                : blockAt(fort, UnderwayTemple.AISLE, temple.rowY(row), UnderwayTemple.DEPTH);
        aim(context, world, fort, w, d, to);
    }

    /** Stands at a place in the temple and looks at one tile of the mosaic, by its column of the picture and its row. */
    private static void lookAt(ClientGameTestContext context, TestSingleplayerContext world, UnderwayFort fort, int w, int d, int column, int row)
    {
        aim(context, world, fort, w, d, blockAt(fort, columnAt(fort, column), fort.temple().rowY(row), UnderwayTemple.DEPTH));
    }

    private static void aim(ClientGameTestContext context, TestSingleplayerContext world, UnderwayFort fort, int w, int d, BlockPos to)
    {
        int floor = fort.temple().room().floorY();
        var from = blockAt(fort, w, floor + 1 + (d >= UnderwayTemple.DEPTH - 3 ? 1 : 0), d);
        world.getServer().runCommand("execute in voidheart:daedalus run tp @a " + aimedPose(from.getX() + 0.5, from.getY() + 0.2, from.getZ() + 0.5, to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5));
        context.waitTicks(3);
    }

    /** @return the {@code w} of the temple where a column of the picture is, whichever way its fort was turned */
    private static int columnAt(UnderwayFort fort, int column)
    {
        for (int w = UnderwayTemple.FIRST_COLUMN; w < UnderwayTemple.FIRST_COLUMN + UnderwayTemple.COLUMNS; w++)
        {
            if (fort.temple().imageColumn(fort.frame(), w) == column)
                return w;
        }
        throw new AssertionError("No column " + column + " in the temple's mosaic");
    }

    /**
     * With the world frozen at full reach, so nothing moves but the camera, steps the camera a little at a time close to where the flesh, a tendril and the
     * wheels meet, for any flicker between the layers to show across the frames.
     */
    private static void sweep(ClientGameTestContext context, TestSingleplayerContext world, UnderwayFort fort)
    {
        var server = world.getServer();
        var temple = fort.temple();
        int floor = temple.room().floorY();
        var from = blockAt(fort, columnAt(fort, ANGEL_COLUMN + 2), floor + 2, UnderwayTemple.DEPTH - 3);
        var to = blockAt(fort, columnAt(fort, ANGEL_COLUMN + 1), temple.rowY(2), UnderwayTemple.DEPTH);
        // Steps across the wall and toward it, in the temple's own directions whichever way the fort was turned
        var across = blockAt(fort, columnAt(fort, ANGEL_COLUMN + 3), floor + 2, UnderwayTemple.DEPTH - 3).subtract(from);
        var inward = blockAt(fort, columnAt(fort, ANGEL_COLUMN + 2), floor + 2, UnderwayTemple.DEPTH - 2).subtract(from);
        server.runCommand("tick freeze");
        try
        {
            context.waitTicks(2);
            for (int step = 0; step < SWEEP_STEPS; step++)
            {
                double x = from.getX() + 0.5 + across.getX() * step * SWEEP_ACROSS + inward.getX() * step * SWEEP_INWARD;
                double z = from.getZ() + 0.5 + across.getZ() * step * SWEEP_ACROSS + inward.getZ() * step * SWEEP_INWARD;
                server.runCommand("execute in voidheart:daedalus run tp @a " + aimedPose(x, from.getY() + 0.2, z, to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5));
                context.waitTicks(2);
                shot(context, "sweep-" + step);
            }
        } finally
        {
            server.runCommand("tick unfreeze");
        }
    }

    /** What the player's look meets, and whether a mosaic is found there, to explain a stare that did not count. */
    private static String seen(Minecraft minecraft)
    {
        var player = minecraft.player;
        var eye = player.getEyePosition();
        var hit = minecraft.level.clip(new ClipContext(eye, eye.add(player.getViewVector(1).scale(16)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK)
            return "nothing within 16 blocks of " + eye + " looking " + player.getViewVector(1) + " at " + player.getYRot() + ", " + player.getXRot();
        var state = minecraft.level.getBlockState(block.getBlockPos());
        return state + " at " + block.getBlockPos() + " on its " + block.getDirection() + " side from " + eye + ", wall " + MosaicWall.find(minecraft.level, block.getBlockPos(), block.getDirection())
                + ", paused " + minecraft.isPaused() + ", stare " + MosaicStare.wake().stareTicks() + ", art " + MosaicStare.art();
    }

    private static void waitForStare(ClientGameTestContext context, int ticks)
    {
        for (int tick = 0; tick < ticks + 20 && context.computeOnClient(minecraft -> MosaicStare.wake().stareTicks()) < ticks; tick++)
            context.waitTick();
        int stare = context.computeOnClient(minecraft -> MosaicStare.wake().stareTicks());
        expect(stare >= ticks, "The stare reached only " + stare + " of " + ticks + " ticks");
    }

    private static void shot(ClientGameTestContext context, String name)
    {
        context.takeScreenshot(TestScreenshotOptions.of("mosaic-wake-" + name).withSize(1280, 720));
    }

    private static BlockPos blockAt(UnderwayFort fort, int w, int y, int d)
    {
        var temple = fort.temple();
        int along = temple.along(w, d);
        int across = temple.across(w, d);
        return new BlockPos(fort.frame().x(along, across), y, fort.frame().z(along, across));
    }
}
