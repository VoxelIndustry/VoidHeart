package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.client.curse.CurseDirector;
import net.voxelindustry.voidheart.client.curse.RaptureCurse;
import net.voxelindustry.voidheart.common.curse.CurseKind;
import net.voxelindustry.voidheart.common.curse.Curses;
import net.voxelindustry.voidheart.common.curse.RaptureLift;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The sky opens its eyes and they find the player, the ground comes apart upward, a mouth opens overhead and really lifts them, then it all lets go.
 * Also that the lift lets go at once when cut short and leaves a levitation from anywhere else alone.
 */
public class RaptureClientTest implements FabricClientGameTest
{
    private static final int   GROUND = -61;
    private static final float YAW    = 0;

    private static final float LOOK_SKY  = -55;
    private static final float LOOK_UP   = -90;
    private static final float LOOK_DOWN = 38;

    private static final int LARGE_WIDTH  = 1280;
    private static final int LARGE_HEIGHT = 720;

    private static final double HEIGHT_TOLERANCE = 0.6;
    // Levitation settles a bit under 0.05 blocks a tick
    private static final double MOST_RISE_PER_TICK = 0.06;
    private static final int    LONGEST_CURSE      = 600;

    private static final int TICKS_RISING_AT_LEAST = 60;
    private static final int CHIPS_AT_MOST         = 920;
    // At the sky frame's size, for stars growing by a part of a pixel on the very first tick
    private static final int PIXELS_OFF_AT_START   = 30;

