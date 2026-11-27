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
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.mausoleum.Dullahan;
import net.voxelindustry.voidheart.common.content.mausoleum.DullahanCrown;
import net.voxelindustry.voidheart.common.content.mausoleum.FuneraryFire;
import net.voxelindustry.voidheart.common.content.mausoleum.MaleficentWisp;
import net.voxelindustry.voidheart.common.content.mausoleum.MausoleumBalance;
import net.voxelindustry.voidheart.common.content.mausoleum.Mausoleums;
import net.voxelindustry.voidheart.common.content.mausoleum.SarcophagusTile;
import net.voxelindustry.voidheart.common.content.mob.Wraith;
import net.voxelindustry.voidheart.common.content.shroud.ShroudAltarBlock;
import net.voxelindustry.voidheart.common.content.shroud.ShroudAltarTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPlans;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.ShroudGate;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;
import net.voxelindustry.voidheart.common.world.mausoleum.MausoleumAddress;
import net.voxelindustry.voidheart.common.world.mausoleum.MausoleumPlacer;
import net.voxelindustry.voidheart.common.world.mausoleum.MausoleumPlan;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Walks through a shroud gate into its mausoleum, looks round the antechamber, the corridor of orbs and the hall, then fights the Dullahan: its sweep,
 * the first flame leaving as a wisp with the rider stunned and out of reach, wraiths crossing in from the burial chambers, a wisp setting the player
 * alight with green fire, the rest of the flames and their waves, and its end, cracking the sarcophagus open on a blessed contraption. The fight is saved
 * and reopened halfway, and the way out through the antechamber's gate leads back to the Daedalus.
 */
