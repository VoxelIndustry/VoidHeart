package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;
import net.voxelindustry.voidheart.common.content.underway.UnderwayPropBlock;
import net.voxelindustry.voidheart.common.content.underway.UnderwaySecurity;
import net.voxelindustry.voidheart.common.content.underway.security.EmbrasureGun;
import net.voxelindustry.voidheart.common.content.underway.security.Automaton;
import net.voxelindustry.voidheart.common.content.underway.security.Fitter;
import net.voxelindustry.voidheart.common.content.underway.security.MountedUnit;
import net.voxelindustry.voidheart.common.content.underway.security.SecurityMob;
import net.voxelindustry.voidheart.common.content.underway.security.Tripwire;
import net.voxelindustry.voidheart.common.world.underway.UnderwayRoomKind;
import net.minecraft.world.level.block.DoorBlock;
import net.voxelindustry.voidheart.common.content.underway.security.SentinelEye;
import net.voxelindustry.voidheart.common.content.underway.security.TurretCupola;
import net.voxelindustry.voidheart.common.content.underway.security.UnderwayGun;
import net.voxelindustry.voidheart.common.content.underway.security.UnderwayUnitSpawner;
import net.voxelindustry.voidheart.common.energy.storage.RubrumBalance;
import net.voxelindustry.voidheart.common.energy.storage.RubrumCellItem;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.underway.UnderwayCanvas;
import net.voxelindustry.voidheart.common.world.underway.UnderwayFort;
import net.voxelindustry.voidheart.common.world.underway.UnderwayLevels;
import net.voxelindustry.voidheart.common.world.underway.UnderwayMaterial;
import net.voxelindustry.voidheart.common.world.underway.UnderwayNode;
import net.voxelindustry.voidheart.common.world.underway.UnderwayPlans;
import net.voxelindustry.voidheart.common.world.underway.UnderwayProp;
import net.voxelindustry.voidheart.common.world.underway.UnderwayRuin;
import net.voxelindustry.voidheart.common.world.underway.UnderwaySection;
import net.voxelindustry.voidheart.common.world.underway.UnderwayStroke;
import net.voxelindustry.voidheart.common.world.underway.UnderwayUnit;
import net.voxelindustry.voidheart.common.world.underway.UnderwayUnitKind;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The security of a fort's entry level: its units are posted once as the fort generates and sleep, an eye that makes out a visitor sounds the alarm and wakes the emergency circuit, the gun behind a slit fires down its gallery and is only wrecked from behind, a rubrum cell set in the cabinet with a real click wakes the rest and lets a shutter rise, and the level is secured once every unit is a wreck.
 */
public class UnderwaySecurityClientTest implements FabricClientGameTest
{
    private static final int CHUNK_TIMEOUT_TICKS = 1200;
    private static final int FLOOR = UnderwayLevels.ENTRY_FLOOR;
    private static final int FEET = FLOOR + 1;
    private static final int SEARCH = 10;
    private static final float KILL = 100_000;
    /** What the stages of the entry level need at least one of. */
    private static final UnderwayUnitKind[] ENTRY_KINDS = { UnderwayUnitKind.SENTINEL_EYE, UnderwayUnitKind.TURRET_CUPOLA, UnderwayUnitKind.EMBRASURE_GUN, UnderwayUnitKind.AUTOMATON, UnderwayUnitKind.TRIPWIRE };
    /** The Automaton the test stages in the living gallery, which answers to no section. */
    private static java.util.UUID stagedId;

    /** A place in the world, with the step toward what it faces. */
    private record Spot(int x, int y, int z, int dx, int dz)
    {
        Vec3 middle()
        {
            return new Vec3(x + 0.5, y + 0.5, z + 0.5);
        }

