package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.curse.CurseDirector;
import net.voxelindustry.voidheart.client.curse.VigilCurse;
import net.voxelindustry.voidheart.client.model.VigilFigureModel;
import net.voxelindustry.voidheart.common.curse.CurseTimings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Seven figures stand in an arc before the player, none of them in a wall, dripping, point at the player, say their line once and vanish, caves sounding all the while and all without the server knowing.
 */
public class VigilClientTest implements FabricClientGameTest
{
    private static final int    GROUND  = -61;
    private static final String MESSAGE = "Pilgrims: We were you. You will be us. Bound in the void.";

    private static final int STILL_AGE    = 40;
    private static final int POINTED_AGE  = 95;
    private static final int GLOWING_AGE  = 150;
    private static final int VANISH_AGE   = 200;
    private static final int VANISHED_AGE = 205;

    private static final float FINGER_LEAN_MIN = 8;
    private static final float FINGER_LEAN_MAX = 35;

    private static final int    LARGE_HEIGHT        = 720;
    private static final int    FINGER_PIXELS_LONG  = 9;
    private static final int    FINGER_PIXELS_THICK = 4;
    private static final Logger LOGGER              = LoggerFactory.getLogger(VigilClientTest.class);

    private static final int    ARC_STEP            = 14;
    // An arm reaching out to the side of the last figure, as seen from the player
    private static final int    ARM_DEGREES         = 6;
    private static final double DROP_REACH          = 2.2;
    private static final int    CAVE_SOUNDS_LEAST   = 3;
    private static final int    SOUND_RELEASE_TICKS = 60;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("weather clear");
            server.runCommand("gamemode creative @a");
            standAtOrigin(context, server, 0);
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);

            testPlaysOut(context, server, "day");
            testCloseUps(context, server);

            server.runCommand("time set midnight");
            context.waitTicks(3);
            testPlaysOut(context, server, "night");
            testSweep(context, server);

            server.runCommand("time set noon");
            testChatShows(context, server);
            testTracksPlayer(context, server);
            testWallAhead(context, server);
            testTightCave(context, server);
            testOverDrop(context, server);
            testStop(context, server);

            context.getInput().pressKey(InputConstants.KEY_F1);
            context.waitTicks(2);
        }
    }

    /**
     * The whole timeline from the first person, with what can be checked at each stage.
     */
    private void testPlaysOut(ClientGameTestContext context, TestServerContext server, String label)
    {
        standAtOrigin(context, server, 0);
        context.runOnClient(VigilClientTest::clearChat);
        int entitiesBefore = server.computeOnServer(VigilClientTest::entityCount);
        // A set, as the engine announces a looping sound to its listeners again every 10 ticks
        var heard = Collections.synchronizedSet(new HashSet<SoundInstance>());
        SoundEventListener listener = (SoundInstance sound, WeighedSoundEvents events, float range) -> heard.add(sound);
        context.runOnClient(minecraft -> minecraft.getSoundManager().addListener(listener));

        server.runCommand("voidheart curse trigger vigil @a");
        waitForAge(context, 6);
        expectLayout(context, true);
        expectOnArc(context);
        context.takeScreenshot("vigil-" + label + "-01-runes-first");
        waitForAge(context, 11);
        context.takeScreenshot("vigil-" + label + "-02-forming");
        waitForAge(context, STILL_AGE);
        context.takeScreenshot("vigil-" + label + "-03-still");

        if (server.computeOnServer(VigilClientTest::entityCount) != entitiesBefore)
            throw new AssertionError("Expected the server to know of no new entity");
        if (context.computeOnClient(minecraft -> curse().drawnLastFrame()) != 7)
            throw new AssertionError("Expected the seven figures to be drawn");
        if (context.computeOnClient(minecraft -> curse().shadesLastFrame()) != 7)
            throw new AssertionError("Expected a shade on the floor below each of them");
        expectDripping(context);

        waitForAge(context, 80);
        context.takeScreenshot("vigil-" + label + "-04-raising");
        if (messages(context) != 0)
            throw new AssertionError("Expected no message before the arms are up");

        waitForAge(context, POINTED_AGE);
        context.takeScreenshot("vigil-" + label + "-05-pointing");
        context.takeScreenshot(TestScreenshotOptions.of("vigil-" + label + "-05-large").withSize(1280, 720));
        if (messages(context) != 1)
            throw new AssertionError("Expected the message once the arms are up");
        expectFingersLeanIn(context);
        expectDripping(context);

        waitForAge(context, 113);
        context.takeScreenshot("vigil-" + label + "-06-hoods-lighting");
        waitForAge(context, GLOWING_AGE);
        context.takeScreenshot("vigil-" + label + "-07-hoods-glowing");
        context.takeScreenshot(TestScreenshotOptions.of("vigil-" + label + "-07-large").withSize(1280, 720));
        expectDripping(context);
        if (context.computeOnClient(minecraft -> curse().stainsAlive()) == 0)
            throw new AssertionError("Expected what they drip to have stained the floor by now");
        waitForAge(context, VANISH_AGE - 1);
        context.takeScreenshot("vigil-" + label + "-08-before-vanish");
        waitForAge(context, VANISH_AGE + 2);
        context.takeScreenshot("vigil-" + label + "-09-vanishing");
        waitForAge(context, VANISHED_AGE);
        context.takeScreenshot("vigil-" + label + "-10-after-vanish");
        if (context.computeOnClient(minecraft -> curse().drawnLastFrame() + curse().shadesLastFrame()) != 0)
            throw new AssertionError("Expected nothing drawn once they vanished");
        if (context.computeOnClient(minecraft -> curse().dropPositions().size() + curse().stainsAlive()) != 0)
            throw new AssertionError("Expected no drop nor stain left once they vanished");

        waitForAge(context, VANISH_AGE + 10);
        long smoke = context.computeOnClient(minecraft -> curse().burstAlive());
        if (smoke != 0)
            throw new AssertionError("Expected the view empty 10 ticks after they vanished, " + smoke + " wisps of smoke left");
        context.takeScreenshot("vigil-" + label + "-11-empty");

        context.waitFor(minecraft -> CurseDirector.active() == null, CurseTimings.VIGIL_TICKS);
        context.waitTicks(3);
        context.runOnClient(minecraft -> minecraft.getSoundManager().removeListener(listener));

        if (messages(context) != 1)
            throw new AssertionError("Expected the message exactly once, got " + messages(context));
        if (soundsOf(heard, SoundEvents.BEACON_AMBIENT.location()).size() != 2)
            throw new AssertionError("Expected the appearing drone and the held one, got " + heard);
        if (soundsOf(heard, SoundEvents.BEACON_DEACTIVATE.location()).isEmpty())
            throw new AssertionError("Expected a low sound as they vanish, got " + heard);
        if (soundsOf(heard, SoundEvents.AMBIENT_CAVE.value().location()).size() < CAVE_SOUNDS_LEAST)
            throw new AssertionError("Expected cave sounds all through the scene, got " + heard);
        if (server.computeOnServer(VigilClientTest::entityCount) != entitiesBefore)
            throw new AssertionError("Expected the server to know of no new entity");
        if (context.computeOnClient(minecraft -> minecraft.level.getEntitiesOfClass(Entity.class, new AABB(-40, -80, -40, 40, 0, 40)).size()) != 1)
            throw new AssertionError("Expected no entity on the client but the player");
    }

    /**
     * Frozen with their hoods lit, the camera walks up to the leader and a plain figure, then rises to take in the whole half circle.
     */
    private void testCloseUps(ClientGameTestContext context, TestServerContext server)
    {
        standAtOrigin(context, server, 0);
        server.runCommand("voidheart curse trigger vigil @a");
        waitForAge(context, STILL_AGE);
        server.runCommand("tick freeze");
        context.waitTicks(2);
        server.runCommand("gamemode spectator @a");
        var leader = context.computeOnClient(minecraft -> figure(0));
        var plain = context.computeOnClient(minecraft -> figure(-1));
        closeUps(context, server, leader, "leader-idle", 3.4, 0.5);
        server.runCommand("tick unfreeze");
        standAtOrigin(context, server, 0);
        server.runCommand("gamemode creative @a");
        waitForAge(context, GLOWING_AGE);
        server.runCommand("tick freeze");
        context.waitTicks(2);
        server.runCommand("gamemode spectator @a");

        closeUps(context, server, leader, "leader", 3.4, 0.5);
        closeUps(context, server, plain, "plain", 2.8, 0.25);

        // The player turning their head finds the rest of the half circle
        server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 -65 -6");
        context.waitTicks(3);
        context.takeScreenshot(TestScreenshotOptions.of("vigil-look-left").withSize(1280, 720));
        server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 65 -6");
        context.waitTicks(3);
        context.takeScreenshot(TestScreenshotOptions.of("vigil-look-right").withSize(1280, 720));

        aimFrom(context, server, leader.x, -44, leader.z * 0.05 - 3, leader.x * 0.5, GROUND + 2, leader.z * 0.45);
        context.takeScreenshot(TestScreenshotOptions.of("vigil-half-circle-above").withSize(1280, 720));
        aimFrom(context, server, 0.5, GROUND + 8, -11, 0.5, GROUND + 2, 3);
        context.takeScreenshot(TestScreenshotOptions.of("vigil-half-circle-behind").withSize(1280, 720));

        server.runCommand("gamemode creative @a");
        server.runCommand("tick unfreeze");
        server.runCommand("voidheart curse stop @a");
        context.waitTicks(3);
    }

    private void closeUps(ClientGameTestContext context, TestServerContext server, Spot figure, String name, double distance, double heightRatio)
    {
        double lookAtY = figure.y + figure.scale * 2 * 0.75;
        double feetY = lookAtY - 1.62 + (heightRatio - 0.5);

        // The figure faces the origin. Its front is along that line, its side across it
        double toOriginX = 0.5 - figure.x;
        double toOriginZ = 0.5 - figure.z;
        double length = Math.hypot(toOriginX, toOriginZ);
        toOriginX /= length;
        toOriginZ /= length;

        aimFrom(context, server, figure.x + toOriginX * distance, feetY, figure.z + toOriginZ * distance, figure.x, lookAtY, figure.z);
        context.takeScreenshot(TestScreenshotOptions.of("vigil-close-" + name + "-front").withSize(1280, 720));

        aimFrom(context, server, figure.x + toOriginZ * distance, feetY, figure.z - toOriginX * distance, figure.x, lookAtY, figure.z);
        context.takeScreenshot(TestScreenshotOptions.of("vigil-close-" + name + "-side-a").withSize(1280, 720));

        aimFrom(context, server, figure.x - toOriginZ * distance, feetY, figure.z + toOriginX * distance, figure.x, lookAtY, figure.z);
        context.takeScreenshot(TestScreenshotOptions.of("vigil-close-" + name + "-side-b").withSize(1280, 720));

        aimFrom(context, server, figure.x - toOriginX * distance, feetY, figure.z - toOriginZ * distance, figure.x, lookAtY, figure.z);
        context.takeScreenshot(TestScreenshotOptions.of("vigil-close-" + name + "-back").withSize(1280, 720));
    }

    /**
     * A sweep around the glowing figures, against the open sky at night, and from both sides of a figure.
     */
    private void testSweep(ClientGameTestContext context, TestServerContext server)
    {
        standAtOrigin(context, server, 0);
        server.runCommand("voidheart curse trigger vigil @a");
        waitForAge(context, GLOWING_AGE);
        server.runCommand("tick freeze");
        context.waitTicks(2);
        server.runCommand("gamemode spectator @a");

        var leader = context.computeOnClient(minecraft -> figure(0));
        double centerY = leader.y + leader.scale;
        for (int step = 0; step < 8; step++)
        {
            aimFrom(context, server, 0.5 + step * 0.06, GROUND + 1 + step * 0.03, 1.5 + step * 0.08, leader.x, centerY, leader.z);
            context.takeScreenshot("vigil-sweep-" + step);
        }

        server.runCommand("gamemode creative @a");
        server.runCommand("tick unfreeze");
        server.runCommand("voidheart curse stop @a");
        context.waitTicks(3);
    }

    /**
     * The message as the player sees it, with the HUD on, and read back to the letter from the chat itself.
     */
    private void testChatShows(ClientGameTestContext context, TestServerContext server)
    {
        standAtOrigin(context, server, 0);
        context.runOnClient(VigilClientTest::clearChat);
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runCommand("voidheart curse trigger vigil @a");
        waitForAge(context, POINTED_AGE);
        context.takeScreenshot("vigil-chat-message");
        context.getInput().pressKey(InputConstants.KEY_F1);

        var texts = context.computeOnClient(VigilClientTest::chatTexts);
        if (texts.stream().filter(MESSAGE::equals).count() != 1)
            throw new AssertionError("Expected exactly the message, got " + texts);

        server.runCommand("voidheart curse stop @a");
        context.waitTicks(3);
    }

    /**
     * The player walks off and climbs high: the figures stay on their spots but turn to follow, their hands aiming at the player up there.
     */
    private void testTracksPlayer(ClientGameTestContext context, TestServerContext server)
    {
        standAtOrigin(context, server, 0);
        server.runCommand("voidheart curse trigger vigil @a");
        waitForAge(context, 3);
        var before = context.computeOnClient(minecraft -> curse().figures().stream().map(figure -> figure.bounds().getCenter()).toList());

        server.runCommand("gamemode spectator @a");
        double awayX = 7.5;
        double awayY = GROUND + 7;
        double awayZ = -3.5;
        server.runCommand("tp @a " + awayX + " " + awayY + " " + awayZ + " 55 15");
        waitForAge(context, POINTED_AGE + 30);
        context.takeScreenshot(TestScreenshotOptions.of("vigil-player-moved-above").withSize(1280, 720));

        context.runOnClient(minecraft ->
        {
            var figures = curse().figures();
            for (int i = 0; i < figures.size(); i++)
            {
                var figure = figures.get(i);
                if (figure.bounds().getCenter().distanceTo(before.get(i)) > 1.0E-6)
                    throw new AssertionError("Expected figure " + i + " to stay on its spot");

                double toPlayer = Math.toDegrees(Math.atan2(-(awayX - figure.x()), awayZ - figure.z()));
                if (Math.abs(Mth.wrapDegrees(toPlayer - figure.yaw())) > 3)
                    throw new AssertionError("Expected figure " + i + " to have turned to " + toPlayer + ", got " + figure.yaw());
            }
        });

        server.runCommand("gamemode creative @a");
        server.runCommand("voidheart curse stop @a");
        context.waitTicks(3);
    }

    /**
     * A wall right ahead and to one side: figures draw closer rather than stand inside it.
     */
    private void testWallAhead(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("fill -12 " + (GROUND + 1) + " 3 14 " + (GROUND + 7) + " 3 minecraft:stone");
        server.runCommand("fill -2 " + (GROUND + 1) + " -8 -2 " + (GROUND + 7) + " 3 minecraft:stone");
        standAtOrigin(context, server, 0);
        context.waitTicks(3);
        server.runCommand("voidheart curse trigger vigil @a");
        waitForAge(context, 3);
        expectLayout(context, true);
        waitForAge(context, GLOWING_AGE);
        context.takeScreenshot("vigil-wall-first-person");
        server.runCommand("tick freeze");
        context.waitTicks(2);
        server.runCommand("gamemode spectator @a");
        aimFrom(context, server, 6.5, GROUND + 9, -7, -0.5, GROUND + 2, 1);
        context.takeScreenshot("vigil-wall-above");
        server.runCommand("gamemode creative @a");
        server.runCommand("tick unfreeze");
        server.runCommand("voidheart curse stop @a");
        server.runCommand("fill -12 " + (GROUND + 1) + " -8 14 " + (GROUND + 7) + " 3 minecraft:air");
        context.waitTicks(3);
    }

    /**
     * A tunnel barely taller and wider than the player, closed at both ends.
     */
    private void testTightCave(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("fill -5 " + (GROUND + 1) + " -6 6 " + (GROUND + 5) + " 7 minecraft:stone");
        server.runCommand("fill 0 " + (GROUND + 1) + " -5 1 " + (GROUND + 3) + " 6 minecraft:air");
        standAtOrigin(context, server, 0);
        context.waitTicks(3);
        server.runCommand("voidheart curse trigger vigil @a");
        waitForAge(context, 3);
        expectLayout(context, false);
        waitForAge(context, GLOWING_AGE);
        context.takeScreenshot("vigil-cave-first-person");
        server.runCommand("voidheart curse stop @a");
        server.runCommand("fill -5 " + (GROUND + 1) + " -6 6 " + (GROUND + 5) + " 7 minecraft:air");
        context.waitTicks(3);
    }

    /**
     * On a pillar high over the ground, they hang level with the player rather than sink to the distant floor.
     */
    private void testOverDrop(ClientGameTestContext context, TestServerContext server)
    {
        int top = GROUND + 16;
        server.runCommand("fill -1 " + top + " -1 2 " + top + " 2 minecraft:stone");
        server.runCommand("tp @a 0.5 " + (top + 1) + " 0.5 0 -6");
        context.waitTicks(3);
        server.runCommand("voidheart curse trigger vigil @a");
        waitForAge(context, 3);
        expectLayout(context, true);
        context.runOnClient(minecraft ->
        {
            for (var figure : curse().figures())
            {
                double hover = figure.y() - (top + 1);
                if (hover < 1 || hover > 1.3)
                    throw new AssertionError("Expected a figure hanging level with the player over the drop, got it " + hover + " from their feet");
                if (!Double.isNaN(figure.floor()))
                    throw new AssertionError("Expected no floor and so no shade over the drop, got " + figure.floor());
            }
        });
        waitForAge(context, GLOWING_AGE);
        context.takeScreenshot("vigil-over-drop");
        if (context.computeOnClient(minecraft -> curse().shadesLastFrame()) != 0)
            throw new AssertionError("Expected no shade drawn over the drop");
        expectDripping(context);
        if (context.computeOnClient(minecraft -> curse().stainsAlive()) != 0)
            throw new AssertionError("Expected no stain with no floor in reach");
        server.runCommand("voidheart curse stop @a");
        server.runCommand("fill -1 " + top + " -1 2 " + top + " 2 minecraft:air");
        context.waitTicks(3);
    }

    private void testStop(ClientGameTestContext context, TestServerContext server)
    {
        standAtOrigin(context, server, 0);
        context.runOnClient(VigilClientTest::clearChat);
        server.runCommand("voidheart curse trigger vigil @a");
        waitForAge(context, 50);
        var stopped = context.computeOnClient(minecraft -> curse());
        server.runCommand("voidheart curse stop @a");
        context.waitTicks(3);
        if (context.computeOnClient(minecraft -> CurseDirector.active() != null))
            throw new AssertionError("Expected the stopped curse gone at once");
        if (context.computeOnClient(minecraft -> stopped.dropPositions().size() + stopped.stainsAlive()) != 0)
            throw new AssertionError("Expected no drop nor stain left by a stopped curse");
        context.takeScreenshot("vigil-after-stop");
        if (messages(context) != 0)
            throw new AssertionError("Expected no message from a curse stopped before it came");

        // Cut short while its drone holds, nothing may keep playing
        var sounds = Collections.synchronizedSet(new HashSet<SoundInstance>());
        SoundEventListener listener = (SoundInstance sound, WeighedSoundEvents events, float range) -> sounds.add(sound);
        context.runOnClient(minecraft -> minecraft.getSoundManager().addListener(listener));
        server.runCommand("voidheart curse trigger vigil @a");
        waitForAge(context, 120);
        server.runCommand("voidheart curse stop @a");
        context.waitFor(minecraft -> CurseDirector.active() == null);
        var played = context.computeOnClient(minecraft ->
        {
            minecraft.getSoundManager().removeListener(listener);
            var caves = soundsOf(sounds, SoundEvents.AMBIENT_CAVE.value().location());
            if (caves.isEmpty())
                throw new AssertionError("Expected cave sounds before it was stopped, got " + sounds);
            var held = soundsOf(sounds, SoundEvents.BEACON_AMBIENT.location());
            if (held.size() != 2)
                throw new AssertionError("Expected the appearing and the held drone, got " + held);

            var all = new ArrayList<>(caves);
            all.addAll(held);
            // Only a sound flagged stopped is stopped again by the engine if it was still loading the first time
            for (var sound : all)
            {
                if (!(sound instanceof TickableSoundInstance tickable && tickable.isStopped()))
                    throw new AssertionError("Expected " + sound + " flagged stopped along with the curse");
            }
            return all;
        });
        // The engine keeps even a stopped sound for 20 ticks from its start, and a cave can start just before the stop reaches the client
        context.waitFor(minecraft -> played.stream().noneMatch(minecraft.getSoundManager()::isActive), SOUND_RELEASE_TICKS);
    }

    private static List<SoundInstance> soundsOf(Collection<SoundInstance> sounds, Identifier id)
    {
        return sounds.stream().filter(sound -> sound.getIdentifier().equals(id)).toList();
    }

    /**
     * Whatever the surroundings: seven of them with a larger leader in the middle, each its own rune, all in sight of the player and none in a block.
     * Where there's less headroom than a figure, its hood can't but touch the ceiling. Its chest still must not.
     */
    private static void expectLayout(ClientGameTestContext context, boolean fullHeight)
    {
        context.runOnClient(minecraft ->
        {
            var figures = curse().figures();
            if (figures.size() != 7)
                throw new AssertionError("Expected seven figures, got " + figures.size());

            var leaders = figures.stream().filter(VigilCurse.Figure::leader).toList();
            if (leaders.size() != 1 || figures.get(3) != leaders.getFirst())
                throw new AssertionError("Expected one leader at the center of the arc");
            var plain = figures.get(0).scale();
            if (Math.abs(leaders.getFirst().scale() / plain - 1.3F) > 1.0E-4F)
                throw new AssertionError("Expected the leader 1.3 times larger, got " + leaders.getFirst().scale() / plain);

            var eye = minecraft.player.getEyePosition();
            for (var figure : figures)
            {
                if (!figure.leader() && figure.scale() != plain)
                    throw new AssertionError("Expected the six to share one scale");

                var bounds = figure.bounds();
                if (!minecraft.level.noBlockCollision(null, fullHeight ? bounds : bounds.setMaxY(bounds.minY + 1.2)))
                    throw new AssertionError("Expected no figure inside a block, got one at " + bounds);

                var chest = new Vec3(figure.x(), bounds.minY + 1, figure.z());
                var sight = minecraft.level.clip(new ClipContext(eye, chest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
                if (sight.getType() != HitResult.Type.MISS)
                    throw new AssertionError("Expected every figure in sight of the player, got a wall at " + sight.getLocation() + " before " + chest);
            }

            var runes = new HashSet<Integer>();
            figures.forEach(figure -> runes.add(figure.rune()));
            if (runes.size() != 7)
                throw new AssertionError("Expected each figure its own rune, got " + runes);
        });
    }

    /**
     * Something hanging or falling at this very moment, near one of the figures.
     */
    private static void expectDripping(ClientGameTestContext context)
    {
        context.runOnClient(minecraft ->
        {
            var drops = curse().dropPositions();
            if (drops.isEmpty())
                throw new AssertionError("Expected drops running off the figures, the whole time they stand there");
            for (var drop : drops)
            {
                boolean near = curse().figures().stream().anyMatch(figure -> Math.hypot(figure.x() - drop.x, figure.z() - drop.z) < DROP_REACH);
                if (!near)
                    throw new AssertionError("Expected every drop to come off a figure, got one at " + drop);
            }
        });
    }

    /**
     * From where the player stands the finger must show its length and head for them, never past them.
     */
    private static void expectFingersLeanIn(ClientGameTestContext context)
    {
        context.runOnClient(minecraft ->
        {
            for (var figure : curse().figures())
            {
                var lean = figure.fingerLean();
                if (lean.degrees() < FINGER_LEAN_MIN || lean.degrees() > FINGER_LEAN_MAX)
                    throw new AssertionError("Expected the finger a little off the line to the eye, got " + lean);
                if (lean.inward() <= 0 || lean.acrossTheLine() <= 0)
                    throw new AssertionError("Expected the finger to lean in toward the player, got " + lean);

                var ends = figure.fingerEnds();
                float focal = (float) (LARGE_HEIGHT / 2D / Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2));
                double distance = ends[0].distanceTo(minecraft.player.getEyePosition());
                double length = onScreenLength(minecraft.player.getEyePosition(), ends[0], ends[1], focal);
                double thickness = VigilFigureModel.FINGER_WIDTH * figure.scale() / 16 / distance * focal;
                LOGGER.info("Vigil finger at {} blocks: {} px long, {} px thick, leaning {} degrees", distance, length, thickness, lean.degrees());
                if (length < FINGER_PIXELS_LONG || thickness < FINGER_PIXELS_THICK)
                    throw new AssertionError("Expected the finger to show clearly at " + LARGE_HEIGHT + " pixels high, got it " + length + " long and " + thickness + " thick");
            }
        });
    }

    /**
     * How long a segment shows on a screen looking right at its start.
     */
    private static double onScreenLength(Vec3 eye, Vec3 from, Vec3 to, float focal)
    {
        var forward = from.subtract(eye).normalize();
        var toEnd = to.subtract(eye);
        double depth = toEnd.dot(forward);
        double across = toEnd.subtract(forward.scale(depth)).length();
        return across / depth * focal;
    }

    /**
     * On open ground: an arc in even steps centered on where the player looks and all of it on the screen at once, hovering over the ground.
     */
    private static void expectOnArc(ClientGameTestContext context)
    {
        context.runOnClient(minecraft ->
        {
            var figures = curse().figures();
            var player = minecraft.player.position();
            float look = minecraft.player.getYRot();
            double aspect = (double) minecraft.getWindow().getWidth() / minecraft.getWindow().getHeight();
            double halfView = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2) * aspect));
            if ((figures.size() - 1) / 2 * ARC_STEP + ARM_DEGREES > halfView)
                throw new AssertionError("Expected all the figures on the screen at once, the view only reaches " + halfView + " degrees to each side");
            for (int i = 0; i < figures.size(); i++)
            {
                var figure = figures.get(i);
                double dx = figure.x() - player.x;
                double dz = figure.z() - player.z;
                double distance = Math.hypot(dx, dz);
                if (distance < 7 || distance > 9)
                    throw new AssertionError("Expected figure " + i + " 7 to 9 blocks away, got " + distance);

                double angle = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-dx, dz)) - look);
                if (Math.abs(angle - (i - 3) * ARC_STEP) > 0.01)
                    throw new AssertionError("Expected figure " + i + " at " + (i - 3) * ARC_STEP + " degrees from the look, got " + angle);

                double hover = figure.y() - (GROUND + 1);
                if (hover < 1 || hover > 1.3)
                    throw new AssertionError("Expected figure " + i + " hovering 1 to 1.3 blocks over the ground, got " + hover);
                if (figure.floor() != GROUND + 1)
                    throw new AssertionError("Expected figure " + i + " to have found the ground, for its shade, got " + figure.floor());
            }
        });
    }

    private static VigilCurse curse()
    {
        if (!(CurseDirector.active() instanceof VigilCurse curse))
            throw new AssertionError("Expected the curse to run on the client, got " + CurseDirector.active());
        return curse;
    }

    private static void waitForAge(ClientGameTestContext context, int age)
    {
        context.waitFor(minecraft -> CurseDirector.active() instanceof VigilCurse curse && curse.age() >= age, CurseTimings.VIGIL_TICKS + 20);
    }

    private static Spot figure(int fromCenter)
    {
        var figure = curse().figures().get(3 + fromCenter);
        return new Spot(figure.x(), figure.y(), figure.z(), figure.scale());
    }

    private static void standAtOrigin(ClientGameTestContext context, TestServerContext server, float yaw)
    {
        server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 " + yaw + " -6");
        context.waitTicks(2);
    }

    /**
     * Places the player's feet there and turns their eyes toward the point.
     */
    private static void aimFrom(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z)
    {
        aimCamera(server, feetX, feetY, feetZ, x, y, z);
        context.waitTicks(3);
    }

    private static int entityCount(MinecraftServer minecraft)
    {
        int count = 0;
        for (var level : minecraft.getAllLevels())
        {
            for (var entity : level.getAllEntities())
            {
                if (entity != null)
                    count++;
            }
        }
        return count;
    }

    private static int messages(ClientGameTestContext context)
    {
        var texts = context.computeOnClient(VigilClientTest::chatTexts);
        return (int) texts.stream().filter(MESSAGE::equals).count();
    }

    private static void clearChat(Minecraft minecraft)
    {
        minecraft.gui.hud.getChat().clearMessages(false);
    }

    @SuppressWarnings("unchecked")
    private static List<String> chatTexts(Minecraft minecraft)
    {
        try
        {
            var field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            var all = (List<GuiMessage>) field.get(minecraft.gui.hud.getChat());
            return all.stream().map(GuiMessage::content).filter(Objects::nonNull).map(content -> content.getString()).toList();
        }
        catch (ReflectiveOperationException exception)
        {
            throw new AssertionError("Expected to read the chat", exception);
        }
    }

    private record Spot(double x, double y, double z, float scale)
    {
    }
}
