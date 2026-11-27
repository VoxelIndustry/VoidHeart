package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.shroud.ShroudAltarTile;
import net.voxelindustry.voidheart.common.content.shroud.ShroudVeil;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPlans;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.ShroudGate;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * A shroud gate in the Daedalus: found standing whole, its altar's outline, a Wraith Ink offered by a real click lifting the seal into smoke, the gate
 * open with motes in its void, held still and seen from either side, still open after the world is saved and reopened, then the ink taken back by
 * hand and the seal settling.
 */
public class ShroudGateClientTest implements FabricClientGameTest
{
    private static final int CHUNK_TIMEOUT_TICKS = 1200;
    private static final int MOVE_TIMEOUT_TICKS = 200;
    private static final int ACTION_TIMEOUT_TICKS = 40;
    /** How far back from the wall the wide shots stand. */
    private static final int WIDE_DEPTH = ShroudGate.ALTAR_DEPTH + 3;
    private static final int SWEEP_STEPS = 6;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        var cameraBefore = context.computeOnClient(minecraft -> minecraft.options.getCameraType());
        TestWorldSave save;
        ShroudGate gate;
        boolean hudWasHidden;
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            hudWasHidden = context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden());
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);
            try
            {
                server.runCommand("difficulty peaceful");
                server.runCommand("gamemode creative @a");
                gate = visit(context, world);
                checkBuilt(server, gate);
                offerAndWatch(context, server, gate);
            } catch (RuntimeException | Error e)
            {
                restore(context, cameraBefore, hudWasHidden);
                throw e;
            }
        }

        // Saved with the ink on the altar, the gate comes back open without lifting again
        try (var world = save.open())
        {
            var server = world.getServer();
            try
            {
                world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
                checkAfterReload(context, server, gate);
                takeBackAndWatch(context, server, gate);
            } finally
            {
                server.runCommand("tick unfreeze");
                server.runCommand("gamemode creative @a");
                restore(context, cameraBefore, hudWasHidden);
            }
        }
    }

    private static void restore(ClientGameTestContext context, CameraType camera, boolean hudWasHidden)
    {
        context.runOnClient(minecraft ->
        {
            minecraft.options.setCameraType(camera);
            if (minecraft.gui.hud.isHidden() != hudWasHidden)
                minecraft.gui.hud.toggle();
        });
    }

    private static void checkAfterReload(ClientGameTestContext context, TestServerContext server, ShroudGate gate)
    {
        expect(server.computeOnServer(minecraft -> altar(minecraft, gate).holdsInk()), "The ink was lost with the reload");
        expect(server.computeOnServer(minecraft -> altar(minecraft, gate).progress(VoidDaedalus.level(minecraft).getGameTime()) >= 1), "The gate was not open after the reload");
        context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getBlockEntity(altarPos(gate)) instanceof ShroudAltarTile, MOVE_TIMEOUT_TICKS);
        expectClient(context, "The client saw the gate lift again after the reload", minecraft ->
        {
            var altar = (ShroudAltarTile) minecraft.level.getBlockEntity(altarPos(gate));
            return altar.holdsInk() && altar.progress(minecraft.level.getGameTime()) >= 1;
        });
        server.runCommand("gamemode creative @a");
        wide(context, server, gate, 0);
        shot(context, "open-after-reload");
    }

    private static ShroudGate visit(ClientGameTestContext context, TestSingleplayerContext world)
    {
        var server = world.getServer();
        var before = server.computeOnServer(minecraft -> player(minecraft).blockPosition());
        server.runCommand("execute as @a run voidheart daedalus shroud");
        for (int tick = 0; tick < MOVE_TIMEOUT_TICKS && server.computeOnServer(minecraft -> player(minecraft).blockPosition()).equals(before); tick++)
            context.waitTick();
        var gate = server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.level().dimension() == VoidDaedalus.WORLD_KEY, "The command did not lead into the Daedalus");
            return DistrictPlans.get(VoidDaedalus.level(minecraft).getSeed(), DistrictPos.at(player.getBlockX(), player.getBlockZ())).shroudGate();
        });
        expect(gate != null, "The command led to a district without a shroud gate");
        context.waitFor(minecraft -> minecraft.level != null && minecraft.level.dimension() == VoidDaedalus.WORLD_KEY && minecraft.gui.screen() == null, MOVE_TIMEOUT_TICKS);
        world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
        return gate;
    }

    private static void checkBuilt(TestServerContext server, ShroudGate gate)
    {
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            gate.forEach((x, y, z, part) ->
            {
                var state = level.getBlockState(new BlockPos(x, y, z));
                var expected = switch (part)
                {
                    case SEAL -> VoidHeartBlocks.SHROUD_SEAL;
                    case ALTAR -> VoidHeartBlocks.SHROUD_ALTAR;
                    default -> VoidHeartBlocks.SHROUD_FRAME;
                };
                expect(state.is(expected), "Expected " + part + " at " + x + " " + y + " " + z + " but found " + state);
            });
            expect(level.getBlockEntity(altarPos(gate)) instanceof ShroudAltarTile, "The altar has no block entity");
            expect(level.getBlockState(altarPos(gate)).getDestroySpeed(level, altarPos(gate)) < 0, "The altar can be broken");
        });
    }

    private static void offerAndWatch(ClientGameTestContext context, TestServerContext server, ShroudGate gate)
    {
        wide(context, server, gate, 0);
        shot(context, "closed");
        closeOnAltar(context, server, gate);
        shot(context, "altar-outline");

        // A bare hand, or anything but ink, is told what the altar wants and opens nothing
        server.runCommand("gamemode survival @a");
        server.runCommand("clear @a");
        standAtAltar(context, server, gate);
        expectHint(context, server, gate, "a bare hand");
        server.runCommand("item replace entity @a weapon.mainhand with minecraft:cobblestone");
        expectHint(context, server, gate, "cobblestone");
        expect(server.computeOnServer(minecraft -> player(minecraft).getMainHandItem().is(Items.COBBLESTONE)), "The cobblestone was placed against the altar");

        // Offered by hand, as a survival player would, so the ink leaves the hand
        server.runCommand("item replace entity @a weapon.mainhand with voidheart:wraith_ink");
        rightClick(context);
        waitFor(context, server, ACTION_TIMEOUT_TICKS, "Offering the ink opened nothing", minecraft -> altar(minecraft, gate).holdsInk());
        expect(server.computeOnServer(minecraft -> player(minecraft).getMainHandItem().isEmpty()), "The ink stayed in hand");
        closeOnAltar(context, server, gate);
        shot(context, "altar-ink");

        server.runCommand("gamemode creative @a");
        wide(context, server, gate, 0);
        shot(context, "lifting-early");
        waitUntilProgress(context, server, gate, 0.45F);
        shot(context, "lifting-middle");
        // Held mid-lift, where the void, the seal and the glowing edge meet, and swept a little for flicker
        server.runCommand("tick freeze");
        for (int step = 0; step < SWEEP_STEPS; step++)
        {
            look(context, server, gate, -1 + step * 0.4, 3.5 - step * 0.1, 0.5, ShroudGate.SEAL_BOTTOM + 2.5);
            shot(context, "lifting-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
        wide(context, server, gate, 0);
        waitUntilProgress(context, server, gate, 0.8F);
        shot(context, "lifting-late");
        waitUntilProgress(context, server, gate, 1);
        context.waitTicks(40);
        shot(context, "open");

        // Held still to look for flicker from aside and close
        server.runCommand("tick freeze");
        wide(context, server, gate, -3);
        shot(context, "open-left");
        wide(context, server, gate, 3);
        shot(context, "open-right");
        look(context, server, gate, 0, 2.5, 1, ShroudGate.SEAL_BOTTOM + 2);
        shot(context, "open-close");
        server.runCommand("tick unfreeze");
    }

    private static void expectHint(ClientGameTestContext context, TestServerContext server, ShroudGate gate, String held)
    {
        context.runOnClient(minecraft -> minecraft.gui.hud.setOverlayMessage(Component.empty(), false));
        rightClick(context);
        expect(overlay(context).equals("It requires Wraith Ink"), "Clicking the bare altar with " + held + " should hint at the ink, the action bar shows: " + overlay(context));
        expect(!server.computeOnServer(minecraft -> altar(minecraft, gate).holdsInk()), "Clicking with " + held + " opened the gate");
    }

    private static void takeBackAndWatch(ClientGameTestContext context, TestServerContext server, ShroudGate gate)
    {
        server.runCommand("gamemode survival @a");
        server.runCommand("clear @a");
        standAtAltar(context, server, gate);
        rightClick(context);
        waitFor(context, server, ACTION_TIMEOUT_TICKS, "Taking the ink back left it on the altar", minecraft -> !altar(minecraft, gate).holdsInk());
        expect(server.computeOnServer(minecraft -> player(minecraft).getInventory().countItem(VoidHeartItems.WRAITH_INK) == 1), "The ink did not come back to the hand");

        server.runCommand("gamemode creative @a");
        wide(context, server, gate, 0);
        shot(context, "settling-early");
        waitUntilProgressBelow(context, server, gate, 0.5F);
        shot(context, "settling-middle");
        waitUntilProgressBelow(context, server, gate, 0.0001F);
        context.waitTicks(20);
        shot(context, "sealed");
    }

    private static void waitUntilProgress(ClientGameTestContext context, TestServerContext server, ShroudGate gate, float wanted)
    {
        waitFor(context, server, ShroudVeil.LIFT_TICKS * 2, "The seal never lifted to " + wanted,
                minecraft -> altar(minecraft, gate).progress(VoidDaedalus.level(minecraft).getGameTime()) >= wanted);
    }

    private static void waitUntilProgressBelow(ClientGameTestContext context, TestServerContext server, ShroudGate gate, float wanted)
    {
        waitFor(context, server, ShroudVeil.SETTLE_TICKS * 2, "The seal never settled to " + wanted,
                minecraft -> altar(minecraft, gate).progress(VoidDaedalus.level(minecraft).getGameTime()) < wanted);
    }

    private static ShroudAltarTile altar(MinecraftServer minecraft, ShroudGate gate)
    {
        return (ShroudAltarTile) VoidDaedalus.level(minecraft).getBlockEntity(altarPos(gate));
    }

    private static BlockPos altarPos(ShroudGate gate)
    {
        return new BlockPos(gate.altarX(), gate.altarY(), gate.altarZ());
    }

    /** Standing behind the altar, looking down at its top, close enough to reach it. */
    private static void standAtAltar(ClientGameTestContext context, TestServerContext server, ShroudGate gate)
    {
        var feet = point(gate, 0, ShroudGate.ALTAR_DEPTH + 1.6, 0);
        var top = point(gate, 0, ShroudGate.ALTAR_DEPTH, 1);
        server.runCommand("execute in voidheart:daedalus run tp @a " + aimedPose(feet.x, feet.y, feet.z, top.x, top.y, top.z));
        context.waitTicks(6);
    }

    private static void closeOnAltar(ClientGameTestContext context, TestServerContext server, ShroudGate gate)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        var eye = point(gate, 0, ShroudGate.ALTAR_DEPTH + 1.2, 1.2);
        var top = point(gate, 0, ShroudGate.ALTAR_DEPTH, 1);
        server.runCommand("execute in voidheart:daedalus run tp @a " + aimedPose(eye.x, eye.y, eye.z, top.x, top.y, top.z));
        context.waitTicks(6);
    }

    /** Looking at the middle of the seal from the room, a few blocks across from the middle. */
    private static void wide(ClientGameTestContext context, TestServerContext server, ShroudGate gate, double across)
    {
        look(context, server, gate, across, WIDE_DEPTH, 0, ShroudGate.SEAL_BOTTOM + ShroudGate.SEAL_HEIGHT / 2.0 + 0.5);
    }

    private static void look(ClientGameTestContext context, TestServerContext server, ShroudGate gate, double across, double depth, double feetUp, double lookUp)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        var feet = point(gate, across, depth, feetUp);
        var at = point(gate, 0, 0.5, lookUp);
        server.runCommand("execute in voidheart:daedalus run tp @a " + aimedPose(feet.x, feet.y, feet.z, at.x, at.y, at.z));
        context.waitTicks(6);
    }

    /**
     * @param depth blocks out from the middle of the wall block behind the threshold, so 0.5 is the seal's face and the altar's middle is at its depth
     */
    private static Vec3 point(ShroudGate gate, double across, double depth, double up)
    {
        int facing = gate.facing();
        int lateral = gate.lateral();
        double x = gate.x() + 0.5 + dx(facing) * depth + dx(lateral) * across;
        double z = gate.z() + 0.5 + dz(facing) * depth + dz(lateral) * across;
        return new Vec3(x, gate.y() + up, z);
    }

    private static int dx(int compass)
    {
        return compass == 1 ? 1 : compass == 3 ? -1 : 0;
    }

    private static int dz(int compass)
    {
        return compass == 2 ? 1 : compass == 0 ? -1 : 0;
    }

    private static void shot(ClientGameTestContext context, String name)
    {
        context.takeScreenshot(TestScreenshotOptions.of("shroud-" + name).withSize(1280, 720));
    }
}
