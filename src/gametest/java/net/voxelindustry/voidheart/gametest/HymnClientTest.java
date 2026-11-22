package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundEvents;
import net.voxelindustry.voidheart.client.curse.CurseDirector;
import net.voxelindustry.voidheart.client.curse.HymnCurse;
import net.voxelindustry.voidheart.client.curse.HymnTimeline;
import net.voxelindustry.voidheart.common.curse.CurseKind;
import net.voxelindustry.voidheart.common.curse.CurseTimings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The curse plays out on the afflicted player's HUD alone, from every edge of the screen, to a heart beating faster and faster, then snaps back.
 */
public class HymnClientTest implements FabricClientGameTest
{
    private static final int GROUND = -61;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("weather clear");
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 0 10");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(20);

            testPlaysOut(context, server);
            testStop(context, server);
        }
    }

    private void testPlaysOut(ClientGameTestContext context, TestServerContext server)
    {
        var heard = Collections.synchronizedList(new ArrayList<Heard>());
        SoundEventListener listener = (SoundInstance sound, WeighedSoundEvents events, float range) -> heard.add(new Heard(sound, ageNow()));
        context.runOnClient(minecraft -> minecraft.getSoundManager().addListener(listener));
        context.takeScreenshot("hymn-00-before");

        server.runCommand("voidheart curse trigger hymn @a");
        waitForAge(context, 1);
        expectRunning(server, CurseKind.HYMN, "the curse just struck");

        for (int age : new int[] {6, 14, 24, 40})
        {
            waitForAge(context, age);
            context.takeScreenshot("hymn-01-crawling-" + age);
        }
        waitForAge(context, 60);
        context.takeScreenshot("hymn-02-held-a");
        int drawn = context.computeOnClient(minecraft -> curse().drawnLastFrame());
        if (drawn < 1000)
            throw new AssertionError("Expected the flesh drawn all around the screen, got " + drawn + " quads");
        context.waitTicks(3);
        context.takeScreenshot("hymn-02-held-b");
        // The flesh is laid out again for a screen of another shape
        int scale = context.computeOnClient(minecraft -> minecraft.options.guiScale().get());
        resize(context, 1280, 720, 3);
        context.takeScreenshot(TestScreenshotOptions.of("hymn-02-held-720").withSize(1280, 720));
        resize(context, 854, 480, scale);
        waitForAge(context, 85);
        context.takeScreenshot("hymn-03-late");
        waitForAge(context, HymnTimeline.RETRACT_FROM + 2);
        context.takeScreenshot("hymn-04-snapping-back");
        waitForAge(context, HymnTimeline.RETRACT_FROM + 5);
        context.takeScreenshot("hymn-05-nearly-gone");

        context.waitFor(minecraft -> CurseDirector.active() == null, CurseTimings.HYMN_TICKS);
        context.waitTicks(2);
        context.takeScreenshot("hymn-06-after");
        context.runOnClient(minecraft -> minecraft.getSoundManager().removeListener(listener));
        expectRunning(server, null, "the curse over");

        var beats = new ArrayList<Heard>();
        synchronized (heard)
        {
            for (var sound : heard)
                if (sound.id().equals(SoundEvents.WARDEN_HEARTBEAT.location().toString()))
                    beats.add(sound);
        }
        int expected = HymnTimeline.beatTicks().length;
        if (beats.size() != expected)
            throw new AssertionError("Expected " + expected + " heartbeats, got " + heard);
        if (beats.stream().anyMatch(beat -> !beat.relative()))
            throw new AssertionError("Expected the heart to beat inside the player's head, got " + beats);
        if (beats.getLast().pitch() <= beats.getFirst().pitch())
            throw new AssertionError("Expected the heart to rise as it quickens, got " + beats);
    }

    private void testStop(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("voidheart curse trigger hymn @a");
        waitForAge(context, 50);
        server.runCommand("voidheart curse stop @a");
        context.waitTicks(3);
        expectRunning(server, null, "a stopped curse");
        if (context.computeOnClient(minecraft -> CurseDirector.active() != null))
            throw new AssertionError("Expected the client to let go of the stopped curse");
        context.takeScreenshot("hymn-07-stopped");
    }

    private static int ageNow()
    {
        return CurseDirector.active() instanceof HymnCurse curse ? curse.age() : -1;
    }

    private static HymnCurse curse()
    {
        if (!(CurseDirector.active() instanceof HymnCurse curse))
            throw new AssertionError("Expected the curse to run on the client, got " + CurseDirector.active());
        return curse;
    }

    private static void waitForAge(ClientGameTestContext context, int age)
    {
        context.waitFor(minecraft -> CurseDirector.active() instanceof HymnCurse curse && curse.age() >= age, CurseTimings.HYMN_TICKS + 20);
    }

    private record Heard(String id, float pitch, boolean relative, int age)
    {
        Heard(SoundInstance sound, int age)
        {
            this(sound.getIdentifier().toString(), sound.getPitch(), sound.isRelative(), age);
        }
    }
}
