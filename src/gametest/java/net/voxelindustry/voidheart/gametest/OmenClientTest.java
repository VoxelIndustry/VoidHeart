package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundEvents;
import net.voxelindustry.voidheart.client.curse.CurseDirector;
import net.voxelindustry.voidheart.client.curse.OmenCurse;
import net.voxelindustry.voidheart.common.curse.CurseTimings;
import net.voxelindustry.voidheart.common.curse.Curses;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The sky must leave no trace of the curse behind, and the rain must never fall through a roof. How it all looks is for the screenshots to show.
 */
public class OmenClientTest implements FabricClientGameTest
{
    private static final int WIDTH  = 1280;
    private static final int HEIGHT = 720;

    private static final int FEET = -60;

    private static final int HUT_MIN_X = 4;
    private static final int HUT_MAX_X = 10;
    private static final int HUT_MIN_Z = -4;
    private static final int HUT_MAX_Z = 4;
    private static final int HUT_ROOF  = FEET + 5;

    private static final String LOOK_UP      = "90 -55";
    private static final String LOOK_TOP     = "90 -82";
    private static final String LOOK_AT_HUT  = "-90 -4";
    private static final String LOOK_AWAY    = "120 3";
    private static final String LOOK_DOWN    = "120 38";
    private static final String BESIDE_HUT   = "1.5 " + FEET + " 0.5 ";
    private static final String FACING_HUT   = "-7.5 " + FEET + " 0.5 ";
    private static final String INSIDE_HUT   = "8.5 " + (FEET + 1) + " 0.5 ";
    private static final String LOOK_OUT     = "90 -8";

    private static final int AGE_TIMEOUT = 600;

    private int fullRainPeak;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        var clouds = new AtomicReference<CloudStatus>();
        var cameraType = new AtomicReference<CameraType>();
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set midnight");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            server.runCommand("gamemode creative @a");
            server.runCommand("fill " + HUT_MIN_X + " " + FEET + " " + HUT_MIN_Z + " " + HUT_MAX_X + " " + HUT_ROOF + " " + HUT_MAX_Z + " stone_bricks hollow");
            server.runCommand("fill " + HUT_MIN_X + " " + (FEET + 1) + " -2 " + HUT_MIN_X + " " + (HUT_ROOF - 1) + " 2 air");
            server.runCommand("setblock " + (HUT_MAX_X - 1) + " " + (FEET + 1) + " 0 sea_lantern");
            look(context, server, LOOK_UP);
            context.runOnClient(minecraft ->
            {
                cameraType.set(minecraft.options.getCameraType());
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                clouds.set(minecraft.options.cloudStatus().get());
                // Clouds drift, which would get in the way of comparing the sky before and after
                minecraft.options.cloudStatus().set(CloudStatus.OFF);
            });
            context.getInput().pressKey(InputConstants.KEY_F1);
            context.waitTicks(20);

            var vanilla = shot(context, "omen-sky-0-vanilla");
            testFullCourse(context, server, vanilla);
            testCutShort(context, server, vanilla);