        BlockPos block()
        {
            return new BlockPos(x, y, z);
        }
    }

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("difficulty normal");
            server.runCommand("gamemode creative @a");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            boolean hudWasHidden = context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden());
            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);

            var fort = server.computeOnServer(minecraft -> findFort(VoidDaedalus.level(minecraft).getSeed()));
            var canvas = server.computeOnServer(minecraft -> paint(VoidDaedalus.level(minecraft).getSeed(), fort));
            var key = new UnderwaySecurity.Key(fort.pos().x(), fort.pos().z(), UnderwaySection.ENTRY);
            var frame = fort.frame();
            int footX = frame.x(fort.footAlong(), fort.footAcross());
            int footZ = frame.z(fort.footAlong(), fort.footAcross());
            aim(context, server, footX + 0.5, FEET, footZ + 0.5, footX + 4, FEET + 1, footZ + 0.5);
            int x0 = fort.pos().originX();
            int z0 = fort.pos().originZ();
            server.runCommand("execute in voidheart:daedalus run forceload add " + x0 + " " + z0 + " " + (x0 + DistrictPos.SIZE - 1) + " " + (z0 + DistrictPos.SIZE - 1));
            world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
            server.runCommand("effect give @a minecraft:night_vision infinite 0 true");
            waitFor(context, server, CHUNK_TIMEOUT_TICKS, () -> "The fort's units were not all posted: " + server.computeOnServer(minecraft -> missing(minecraft, fort)),
                    minecraft -> missing(minecraft, fort).isEmpty());

            asleep(context, server, fort, canvas, key);
            eye(context, server, fort, canvas, key);
            embrasure(context, server, fort, canvas, key);
            tripwire(context, server, fort, key);
            automaton(context, server, fort, canvas);
            rebuild(context, server, fort, canvas);
            power(context, server, fort, canvas, key);
            secured(context, server, fort, key);

            server.runCommand("execute in voidheart:daedalus run forceload remove all");
            server.runCommand("effect clear @a");
            server.runCommand("gamemode creative @a");
            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);
        }
    }

    /** Nothing is awake in a fort nobody was seen in, and what sleeps cannot be harmed. Without power a shutter stays down. */
    private static void asleep(ClientGameTestContext context, TestServerContext server, UnderwayFort fort, UnderwayCanvas canvas, UnderwaySecurity.Key key)
    {
        var cabinet = cabinet(fort);
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var security = UnderwaySecurity.of(level);
            expect(!security.isLive(level, key) && !security.isAlarmed(key) && !security.hasCell(key), "An untouched section should be dormant and silent");
            expect(!security.isSecured(level, key), "A section with " + fort.unitCount(UnderwaySection.ENTRY) + " units counts as secured before any fell");
            expect(level.getBlockState(cabinet.block()).is(VoidHeartBlocks.underwayProp(UnderwayProp.BREAKER_CABINET)), "No breaker cabinet at " + cabinet.block() + ": " + level.getBlockState(cabinet.block()));
            var blow = level.damageSources().playerAttack(player(minecraft));
            for (var cupola : units(level, fort, TurretCupola.class))
            {
                expect(!cupola.isAwake() && cupola.phase() == UnderwayGun.Phase.FOLDED, "A cupola without power is " + cupola.phase());
                expect(!cupola.hurtServer(level, blow, 20) && cupola.getHealth() == cupola.getMaxHealth(), "A folded cupola was harmed");
            }
            for (var automaton : units(level, fort, Automaton.class))
            {
                expect(!automaton.isAwake() && automaton.isNoAi(), "An Automaton is awake in a silent fort");
                expect(!automaton.hurtServer(level, blow, 20) && automaton.getHealth() == automaton.getMaxHealth(), "A sleeping Automaton was harmed");
            }
            expect(!security.isAlarmed(key), "A blow from a player in creative was reported");
        });

        // A lamp left showing a state its section no longer has, as after a reset its chunk was not there for, turns back when its chunk loads
        var lamp = lamp(fort, canvas);
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var facing = level.getBlockState(lamp).getValue(UnderwayPropBlock.FACING);
            level.setBlock(lamp, VoidHeartBlocks.underwayProp(UnderwayProp.WALL_LAMP_LIT).defaultBlockState().setValue(UnderwayPropBlock.FACING, facing), 3);
            UnderwaySecurity.chunkLoaded(level, net.minecraft.world.level.ChunkPos.containing(lamp));
        });
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            var state = VoidDaedalus.level(minecraft).getBlockState(lamp);
            expect(state.is(VoidHeartBlocks.underwayProp(UnderwayProp.EMERGENCY_WALL_LAMP)), "A stale lamp of a section with nothing saved did not turn back as its chunk loaded: " + state);
        });

        var stand = before(canvas, cabinet, 2);
        aim(context, server, stand.x() + 0.5, FEET, stand.z() + 0.5, cabinet.x() + 0.5, cabinet.y() + 0.5, cabinet.z() + 0.5);
        context.takeScreenshot(TestScreenshotOptions.of("underway-cabinet-dark").withSize(1280, 720));

        var shutter = shutter(fort, canvas);
        aim(context, server, shutter.x() + 0.5 + shutter.dx() * 2, FEET, shutter.z() + 0.5 + shutter.dz() * 2, shutter.x() + 0.5, FEET + 1.2, shutter.z() + 0.5);
        context.takeScreenshot(TestScreenshotOptions.of("underway-shutter-down").withSize(1280, 720));
        rightClick(context);
        server.runOnServer(minecraft -> expect(VoidDaedalus.level(minecraft).getBlockState(shutter.block()).is(VoidHeartBlocks.UNDERWAY_SHUTTER), "A shutter rose without power"));
        String dead = overlay(context);
        expect(dead.contains("no power"), "No word that the shutter has no power, the overlay says: " + dead);
    }

    /** An eye sweeps its cone, stops on a visitor who is not in creative, and sounds the alarm once they have stayed in it, which wakes the emergency circuit only. */
    private static void eye(ClientGameTestContext context, TestServerContext server, UnderwayFort fort, UnderwayCanvas canvas, UnderwaySecurity.Key key)
    {
        var eye = unit(fort, canvas, UnderwayUnitKind.SENTINEL_EYE, 4);
        int sideX = -eye.dz();
        int sideZ = eye.dx();
        // From the side, where the cone shows as a whole
        aim(context, server, eye.x() + 0.5 + eye.dx() * 3 + sideX * 0.9, FEET, eye.z() + 0.5 + eye.dz() * 3 + sideZ * 0.9, eye.x() + 0.5 + eye.dx(), eye.y(), eye.z() + 0.5 + eye.dz());
        context.waitTicks(10);
        context.takeScreenshot(TestScreenshotOptions.of("underway-eye-watching").withSize(1280, 720));
        server.runCommand("tick freeze");
        for (int step = 0; step < 6; step++)
        {
            aim(context, server, eye.x() + 0.5 + eye.dx() * (3 - step * 0.08) + sideX * 0.9, FEET, eye.z() + 0.5 + eye.dz() * (3 - step * 0.08) + sideZ * 0.9,
                    eye.x() + 0.5 + eye.dx(), eye.y(), eye.z() + 0.5 + eye.dz());
            context.takeScreenshot("underway-eye-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
        server.runOnServer(minecraft -> expect(!UnderwaySecurity.of(VoidDaedalus.level(minecraft)).isAlarmed(key), "An eye reported a player in creative"));

        server.runCommand("gamemode survival @a");
        server.runCommand("effect give @a minecraft:resistance infinite 4 true");
        aim(context, server, eye.x() + 0.5 + eye.dx() * 3, FEET, eye.z() + 0.5 + eye.dz() * 3, eye.x() + 0.5, eye.y() + 0.5, eye.z() + 0.5);
        waitFor(context, server, SecurityBalance.EYE_SWEEP_TICKS + 20, "The eye never made out the player standing before it", minecraft -> eyeAt(minecraft, eye).notice() > 0);
        float held = server.computeOnServer(minecraft -> eyeAt(minecraft, eye).sweep(0));
        context.waitTicks(10);
        context.takeScreenshot(TestScreenshotOptions.of("underway-eye-noticing").withSize(1280, 720));
        server.runOnServer(minecraft -> expect(Math.abs(eyeAt(minecraft, eye).sweep(0) - held) < 0.01, "The eye kept sweeping past the player it had made out"));
        // Another eye of the room may report the player first, so the wait is on this one
        waitFor(context, server, SecurityBalance.EYE_NOTICE_TICKS + 20, "The eye never reported the player it held in its cone", minecraft -> eyeAt(minecraft, eye).hasNoticed());
        server.runOnServer(minecraft -> expect(UnderwaySecurity.of(VoidDaedalus.level(minecraft)).isAlarmed(key), "An eye reported a player and no alarm sounds"));
        context.waitTicks(3);
        String alarm = overlay(context);
        expect(alarm.contains("alarm"), "No word of the alarm, the overlay says: " + alarm);
        context.takeScreenshot(TestScreenshotOptions.of("underway-eye-alarm").withSize(1280, 720));
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            expect(eyeAt(minecraft, eye).hasNoticed(), "The alarm sounds but the eye has not noticed anyone");
            for (var gun : units(level, fort, EmbrasureGun.class))
                expect(gun.isAwake(), "An alarm should wake the gun behind a slit");
            for (var cupola : units(level, fort, TurretCupola.class))
                expect(!cupola.isAwake(), "An alarm without power woke a cupola");
            int awake = 0;
            for (var automaton : units(level, fort, Automaton.class))
                awake += automaton.isAwake() ? 1 : 0;
            int posted = (int) fort.units().stream().filter(unit -> unit.kind() == UnderwayUnitKind.AUTOMATON && unit.section() == UnderwaySection.ENTRY).count();
            expect(awake == posted, awake + " of the " + posted + " Automaton of the guard rooms woke on the alarm");
            expect(!UnderwaySecurity.of(level).isLive(level, key), "An alarm gave the section power");
        });
        server.runCommand("gamemode creative @a");
    }

    /** The gun behind a slit aims down its gallery, fires a burst along a line it held still, shrugs off what comes through the armour, and falls to a blow from inside its guard room. */
    private static void embrasure(ClientGameTestContext context, TestServerContext server, UnderwayFort fort, UnderwayCanvas canvas, UnderwaySecurity.Key key)
    {
        var gun = unit(fort, canvas, UnderwayUnitKind.EMBRASURE_GUN, 0);
        int lane = 3;
        while (lane < 12 && !standable(canvas, gun.x() + gun.dx() * lane, FEET, gun.z() + gun.dz() * lane))
            lane++;
        expect(lane < 12, "No floor in the gallery before the slit of the gun at " + gun.block());
        double laneX = gun.x() + 0.5 + gun.dx() * lane;
        double laneZ = gun.z() + 0.5 + gun.dz() * lane;

        server.runCommand("gamemode survival @a");
        aim(context, server, laneX, FEET, laneZ, gun.x() + 0.5 + gun.dx(), gun.y() + 0.5, gun.z() + 0.5 + gun.dz());
        waitFor(context, server, 100, "The gun never aimed at a player standing in its gallery", minecraft -> gunAt(minecraft, gun).phase() == UnderwayGun.Phase.AIMING);
        context.waitTicks(4);
        context.takeScreenshot(TestScreenshotOptions.of("underway-embrasure-aiming").withSize(1280, 720));
        waitFor(context, server, 60, "The gun never fired", minecraft -> gunAt(minecraft, gun).shotsFired() > 0);
        context.takeScreenshot(TestScreenshotOptions.of("underway-embrasure-firing").withSize(1280, 720));
        waitFor(context, server, 60, "The burst stopped short", minecraft -> gunAt(minecraft, gun).shotsFired() >= SecurityBalance.GUN_BURST_SHOTS);

        // Without the test's resistance a burst costs health, through any armour
        exposed(context, server);
        int fired = server.computeOnServer(minecraft -> gunAt(minecraft, gun).shotsFired());
        waitFor(context, server, 200, "No second burst", minecraft -> gunAt(minecraft, gun).shotsFired() >= fired + SecurityBalance.GUN_BURST_SHOTS);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getHealth() < player.getMaxHealth(), "A burst that was not dodged did no harm");
            expect(player.isAlive(), "The player died with eighty health to spare");
            var level = VoidDaedalus.level(minecraft);
            var mounted = gunAt(minecraft, gun);
            expect(!mounted.hurtServer(level, level.damageSources().playerAttack(player), 20) && mounted.getHealth() == mounted.getMaxHealth(), "The gun was harmed through its armour, from the gallery");
        });
        shielded(server);
        server.runCommand("gamemode creative @a");

        var inside = near(canvas, gun.x() - gun.dx() * 2, gun.z() - gun.dz() * 2);
        aim(context, server, inside.x() + 0.5, FEET, inside.z() + 0.5, gun.x() + 0.5, gun.y() + 0.4, gun.z() + 0.5);
        context.waitTicks(10);
        context.takeScreenshot(TestScreenshotOptions.of("underway-embrasure-behind").withSize(1280, 720));
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var mounted = gunAt(minecraft, gun);
            expect(mounted.hurtServer(level, level.damageSources().playerAttack(player(minecraft)), 5) && mounted.getHealth() < mounted.getMaxHealth(), "The gun shrugged off a blow from inside its guard room");
            mounted.hurtServer(level, level.damageSources().playerAttack(player(minecraft)), KILL);
        });
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            expect(level.getEntitiesOfClass(EmbrasureGun.class, new AABB(gun.block()).inflate(1)).isEmpty(), "A destroyed gun is still there");
            expect(UnderwaySecurity.of(level).destroyedCount(key) == 1, "The section counts " + UnderwaySecurity.of(level).destroyedCount(key) + " wrecks after one gun fell");
        });
        context.takeScreenshot(TestScreenshotOptions.of("underway-embrasure-wreck").withSize(1280, 720));
    }

    /** An Automaton asleep at its post in a barracks, then one awake in a gallery: it shows its line, shoots, and comes in with its lance. */
    private static void automaton(ClientGameTestContext context, TestServerContext server, UnderwayFort fort, UnderwayCanvas canvas)
    {
        var frame = fort.frame();
        var sleeper = fort.units().stream().filter(unit -> unit.kind() == UnderwayUnitKind.AUTOMATON && unit.section() == UnderwaySection.LIVING).findFirst().orElseThrow();
        int sx = frame.x(sleeper.along(), sleeper.across());
        int sz = frame.z(sleeper.along(), sleeper.across());
        var beside = near(canvas, sx, sleeper.y(), sz, 2, 3);
        aim(context, server, beside.x() + 0.5, sleeper.y(), beside.z() + 0.5, sx + 0.5, sleeper.y() + 1.3, sz + 0.5);
        context.waitTicks(10);
        context.takeScreenshot(TestScreenshotOptions.of("underway-automaton-asleep").withSize(1280, 720));

        // A long straight of the hall: the living gallery's middle line
        var gallery = fort.rooms().stream().filter(room -> room.kind() == net.voxelindustry.voidheart.common.world.underway.UnderwayRoomKind.GALLERY).findFirst().orElseThrow();
        boolean longAlong = gallery.a1() - gallery.a0() >= gallery.c1() - gallery.c0();
        int feet = gallery.floorY() + 1;
        int[] post = null;
        int[] stand = null;
        // A lane beside the gallery's track, wherever twelve blocks of it are clear
        for (int lane : new int[] { 1, -1, 2, -2, 0 })
        {
            int middle = (longAlong ? gallery.centreAcross() : gallery.centreAlong()) + lane;
            for (int run = (longAlong ? gallery.a0() : gallery.c0()) + 2; run < (longAlong ? gallery.a1() : gallery.c1()) - 14 && stand == null; run++)
            {
                boolean clear = true;
                for (int step = 0; step <= 12 && clear; step++)
                    clear = standable(canvas, frame.x(longAlong ? run + step : middle, longAlong ? middle : run + step), feet, frame.z(longAlong ? run + step : middle, longAlong ? middle : run + step));
                if (clear)
                {
                    post = new int[] { frame.x(longAlong ? run : middle, longAlong ? middle : run), frame.z(longAlong ? run : middle, longAlong ? middle : run) };
                    stand = new int[] { frame.x(longAlong ? run + 12 : middle, longAlong ? middle : run + 12), frame.z(longAlong ? run + 12 : middle, longAlong ? middle : run + 12) };
                }
            }
        }
        expect(stand != null, "No clear straight in the living gallery to stage an Automaton in");
        int[] at = post;
        int[] here = stand;

        // Still in creative, from close by: it wakes cold, builds pressure, and only then opens its eye
        int towardX = Integer.signum(here[0] - at[0]);
        int towardZ = Integer.signum(here[1] - at[1]);
        aim(context, server, at[0] + 0.5 + towardX * 2.6, feet, at[1] + 0.5 + towardZ * 2.6, at[0] + 0.5, feet + 1.2, at[1] + 0.5);
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var automaton = VoidHeartEntities.AUTOMATON.create(level, EntitySpawnReason.COMMAND);
            float yaw = (float) Math.toDegrees(Math.atan2(-towardX, towardZ));
            automaton.snapTo(at[0] + 0.5, feet, at[1] + 0.5, yaw, 0);
            automaton.setYBodyRot(yaw);
            automaton.setYHeadRot(yaw);
            stagedId = automaton.getUUID();
            level.addFreshEntity(automaton);
        });
        context.waitTicks(Automaton.WAKE_TICKS / 2);
        server.runOnServer(minecraft -> expect(staged(minecraft).isAwake() && !staged(minecraft).isUnderPressure(), "An Automaton should stand building pressure before it moves"));
        context.takeScreenshot(TestScreenshotOptions.of("underway-automaton-waking").withSize(1280, 720));
        waitFor(context, server, Automaton.WAKE_TICKS + 10, "The Automaton never came under pressure", minecraft -> staged(minecraft).isUnderPressure());
        context.waitTicks(4);
        context.takeScreenshot(TestScreenshotOptions.of("underway-automaton-awake").withSize(1280, 720));
        aim(context, server, at[0] + 0.5 - towardX * 2.6 + towardZ * 1.2, feet, at[1] + 0.5 - towardZ * 2.6 + towardX * 1.2, at[0] + 0.5, feet + 1.2, at[1] + 0.5);
        context.takeScreenshot(TestScreenshotOptions.of("underway-automaton-back").withSize(1280, 720));

        aim(context, server, here[0] + 0.5, feet, here[1] + 0.5, at[0] + 0.5, feet + 1.3, at[1] + 0.5);
        server.runCommand("gamemode survival @a");
        waitFor(context, server, 100, "An Automaton nobody posted should be awake and aim at a player in sight", minecraft -> staged(minecraft).isAwake() && staged(minecraft).aimTicks() > 3);
        context.takeScreenshot(TestScreenshotOptions.of("underway-automaton-aiming").withSize(1280, 720));
        waitFor(context, server, 60, "The Automaton never fired its lance", minecraft -> staged(minecraft).shotsFired() > 0);
        context.takeScreenshot(TestScreenshotOptions.of("underway-automaton-fired").withSize(1280, 720));

        // Close in, it charges
        exposed(context, server);
        aim(context, server, at[0] + 0.5 + Integer.signum(here[0] - at[0]) * 3, feet, at[1] + 0.5 + Integer.signum(here[1] - at[1]) * 3, at[0] + 0.5, feet + 1.3, at[1] + 0.5);
        server.runOnServer(GameTestKit::heal);
        waitFor(context, server, 400, "The Automaton never reached the player with its lance", minecraft -> player(minecraft).getHealth() < player(minecraft).getMaxHealth() && staged(minecraft).distanceTo(player(minecraft)) < 4);
        context.takeScreenshot(TestScreenshotOptions.of("underway-automaton-close").withSize(1280, 720));
        server.runCommand("gamemode creative @a");
        shielded(server);
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var automaton = staged(minecraft);
            expect(automaton.hurtServer(level, level.damageSources().playerAttack(player(minecraft)), 5), "An awake Automaton shrugged off a blow");
            automaton.hurtServer(level, level.damageSources().genericKill(), KILL);
        });
        context.waitTicks(30);
    }

    /** A beam crosses the grating of a pit at ankle height. Standing short of it does nothing; walking through it drops the grating, and the alarm sounds. */
    private static void tripwire(ClientGameTestContext context, TestServerContext server, UnderwayFort fort, UnderwaySecurity.Key key)
    {
        var unit = pitWire(fort);
        var wire = spot(fort, unit.along(), unit.y(), unit.across(), unit.faceAlong(), unit.faceAcross());
        // The grating under the beam, and the step along the strip of grating, square to the beam
        int gx = wire.x() + wire.dx();
        int gz = wire.z() + wire.dz();
        int px = wire.dz();
        int pz = wire.dx();
        var grating = new BlockPos(gx, FLOOR, gz);
        aim(context, server, gx - px * 3 + 0.5, FEET, gz - pz * 3 + 0.5, gx + 0.5, FEET + 0.2, gz + 0.5);
        context.waitTicks(25);
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var emitter = at(minecraft, Tripwire.class, wire);
            expect(level.getBlockState(grating).is(VoidHeartBlocks.UNDERWAY_GRATING), "No grating under the tripwire at " + grating + ": " + level.getBlockState(grating));
            expect(emitter.isArmed() && emitter.reach() > 2.4 && emitter.reach() < 3.6, "The beam over a pit three blocks wide reaches " + emitter.reach());
        });
        context.takeScreenshot(TestScreenshotOptions.of("underway-tripwire").withSize(1280, 720));

        server.runCommand("gamemode survival @a");
        aim(context, server, gx - px + 0.5, FEET, gz - pz + 0.5, gx + px * 3 + 0.5, FEET + 1, gz + pz * 3 + 0.5);
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            expect(at(minecraft, Tripwire.class, wire).isArmed(), "The beam broke for a player standing a block short of it");
            expect(VoidDaedalus.level(minecraft).getBlockState(grating).is(VoidHeartBlocks.UNDERWAY_GRATING), "The grating dropped before the beam was broken");
        });
        aim(context, server, gx + 0.5, FEET, gz + 0.5, gx + px * 3 + 0.5, FEET + 1, gz + pz * 3 + 0.5);
        waitFor(context, server, 20, "Walking through the beam did not break it", minecraft -> !at(minecraft, Tripwire.class, wire).isArmed());
        context.waitTicks(15);
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            for (int step = -1; step <= 1; step++)
                expect(level.getBlockState(grating.offset(px * step, 0, pz * step)).isAir(), "The grating at " + grating.offset(px * step, 0, pz * step) + " stayed up: " + level.getBlockState(grating.offset(px * step, 0, pz * step)));
            expect(player(minecraft).getY() < FEET - 0.5, "The player did not fall into the pit, they stand at " + player(minecraft).position());
            expect(UnderwaySecurity.of(level).isAlarmed(key), "A broken tripwire did not sound the alarm");
        });
        server.runCommand("gamemode creative @a");
        aim(context, server, gx - px * 3 + 0.5, FEET, gz - pz * 3 + 0.5, gx + 0.5, FLOOR - 1, gz + 0.5);
        context.takeScreenshot(TestScreenshotOptions.of("underway-pit-open").withSize(1280, 720));
    }

    /** On a level with power, a fitter goes to the wreck of the Automaton posted beside it while the alarm sounds, and the Automaton stands again. A searchlight's catch is known to the whole section. */
    private static void rebuild(ClientGameTestContext context, TestServerContext server, UnderwayFort fort, UnderwayCanvas canvas)
    {
        int[] pair = fitterPair(fort);
        var fitterUnit = fort.units().get(pair[0]);
        var wreckUnit = fort.units().get(pair[1]);
        var key = new UnderwaySecurity.Key(fort.pos().x(), fort.pos().z(), fitterUnit.section());
        var post = spot(fort, fitterUnit.along(), fitterUnit.y(), fitterUnit.across(), 0, 0);
        var wreck = spot(fort, wreckUnit.along(), wreckUnit.y(), wreckUnit.across(), 0, 0);
        // From the far side of the wreck, a little above, so that the fitter comes toward the camera
        double awayX = Math.signum(wreck.x() - post.x()) * 3 + (wreck.x() == post.x() ? 2 : 0);
        double awayZ = Math.signum(wreck.z() - post.z()) * 3 + (wreck.z() == post.z() ? 2 : 0);
        server.runCommand("execute in voidheart:daedalus run tp @a " + aimedPose(wreck.x() + 0.5 + awayX, wreckUnit.y() + 1.5, wreck.z() + 0.5 + awayZ, wreck.x() + 0.5, wreckUnit.y() + 0.3, wreck.z() + 0.5));
        server.runOnServer(minecraft ->
        {
            player(minecraft).getAbilities().flying = true;
            player(minecraft).onUpdateAbilities();
        });
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            UnderwaySecurity.of(level).insertCell(level, key, RubrumBalance.CELL_CAPACITY);
        });
        waitFor(context, server, 100, "The fitter never woke on power", minecraft -> fitter(minecraft, post).isUnderPressure());
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var standing = level.getEntitiesOfClass(Automaton.class, new AABB(wreck.block()).inflate(1.5), Entity::isAlive);
            expect(!standing.isEmpty(), "No Automaton near its post at " + wreck.block());
            // The one posted there, not the other Automaton of the room, which may stand a block away
            standing.stream().min(java.util.Comparator.comparingDouble(automaton -> automaton.distanceToSqr(wreck.x() + 0.5, wreckUnit.y(), wreck.z() + 0.5))).orElseThrow()
                    .hurtServer(level, level.damageSources().genericKill(), KILL);
        });
        waitFor(context, server, 60, "The Automaton's wreck was not recorded", minecraft -> UnderwaySecurity.of(VoidDaedalus.level(minecraft)).isDestroyed(key, pair[1]));
        waitFor(context, server, 400, "The fitter never set to work on the wreck beside it", minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            UnderwaySecurity.of(level).raiseAlarm(level, key);
            return fitter(minecraft, post).isWorking();
        });
        context.waitTicks(20);
        context.takeScreenshot(TestScreenshotOptions.of("underway-fitter-working").withSize(1280, 720));
        waitFor(context, server, SecurityBalance.FITTER_REPAIR_TICKS + 60, "The fitter never rebuilt the Automaton", minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            return !UnderwaySecurity.of(level).isDestroyed(key, pair[1]) && !level.getEntitiesOfClass(Automaton.class, new AABB(wreck.block()).inflate(0.5), Entity::isAlive).isEmpty();
        });
        context.waitTicks(Automaton.WAKE_TICKS + 10);
        context.takeScreenshot(TestScreenshotOptions.of("underway-fitter-rebuilt").withSize(1280, 720));

        server.runCommand("gamemode survival @a");
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var security = UnderwaySecurity.of(level);
            security.spot(level, key, player(minecraft));
            expect(security.spotted(level, key) == player(minecraft), "A section does not know whom its searchlight caught");
        });
        server.runCommand("gamemode creative @a");
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var security = UnderwaySecurity.of(level);
            expect(security.spotted(level, key) == null, "A section still hunts a player who went into creative");
            // Back to sleep, so that nothing of this level wanders into the stages that follow
            security.reset(level, key);
        });
        context.waitTicks(5);
    }

    private static Fitter fitter(MinecraftServer minecraft, Spot post)
    {
        var found = VoidDaedalus.level(minecraft).getEntitiesOfClass(Fitter.class, new AABB(post.block()).inflate(8, 2, 8), Entity::isAlive);
        expect(!found.isEmpty(), "No fitter near its post at " + post.block());
        return found.getFirst();
    }

    /** A charged cell set in the cabinet with a real click powers the level: lamps, cupolas and shutters. Taking it back gives the cell less what it fed, and calms nothing. */
    private static void power(ClientGameTestContext context, TestServerContext server, UnderwayFort fort, UnderwayCanvas canvas, UnderwaySecurity.Key key)
    {
        var cabinet = cabinet(fort);
        var lamp = lamp(fort, canvas);
        var stand = before(canvas, cabinet, 2);
        // The alarm the tripwire sounded strobes the lamps until it stops
        waitFor(context, server, SecurityBalance.ALARM_TICKS + 60, "The alarm of the stages before never stopped", minecraft -> !UnderwaySecurity.of(VoidDaedalus.level(minecraft)).isAlarmed(key));
        server.runOnServer(minecraft ->
        {
            var cell = new ItemStack(RubrumContent.RUBRUM_CELL);
            RubrumCellItem.setCharge(cell, RubrumBalance.CELL_CAPACITY);
            player(minecraft).getInventory().setSelectedSlot(0);
            player(minecraft).getInventory().setItem(0, cell);
            expect(VoidDaedalus.level(minecraft).getBlockState(lamp).is(VoidHeartBlocks.underwayProp(UnderwayProp.EMERGENCY_WALL_LAMP)), "No emergency lamp at " + lamp);
        });
        server.runCommand("gamemode survival @a");
        aim(context, server, stand.x() + 0.5, FEET, stand.z() + 0.5, cabinet.x() + 0.5, cabinet.y() + 0.5, cabinet.z() + 0.5);
        rightClick(context);
        String live = overlay(context);
        expect(live.contains("draws power"), "No word that the section is live, the overlay says: " + live);
        context.waitTicks(20);
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var security = UnderwaySecurity.of(level);
            expect(security.isLive(level, key) && security.hasCell(key), "A charged cell clicked into the cabinet did not power the section");
            expect(player(minecraft).getMainHandItem().isEmpty(), "The cell stayed in hand");
            expect(security.charge(key) < RubrumBalance.CELL_CAPACITY && security.charge(key) > RubrumBalance.CELL_CAPACITY - 40 * SecurityBalance.DRAIN_PER_TICK, "The cell holds " + security.charge(key) + " a second into powering the section");
            expect(level.getBlockState(cabinet.block()).is(VoidHeartBlocks.underwayProp(UnderwayProp.BREAKER_CABINET_LIVE)), "The cabinet does not show its cell");
            expect(security.isAlarmed(key) && security.isLockedDown(level, key), "Powering a section with units standing should sound its alarm and lock it down");
            expect(level.getBlockState(lamp).is(VoidHeartBlocks.underwayProp(UnderwayProp.EMERGENCY_WALL_LAMP_ALARM)), "The lamp at " + lamp + " does not strobe while the alarm sounds: " + level.getBlockState(lamp));
            for (var cupola : units(level, fort, TurretCupola.class))
                expect(cupola.isAwake(), "Power should wake a cupola");
            for (var automaton : units(level, fort, Automaton.class))
                expect(automaton.isLive(), "An Automaton of a powered section does not show its rubrum lit");
        });
        context.takeScreenshot(TestScreenshotOptions.of("underway-cabinet-live").withSize(1280, 720));

        // Locked down, an armoured door stays shut to a hand
        var door = door(fort, canvas);
        aim(context, server, door.x() + 0.5 + door.dx() * 2, FEET, door.z() + 0.5 + door.dz() * 2, door.x() + 0.5, FEET + 1.2, door.z() + 0.5);
        String aimed = context.computeOnClient(minecraft -> String.valueOf(minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit ? hit.getBlockPos() + " " + minecraft.level.getBlockState(hit.getBlockPos()) : minecraft.hitResult))
                + server.computeOnServer(minecraft ->
        {
            var text = new StringBuilder();
            for (int back = 0; back <= 3; back++)
                text.append(" | ").append(VoidDaedalus.level(minecraft).getBlockState(door.block().offset(door.dx() * back, 0, door.dz() * back)));
            return text.toString();
        });
        rightClick(context);
        context.waitTicks(2);
        String locked = overlay(context) + " (aimed at " + aimed + ", door at " + door.block() + ")";
        context.takeScreenshot(TestScreenshotOptions.of("underway-lockdown-door").withSize(1280, 720));
        server.runCommand("gamemode creative @a");
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            expect(UnderwaySecurity.of(level).isLockedDown(level, key), "The lockdown ended while a player stood at a door");
            expect(!level.getBlockState(door.block()).getValue(DoorBlock.OPEN), "A hand opened an armoured door during a lockdown");
        });
        expect(locked.contains("locked down"), "No word that the door is locked down, the overlay says: " + locked);
        var underLamp = near(canvas, lamp.getX(), FEET, lamp.getZ(), 1, 3);
        aim(context, server, underLamp.x() + 0.5, FEET, underLamp.z() + 0.5, lamp.getX() + 0.5, lamp.getY() + 0.5, lamp.getZ() + 0.5);
        context.takeScreenshot(TestScreenshotOptions.of("underway-lamp-alarm").withSize(1280, 720));
        server.runCommand("gamemode survival @a");

        var cupola = unit(fort, canvas, UnderwayUnitKind.TURRET_CUPOLA, 0);
        var under = near(canvas, cupola.x(), FEET, cupola.z(), 2, 3);
        aim(context, server, under.x() + 0.5, FEET, under.z() + 0.5, cupola.x() + 0.5, cupola.y() + 0.2, cupola.z() + 0.5);
        waitFor(context, server, 100, "The cupola never dropped to aim at a player under it", minecraft -> cupolaAt(minecraft, cupola).phase() == UnderwayGun.Phase.AIMING);
        context.waitTicks(4);
        context.takeScreenshot(TestScreenshotOptions.of("underway-cupola-aiming").withSize(1280, 720));
        waitFor(context, server, 60, "The cupola never fired", minecraft -> cupolaAt(minecraft, cupola).shotsFired() > 0);
        server.runCommand("gamemode creative @a");
        server.runCommand("tick freeze");
        for (int step = 0; step < 6; step++)
        {
            aim(context, server, under.x() + 0.5 + step * 0.06, FEET, under.z() + 0.5 + step * 0.08, cupola.x() + 0.5, cupola.y() + 0.1, cupola.z() + 0.5);
            context.takeScreenshot("underway-cupola-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var gun = cupolaAt(minecraft, cupola);
            expect(gun.hurtServer(level, level.damageSources().playerAttack(player(minecraft)), 5) && gun.getHealth() < gun.getMaxHealth(), "A cupola that is down shrugged off a blow, in phase " + gun.phase());
        });

        // With nobody in sight the alarm stops: the lamps turn warm and the doors free
        waitFor(context, server, SecurityBalance.ALARM_TICKS + 60, "The alarm never stopped with the player in creative", minecraft -> !UnderwaySecurity.of(VoidDaedalus.level(minecraft)).isAlarmed(key));
        context.waitTicks(3);
        String calm = overlay(context);
        expect(calm.contains("falls silent"), "No word that the alarm stopped, the overlay says: " + calm);
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            expect(level.getBlockState(lamp).is(VoidHeartBlocks.underwayProp(UnderwayProp.WALL_LAMP_LIT)), "The lamp at " + lamp + " did not turn warm once the alarm stopped: " + level.getBlockState(lamp));
            expect(!UnderwaySecurity.of(level).isLockedDown(level, key), "The section stayed locked down after its alarm stopped");
        });
        aim(context, server, underLamp.x() + 0.5, FEET, underLamp.z() + 0.5, lamp.getX() + 0.5, lamp.getY() + 0.5, lamp.getZ() + 0.5);
        context.takeScreenshot(TestScreenshotOptions.of("underway-lamp-live").withSize(1280, 720));
        aim(context, server, door.x() + 0.5 + door.dx() * 2, FEET, door.z() + 0.5 + door.dz() * 2, door.x() + 0.5, FEET + 1.2, door.z() + 0.5);
        server.runCommand("gamemode survival @a");
        rightClick(context);
        server.runCommand("gamemode creative @a");
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            // Something that caught sight of the player in that moment locks the door again, which is no fault of the door
            expect(level.getBlockState(door.block()).getValue(DoorBlock.OPEN) || UnderwaySecurity.of(level).isAlarmed(key), "A hand did not open an armoured door once the lockdown ended");
        });

        var shutter = shutter(fort, canvas);
        aim(context, server, shutter.x() + 0.5 + shutter.dx() * 2, FEET, shutter.z() + 0.5 + shutter.dz() * 2, shutter.x() + 0.5, FEET + 1.2, shutter.z() + 0.5);
        rightClick(context);
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            for (var stroke : fort.strokes())
            {
                if (stroke.material() != UnderwayMaterial.SHUTTER || !covers(fort, stroke, shutter))
                    continue;
                for (int along = stroke.a0(); along <= stroke.a1(); along++)
                {
                    for (int across = stroke.c0(); across <= stroke.c1(); across++)
                    {
                        for (int y = stroke.y0(); y <= stroke.y1(); y++)
                            expect(level.getBlockState(new BlockPos(fort.frame().x(along, across), y, fort.frame().z(along, across))).isAir(), "Part of a shutter stayed down on power");
                    }
                }
            }
        });
        context.takeScreenshot(TestScreenshotOptions.of("underway-shutter-raised").withSize(1280, 720));

        server.runCommand("gamemode survival @a");
        aim(context, server, stand.x() + 0.5, FEET, stand.z() + 0.5, cabinet.x() + 0.5, cabinet.y() + 0.5, cabinet.z() + 0.5);
        rightClick(context);
        server.runCommand("gamemode creative @a");
        String dark = overlay(context);
        expect(dark.contains("falls dark"), "No word that the section fell dark, the overlay says: " + dark);
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var security = UnderwaySecurity.of(level);
            var held = player(minecraft).getMainHandItem();
            expect(held.is(RubrumContent.RUBRUM_CELL) && RubrumCellItem.charge(held) > 0 && RubrumCellItem.charge(held) < RubrumBalance.CELL_CAPACITY, "The cell came back holding " + RubrumCellItem.charge(held));
            expect(!security.isLive(level, key) && !security.hasCell(key), "The section is still live with its cell gone");
            expect(level.getBlockState(cabinet.block()).is(VoidHeartBlocks.underwayProp(UnderwayProp.BREAKER_CABINET)), "The cabinet still shows a cell");
            // Red again: steady, or strobing if something saw the player at the cabinet
            expect(level.getBlockState(lamp).is(VoidHeartBlocks.underwayProp(UnderwayProp.EMERGENCY_WALL_LAMP)) || level.getBlockState(lamp).is(VoidHeartBlocks.underwayProp(UnderwayProp.EMERGENCY_WALL_LAMP_ALARM)),
                    "The lamp at " + lamp + " stayed on: " + level.getBlockState(lamp));
        });
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            for (var gun : units(VoidDaedalus.level(minecraft), fort, TurretCupola.class))
                expect(gun.isAwake(), "Pulling the cell put a cupola back to sleep");
        });
    }

    /** Once every unit of the level is a wreck the level is secured: no alarm sounds there again, and the state survives saving. */
    private static void secured(ClientGameTestContext context, TestServerContext server, UnderwayFort fort, UnderwaySecurity.Key key)
    {
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            expect(!UnderwaySecurity.of(level).isSecured(level, key), "The level counts as secured with units standing");
            for (var unit : units(level, fort, MountedUnit.class))
                unit.hurtServer(level, level.damageSources().genericKill(), KILL);
            for (var machine : units(level, fort, SecurityMob.class))
                machine.hurtServer(level, level.damageSources().genericKill(), KILL);
        });
        waitFor(context, server, 100, () -> "The level is not secured: " + server.computeOnServer(minecraft -> UnderwaySecurity.of(VoidDaedalus.level(minecraft)).destroyedCount(key))
                + " wrecks of " + fort.unitCount(UnderwaySection.ENTRY), minecraft -> UnderwaySecurity.of(VoidDaedalus.level(minecraft)).isSecured(VoidDaedalus.level(minecraft), key));
        context.waitTicks(3);
        String word = overlay(context);
        expect(word.contains("secured"), "No word that the section is secured, the overlay says: " + word);
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var security = UnderwaySecurity.of(level);
            security.raiseAlarm(level, key);
            expect(!security.isAlarmed(key), "An alarm sounded in a secured section");
            var living = new UnderwaySecurity.Key(key.x(), key.z(), UnderwaySection.LIVING);
            expect(!security.isSecured(level, living), "Securing the entry level secured the living level too");

            var saved = UnderwaySecurity.CODEC.encodeStart(JsonOps.INSTANCE, security).getOrThrow();
            var loaded = UnderwaySecurity.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
            expect(loaded.destroyedCount(key) == security.destroyedCount(key) && loaded.isSecured(level, key), "The wrecks did not survive saving");
            expect(loaded.isAwake(level, key, false), "What power woke did not stay awake through saving");
            var lamp = lamp(fort, paint(level.getSeed(), fort));
            expect(level.getBlockState(lamp).is(VoidHeartBlocks.underwayProp(UnderwayProp.WALL_LAMP)), "The lamp of a secured level without power should be dark: " + level.getBlockState(lamp));
        });
        context.takeScreenshot(TestScreenshotOptions.of("underway-section-secured").withSize(1280, 720));
    }

    /** @return the nearest dry station whose entry level has every kind of unit, a cabinet, a lamp and a shutter */
    private static UnderwayFort findFort(long seed)
    {
        for (int radius = 0; radius <= SEARCH; radius++)
        {
            for (int z = -radius; z <= radius; z++)
            {
                for (int x = -radius; x <= radius; x++)
                {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius)
                        continue;
                    var fort = UnderwayPlans.fort(seed, new DistrictPos(x, z));
                    if (fort.node() != UnderwayNode.STATION || fort.ruin() != UnderwayRuin.DRY || fort.cabinet(UnderwaySection.ENTRY) == null)
                        continue;
                    boolean every = pitWire(fort) != null && fitterPair(fort) != null;
                    for (var kind : ENTRY_KINDS)
                        every &= fort.units().stream().anyMatch(unit -> unit.kind() == kind && unit.section() == UnderwaySection.ENTRY);
                    boolean shutter = fort.strokes().stream().anyMatch(stroke -> stroke.material() == UnderwayMaterial.SHUTTER && UnderwaySection.at(stroke.y0()) == UnderwaySection.ENTRY);
                    boolean lamp = fort.props().stream().anyMatch(spot -> spot.prop() == UnderwayProp.EMERGENCY_WALL_LAMP && UnderwaySection.at(spot.y()) == UnderwaySection.ENTRY);
                    boolean barracks = fort.units().stream().anyMatch(unit -> unit.kind() == UnderwayUnitKind.AUTOMATON && unit.section() == UnderwaySection.LIVING);
                    if (every && shutter && lamp && barracks)
                        return fort;
                }
            }
        }
        throw new AssertionError("No dry station with a full entry level within " + SEARCH + " districts");
    }

    /** @return the tripwire that crosses the grating of a pit, or null when the fort has no pit */
    private static UnderwayUnit pitWire(UnderwayFort fort)
    {
        for (var room : fort.rooms())
        {
            if (room.kind() != UnderwayRoomKind.PIT)
                continue;
            for (var unit : fort.units())
            {
                if (unit.kind() == UnderwayUnitKind.TRIPWIRE && unit.along() >= room.a0() && unit.along() <= room.a1() && unit.across() >= room.c0() && unit.across() <= room.c1())
                    return unit;
            }
        }
        return null;
    }

    /** @return a fitter and an Automaton posted in the same room, by their places in the fort's list of units, or null when the fort has none */
    private static int[] fitterPair(UnderwayFort fort)
    {
        var units = fort.units();
        for (int fitter = 0; fitter < units.size(); fitter++)
        {
            if (units.get(fitter).kind() != UnderwayUnitKind.FITTER)
                continue;
            for (int other = 0; other < units.size(); other++)
            {
                var near = units.get(other);
                if (near.kind() == UnderwayUnitKind.AUTOMATON && near.y() == units.get(fitter).y()
                        && Math.abs(near.along() - units.get(fitter).along()) + Math.abs(near.across() - units.get(fitter).across()) <= 5)
                    return new int[] { fitter, other };
            }
        }
        return null;
    }

    /** @return the foot of an armoured door of the entry level, with the step toward a side to stand on */
    private static Spot door(UnderwayFort fort, UnderwayCanvas canvas)
    {
        var frame = fort.frame();
        for (var stroke : fort.strokes())
        {
            if (stroke.material() != UnderwayMaterial.DOOR || stroke.y0() != FEET)
                continue;
            int x = frame.x(stroke.a0(), stroke.c0());
            int z = frame.z(stroke.a0(), stroke.c0());
            for (int[] step : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
            {
                // Along the way through the door, so that the leaf is faced and not seen edge on
                if (standable(canvas, x + step[0], FEET, z + step[1]) && standable(canvas, x + step[0] * 2, FEET, z + step[1] * 2) && canvas.get(x - step[0], FEET, z - step[1]) == UnderwayMaterial.AIR
                        && canvas.get(x + step[1], FEET, z + step[0]) != UnderwayMaterial.AIR && canvas.get(x - step[1], FEET, z - step[0]) != UnderwayMaterial.AIR)
                    return new Spot(x, FEET, z, step[0], step[1]);
            }
        }
        throw new AssertionError("No armoured door on the entry level with room to stand before it");
    }

    private static UnderwayCanvas paint(long seed, UnderwayFort fort)
    {
        var canvas = new UnderwayCanvas(DistrictPos.PITCH, DistrictPos.PITCH);
        canvas.reset(fort.pos().originX(), fort.pos().originZ());
        UnderwayPlans.paint(seed, canvas);
        return canvas;
    }

    private static void aim(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z)
    {
        server.runCommand("execute in voidheart:daedalus run tp @a " + aimedPose(feetX, feetY, feetZ, x, y, z));
        context.waitTicks(5);
    }

    /** Takes the test's resistance away and gives the player health to spare, so that harm shows and does not kill. */
    private static void exposed(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("effect clear @a minecraft:resistance");
        server.runCommand("effect give @a minecraft:health_boost infinite 20 true");
        context.waitTicks(2);
        server.runOnServer(GameTestKit::heal);
    }

    private static void shielded(TestServerContext server)
    {
        server.runCommand("effect clear @a minecraft:health_boost");
        server.runCommand("effect give @a minecraft:resistance infinite 4 true");
    }

    private static boolean standable(UnderwayCanvas canvas, int x, int y, int z)
    {
        return canvas.get(x, y, z) == UnderwayMaterial.AIR && canvas.get(x, y + 1, z) == UnderwayMaterial.AIR && canvas.propAt(x, y, z) == null && canvas.propAt(x, y + 1, z) == null
                && !canvas.get(x, y - 1, z).isOpen();
    }

    /** @return a place to stand on the entry level, as near to a block as there is one */
    private static Spot near(UnderwayCanvas canvas, int x, int z)
    {
        return near(canvas, x, FEET, z, 0, 3);
    }

    private static Spot near(UnderwayCanvas canvas, int x, int y, int z, int from, int to)
    {
        for (int reach = from; reach <= to; reach++)
        {
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == reach && standable(canvas, x + dx, y, z + dz))
                        return new Spot(x + dx, y, z + dz, 0, 0);
                }
            }
        }
        throw new AssertionError("Nowhere to stand within " + to + " blocks of " + x + " " + y + " " + z);
    }

    /** @return a place to stand in front of something hung on a wall */
    private static Spot before(UnderwayCanvas canvas, Spot hung, int steps)
    {
        for (int step = steps; step >= 1; step--)
        {
            if (standable(canvas, hung.x() + hung.dx() * step, FEET, hung.z() + hung.dz() * step))
                return new Spot(hung.x() + hung.dx() * step, FEET, hung.z() + hung.dz() * step, 0, 0);
        }
        throw new AssertionError("Nowhere to stand before " + hung);
    }

    private static Spot cabinet(UnderwayFort fort)
    {
        var cabinet = fort.cabinet(UnderwaySection.ENTRY);
        return spot(fort, cabinet.along(), cabinet.y(), cabinet.across(), cabinet.faceAlong(), cabinet.faceAcross());
    }

    private static Spot spot(UnderwayFort fort, int along, int y, int across, int faceAlong, int faceAcross)
    {
        var frame = fort.frame();
        int x = frame.x(along, across);
        int z = frame.z(along, across);
        return new Spot(x, y, z, frame.x(along + faceAlong, across + faceAcross) - x, frame.z(along + faceAlong, across + faceAcross) - z);
    }

    /** @return a unit of the entry level of that kind with clear floor for some blocks in front of it */
    private static Spot unit(UnderwayFort fort, UnderwayCanvas canvas, UnderwayUnitKind kind, int clear)
    {
        for (var unit : fort.units())
        {
            if (unit.kind() != kind || unit.section() != UnderwaySection.ENTRY)
                continue;
            var spot = spot(fort, unit.along(), unit.y(), unit.across(), unit.faceAlong(), unit.faceAcross());
            boolean open = true;
            for (int step = 1; step <= clear && open; step++)
                open = standable(canvas, spot.x() + spot.dx() * step, FEET, spot.z() + spot.dz() * step);
            if (open)
                return spot;
        }
        throw new AssertionError("No " + kind + " on the entry level with " + clear + " clear blocks before it");
    }

    /** @return the foot of a shutter of the entry level, with the step toward the side to stand on */
    private static Spot shutter(UnderwayFort fort, UnderwayCanvas canvas)
    {
        var frame = fort.frame();
        for (var stroke : fort.strokes())
        {
            if (stroke.material() != UnderwayMaterial.SHUTTER || UnderwaySection.at(stroke.y0()) != UnderwaySection.ENTRY)
                continue;
            int x = frame.x(stroke.a0(), stroke.c0());
            int z = frame.z(stroke.a0(), stroke.c0());
            for (int[] step : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
            {
                if (standable(canvas, x + step[0], FEET, z + step[1]) && standable(canvas, x + step[0] * 2, FEET, z + step[1] * 2))
                    return new Spot(x, FEET, z, step[0], step[1]);
            }
        }
        throw new AssertionError("No shutter on the entry level with room to stand before it");
    }

    private static boolean covers(UnderwayFort fort, UnderwayStroke stroke, Spot shutter)
    {
        return fort.frame().x(stroke.a0(), stroke.c0()) == shutter.x() && fort.frame().z(stroke.a0(), stroke.c0()) == shutter.z();
    }

    /** @return an emergency lamp on a wall of the entry level that nothing was built over */
    private static BlockPos lamp(UnderwayFort fort, UnderwayCanvas canvas)
    {
        for (var spot : fort.props())
        {
            int x = fort.frame().x(spot.along(), spot.across());
            int z = fort.frame().z(spot.along(), spot.across());
            if (spot.prop() == UnderwayProp.EMERGENCY_WALL_LAMP && UnderwaySection.at(spot.y()) == UnderwaySection.ENTRY && canvas.propAt(x, spot.y(), z) == UnderwayProp.EMERGENCY_WALL_LAMP)
                return new BlockPos(x, spot.y(), z);
        }
        throw new AssertionError("No emergency lamp on the entry level");
    }

    /** @return the units of the plan that no entity of the right kind stands for */
    private static List<UnderwayUnit> missing(MinecraftServer minecraft, UnderwayFort fort)
    {
        var level = VoidDaedalus.level(minecraft);
        var frame = fort.frame();
        return fort.units().stream().filter(unit ->
        {
            double x = frame.x(unit.along(), unit.across()) + 0.5;
            double z = frame.z(unit.along(), unit.across()) + 0.5;
            double y = unit.y() + UnderwayUnitSpawner.heightInBlock(unit);
            return level.getEntities(UnderwayUnitSpawner.type(unit.kind()), new AABB(x, y, z, x, y, z).inflate(0.4, 0.3, 0.4), Entity::isAlive).isEmpty();
        }).toList();
    }

    /** @return the living units of a kind posted on the fort's entry level, wherever they have walked to since */
    private static <T extends Entity> List<T> units(ServerLevel level, UnderwayFort fort, Class<T> kind)
    {
        int x = fort.pos().originX();
        int z = fort.pos().originZ();
        return level.getEntitiesOfClass(kind, new AABB(x, UnderwayLevels.MIN_Y, z, x + DistrictPos.SIZE, UnderwayLevels.TOP_Y, z + DistrictPos.SIZE), entity ->
        {
            var link = entity instanceof MountedUnit mounted ? mounted.link() : ((SecurityMob) entity).link();
            return entity.isAlive() && link.isPosted() && link.key().section() == UnderwaySection.ENTRY;
        });
    }

    private static SentinelEye eyeAt(MinecraftServer minecraft, Spot spot)
    {
        return at(minecraft, SentinelEye.class, spot);
    }

    private static EmbrasureGun gunAt(MinecraftServer minecraft, Spot spot)
    {
        return at(minecraft, EmbrasureGun.class, spot);
    }

    private static TurretCupola cupolaAt(MinecraftServer minecraft, Spot spot)
    {
        return at(minecraft, TurretCupola.class, spot);
    }

    private static <T extends Entity> T at(MinecraftServer minecraft, Class<T> kind, Spot spot)
    {
        var found = VoidDaedalus.level(minecraft).getEntitiesOfClass(kind, new AABB(spot.block()).inflate(0.2, 0.8, 0.2), Entity::isAlive);
        expect(!found.isEmpty(), "No " + kind.getSimpleName() + " at " + spot.block());
        return found.getFirst();
    }

    private static Automaton staged(MinecraftServer minecraft)
    {
        if (VoidDaedalus.level(minecraft).getEntity(stagedId) instanceof Automaton automaton)
            return automaton;
        throw new AssertionError("The staged Automaton is gone");
    }
}