public class MausoleumClientTest implements FabricClientGameTest
{
    private static final int CHUNK_TIMEOUT_TICKS = 1200;
    private static final int MOVE_TIMEOUT_TICKS = 200;
    private static final int FIGHT_TIMEOUT_TICKS = 400;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        var cameraBefore = context.computeOnClient(minecraft -> minecraft.options.getCameraType());
        TestWorldSave save;
        ShroudGate gate;
        int index;
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
                server.runCommand("difficulty normal");
                server.runCommand("gamemode creative @a");
                server.runCommand("gamerule advance_time false");
                gate = visitGate(context, world);
                index = walkIn(context, world, gate);
                checkBuilt(context, server, index);
                tour(context, server, index);
                fightFirstFlame(context, server, index);
                secondFlame(context, server, index);
            } catch (RuntimeException | Error e)
            {
                restore(context, cameraBefore, hudWasHidden);
                throw e;
            }
        }

        // Saved with a wisp burning, the rider comes back with its crown as it was and still out of reach
        try (var world = save.open())
        {
            var server = world.getServer();
            try
            {
                world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
                checkAfterReload(context, server, index);
                lastFlames(context, server, index);
                slay(context, server, index);
                leave(context, world, gate, index);
            } finally
            {
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

    private static ShroudGate visitGate(ClientGameTestContext context, TestSingleplayerContext world)
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

    /** Opens the gate with an ink, then presses into its void, which takes the player to the mausoleum's antechamber. */
    private static int walkIn(ClientGameTestContext context, TestSingleplayerContext world, ShroudGate gate)
    {
        var server = world.getServer();
        server.runOnServer(minecraft -> ((ShroudAltarTile) VoidDaedalus.level(minecraft).getBlockEntity(new BlockPos(gate.altarX(), gate.altarY(), gate.altarZ())))
                .offer(new ItemStack(VoidHeartItems.WRAITH_INK)));
        waitFor(context, server, MOVE_TIMEOUT_TICKS, "The gate never opened", minecraft ->
        {
            var altar = (ShroudAltarTile) VoidDaedalus.level(minecraft).getBlockEntity(new BlockPos(gate.altarX(), gate.altarY(), gate.altarZ()));
            return altar.progress(VoidDaedalus.level(minecraft).getGameTime()) >= 1;
        });

        var into = point(gate, 0, 0.75, ShroudGate.SEAL_BOTTOM);
        var seal = point(gate, 0, 0, ShroudGate.SEAL_BOTTOM + 1.6);
        server.runCommand("execute in voidheart:daedalus run tp @a " + aimedPose(into.x, into.y, into.z, seal.x, seal.y, seal.z));
        waitFor(context, server, MOVE_TIMEOUT_TICKS, "Walking into the open gate led nowhere", minecraft ->
        {
            var player = player(minecraft);
            return player.level().dimension() == VoidDungeons.WORLD_KEY && Mausoleums.indexAt(player.blockPosition()) >= 0;
        });
        int index = server.computeOnServer(minecraft -> Mausoleums.indexAt(player(minecraft).blockPosition()));
        context.waitFor(minecraft -> minecraft.level != null && minecraft.level.dimension() == VoidDungeons.WORLD_KEY && minecraft.gui.screen() == null, MOVE_TIMEOUT_TICKS);
        world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);

        var arrival = at(index, MausoleumPlan.ARRIVAL_X + 0.5, MausoleumPlan.FLOOR_Y, MausoleumPlan.ARRIVAL_Z + 0.5);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.position().distanceTo(arrival) < 0.6, "Came out at " + player.position() + " rather than before the antechamber's gate at " + arrival);
            expect(Math.abs(net.minecraft.util.Mth.wrapDegrees(player.getYRot() - MausoleumPlan.ARRIVAL_YAW)) < 1, "Came out facing " + player.getYRot());
        });
        return index;
    }

    private static void checkBuilt(ClientGameTestContext context, TestServerContext server, int index)
    {
        waitFor(context, server, CHUNK_TIMEOUT_TICKS, "The rider never showed up at its post", minecraft -> rider(minecraft, index) != null);
        server.runOnServer(minecraft ->
        {
            var level = VoidDungeons.level(minecraft);
            var rider = rider(minecraft, index);
            var post = at(index, MausoleumPlan.RIDER_X + 0.5, MausoleumPlan.RIDER_Y, MausoleumPlan.RIDER_Z + 0.5);
            expect(rider.position().distanceTo(post) < 1.5, "The rider stands at " + rider.position() + " rather than its post " + post);
            expect(rider.released() == 0 && !rider.isAwake(), "The rider was not asleep with its whole crown");

            var gate = MausoleumPlacer.gate(MausoleumAddress.origin(index));
            var altarPos = new BlockPos(gate.altarX(), gate.altarY(), gate.altarZ());
            var altar = level.getBlockState(altarPos);
            expect(altar.is(VoidHeartBlocks.SHROUD_ALTAR) && altar.getValue(ShroudAltarBlock.KEPT), "The way back has no kept altar");
            gate.forEachSealBlock((x, y, z, column, row) ->
                    expect(level.getBlockState(new BlockPos(x, y, z)).is(VoidHeartBlocks.SHROUD_SEAL), "The way back's seal is missing a block"));

            expect(level.getBlockEntity(Mausoleums.sarcophagus(index)) instanceof SarcophagusTile sarcophagus && !sarcophagus.isOpen(),
                    "The sarcophagus is missing or already open");
            var plan = MausoleumPlacer.plan(level.getSeed(), index);
            long orbs = plan.orbs().stream().filter(orb -> level.getBlockState(Mausoleums.local(index, orb.x(), orb.y(), orb.z())).is(VoidHeartBlocks.FUNERARY_ORB)).count();
            expect(orbs == plan.orbs().size(), "Only " + orbs + " of " + plan.orbs().size() + " orbs hang in place");
        });
    }

    private static void tour(ClientGameTestContext context, TestServerContext server, int index)
    {
        int ax = MausoleumPlan.AXIS;
        int floor = MausoleumPlan.FLOOR_Y;
        look(context, server, index, ax + 0.5, floor, 4.5, ax + 0.5, floor + 3, 16);
        shot(context, "antechamber");
        look(context, server, index, ax + 3.5, floor, 12, ax + 0.5, floor + 4, 1.5);
        shot(context, "way-back");
        look(context, server, index, ax + 0.5, floor, 19, ax + 0.5, floor + 2.5, 50);
        shot(context, "corridor");
        // The first orb on the corridor's left, from just under it, and from below to the side
        var orb = MausoleumPlacer.plan(server.computeOnServer(minecraft -> VoidDungeons.level(minecraft).getSeed()), index).orbs().stream()
                .filter(candidate -> candidate.x() < MausoleumPlan.AXIS && candidate.z() > 20).findFirst().orElseThrow();
        look(context, server, index, orb.x() + 2.5, floor, orb.z() + 2.5, orb.x() + 0.5, orb.y() + 0.5, orb.z() + 0.5);
        shot(context, "orb-close");
        look(context, server, index, orb.x() + 0.5, floor, orb.z() - 3.5, orb.x() + 0.5, orb.y() + 0.5, orb.z() + 0.5);
        shot(context, "orb-under");
        look(context, server, index, ax + 0.5, floor, 58, ax + 0.5, floor + 2, MausoleumPlan.RIDER_Z);
        shot(context, "hall");
        look(context, server, index, ax + 5.5, floor + 1, MausoleumPlan.RIDER_Z - 7, ax + 0.5, floor + 2.2, MausoleumPlan.RIDER_Z);
        shot(context, "rider-asleep");
        // The crown close, from a little above at the front, on the side of the reins, and from the side at its own height
        look(context, server, index, ax - 0.5, floor + 2.2, MausoleumPlan.RIDER_Z - 2.8, ax + 0.5, floor + 2.6, MausoleumPlan.RIDER_Z);
        shot(context, "crown-front");
        look(context, server, index, ax + 4.5, floor + 1.2, MausoleumPlan.RIDER_Z + 0.5, ax + 0.5, floor + 2.5, MausoleumPlan.RIDER_Z + 0.5);
        shot(context, "crown-side");
        look(context, server, index, ax + 3.5, MausoleumPlan.DAIS_Y + 1.5, MausoleumPlan.SARCOPHAGUS_Z - 3, ax + 0.5, MausoleumPlan.DAIS_Y + 0.6, MausoleumPlan.SARCOPHAGUS_Z);
        shot(context, "sarcophagus");
        look(context, server, index, MausoleumPlan.HALL_X0 + 4.5, floor, 67, MausoleumPlan.HALL_X0 - 1, floor + 3, 74);
        shot(context, "false-doors");
    }

    /** Wakes the rider, takes one of its sweeps, and spends the first ninth of its health. */
    private static void fightFirstFlame(ClientGameTestContext context, TestServerContext server, int index)
    {
        survive(server);
        var front = at(index, MausoleumPlan.RIDER_X + 0.5, MausoleumPlan.RIDER_Y, MausoleumPlan.RIDER_Z - 9.5);
        server.runCommand("execute in voidheart:dungeons run tp @a " + front.x + " " + front.y + " " + front.z + " 0 0");
        waitFor(context, server, MOVE_TIMEOUT_TICKS, "The rider never woke to the player in its hall", minecraft -> rider(minecraft, index).isAwake());
        expect(server.computeOnServer(minecraft -> rider(minecraft, index).isBossBarShownTo(player(minecraft))), "No boss bar for the awake rider");

        float before = server.computeOnServer(minecraft -> player(minecraft).getHealth());
        waitFor(context, server, FIGHT_TIMEOUT_TICKS, "The rider never wound up a sweep", minecraft -> rider(minecraft, index).phase() == Dullahan.Phase.WINDUP);
        context.waitTicks(MausoleumBalance.SWEEP_WINDUP - 8);
        aimAtRider(context, server, index);
        shot(context, "sweep-wound");
        waitFor(context, server, MausoleumBalance.SWEEP_WINDUP, "The sweep never fell", minecraft -> rider(minecraft, index).phase() == Dullahan.Phase.RECOVER);
        context.waitTicks(2);
        shot(context, "sweep-swung");
        expect(server.computeOnServer(minecraft -> player(minecraft).getHealth()) < before, "The sweep missed a player standing before the rider");

        // A blow past the first share stops at it: the first flame leaves, the rider is stunned and out of reach, and a wraith comes
        server.runOnServer(minecraft ->
        {
            var rider = rider(minecraft, index);
            var player = player(minecraft);
            rider.hurtServer(VoidDungeons.level(minecraft), VoidDungeons.level(minecraft).damageSources().playerAttack(player), 80);
            float threshold = DullahanCrown.threshold(0) * rider.getMaxHealth();
            expect(Math.abs(rider.getHealth() - threshold) < 0.01, "The blow took it to " + rider.getHealth() + " rather than stopping at " + threshold);
            expect(rider.released() == 1, "The first flame never left");
            expect(rider.phase() == Dullahan.Phase.STUNNED, "The rider was not stunned");
            expect(wisps(minecraft, index).size() == 1, "Expected one wisp, found " + wisps(minecraft, index).size());
            expect(wraiths(minecraft, index).size() == DullahanCrown.wave(1), "Expected " + DullahanCrown.wave(1) + " wraith, found " + wraiths(minecraft, index).size());
            for (var wraith : wraiths(minecraft, index))
            {
                var local = wraith.position().subtract(Vec3.atLowerCornerOf(MausoleumAddress.origin(index)));
                expect(!MausoleumPlan.inHall(local.x, local.z), "A wraith came out in the hall rather than a burial chamber");
            }
            boolean hurt = rider.hurtServer(VoidDungeons.level(minecraft), VoidDungeons.level(minecraft).damageSources().playerAttack(player), 40);
            expect(!hurt && Math.abs(rider.getHealth() - threshold) < 0.01, "The rider was hurt while its wisp burned");
        });
        context.waitTicks(20);
        aimAtRider(context, server, index);
        shot(context, "stunned");

        // The wraith crosses the chamber's wall into the hall, and the wisp rams the player with green fire
        waitFor(context, server, FIGHT_TIMEOUT_TICKS, "No wraith crossed into the hall", minecraft -> wraiths(minecraft, index).stream().anyMatch(wraith ->
        {
            var local = wraith.position().subtract(Vec3.atLowerCornerOf(MausoleumAddress.origin(index)));
            return MausoleumPlan.inHall(local.x, local.z) && !wraith.isCrossing();
        }));
        waitFor(context, server, FIGHT_TIMEOUT_TICKS, "The wisp never set the player alight", minecraft -> FuneraryFire.burnsGreen(player(minecraft)));
        // The fire and its colour reach the client a tick or two after the server sets them
        context.waitFor(minecraft -> minecraft.player != null && FuneraryFire.burnsGreen(minecraft.player), 20);
        shot(context, "burning");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(2);
        shot(context, "burning-third-person");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

        // Putting out the wisp ends the stun
        server.runCommand("kill @e[type=voidheart:maleficent_wisp]");
        waitFor(context, server, 20, "Putting out the wisp did not end the stun", minecraft -> rider(minecraft, index).phase() != Dullahan.Phase.STUNNED);
        expect(server.computeOnServer(minecraft -> !rider(minecraft, index).wispsBurning()), "The rider still counts a wisp burning");
    }

    /** The second flame leaves by command; the world is then saved with its wisp burning. */
    private static void secondFlame(ClientGameTestContext context, TestServerContext server, int index)
    {
        calm(server);
        server.runCommand("execute as @a run voidheart mausoleum flame");
        server.runOnServer(minecraft ->
        {
            expect(rider(minecraft, index).released() == 2, "The second flame never left");
            expect(wraiths(minecraft, index).size() == DullahanCrown.wave(2), "The second wave has " + wraiths(minecraft, index).size() + " wraiths");
        });
        context.waitTicks(10);
        aimAtRider(context, server, index);
        shot(context, "second-wisp");
    }

    private static void checkAfterReload(ClientGameTestContext context, TestServerContext server, int index)
    {
        waitFor(context, server, CHUNK_TIMEOUT_TICKS, "The rider was lost with the reload", minecraft -> rider(minecraft, index) != null && !wisps(minecraft, index).isEmpty());
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            var rider = rider(minecraft, index);
            expect(rider.released() == 2, "The crown came back with " + rider.released() + " flames gone rather than 2");
            expect(rider.wispsBurning(), "The rider forgot its wisp");
            float health = rider.getHealth();
            boolean hurt = rider.hurtServer(VoidDungeons.level(minecraft), VoidDungeons.level(minecraft).damageSources().playerAttack(player(minecraft)), 40);
            expect(!hurt && rider.getHealth() == health, "The rider could be hurt after the reload while its wisp burned");
        });
    }

    /** The other flames leave one by one, each with a larger wave, until the rider can be slain. */
    private static void lastFlames(ClientGameTestContext context, TestServerContext server, int index)
    {
        calm(server);
        for (int released = 3; released <= DullahanCrown.FLAMES; released++)
        {
            int flame = released;
            server.runCommand("execute as @a run voidheart mausoleum flame");
            server.runOnServer(minecraft ->
            {
                expect(rider(minecraft, index).released() == flame, "Flame " + flame + " never left");
                expect(wraiths(minecraft, index).size() == DullahanCrown.wave(flame), "Wave " + flame + " has " + wraiths(minecraft, index).size() + " wraiths, not " + DullahanCrown.wave(flame));
            });
            if (flame == DullahanCrown.FLAMES)
            {
                context.waitTicks(10);
                aimAtRider(context, server, index);
                shot(context, "crown-spent");
            }
            calm(server);
        }
    }

    /** Once every flame is out, it dies; the sarcophagus cracks and bares a blessed contraption, which the player takes by hand. */
    private static void slay(ClientGameTestContext context, TestServerContext server, int index)
    {
        context.waitTicks(25);
        server.runOnServer(minecraft ->
        {
            var rider = rider(minecraft, index);
            expect(!rider.wispsBurning(), "A wisp still burns");
            rider.hurtServer(VoidDungeons.level(minecraft), VoidDungeons.level(minecraft).damageSources().playerAttack(player(minecraft)), 1000);
            expect(rider.isDeadOrDying(), "The rider did not die once its crown was spent");
            expect(Mausoleums.state(minecraft).get(index).slain(), "The mausoleum does not know its rider is slain");
        });

        int ax = MausoleumPlan.AXIS;
        look(context, server, index, ax + 3.5, MausoleumPlan.DAIS_Y + 1.5, MausoleumPlan.SARCOPHAGUS_Z - 3, ax + 0.5, MausoleumPlan.DAIS_Y + 0.6, MausoleumPlan.SARCOPHAGUS_Z);
        waitFor(context, server, Dullahan.DEATH_TICKS + 40, "The sarcophagus never cracked", minecraft ->
                VoidDungeons.level(minecraft).getBlockEntity(Mausoleums.sarcophagus(index)) instanceof SarcophagusTile sarcophagus && sarcophagus.isOpen());
        context.waitTicks(SarcophagusTile.CRACK_TICKS / 2);
        shot(context, "cracking");
        context.waitTicks(SarcophagusTile.CRACK_TICKS / 2 + SarcophagusTile.SPLIT_TICKS / 2);
        shot(context, "splitting");
        context.waitTicks(SarcophagusTile.SPLIT_TICKS + 20);
        shot(context, "opened");
        expect(server.computeOnServer(minecraft -> Mausoleums.state(minecraft).get(index).opened()), "The mausoleum does not know the sarcophagus opened");
        expect(server.computeOnServer(minecraft -> rider(minecraft, index) == null), "The rider's body is still there");

        // Taken by hand, as a player would
        server.runCommand("gamemode survival @a");
        server.runCommand("clear @a");
        var body = at(index, MausoleumPlan.SARCOPHAGUS_X + 0.5, MausoleumPlan.SARCOPHAGUS_Y, MausoleumPlan.SARCOPHAGUS_Z + 0.5);
        var feet = at(index, MausoleumPlan.SARCOPHAGUS_X + 2.2, MausoleumPlan.DAIS_Y, MausoleumPlan.SARCOPHAGUS_Z + 0.5);
        server.runCommand("execute in voidheart:dungeons run tp @a " + aimedPose(feet.x, feet.y, feet.z, body.x, body.y + 0.7, body.z));
        context.waitTicks(6);
        rightClick(context);
        waitFor(context, server, 40, "Clicking the open sarcophagus gave nothing", minecraft ->
                player(minecraft).getInventory().countItem(VoidHeartItems.BLESSED_CONTRAPTION) == 1);
        expect(server.computeOnServer(minecraft -> ((SarcophagusTile) VoidDungeons.level(minecraft).getBlockEntity(Mausoleums.sarcophagus(index))).getContents().isEmpty()),
                "The contraption is still in the sarcophagus");
        server.runCommand("gamemode creative @a");
    }

    /** Walks into the antechamber's gate, which leads back before the gate in the Daedalus. */
    private static void leave(ClientGameTestContext context, TestSingleplayerContext world, ShroudGate gate, int index)
    {
        var server = world.getServer();
        var way = MausoleumPlacer.gate(MausoleumAddress.origin(index));
        var into = point(way, 0, 0.75, ShroudGate.SEAL_BOTTOM);
        var seal = point(way, 0, 0, ShroudGate.SEAL_BOTTOM + 1.6);
        context.waitTicks(70);
        server.runCommand("execute in voidheart:dungeons run tp @a " + aimedPose(into.x, into.y, into.z, seal.x, seal.y, seal.z));
        waitFor(context, server, MOVE_TIMEOUT_TICKS, "The way back led nowhere", minecraft -> player(minecraft).level().dimension() == VoidDaedalus.WORLD_KEY);
        var home = point(gate, 0, 2, 0);
        server.runOnServer(minecraft ->
                expect(player(minecraft).position().distanceTo(home) < 1, "Came back at " + player(minecraft).position() + " rather than before the gate at " + home));
        context.waitFor(minecraft -> minecraft.level != null && minecraft.level.dimension() == VoidDaedalus.WORLD_KEY && minecraft.gui.screen() == null, MOVE_TIMEOUT_TICKS);
    }

    /** Survival with plenty of health, so the fight can land on the player without ending the test. */
    private static void survive(TestServerContext server)
    {
        server.runCommand("gamemode survival @a");
        server.runCommand("effect give @a minecraft:health_boost 900 9 true");
        server.runCommand("effect give @a minecraft:regeneration 900 2 true");
        server.runCommand("effect give @a minecraft:instant_health 1 9 true");
    }

    /** Creative again, with every wisp and wraith put out, so the next flame's wave can be counted alone. */
    private static void calm(TestServerContext server)
    {
        server.runCommand("gamemode creative @a");
        server.runCommand("kill @e[type=voidheart:maleficent_wisp]");
        server.runCommand("kill @e[type=voidheart:wraith]");
    }

    private static Dullahan rider(MinecraftServer minecraft, int index)
    {
        var riders = VoidDungeons.level(minecraft).getEntitiesOfClass(Dullahan.class, bounds(index), Dullahan::isAlive);
        return riders.isEmpty() ? null : riders.getFirst();
    }

    private static List<MaleficentWisp> wisps(MinecraftServer minecraft, int index)
    {
        return VoidDungeons.level(minecraft).getEntitiesOfClass(MaleficentWisp.class, bounds(index), MaleficentWisp::isAlive);
    }

    private static List<Wraith> wraiths(MinecraftServer minecraft, int index)
    {
        return VoidDungeons.level(minecraft).getEntitiesOfClass(Wraith.class, bounds(index), Wraith::isAlive);
    }

    private static AABB bounds(int index)
    {
        var origin = MausoleumAddress.origin(index);
        return new AABB(Vec3.atLowerCornerOf(origin), Vec3.atLowerCornerOf(origin).add(MausoleumPlan.WIDTH, MausoleumPlan.HEIGHT, MausoleumPlan.LENGTH));
    }

    private static Vec3 at(int index, double x, double y, double z)
    {
        return Vec3.atLowerCornerOf(MausoleumAddress.origin(index)).add(x, y, z);
    }

    private static void look(ClientGameTestContext context, TestServerContext server, int index, double x, double y, double z, double tx, double ty, double tz)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        var feet = at(index, x, y, z);
        var target = at(index, tx, ty, tz);
        server.runCommand("execute in voidheart:dungeons run tp @a " + aimedPose(feet.x, feet.y, feet.z, target.x, target.y, target.z));
        context.waitTicks(10);
    }

    /** Turns the player to face the rider from where they stand. */
    private static void aimAtRider(ClientGameTestContext context, TestServerContext server, int index)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var rider = rider(minecraft, index);
            if (rider != null)
                player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, rider.position().add(0, 2, 0));
        });
        context.waitTicks(2);
    }

    /**
     * @param depth blocks out from the middle of the wall block behind the threshold, so 0.5 is the seal's face
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
        context.takeScreenshot(TestScreenshotOptions.of("mausoleum-" + name).withSize(1280, 720));
    }
}