            context.getInput().pressKey(InputConstants.KEY_F1);
            context.runOnClient(minecraft ->
            {
                minecraft.options.cloudStatus().set(clouds.get());
                minecraft.options.setCameraType(cameraType.get());
            });
        }
    }

    private void testFullCourse(ClientGameTestContext context, TestServerContext server, Path vanilla)
    {
        var heard = Collections.synchronizedList(new ArrayList<Heard>());
        SoundEventListener listener = (SoundInstance sound, WeighedSoundEvents events, float range) -> heard.add(new Heard(sound));
        context.runOnClient(minecraft -> minecraft.getSoundManager().addListener(listener));

        server.runCommand("voidheart curse trigger omen @a");
        context.waitFor(minecraft -> CurseDirector.active() instanceof OmenCurse, AGE_TIMEOUT);
        expectSameSky(vanilla, shot(context, "omen-sky-1-first-frame"), "the first frame of the curse");

        waitForAge(context, 22);
        shot(context, "omen-sky-2-turning");
        waitForAge(context, 42);
        shot(context, "omen-sky-3-turned");
        for (int step = 0; step < 6; step++)
        {
            waitForAge(context, 50 + step * 2);
            shot(context, "omen-wobble-" + step);
        }
        waitForAge(context, 66);
        shot(context, "omen-sky-4-ripples-a");
        waitForAge(context, 80);
        shot(context, "omen-sky-4-ripples-b");
        waitForAge(context, 86);
        shot(context, "omen-sky-4-ripples-c");
        context.takeScreenshot(TestScreenshotOptions.of("omen-sky-4-ripples-c-1080").withSize(1920, 1080));
        context.takeScreenshot(TestScreenshotOptions.of("omen-sky-4-ripples-c-480").withSize(854, 480));
        sweep(context, server);

        look(context, server, LOOK_TOP);
        shot(context, "omen-sky-5-moon");
        look(context, server, LOOK_UP);
        waitForAge(context, OmenCurse.RAIN_START + 8);
        shot(context, "omen-sky-6-rain-begins");

        waitForAge(context, OmenCurse.RAIN_START + 30);
        look(context, server, LOOK_AWAY);
        shot(context, "omen-rain-horizon");
        server.runCommand("tp @a " + FACING_HUT + LOOK_AT_HUT);
        context.waitTicks(2);
        shot(context, "omen-rain-hut");
        server.runCommand("tp @a " + INSIDE_HUT + LOOK_OUT);
        context.waitTicks(2);
        shot(context, "omen-rain-from-inside");
        look(context, server, LOOK_DOWN);
        shot(context, "omen-rain-ground");
        look(context, server, LOOK_UP);
        shot(context, "omen-rain-up");
        expectNoneThroughRoof(context);

        waitForAge(context, OmenCurse.RAIN_END - 10);
        shot(context, "omen-rain-thinning");
        var duration = CurseTimings.OMEN_TICKS;
        waitForAge(context, duration - 40);
        shot(context, "omen-sky-7-after-rain");
        waitForAge(context, duration - 22);
        shot(context, "omen-sky-8-returning");
        waitForAge(context, duration - 2);
        expectSameSky(vanilla, shot(context, "omen-sky-9-last-frame"), "the last frame of the curse");

        context.waitTicks(8);
        expectOver(context, server, "the curse having run its course");
        expectSameSky(vanilla, shot(context, "omen-sky-10-after"), "the sky after the curse");
        context.waitTicks(30);
        expectParticles(context, 0, "the rain over");

        context.runOnClient(minecraft -> minecraft.getSoundManager().removeListener(listener));
        var patter = SoundEvents.WEATHER_RAIN.location().toString();
        var patters = heard.stream().filter(sound -> sound.id().equals(patter)).toList();
        if (patters.isEmpty() || patters.stream().anyMatch(sound -> sound.pitch() > 0.7F || !sound.relative()))
            throw new AssertionError("Expected a low patter around the player, got " + heard);
    }

    /**
     * Also runs on the lowest particle setting, which must thin out the rain.
     */
    private void testCutShort(ClientGameTestContext context, TestServerContext server, Path vanilla)
    {
        var particles = context.computeOnClient(minecraft -> minecraft.options.particles().get());
        context.runOnClient(minecraft -> minecraft.options.particles().set(ParticleStatus.MINIMAL));

        var heard = Collections.synchronizedList(new ArrayList<Heard>());
        SoundEventListener listener = (SoundInstance sound, WeighedSoundEvents events, float range) -> heard.add(new Heard(sound));
        context.runOnClient(minecraft -> minecraft.getSoundManager().addListener(listener));

        server.runCommand("voidheart curse trigger omen @a");
        context.waitFor(minecraft -> CurseDirector.active() instanceof OmenCurse, AGE_TIMEOUT);
        waitForAge(context, OmenCurse.RAIN_START + 40);

        int live = context.computeOnClient(minecraft -> curse(minecraft).liveParticles());
        if (live == 0 || live > fullRainPeak * 0.4F)
            throw new AssertionError("Expected a thin rain on the lowest particle setting, got " + live + " particles alive where a full one reached " + fullRainPeak);
        var patter = context.computeOnClient(minecraft -> curse(minecraft).patter());
        if (patter == null)
            throw new AssertionError("Expected the patter before cutting the curse short");
        shot(context, "omen-stop-before");

        server.runCommand("voidheart curse stop @a");
        context.waitTicks(3);
        expectOver(context, server, "a curse cut short");
        if (context.computeOnClient(minecraft -> minecraft.getSoundManager().isActive(patter)))
            throw new AssertionError("Expected the patter to stop with the curse");
        expectParticles(context, 0, "a curse cut short");
        expectSameSky(vanilla, shot(context, "omen-stop-after"), "the sky after a curse cut short");

        // The engine keeps reporting a looping sound to its listeners for as long as it plays
        var patterId = SoundEvents.WEATHER_RAIN.location().toString();
        heard.clear();
        context.waitTicks(30);
        expectParticles(context, 0, "a while after the curse was cut short");
        context.runOnClient(minecraft -> minecraft.getSoundManager().removeListener(listener));
        if (heard.stream().anyMatch(sound -> sound.id().equals(patterId)))
            throw new AssertionError("Expected no more patter after the curse was cut short, got " + heard);
        context.runOnClient(minecraft -> minecraft.options.particles().set(particles));
    }

    /**
     * With the sky standing still, moving the camera in small steps must leave the stars and the waves steady.
     */
    private void sweep(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("tick freeze");
        context.waitTicks(2);
        for (int step = 0; step < 8; step++)
        {
            server.runCommand("tp @a " + BESIDE_HUT + (90 + step * 0.35) + " " + (-55 - step * 0.2));
            context.waitTicks(2);
            shot(context, "omen-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
        context.waitTicks(1);
    }

    private void expectNoneThroughRoof(ClientGameTestContext context)
    {
        int above = 0;
        int peak = 0;
        for (int sample = 0; sample < 40; sample++)
        {
            context.waitTick();
            var drops = context.computeOnClient(minecraft -> curse(minecraft).dropPositions());
            peak = Math.max(peak, context.computeOnClient(minecraft -> curse(minecraft).liveParticles()));
            for (var drop : drops)
            {
                if (drop.x < HUT_MIN_X || drop.x >= HUT_MAX_X + 1 || drop.z < HUT_MIN_Z || drop.z >= HUT_MAX_Z + 1)
                    continue;
                if (drop.y < HUT_ROOF + 1)
                    throw new AssertionError("Expected no drop under the roof, got one at " + drop);
                above++;
            }
        }
        if (above == 0)
            throw new AssertionError("Expected drops falling on the roof");

        fullRainPeak = peak;
        int livePeak = peak;
        context.runOnClient(minecraft -> System.out.println("[Omen] peak of " + livePeak + " live particles of its own, engine holds " + minecraft.particleEngine.countParticles()));
        if (peak > 1400)
            throw new AssertionError("Expected a bounded rain, got " + peak + " particles alive");
        if (peak < 100)
            throw new AssertionError("Expected a real rain, got " + peak + " particles alive");
    }

    private static void expectOver(ClientGameTestContext context, TestServerContext server, String what)
    {
        if (context.computeOnClient(minecraft -> CurseDirector.active() != null))
            throw new AssertionError("Expected the client to let go after " + what);
        var running = server.computeOnServer(minecraft -> Curses.running(minecraft.getPlayerList().getPlayers().getFirst()));
        if (running != null)
            throw new AssertionError("Expected the server to let go after " + what + ", it runs " + running);
    }

    private static void expectParticles(ClientGameTestContext context, int expected, String what)
    {
        var count = context.computeOnClient(minecraft -> minecraft.particleEngine.countParticles());
        if (!count.endsWith("T " + expected))
            throw new AssertionError("Expected " + expected + " particles after " + what + ", the engine holds " + count);
    }

    private static void expectSameSky(Path expected, Path actual, String what)
    {
        int differing = differingPixels(expected, actual);
        System.out.println("[Omen] " + differing + " pixels differ from the vanilla sky in " + what);
        if (differing > 0)
            throw new AssertionError("Expected " + what + " to equal the vanilla sky, but " + differing + " pixels differ");
    }

    private static int differingPixels(Path first, Path second)
    {
        try (var imageA = NativeImage.read(Files.newInputStream(first));
             var imageB = NativeImage.read(Files.newInputStream(second)))
        {
            int differing = 0;
            int[] a = imageA.getPixelsABGR();
            int[] b = imageB.getPixelsABGR();
            for (int i = 0; i < a.length; i++)
            {
                if (a[i] != b[i])
                    differing++;
            }
            return differing;
        } catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static void waitForAge(ClientGameTestContext context, int age)
    {
        context.waitFor(minecraft -> CurseDirector.active() instanceof OmenCurse curse && curse.age() >= age, AGE_TIMEOUT);
    }

    private static OmenCurse curse(Minecraft minecraft)
    {
        return (OmenCurse) CurseDirector.active();
    }

    private static void look(ClientGameTestContext context, TestServerContext server, String yawAndPitch)
    {
        server.runCommand("tp @a " + BESIDE_HUT + yawAndPitch);
        context.waitTicks(2);
    }

    private static Path shot(ClientGameTestContext context, String name)
    {
        return context.takeScreenshot(TestScreenshotOptions.of(name).withSize(WIDTH, HEIGHT));
    }

    private record Heard(String id, float pitch, boolean relative)
    {
        Heard(SoundInstance sound)
        {
            this(sound.getIdentifier().toString(), sound.getPitch(), sound.isRelative());
        }
    }
}