    private static final int SWEEP_FRAMES         = 8;
    private static final int RISE_SEQUENCE_FRAMES = 3;
    private static final int RISE_SEQUENCE_TICKS  = 2;
    private static final int RUMBLE_STOP_TICKS    = 60;
    private static final int CHIPS_GONE_TICKS     = 12;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set midnight");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 0 0");
            buildScene(server);
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();

            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);

            testFullCurse(context, server);
            testCutShort(context, server);
            testOtherLevitationSurvivesStop(context, server);
            testOtherLevitationSurvivesNaturalEnd(context, server);
            testByDayAndInRain(context, server);
            testCloudsPart(context, server);

            context.getInput().pressKey(InputConstants.KEY_F1);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }

    private void buildScene(TestServerContext server)
    {
        int top = GROUND + 7;
        server.runCommand("fill -10 " + (GROUND + 1) + " 7 10 " + top + " 7 stone_bricks");
        server.runCommand("fill -9 " + (GROUND + 1) + " 6 -9 " + (top - 2) + " -6 cobblestone");
        server.runCommand("fill 6 " + (GROUND + 1) + " -4 7 " + (top + 2) + " -3 oak_log");
        server.runCommand("fill -5 " + (GROUND + 1) + " -8 -4 " + (top + 1) + " -7 mossy_cobblestone");
        server.runCommand("fill 7 " + (GROUND + 1) + " 3 8 " + (top + 3) + " 4 bricks");
    }

    private void testFullCurse(ClientGameTestContext context, TestServerContext server)
    {
        var heard = Collections.synchronizedList(new ArrayList<SoundInstance>());
        SoundEventListener listener = (SoundInstance sound, WeighedSoundEvents events, float range) -> heard.add(sound);
        context.runOnClient(minecraft -> minecraft.getSoundManager().addListener(listener));

        look(context, YAW, LOOK_SKY);
        var vanilla = context.takeScreenshot("rapture-sky-vanilla");
        var vanillaLarge = context.takeScreenshot(TestScreenshotOptions.of("rapture-sky-vanilla-large").withSize(LARGE_WIDTH, LARGE_HEIGHT));
        double startY = server.computeOnServer(minecraft -> player(minecraft).getY());

        server.runCommand("voidheart curse trigger rapture @a");
        context.waitFor(minecraft -> CurseDirector.active() instanceof RaptureCurse, 40);
        expectRunning(server, CurseKind.RAPTURE, "the curse struck at night under the open sky");

        int height = context.computeOnClient(minecraft -> curse().height());
        int endTick = RaptureLift.LIFT_TICK + RaptureLift.liftTicks(height);
        VoidHeart.LOGGER.info("Rapture test: height " + height + ", the lift ends at tick " + endTick);
        if (height < RaptureLift.MIN_HEIGHT || height > RaptureLift.MAX_HEIGHT)
            throw new AssertionError("Expected a height of 6 to 12 blocks, got " + height);

        expectSameStars(vanilla, context.takeScreenshot("rapture-sky-start"), PIXELS_OFF_AT_START);

        waitForAge(context, 30);
        context.takeScreenshot("rapture-sky-half-open");
        context.takeScreenshot(TestScreenshotOptions.of("rapture-sky-half-open-large").withSize(LARGE_WIDTH, LARGE_HEIGHT));

        waitForAge(context, 64);
        context.takeScreenshot("rapture-sky-wandering");
        context.takeScreenshot(TestScreenshotOptions.of("rapture-sky-wandering-large").withSize(LARGE_WIDTH, LARGE_HEIGHT));

        waitForAge(context, 80);
        context.takeScreenshot("rapture-sky-finding");

        waitForAge(context, 96);
        context.takeScreenshot("rapture-sky-staring");
        context.takeScreenshot(TestScreenshotOptions.of("rapture-sky-staring-large").withSize(LARGE_WIDTH, LARGE_HEIGHT));
        expectNoRumble(heard, "before the eyes have all found the player");

        waitForAge(context, 128);
        captureRisers(context, "early");
        for (int frame = 0; frame < RISE_SEQUENCE_FRAMES; frame++)
        {
            look(context, YAW, -4);
            context.takeScreenshot("rapture-rise-sequence-" + frame);
            context.waitTicks(RISE_SEQUENCE_TICKS);
        }
        expectRumbleHolding(context, "as the ground starts coming apart");
        if (context.computeOnClient(minecraft -> curse().liveRisers()) <= 0)
            throw new AssertionError("Expected block chips rising around the player");

        waitForAge(context, 144);
        look(context, YAW, LOOK_UP);
        context.takeScreenshot("rapture-zenith-seam");
        context.takeScreenshot(TestScreenshotOptions.of("rapture-zenith-seam-large").withSize(LARGE_WIDTH, LARGE_HEIGHT));

        var heights = new ArrayList<HeightSample>();
        waitForAgeTracking(context, server, 152, heights);
        context.takeScreenshot("rapture-zenith-seam-across");
        context.takeScreenshot(TestScreenshotOptions.of("rapture-zenith-seam-across-large").withSize(LARGE_WIDTH, LARGE_HEIGHT));

        waitForAgeTracking(context, server, 163, heights);
        context.takeScreenshot("rapture-zenith-half");
        context.takeScreenshot(TestScreenshotOptions.of("rapture-zenith-half-large").withSize(LARGE_WIDTH, LARGE_HEIGHT));

        waitForAgeTracking(context, server, RaptureLift.MOUTH_TICK + RaptureLift.MOUTH_OPEN_TICKS, heights);
        context.takeScreenshot("rapture-zenith-open");
        context.takeScreenshot(TestScreenshotOptions.of("rapture-zenith-open-large").withSize(LARGE_WIDTH, LARGE_HEIGHT));
        look(context, YAW, LOOK_SKY - 12);
        context.takeScreenshot("rapture-zenith-steep");
        context.takeScreenshot(TestScreenshotOptions.of("rapture-zenith-steep-large").withSize(LARGE_WIDTH, LARGE_HEIGHT));
        look(context, YAW, LOOK_SKY);
        context.takeScreenshot("rapture-zenith-from-sky-look");
        look(context, YAW + 90, LOOK_UP);
        context.takeScreenshot("rapture-zenith-turned");

        sweep(context, "sky");
        sweep(context, "zenith");

        waitForAgeTracking(context, server, Math.min(RaptureLift.LIFT_TICK + 90, endTick - 10), heights);
        captureRisers(context, "lifted");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        look(context, YAW, 8);
        context.takeScreenshot("rapture-lift-third");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.takeScreenshot("rapture-lift-third-front");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        expectRumbleHolding(context, "during the lift");
        var rumble = context.computeOnClient(minecraft -> List.copyOf(curse().rumble()));

        waitForAgeTracking(context, server, endTick - 2, heights);
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        look(context, YAW, LOOK_DOWN);
        context.takeScreenshot("rapture-lift-peak-third");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        look(context, YAW, LOOK_SKY / 2);
        context.takeScreenshot("rapture-lift-peak-third-front");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        look(context, YAW, LOOK_UP);
        context.takeScreenshot("rapture-zenith-latest");
        context.takeScreenshot(TestScreenshotOptions.of("rapture-zenith-latest-large").withSize(LARGE_WIDTH, LARGE_HEIGHT));

        waitForAgeTracking(context, server, endTick + RaptureLift.MOUTH_SNAP_TICKS / 2, heights);
        context.takeScreenshot("rapture-zenith-snapping");
        expectRumbleStopped(context, rumble);

        waitForAgeTracking(context, server, endTick + RaptureLift.MOUTH_SNAP_TICKS + 2, heights);
        context.takeScreenshot("rapture-zenith-shut");
        context.takeScreenshot(TestScreenshotOptions.of("rapture-zenith-shut-large").withSize(LARGE_WIDTH, LARGE_HEIGHT));

        int peakRisers = context.computeOnClient(minecraft -> curse().peakRisers());
        int groundRisers = context.computeOnClient(minecraft -> curse().risersFromGround());
        int wallRisers = context.computeOnClient(minecraft -> curse().risersFromWalls());
        VoidHeart.LOGGER.info("Rapture test: at most " + peakRisers + " block chips alive at once, spawned " + groundRisers + " from the ground and " + wallRisers + " from walls");
        if (wallRisers <= 0 || groundRisers <= 0)
            throw new AssertionError("Expected chips from walls and the ground, got " + wallRisers + " and " + groundRisers);
        if (peakRisers > CHIPS_AT_MOST)
            throw new AssertionError("Expected at most " + CHIPS_AT_MOST + " chips at once, got " + peakRisers);

        context.takeScreenshot("rapture-sky-closing");
        waitForNoCurse(context);
        expectRunning(server, null, "the curse over");

        waitForLanding(context);
        double risen = heights.stream().mapToDouble(HeightSample::y).max().orElse(startY) - startY;
        VoidHeart.LOGGER.info("Rapture test: rose " + risen + " blocks for a wanted height of " + height);
        if (Math.abs(risen - height) > HEIGHT_TOLERANCE)
            throw new AssertionError("Expected to rise " + height + " blocks, rose " + risen);
        expectGradual(heights);

        double landedY = server.computeOnServer(minecraft -> player(minecraft).getY());
        if (Math.abs(landedY - startY) > 0.01)
            throw new AssertionError("Expected to come back down to " + startY + ", stands at " + landedY);
        expectLevitating(context, server, false, "after the curse");
        float health = server.computeOnServer(minecraft -> player(minecraft).getHealth());
        float full = server.computeOnServer(minecraft -> player(minecraft).getMaxHealth());
        VoidHeart.LOGGER.info("Rapture test: landed with " + health + " of " + full + " health");
        if (health >= full)
            throw new AssertionError("Expected normal fall damage from " + height + " blocks, still has " + health);

        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        look(context, YAW, 8);
        context.takeScreenshot("rapture-landed-third");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

        context.runOnClient(minecraft -> minecraft.getSoundManager().removeListener(listener));
        expectThunderLow(heard);

        // The last chips shrink away just after the curse lets go of them
        context.waitTicks(CHIPS_GONE_TICKS);
        look(context, YAW, LOOK_SKY);
        context.takeScreenshot("rapture-sky-after");
        var afterLarge = context.takeScreenshot(TestScreenshotOptions.of("rapture-sky-after-large").withSize(LARGE_WIDTH, LARGE_HEIGHT));
        expectSameStars(vanillaLarge, afterLarge, 0);
        heal(server);
    }

    private void captureRisers(ClientGameTestContext context, String moment)
    {
        look(context, YAW, LOOK_DOWN);
        context.takeScreenshot("rapture-rise-" + moment + "-ground");
        look(context, YAW, -4);
        context.takeScreenshot("rapture-rise-" + moment + "-wall");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.takeScreenshot("rapture-rise-" + moment + "-wall-third");
        look(context, YAW, LOOK_DOWN);
        context.takeScreenshot("rapture-rise-" + moment + "-ground-third");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
    }

    /**
     * The camera turns in small steps against open sky, with the mouth or only the eyes in frame. No tick passes between screenshots, so the curse holds still
     * without a {@code tick freeze}, under which the player would go on rising while the lift doesn't count.
     */
    private void sweep(ClientGameTestContext context, String part)
    {
        for (int step = 0; step < SWEEP_FRAMES; step++)
        {
            if (part.equals("zenith"))
                look(context, YAW + step * 0.6F, LOOK_UP + 8 + step * 0.4F);
            else
                look(context, YAW + 120 + step * 0.6F, LOOK_SKY + 10 + step * 0.4F);
            context.takeScreenshot(part + "-sweep-" + step);
        }
    }

    private void testCutShort(ClientGameTestContext context, TestServerContext server)
    {
        heal(server);
        double startY = server.computeOnServer(minecraft -> player(minecraft).getY());

        server.runCommand("voidheart curse trigger rapture @a");
        context.waitFor(minecraft -> CurseDirector.active() instanceof RaptureCurse, 40);
        waitForAge(context, RaptureLift.LIFT_TICK + 30);

        expectLevitating(context, server, true, "during the lift");
        boolean hidden = context.computeOnClient(minecraft ->
        {
            var levitation = minecraft.player.getEffect(MobEffects.LEVITATION);
            return !levitation.isVisible() && !levitation.showIcon();
        });
        if (!hidden)
            throw new AssertionError("Expected the lift to show no particles or icon");
        double liftedY = server.computeOnServer(minecraft -> player(minecraft).getY());
        if (liftedY <= startY + 0.5)
            throw new AssertionError("Expected to be off the ground 30 ticks into the lift, " + startY + " -> " + liftedY);
        var rumble = context.computeOnClient(minecraft -> List.copyOf(curse().rumble()));
        look(context, YAW, LOOK_UP);

        server.runCommand("voidheart curse stop @a");
        boolean goneAtOnce = server.computeOnServer(minecraft -> !player(minecraft).hasEffect(MobEffects.LEVITATION));
        if (!goneAtOnce)
            throw new AssertionError("Expected the stop to remove the levitation at once");
        context.waitTicks(2);

        expectRunning(server, null, "a stopped curse");
        if (context.computeOnClient(minecraft -> CurseDirector.active() != null))
            throw new AssertionError("Expected the client to let go of the stopped curse");
        expectLevitating(context, server, false, "after the stop");
        expectRumbleStopped(context, rumble);
        context.takeScreenshot("rapture-stopped-sky");

        waitForLanding(context);
        heal(server);
    }

    private void testOtherLevitationSurvivesStop(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("voidheart curse trigger rapture @a");
        context.waitFor(minecraft -> CurseDirector.active() instanceof RaptureCurse, 40);
        waitForAge(context, RaptureLift.LIFT_TICK + 10);

        // As a shulker's bullet would, on top of the curse's own
        server.runCommand("effect give @a levitation 30 0");
        context.waitTicks(2);
        server.runCommand("voidheart curse stop @a");
        context.waitTicks(2);

        expectRunning(server, null, "a stopped curse");
        expectLevitating(context, server, true, "with a levitation given during the lift, after the stop");

        server.runCommand("effect clear @a levitation");
        server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5");
        context.waitTicks(5);
        expectLevitating(context, server, false, "once cleared");
        heal(server);
    }

    private void testOtherLevitationSurvivesNaturalEnd(ClientGameTestContext context, TestServerContext server)
    {
        // Already there when the lift comes around, and it outlasts the curse
        server.runCommand("effect give @a levitation 120 0");
        server.runCommand("voidheart curse trigger rapture @a");
        context.waitFor(minecraft -> CurseDirector.active() instanceof RaptureCurse, 40);
        waitForNoCurse(context);
        context.waitTicks(2);

        expectRunning(server, null, "the curse over");
        expectLevitating(context, server, true, "with a levitation from elsewhere, after the curse's natural end");
        int left = server.computeOnServer(minecraft -> player(minecraft).getEffect(MobEffects.LEVITATION).getDuration());
        if (left <= 0)
            throw new AssertionError("Expected the other levitation to keep its time, has " + left);

        server.runCommand("effect clear @a levitation");
        server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5");
        context.waitTicks(5);
        heal(server);
    }

    /**
     * The curse doesn't depend on vanilla star brightness: forced by day and under rain, the eyes keep their own.
     */
    private void testByDayAndInRain(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("time set noon");
        look(context, YAW, LOOK_SKY);
        context.waitTicks(5);
        context.takeScreenshot("rapture-day-before");

        server.runCommand("voidheart curse force rapture @a");
        context.waitFor(minecraft -> CurseDirector.active() instanceof RaptureCurse, 40);
        waitForAge(context, 96);
        context.takeScreenshot("rapture-day-staring");

        server.runCommand("voidheart curse stop @a");
        server.runCommand("time set midnight");
        server.runCommand("weather rain");
        context.waitTicks(100);
        context.takeScreenshot("rapture-rain-before");

        server.runCommand("voidheart curse force rapture @a");
        context.waitFor(minecraft -> CurseDirector.active() instanceof RaptureCurse, 40);
        waitForAge(context, 96);
        context.takeScreenshot("rapture-rain-staring");

        server.runCommand("voidheart curse stop @a");
        server.runCommand("weather clear");
        context.waitTicks(5);
    }

    /**
     * Clouds are off in these tests by default, but not for players: they must not veil the sky, and must come back.
     */
    private void testCloudsPart(ClientGameTestContext context, TestServerContext server)
    {
        context.runOnClient(minecraft -> minecraft.options.cloudStatus().set(CloudStatus.FANCY));
        look(context, YAW, LOOK_SKY);
        context.waitTicks(5);
        context.takeScreenshot("rapture-clouds-before");

        server.runCommand("voidheart curse trigger rapture @a");
        context.waitFor(minecraft -> CurseDirector.active() instanceof RaptureCurse, 40);
        waitForAge(context, 25);
        context.takeScreenshot("rapture-clouds-parting");
        waitForAge(context, 96);
        context.takeScreenshot("rapture-clouds-staring");

        server.runCommand("voidheart curse stop @a");
        context.waitTicks(CHIPS_GONE_TICKS);
        context.takeScreenshot("rapture-clouds-after");
        context.runOnClient(minecraft -> minecraft.options.cloudStatus().set(CloudStatus.OFF));
    }

    private static void look(ClientGameTestContext context, float yaw, float pitch)
    {
        context.runOnClient(minecraft ->
        {
            var player = minecraft.player;
            player.setYRot(yaw);
            player.setXRot(pitch);
            player.yRotO = yaw;
            player.xRotO = pitch;
            player.setYHeadRot(yaw);
            player.yHeadRotO = yaw;
            player.setYBodyRot(yaw);
            player.yBodyRotO = yaw;
        });
    }

    private static RaptureCurse curse()
    {
        if (CurseDirector.active() instanceof RaptureCurse curse)
            return curse;
        throw new AssertionError("Expected the curse to be running on the client");
    }

    private static void waitForAge(ClientGameTestContext context, int age)
    {
        context.waitFor(minecraft -> CurseDirector.active() == null || CurseDirector.active().age() >= age, LONGEST_CURSE);
    }

    private static void waitForAgeTracking(ClientGameTestContext context, TestServerContext server, int age, List<HeightSample> heights)
    {
        int endTick = context.computeOnClient(minecraft -> RaptureLift.LIFT_TICK + RaptureLift.liftTicks(curse().height()));
        while (context.computeOnClient(minecraft -> CurseDirector.active() != null && CurseDirector.active().age() < age))
        {
            int now = context.computeOnClient(minecraft -> CurseDirector.active().age());
            heights.add(new HeightSample(now, server.computeOnServer(minecraft -> player(minecraft).getY())));
            // Sustained for the whole rest of the curse, with no gaps
            if (now >= RaptureLift.RUMBLE_TICK && now < endTick)
                expectRumbleHolding(context, "at tick " + now);
            context.waitTick();
        }
    }

    private static void waitForNoCurse(ClientGameTestContext context)
    {
        context.waitFor(minecraft -> CurseDirector.active() == null, LONGEST_CURSE);
    }

    private static void waitForLanding(ClientGameTestContext context)
    {
        context.waitFor(minecraft -> minecraft.player.onGround(), LONGEST_CURSE);
        context.waitTicks(3);
    }

    private static void heal(TestServerContext server)
    {
        server.runOnServer(minecraft -> player(minecraft).setHealth(player(minecraft).getMaxHealth()));
    }

    /**
     * Compared per tick of the curse, because the game catches up on the real time a batch of screenshots took, with several ticks in a row.
     */
    private static void expectGradual(List<HeightSample> heights)
    {
        int rising = 0;
        for (int i = 1; i < heights.size(); i++)
        {
            int ticks = Math.max(1, heights.get(i).age() - heights.get(i - 1).age());
            double rise = (heights.get(i).y() - heights.get(i - 1).y()) / ticks;
            if (rise > MOST_RISE_PER_TICK)
                throw new AssertionError("Expected a slow rise, went up " + rise + " per tick before tick " + heights.get(i).age());
            if (rise > 0)
                rising++;
        }
        if (rising < TICKS_RISING_AT_LEAST)
            throw new AssertionError("Expected to rise over many ticks, rose over " + rising);
    }

    private static void expectLevitating(ClientGameTestContext context, TestServerContext server, boolean expected, String when)
    {
        boolean onServer = server.computeOnServer(minecraft -> player(minecraft).hasEffect(MobEffects.LEVITATION));
        boolean onClient = context.computeOnClient(minecraft -> minecraft.player.hasEffect(MobEffects.LEVITATION));
        if (onServer != expected || onClient != expected)
            throw new AssertionError("Expected levitation " + (expected ? "present" : "gone") + " " + when + ", server " + onServer + ", client " + onClient);
    }

    private static void expectNoRumble(List<SoundInstance> heard, String when)
    {
        synchronized (heard)
        {
            for (var sound : heard)
            {
                if (isRumble(sound))
                    throw new AssertionError("Expected no rumble " + when + ", got " + sound);
            }
        }
    }

    private static void expectRumbleHolding(ClientGameTestContext context, String when)
    {
        boolean holding = context.computeOnClient(minecraft -> curse().rumble().stream().filter(SoundInstance::isLooping).count() >= 2
                && curse().rumble().stream().filter(SoundInstance::isLooping).allMatch(minecraft.getSoundManager()::isActive));
        if (!holding)
            throw new AssertionError("Expected the looping layers of the rumble to be playing " + when);
    }

    private static void expectRumbleStopped(ClientGameTestContext context, List<SoundInstance> rumble)
    {
        if (rumble.isEmpty())
            throw new AssertionError("Expected some rumble to have been playing");
        boolean cut = context.computeOnClient(minecraft -> rumble.stream().allMatch(sound ->
                !minecraft.getSoundManager().isActive(sound) || sound instanceof TickableSoundInstance tickable && tickable.isStopped()));
        if (!cut)
            throw new AssertionError("Expected the rumble cut at once, among " + rumble);
        // The sound thread lets go a moment later, and ticks run far faster than real time here
        context.waitFor(minecraft -> rumble.stream().noneMatch(minecraft.getSoundManager()::isActive), RUMBLE_STOP_TICKS);
    }

    private static void expectThunderLow(List<SoundInstance> heard)
    {
        synchronized (heard)
        {
            var thunder = heard.stream().filter(sound -> sound.getIdentifier().equals(SoundEvents.LIGHTNING_BOLT_THUNDER.location())).toList();
            if (thunder.isEmpty())
                throw new AssertionError("Expected thunder in the rumble, got " + heard);
            if (heard.stream().filter(RaptureClientTest::isRumble).anyMatch(sound -> sound.getPitch() > 0.51F || !sound.isRelative()))
                throw new AssertionError("Expected the whole rumble as low as it goes from inside the player's head");
        }
    }

    private static boolean isRumble(SoundInstance sound)
    {
        return sound.getIdentifier().equals(SoundEvents.LIGHTNING_BOLT_THUNDER.location())
                || sound.getIdentifier().equals(SoundEvents.AMBIENT_BASALT_DELTAS_LOOP.value().location())
                || sound.getIdentifier().equals(SoundEvents.BEACON_AMBIENT.location());
    }

    /**
     * The sky must be the vanilla one, down to the pixel, in the upper half of the frame where nothing else stands.
     */
    private static void expectSameStars(Path before, Path after, int tolerated)
    {
        try (var a = NativeImage.read(Files.newInputStream(before)); var b = NativeImage.read(Files.newInputStream(after)))
        {
            int different = 0;
            for (int y = 0; y < a.getHeight() / 2; y++)
            {
                for (int x = 0; x < a.getWidth(); x++)
                {
                    if (a.getPixel(x, y) != b.getPixel(x, y))
                        different++;
                }
            }
            VoidHeart.LOGGER.info("Rapture test: " + different + " sky pixels of " + after.getFileName() + " differ from " + before.getFileName());
            if (different > tolerated)
                throw new AssertionError("Expected the same sky in " + after.getFileName() + " as in " + before.getFileName() + ", " + different + " pixels differ");
        } catch (IOException e)
        {
            throw new AssertionError("Could not read the sky captures", e);
        }
    }

    private static void expectRunning(TestServerContext server, CurseKind expected, String what)
    {
        var actual = server.computeOnServer(minecraft -> Curses.running(player(minecraft)));
        if (actual != expected)
            throw new AssertionError("Expected " + what + ", the server runs " + actual);
    }

    private static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
    }

    private record HeightSample(int age, double y)
    {
    }
}
